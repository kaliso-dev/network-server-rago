// Lobby visibility test bots for RagoLobby (see tools/lobby-check/README.md, run-arena-test.sh).
//
// A player who joins the server must be seen by those already in the lobby, and must see them. A
// 1.8.9 client draws no body for a player it does not know yet: the body (named_entity_spawn) must
// come after the tab list entry (player_info add_player), or only the line above the name
// (HeadLines, "Unranked") is left in the air. It draws no body either for a player in spectator
// mode (game mode 3 in the tab list) or flagged invisible (metadata 0, bit 0x20).
//
// Each bot records every packet of the server about the players, from its very first one; after
// each arrival below, both bots check the other: the body came, after the tab list entry, and it
// is neither a spectator nor invisible (mineflayer, which drops a body that comes first as the
// 1.8.9 client does, must have it too).
//   1. RagoTrois waits at the spawn; RagoQuatre joins for the first time;
//   2. RagoQuatre leaves the server from the lobby, and comes back;
//   3. RagoQuatre leaves the server in the Block edit training, and comes back;
//   4. RagoQuatre leaves the server in a duel (Block placement 1v1), and comes back;
//   5. RagoTrois leaves the server from the lobby, and comes back while RagoQuatre waits at the spawn.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { createHash } from 'node:crypto'
import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 6 * 60 * 1000
/** The packets about players a bot records. */
const RECORDED = new Set(['player_info', 'named_entity_spawn', 'entity_metadata', 'entity_destroy', 'respawn'])
const SPECTATOR = 3
const INVISIBLE = 0x20

let finishing = false

