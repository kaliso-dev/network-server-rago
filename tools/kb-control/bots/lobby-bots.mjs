// Lobby and sumo test bots for RagoLobby and RagoPractice (see tools/lobby-check/README.md).
//
// Two bots join a local 1.8.8 test server that runs RagoLobby, RagoPractice and RagoCombatEngine
// and play the way a player does:
//   1. both arrive in the lobby: adventure mode, the selector in the first hotbar slot, the sidebar;
//   2. RagoUn opens the selector with a right click and clicks the sumo duel; RagoDeux types
//      /file sumo;
//   3. the duel starts: RagoUn walks up to RagoDeux and hits it once (knockback, no damage), then
//      RagoDeux walks off the platform three times, so RagoUn wins 3 - 0;
//   4. both come back to the lobby;
//   5. a second duel, which RagoUn leaves with /spawn: RagoDeux wins by forfeit.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 5 * 60 * 1000
// A new lobby world has a platform at y = 64: players stand at y = 65, at the centre.
const LOBBY_SPAWN = { x: 0.5, y: 65, z: 0.5 }
// The sumo duel is the third mode of the bundled config.yml: slot 12 of the selector.
const SUMO_SLOT = 12
const POINTS_TO_WIN = 3

let finishing = false

function abort (error) {
  console.error(`[lobby-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[lobby-bots] ${text}`)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`)
    await sleep(50)
  }
}

/** Every chat line a bot received, so that a test can wait for a line sent a moment ago. */
const chats = new Map()

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    chats.set(username, [])
    bot.velocities = []
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.on('messagestr', (message) => {
      chats.get(username).push(message)
      log(`${username} < ${message}`)
    })
    bot._client.on('entity_velocity', (packet) => {
      if (bot.entity != null && packet.entityId === bot.entity.id) {
        bot.velocities.push({ time: Date.now(), velocity: packet.velocity })
      }
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

/** Waits for a chat line matching the pattern, received after the line number `from`. */
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

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 10000,
    `${bot.username} in the lobby state (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
  const p = bot.entity.position
  const distance = Math.hypot(p.x - LOBBY_SPAWN.x, p.y - LOBBY_SPAWN.y, p.z - LOBBY_SPAWN.z)
  if (distance > 1.5) throw new Error(`${bot.username} is not at the lobby spawn (${what}): ${p}`)
}

function playerEntity (bot, other) {
  return Object.values(bot.entities).find((e) => e.type === 'player' && e.username === other.username)
}

async function openSelectorAndJoinSumo (bot) {
  bot.setQuickBarSlot(0)
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  await sleep(250)
  bot.activateItem()
  const window = await Promise.race([opened, sleep(5000).then(() => null)])
  if (window == null) throw new Error(`${bot.username}: the selector did not open on a right click`)
  const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
  if (!title.includes('Modes de jeu')) throw new Error(`${bot.username}: unexpected window title ${title}`)
  const icon = window.slots[SUMO_SLOT]
  if (icon?.name !== 'slime_ball') throw new Error(`${bot.username}: slot ${SUMO_SLOT} holds ${icon?.name}, not the sumo duel`)
  const from = mark(bot)
  // The server cancels the click (the menu cannot be emptied): do not wait for its acceptance.
  bot.clickWindow(SUMO_SLOT, 0, 0).catch(() => {})
  await chatLine(bot, /Tu es dans la file .*Duel sumo/, 5000, from)
  log(`${bot.username} joined the sumo queue from the selector`)
}

async function fallOff (bot) {
  bot.setControlState('back', true)
  bot.setControlState('jump', true)
}

function stop (bot) {
  bot.clearControlStates()
}

async function main () {
  const one = await join('RagoUn')
  const two = await join('RagoDeux')
  log('both bots joined')

  // 1. The lobby.
  await expectInLobby(one, 'on joining')
  await expectInLobby(two, 'on joining')
  if (hotbarItem(one, 8)?.name !== 'dye') throw new Error(`no visibility item in slot 8: ${hotbarItem(one, 8)?.name}`)
  await waitFor(() => (one.scoreboard?.sidebar?.title ?? '').includes('RAGO PRACTICE'), 5000,
    `the lobby sidebar; sidebar: ${JSON.stringify(one.scoreboard?.sidebar?.title)}`)
  log('lobby: adventure mode, selector, visibility item and sidebar are there')

  // 2. The queue.
  await openSelectorAndJoinSumo(one)
  let fromOne = mark(one)
  let fromTwo = mark(two)
  two.chat('/file sumo')

  // 3. The duel.
  await chatLine(one, /Duel sumo contre RagoDeux/, 10000, fromOne)
  await chatLine(two, /Duel sumo contre RagoUn/, 10000, fromTwo)
  await waitFor(() => one.game.gameMode === 'survival' && two.game.gameMode === 'survival', 5000, 'survival mode in the duel')
  if (hotbarItem(one, 0) != null) throw new Error('the lobby items are still there in the duel')
  log('the duel started')

  for (let round = 1; round <= POINTS_TO_WIN; round++) {
    await chatLine(two, /Combat !/, 10000, fromTwo)
    fromOne = mark(one)
    fromTwo = mark(two)
    if (round === 1) {
      // RagoUn walks to RagoDeux and hits it once: knockback, but no damage.
      const target = playerEntity(one, two)
      if (target == null) throw new Error('RagoUn does not see RagoDeux in the duel')
      await one.lookAt(target.position.offset(0, 1.6, 0), true)
      one.setControlState('forward', true)
      await waitFor(() => one.entity.position.distanceTo(target.position) < 2.8, 5000, 'RagoUn close to RagoDeux')
      stop(one)
      const before = two.velocities.length
      await one.lookAt(target.position.offset(0, 1.6, 0), true)
      one.attack(target)
      await waitFor(() => two.velocities.length > before, 2000, 'a knockback packet for RagoDeux after the hit')
      await sleep(500)
      if (two.health < 20) throw new Error(`RagoDeux took damage in the sumo duel (health ${two.health})`)
      log('hit: RagoDeux got knockback and no damage')
    }
    await fallOff(two)
    await chatLine(two, /Point pour RagoUn/, 10000, fromTwo)
    stop(two)
    await chatLine(one, /Point pour toi/, 5000, fromOne)
    log(`round ${round}: RagoDeux fell, point for RagoUn`)
  }
  await chatLine(one, /Victoire !.*3 - 0/, 5000, fromOne)
  await chatLine(two, /Défaite.*0 - 3/, 5000, fromTwo)
  log('RagoUn won 3 - 0')

  // 4. Back to the lobby.
  await expectInLobby(one, 'after the duel')
  await expectInLobby(two, 'after the duel')
  log('both are back in the lobby')

  // 5. A forfeit.
  fromOne = mark(one)
  fromTwo = mark(two)
  one.chat('/file sumo')
  two.chat('/file sumo')
  await chatLine(one, /Duel sumo contre RagoDeux/, 10000, fromOne)
  await chatLine(two, /Duel sumo contre RagoUn/, 10000, fromTwo)
  one.chat('/spawn')
  await expectInLobby(one, 'after /spawn in a duel')
  await chatLine(two, /Victoire !.*RagoUn a abandonné/, 5000, fromTwo)
  await expectInLobby(two, 'after winning by forfeit')
  log('/spawn in a duel: RagoUn is back in the lobby, RagoDeux wins by forfeit')

  finishing = true
  one.quit()
  two.quit()
  await sleep(500)
  log('PASSED')
  process.exit(0)
}

main().catch(abort)
