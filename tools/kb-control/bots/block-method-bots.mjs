// Block Method 1v1 test bot for RagoLobby and RagoBedwars (see tools/lobby-check/README.md,
// run-arena-test.sh).
//
// Block Method 1v1 (the owner's ask of 28/11) is Bed Fight as a practice: a player alone queues,
// RagoBot comes for them (ragobot.seul of the lobby's config.yml, much weaker than the RagoBot of
// the parties), and each death starts the round again on the map as it was. On the demo map of
// the CI (its islands 18 blocks apart over the void), RagoSeul stands still on its spawn:
//   1. /queue block-method-1v1: RagoBot comes for it, the practice starts on bw-demo, with a blaze
//      rod and a map in its hotbar;
//   2. RagoBot comes to it as a player: it bridges over the void with the wool of its kit (blocks
//      seen placed between the islands), sneaking at the edges, then kills it;
//   3. the death starts the round again: the bridge of RagoBot is gone (the map as it was);
//   4. the blaze rod starts the round again; the map opens the menu of the maps (bw-demo alone
//      here, the one it plays on);
//   5. /spawn: back in the lobby, RagoBot leaves the server.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 5 * 60 * 1000
const BOT = 'RagoBot'
const PLAYER = 'RagoSeul'
/** The floor of the islands of the demo map (DemoMap.FLOOR): a bridge is at that height. */
const FLOOR = 64

let finishing = false