function abort (error) {
  console.error(`[visibility-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[visibility-bots] ${text}`)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`)
    await sleep(50)
  }
}

/** The uuid of a player in offline mode, as the server makes it: version 3, of "OfflinePlayer:<name>". */
function offlineUuid (name) {
  const md5 = createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest()
  md5[6] = (md5[6] & 0x0f) | 0x30
  md5[8] = (md5[8] & 0x3f) | 0x80
  const hex = md5.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/** Every chat line a bot received, so that a test can wait for a line sent a moment ago. */
const chats = new Map()

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    const started = Date.now()
    // from the very first packet: what the client learns of the others before its spawn counts
    bot.record = []
    bot._client.on('packet', (data, meta) => {
      if (RECORDED.has(meta.name)) bot.record.push({ t: Date.now() - started, name: meta.name, data })
    })
    chats.set(username, [])
    bot.firstMenu = null
    bot.on('windowOpen', (window) => {
      const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
      if (title.includes('What do you want to do?')) bot.firstMenu = window
    })
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.on('messagestr', (message) => {
      chats.get(username).push(message)
      log(`${username} < ${message}`)
    })
    bot.once('spawn', () => {
      bot.removeListener('error', fail)
      bot.removeListener('kicked', fail)
      bot.on('error', (error) => abort(new Error(`${username}: ${error.message}`)))
      bot.on('kicked', (reason) => abort(new Error(`${username} was kicked: ${JSON.stringify(reason)}`)))
      bot.on('end', (reason) => {
        if (!finishing && !bot.leaving) abort(new Error(`${username} disconnected: ${reason}`))
      })
      resolve(bot)
    })
  })
}

async function leave (bot) {
  bot.leaving = true
  const gone = new Promise((resolve) => bot.once('end', resolve))
  bot.quit()
  await gone
  // the server saves the player and tells the others
  await sleep(1500)
}

async function chatLine (bot, pattern, timeoutMs, from = 0) {
  const lines = chats.get(bot.username)
  let found = null
  await waitFor(() => {
    found = lines.slice(from).find((line) => pattern.test(line)) ?? null
    return found != null
  }, timeoutMs, `${bot.username} to read ${pattern}; chat: ${JSON.stringify(lines.slice(from))}`)
  return found
}

function mark (bot) {
  return chats.get(bot.username).length
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
}

/** The first arrival's menu ("What do you want to do?"), closed as Esc does. */
async function closeFirstMenu (bot) {
  await waitFor(() => bot.firstMenu != null, 8000, `${bot.username}: the menu of the first arrival`)
  bot.closeWindow(bot.firstMenu)
  await sleep(200)
}

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 15000,
    `${bot.username} in the lobby (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
}

/** The flags of an entity (metadata 0), or null if the metadata does not say. */
function flagsOf (metadata) {
  const flags = (metadata ?? []).find((entry) => entry.key === 0)
  return flags == null ? null : Number(flags.value)
}

/**
 * What the bot's client knows of this player, read from every packet it received: whether the
 * player is in its tab list and in which game mode, their body (entity id, flags), and each body
 * sent from the packet `since` on, with whether the tab list knew the player then.
 */
function knowledge (bot, name, since) {
  const uuid = offlineUuid(name)
  const state = { known: false, gamemode: null, entityId: null, flags: 0, bodies: [], story: [] }
  bot.record.forEach((event, i) => {
    const { data } = event
    let about = false
    if (event.name === 'player_info') {
      for (const entry of data.data) {
        if (entry.uuid !== uuid) continue
        about = true
        if (data.action === 'add_player') {
          state.known = true
          state.gamemode = entry.gamemode
        } else if (data.action === 'remove_player') {
          state.known = false
        } else if (data.action === 'update_game_mode') {
          state.gamemode = entry.gamemode
        }
      }
    } else if (event.name === 'named_entity_spawn' && data.playerUUID === uuid) {
      about = true
      state.entityId = data.entityId
      state.flags = flagsOf(data.metadata) ?? 0
      if (i >= since) state.bodies.push({ t: event.t, known: state.known })
    } else if (event.name === 'entity_metadata' && state.entityId != null && data.entityId === state.entityId) {
      const flags = flagsOf(data.metadata)
      if (flags != null) {
        about = true
        state.flags = flags
      }
    } else if (event.name === 'entity_destroy' && state.entityId != null && data.entityIds.includes(state.entityId)) {
      about = true
      state.entityId = null
    } else if (event.name === 'respawn') {
      // another world: the client forgets every entity
      about = true
      state.entityId = null
    }
    if (about && i >= since) {
      state.story.push(`${event.t} ms ${event.name}${data.action ? ' ' + data.action : ''}`
        + (event.name === 'player_info' ? ` (mode ${data.data.find((e) => e.uuid === uuid)?.gamemode ?? '-'})` : '')
        + (event.name === 'named_entity_spawn' || event.name === 'entity_metadata' ? ` (flags ${flagsOf(data.metadata)})` : ''))
    }
  })
  return state
}

/** The bot sees this player standing near it: a body after the tab list entry, not a spectator, not invisible. */
async function expectSees (bot, other, since, what) {
  const name = other.username
  const problem = () => {
    const state = knowledge(bot, name, since)
    if (state.bodies.length === 0 || state.entityId == null) return 'the body never came'
    if (state.bodies.some((body) => !body.known)) return 'the body came before the tab list entry (a 1.8.9 client draws nothing)'
    if (!state.known) return 'not in the tab list'
    if (state.gamemode === SPECTATOR) return 'in spectator mode in the tab list'
    if ((state.flags & INVISIBLE) !== 0) return 'flagged invisible'
    if (bot.players[name]?.entity == null) return 'mineflayer has no body for it'
    return null
  }
  try {
    await waitFor(() => problem() == null, 8000, `${bot.username} to see ${name}`)
  } catch (error) {
    const state = knowledge(bot, name, since)
    throw new Error(`${what}: ${bot.username} does not see ${name}: ${problem()}; packets since: ${state.story.join(', ') || 'none'}`)
  }
  // a second later, still seen (nothing took the body away just after)
  await sleep(1000)
  const late = problem()
  if (late != null) {
    throw new Error(`${what}: ${bot.username} saw ${name}, then not: ${late}; packets since: ${knowledge(bot, name, since).story.join(', ')}`)
  }
  log(`${what}: ${bot.username} sees ${name}`)
}

/** Both see each other, after `joiner` arrived; `since`: where the watcher's record stood before. */
async function expectBoth (watcher, joiner, since, what) {
  await expectSees(watcher, joiner, since, what)
  await expectSees(joiner, watcher, 0, what)
}

async function main () {
  // 1. RagoTrois waits at the spawn; RagoQuatre joins for the first time
  const one = await join('RagoTrois')
  await closeFirstMenu(one)
  await expectInLobby(one, 'first arrival')
  let since = one.record.length
  let two = await join('RagoQuatre')
  await closeFirstMenu(two)
  await expectInLobby(two, 'first arrival')
  await expectBoth(one, two, since, 'RagoQuatre joins for the first time')

  // 2. RagoQuatre leaves from the lobby, and comes back
  await leave(two)
  since = one.record.length
  two = await join('RagoQuatre')
  await expectInLobby(two, 'back on the server')
  await expectBoth(one, two, since, 'RagoQuatre comes back from the lobby')

  // 3. RagoQuatre leaves in the Block edit training, and comes back
  let from = mark(two)
  two.chat('/queue block-edit')
  await chatLine(two, /Block edit - /, 10000, from)
  await sleep(1000)
  await leave(two)
  since = one.record.length
  two = await join('RagoQuatre')
  await expectInLobby(two, 'back from the training')
  await expectBoth(one, two, since, 'RagoQuatre comes back after leaving a training')

  // 4. RagoQuatre leaves in a duel, and comes back once RagoTrois is back in the lobby
  from = mark(one)
  one.chat('/queue block-fight-1v1')
  two.chat('/queue block-fight-1v1')
  await chatLine(one, /Block fight against RagoQuatre/, 15000, from)
  await chatLine(one, /Fight!/, 10000, from)
  await leave(two)
  await chatLine(one, /Victory!/, 10000, from)
  await expectInLobby(one, 'after the duel')
  await sleep(1000)
  since = one.record.length
  two = await join('RagoQuatre')
  await expectInLobby(two, 'back from the duel')
  await expectBoth(one, two, since, 'RagoQuatre comes back after leaving a duel')

  // 5. RagoTrois leaves from the lobby, and comes back while RagoQuatre waits at the spawn
  await leave(one)
  since = two.record.length
  const back = await join('RagoTrois')
  await expectInLobby(back, 'back on the server')
  await expectBoth(two, back, since, 'RagoTrois comes back while RagoQuatre waits')

  finishing = true
  log('PASSED: each arrival seen by the other, and seeing them')
  back.quit()
  two.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