function abort (error) {
  console.error(`[block-method-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[block-method-bots] ${text}`)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${typeof what === 'function' ? what() : what}`)
    await sleep(20)
  }
}

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    bot.firstMenu = null
    bot.menu = null
    bot.chats = []
    bot.on('windowOpen', (window) => {
      const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
      if (title.includes('What do you want to do?')) bot.firstMenu = window
      else bot.menu = { title, window }
    })
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.on('messagestr', (message) => {
      bot.chats.push(message)
      log(`${username} < ${message}`)
    })
    bot.once('spawn', () => {
      bot.removeListener('error', fail)
      bot.removeListener('kicked', fail)
      bot.on('error', (error) => abort(new Error(`${username}: ${error.message}`)))
      bot.on('kicked', (reason) => abort(new Error(`${username} was kicked: ${JSON.stringify(reason)}`)))
      bot.on('end', (reason) => {
        if (!finishing) abort(new Error(`${username} disconnected: ${reason}`))
      })
      resolve(bot)
    })
  })
}

function mark (bot) {
  return bot.chats.length
}

async function chatLine (bot, pattern, timeoutMs, from) {
  try {
    await waitFor(() => bot.chats.slice(from).some((line) => pattern.test(line)), timeoutMs, `${bot.username} to read ${pattern}`)
  } catch (error) {
    throw new Error(`${error.message}; chat: ${JSON.stringify(bot.chats.slice(from))}`)
  }
  return bot.chats.slice(from).find((line) => pattern.test(line))
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
}

function hotbarSlotOf (bot, name) {
  for (let slot = 0; slot < 9; slot++) {
    if (hotbarItem(bot, slot)?.name === name) return slot
  }
  return -1
}

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 20000,
    `${bot.username} in the lobby (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
}

function ragoBot (bot) {
  return bot.players[BOT]?.entity ?? null
}

function where (entity) {
  const p = entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
}

/**
 * What the player sees of RagoBot: the blocks placed over the void between the islands (x from
 * -23 to -6 and from 6 to 23 at the floor's height), how often it sneaked, how far it went.
 */
function watch (bot) {
  const seen = { bridge: new Map(), sneaks: 0, swings: 0, farthest: 0, start: null }
  const botId = () => ragoBot(bot)?.id
  const onBlock = (before, after) => {
    const p = after.position
    const gap = (p.x >= -23 && p.x <= -6) || (p.x >= 6 && p.x <= 23)
    if (p.y === FLOOR && gap && after.name !== 'air') seen.bridge.set(`${p.x},${p.y},${p.z}`, p.clone())
  }
  const onPacket = (packet, meta) => {
    if (meta.name === 'entity_metadata' && packet.entityId === botId()) {
      const flags = packet.metadata.find((entry) => entry.key === 0)
      if (flags != null && (flags.value & 0x02) !== 0) seen.sneaks++
    } else if (meta.name === 'animation' && packet.entityId === botId() && packet.animation === 0) {
      seen.swings++
    }
  }
  const onMove = (entity) => {
    if (entity.id !== botId()) return
    if (seen.start == null) seen.start = entity.position.clone()
    seen.farthest = Math.max(seen.farthest, entity.position.distanceTo(seen.start))
  }
  bot.on('blockUpdate', onBlock)
  bot._client.on('packet', onPacket)
  bot.on('entityMoved', onMove)
  seen.stop = () => {
    bot.removeListener('blockUpdate', onBlock)
    bot._client.removeListener('packet', onPacket)
    bot.removeListener('entityMoved', onMove)
  }
  seen.summary = () => `RagoBot as ${bot.username} saw it: ${seen.bridge.size} blocks of its bridge, ${seen.sneaks} sneaks,`
    + ` ${seen.swings} swings, ${seen.farthest.toFixed(1)} blocks from where it first moved,`
    + ` now at ${ragoBot(bot) ? where(ragoBot(bot)) : 'out of sight'}; ${bot.username} at ${where(bot.entity)}`
  return seen
}

/** Right click with the first item of this name in the hotbar. */
async function use (bot, name) {
  const slot = hotbarSlotOf(bot, name)
  if (slot < 0) throw new Error(`${bot.username} has no ${name} in its hotbar`)
  bot.setQuickBarSlot(slot)
  await sleep(300)
  bot.activateItem()
  await sleep(100)
  bot.deactivateItem()
}

async function main () {
  const player = await join(PLAYER)
  await waitFor(() => player.firstMenu != null, 8000, `${PLAYER}: the menu of the first arrival`)
  player.closeWindow(player.firstMenu)
  await sleep(200)
  await expectInLobby(player, 'on joining')

  // 1. alone in the queue: RagoBot comes for it
  let from = mark(player)
  const seen = watch(player)
  player.chat('/queue block-method-1v1')
  await chatLine(player, /RagoBot comes for your Block Method 1v1\./, 10000, from)
  await chatLine(player, /Block Method 1v1 on bw-demo: a practice against RagoBot/, 30000, from)
  await waitFor(() => hotbarSlotOf(player, 'blaze_rod') >= 0 && hotbarSlotOf(player, 'map') >= 0, 5000,
    () => `the blaze rod and the map in the hotbar: ${[...Array(9).keys()].map((s) => hotbarItem(player, s)?.name).join(', ')}`)
  log(`the practice is on: blaze rod in slot ${hotbarSlotOf(player, 'blaze_rod')}, map in slot ${hotbarSlotOf(player, 'map')}`)

  // 2. RagoBot bridges to it, then kills it
  from = mark(player)
  try {
    await chatLine(player, new RegExp(`${PLAYER} (was killed by|was knocked into the void by) ${BOT}\\.`), 150000, from)
  } catch (error) {
    throw new Error(`${error.message}; ${seen.summary()}`)
  }
  const bridge = [...seen.bridge.values()]
  log(`RagoBot killed ${PLAYER}; ${seen.summary()}`)
  if (bridge.length < 10) throw new Error(`RagoBot crossed without a bridge of its own: ${seen.summary()}`)
  if (seen.sneaks === 0) throw new Error(`RagoBot bridged without sneaking once: ${seen.summary()}`)

  // 3. the round again: the map as it was
  await waitFor(() => bridge.every((p) => player.blockAt(p)?.name === 'air'), 5000,
    () => `the bridge of RagoBot gone: ${bridge.filter((p) => player.blockAt(p)?.name !== 'air').length} blocks left`)
  log(`the round starts again: the ${bridge.length} blocks of RagoBot's bridge are gone`)
  seen.stop()

  // 4. the blaze rod, the map
  await sleep(1500)
  from = mark(player)
  await use(player, 'blaze_rod')
  await chatLine(player, /Round \d+: the map as at the start\./, 5000, from)
  log('the blaze rod started the round again')
  player.menu = null
  await use(player, 'map')
  await waitFor(() => player.menu != null, 5000, 'the menu of the maps')
  if (!player.menu.title.includes('Choose your map')) throw new Error(`another menu opened: ${player.menu.title}`)
  const items = player.menu.window.slots.slice(0, 9).filter((item) => item != null)
  if (items.length !== 1) throw new Error(`the demo map alone in the menu, not ${items.length} items`)
  from = mark(player)
  await player.clickWindow(0, 0, 0)
  await chatLine(player, /You are on bw-demo already\./, 5000, from)
  log('the map opened the menu of the maps: bw-demo, the one it plays on')

  // 5. back to the lobby: RagoBot leaves
  player.chat('/spawn')
  await expectInLobby(player, 'after /spawn')
  await waitFor(() => player.players[BOT] == null, 10000, 'RagoBot to leave the server after the practice')
  log('/spawn: back in the lobby, RagoBot left the server')

  finishing = true
  log('PASSED: alone in the queue of Block Method 1v1, RagoBot came, bridged over the void sneaking at the edges'
    + ' and killed the player; the death started the round again on the map as it was; the blaze rod started it again,'
    + ' the map opened the menu of the maps; after /spawn RagoBot left')
  player.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
