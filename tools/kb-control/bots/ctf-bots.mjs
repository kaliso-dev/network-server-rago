// Capture the Flag test bots for RagoBedwars (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Two bots play CTF The Bridge 1v1 (ctf-bridge-1v1) on CtfBridge, the map the plugin makes, with
// the bundled settings (first to 3 captures):
//   1. both start on their bases (red in the west, blue in the east), facing the bridge, with the
//      kit of The Bridge (an iron sword, a bow, a diamond pickaxe, two stacks of clay of their
//      team, 8 golden apples, the arrow in the last slot of the hotbar) and the leather armour
//      without the cap; the bridge is one block of clay wide, each flag a banner on gold, both
//      Safe in the sidebar;
//   2. the guard (blue) breaks the end of the bridge on its side with the pickaxe and puts clay
//      back, eats a golden apple (absorption, no regeneration), shoots its arrow, which comes back
//      in the last slot about 3.5 s later, and steps aside;
//   3. the runner (red) crosses the bridge to the blue flag and takes it (the banner on its head,
//      Taken in the sidebar of the guard), then walks off the bridge with it: the flag goes back to
//      its base, the runner is back at once on its own base, with its kit;
//   4. three times: it takes the blue flag and brings it to its own, a capture each time;
//   5. the third capture wins: the winner and the captures in the end summary, both back in the
//      lobby.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 5 * 60 * 1000
/** CtfMaps: the floor, the bridge from -20 to 20 at z 0, the spawns at 25, the flags at 34. */
const FLOOR = 64
const BRIDGE_HALF = 20
const SPAWN_X = 25
const FLAG_X = 34
/** The arrow comes back this long after it was shot (CaptureTheFlagGame.ARROW_MILLIS). */
const ARROW_MILLIS = 3500
/** The clay of each team: the data of its stained clay. */
const CLAY = { Red: 14, Blue: 11 }
const ABSORPTION = 22
const REGENERATION = 10

let finishing = false
/** The vector class of mineflayer's positions (from the first bot): new Vec3(x, y, z). */
let Vec3 = null

function abort (error) {
  console.error(`[ctf-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[ctf-bots] ${text}`)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`)
    await sleep(20)
  }
}

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    bot.firstMenu = null
    bot.chats = []
    bot.on('windowOpen', (window) => {
      const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
      if (title.includes('What do you want to do?')) bot.firstMenu = window
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
  await waitFor(() => bot.chats.slice(from).some((line) => pattern.test(line)), timeoutMs,
    `${bot.username} to read ${pattern}; chat: ${JSON.stringify(bot.chats.slice(from))}`)
  return bot.chats.slice(from).find((line) => pattern.test(line))
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
}

function hotbar (bot) {
  return [...Array(9).keys()].map((i) => {
    const item = hotbarItem(bot, i)
    return item == null ? null : `${item.name}:${item.metadata} x${item.count}`
  })
}

function countOf (bot, name) {
  return bot.inventory.items().filter((item) => item.name === name).reduce((sum, item) => sum + item.count, 0)
}

/** The lines of the sidebar, top to bottom, without colours. */
function sidebarLines (bot) {
  return (bot.scoreboard?.sidebar?.items ?? []).map((item) => String(item.name).replace(/§./g, ''))
}

function blockText (block) {
  return block == null ? 'not loaded' : `${block.name}:${block.metadata}`
}

function where (bot) {
  const p = bot.entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
}

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 15000,
    `${bot.username} in the lobby (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
}

/** A bot just arrived: the menu of the first arrival closed, in the lobby. */
async function arrive (username) {
  const bot = await join(username)
  await waitFor(() => bot.firstMenu != null, 8000, `${username}: the menu of the first arrival`)
  bot.closeWindow(bot.firstMenu)
  await sleep(200)
  await expectInLobby(bot, 'on joining')
  return bot
}

/** The bot holds the first item of this name in its hotbar. */
async function hold (bot, name) {
  let slot = -1
  await waitFor(() => {
    for (let i = 0; i < 9; i++) {
      if (hotbarItem(bot, i)?.name === name) {
        slot = i
        return true
      }
    }
    return false
  }, 5000, `${bot.username} to have ${name} in its hotbar: ${JSON.stringify(hotbar(bot))}`)
  bot.setQuickBarSlot(slot)
  await sleep(150)
}

/** The kit of The Bridge, where the lobby's kit puts it, and the armour without the cap. */
async function expectKit (bot, team, what) {
  const expected = ['iron_sword', 'bow', 'diamond_pickaxe', 'stained_hardened_clay', 'stained_hardened_clay', 'golden_apple',
    null, null, 'arrow']
  const counts = [1, 1, 1, 64, 64, 8, 0, 0, 1]
  await waitFor(() => bot.game.gameMode === 'survival' && expected.every((name, i) => (hotbarItem(bot, i)?.name ?? null) === name &&
    (name == null || hotbarItem(bot, i).count === counts[i])), 5000,
  `${bot.username} to have the kit of The Bridge (${what}): ${bot.game.gameMode}, hotbar ${JSON.stringify(hotbar(bot))}`)
  for (const slot of [3, 4]) {
    const clay = hotbarItem(bot, slot)
    if (clay.metadata !== CLAY[team]) throw new Error(`${bot.username}: clay of the wrong colour in slot ${slot} (${what}): ${clay.metadata}`)
  }
  const armour = [5, 6, 7, 8].map((slot) => bot.inventory.slots[slot]?.name ?? null)
  if (JSON.stringify(armour) !== JSON.stringify([null, 'leather_chestplate', 'leather_leggings', 'leather_boots'])) {
    throw new Error(`${bot.username}: not the armour of The Bridge (${what}): ${JSON.stringify(armour)}`)
  }
}

/** The line of a team in the sidebar: "R Red: 0 Safe YOU". */
async function expectFlagLine (bot, text, what) {
  await waitFor(() => sidebarLines(bot).includes(text), 5000,
    `${bot.username} to read "${text}" in the sidebar (${what}): ${JSON.stringify(sidebarLines(bot))}`)
}

/** The bot walks (and sprints) along the bridge, at z 0.5, towards this x, until `done`. */
async function runTo (bot, x, done, what, timeoutMs = 30000) {
  const p = bot.entity.position
  await bot.lookAt(new Vec3(x, p.y + 1.62, 0.5), true)
  bot.setControlState('sprint', true)
  bot.setControlState('forward', true)
  try {
    await waitFor(done, timeoutMs, `${what} (${bot.username} at ${where(bot)})`)
  } finally {
    bot.clearControlStates()
  }
}

/** The bot walks to this place of its base, on a straight line, until within half a block. */
async function walkTo (bot, x, z, what) {
  const target = new Vec3(x, bot.entity.position.y, z)
  await bot.lookAt(target.offset(0, 1.62, 0), true)
  bot.setControlState('forward', true)
  try {
    await waitFor(() => Math.hypot(bot.entity.position.x - x, bot.entity.position.z - z) < 0.5, 10000,
      `${bot.username} to walk to ${x}, ${z} (${what}; at ${where(bot)})`)
  } finally {
    bot.clearControlStates()
  }
  await sleep(300)
}

/**
 * Places the clay in hand against this face of the block at `against`; clay must then be at
 * `where`. Tried again a few times: the server can drop a placement that comes too soon.
 */
async function place (bot, against, face, at) {
  for (let attempt = 1; ; attempt++) {
    const error = await bot._placeBlockWithOptions(bot.blockAt(against), face, { swingArm: 'right', forceLook: true })
      .then(() => null, (e) => e)
    try {
      await waitFor(() => bot.blockAt(at)?.name === 'stained_hardened_clay', 600, `clay at ${at}`)
      return
    } catch {
      const why = `clay not placed at ${at} against ${against} (there: ${blockText(bot.blockAt(at))}; ${error?.message ?? 'no error'};`
        + ` the bot at ${where(bot)}, holding ${bot.heldItem?.name ?? 'nothing'})`
      if (attempt >= 4) throw new Error(why)
      log(`try ${attempt}: ${why}`)
      await sleep(200)
    }
  }
}

/** Both in the queue of CTF The Bridge 1v1: the red bot and the blue one, once the fight is on. */
async function start (one, two) {
  const from = [mark(one), mark(two)]
  one.chat('/queue ctf-bridge-1v1')
  two.chat('/queue ctf-bridge-1v1')
  const teams = {}
  for (const [i, bot] of [one, two].entries()) {
    const line = await chatLine(bot,
      /CTF The Bridge 1v1 on CtfBridge: you are in the (Red|Blue) team\. Take the enemy flag and bring it to your own/, 30000, from[i])
    teams[line.includes('Red team') ? 'Red' : 'Blue'] = bot
  }
  if (teams.Red == null || teams.Blue == null) throw new Error('the two bots are not in two teams')
  for (const [i, bot] of [one, two].entries()) {
    await chatLine(bot, /First to 3 captures, 15 minutes at most\. You always come back after a death\. The bridge breaks\./, 5000, from[i])
    await chatLine(bot, /Fight!/, 15000, from[i])
  }
  return { red: teams.Red, blue: teams.Blue }
}

/** 1. Both on their bases with the kit, the bridge and the flags as the plugin made them. */
async function checkStart (red, blue) {
  for (const [bot, team, side] of [[red, 'Red', -1], [blue, 'Blue', 1]]) {
    const p = bot.entity.position
    const spawnX = side * SPAWN_X + 0.5
    if (Math.abs(p.x - spawnX) > 1 || Math.abs(p.z - 0.5) > 1 || Math.abs(p.y - (FLOOR + 1)) > 0.5) {
      throw new Error(`${bot.username} (${team}) does not start on its spawn ${spawnX}, ${FLOOR + 1}, 0.5: at ${where(bot)}`)
    }
    // the look: towards the middle, along x
    const lookX = -Math.sin(bot.entity.yaw)
    if (Math.sign(lookX) !== -side || Math.abs(lookX) < 0.9) {
      throw new Error(`${bot.username} (${team}) does not face the bridge: yaw ${bot.entity.yaw.toFixed(2)}`)
    }
    await expectKit(bot, team, 'at the start')
    await expectFlagLine(bot, `${team[0]} ${team}: 0 Safe YOU`, 'at the start')
    const other = team === 'Red' ? 'Blue' : 'Red'
    await expectFlagLine(bot, `${other[0]} ${other}: 0 Safe`, 'at the start')
    for (const text of ['First to 3 captures', 'Kills: 0', 'Captures: 0']) await expectFlagLine(bot, text, 'at the start')
  }
  // the bridge, one block wide, in the colours of the teams; the flags on gold
  for (const x of [-BRIDGE_HALF, -1, 0, 1, BRIDGE_HALF]) {
    const at = new Vec3(x, FLOOR, 0)
    await waitFor(() => red.blockAt(at) != null, 5000, `the bridge at ${at} loaded`)
    const block = red.blockAt(at)
    const data = x < 0 ? CLAY.Red : x > 0 ? CLAY.Blue : 0
    if (block.name !== 'stained_hardened_clay' || block.metadata !== data) throw new Error(`the bridge at ${at}: ${blockText(block)}`)
    for (const z of [-1, 1]) {
      const beside = red.blockAt(new Vec3(x, FLOOR, z))
      if (beside?.name !== 'air') throw new Error(`the bridge is wider than a block at ${x}, ${FLOOR}, ${z}: ${blockText(beside)}`)
    }
  }
  for (const side of [-1, 1]) {
    const flag = red.blockAt(new Vec3(side * FLAG_X, FLOOR + 1, 0))
    const gold = red.blockAt(new Vec3(side * FLAG_X, FLOOR, 0))
    if (flag?.name !== 'standing_banner' || gold?.name !== 'gold_block') {
      throw new Error(`the flag at x ${side * FLAG_X}: ${blockText(flag)} on ${blockText(gold)}`)
    }
  }
  log('start: both on their bases facing the bridge, the kit of The Bridge, no cap; the bridge one block wide;'
    + ' the flags on gold, Safe')
}
/** 2. The guard: the bridge breaks, the golden apple, the arrow that comes back; then aside. */
async function guard (blue) {
  // the end of the bridge on its side breaks (the clay of the map), and clay goes back there
  const end = new Vec3(BRIDGE_HALF, FLOOR, 0)
  await walkTo(blue, BRIDGE_HALF + 3.5, 0.5, 'to the bridge')
  await hold(blue, 'diamond_pickaxe')
  await blue.dig(blue.blockAt(end), true)
  await waitFor(() => blue.blockAt(end)?.name === 'air', 5000, `the end of the bridge broken (there: ${blockText(blue.blockAt(end))})`)
  await hold(blue, 'stained_hardened_clay')
  await place(blue, new Vec3(BRIDGE_HALF + 1, FLOOR, 0), new Vec3(-1, 0, 0), end)
  log('guard: the end of the bridge broken with the pickaxe, clay put back')

  // the golden apple: health full, absorption, no regeneration
  await hold(blue, 'golden_apple')
  await blue.consume()
  await waitFor(() => countOf(blue, 'golden_apple') === 7 && blue.entity.effects[ABSORPTION] != null, 3000,
    `the golden apple eaten: ${countOf(blue, 'golden_apple')} left, effects ${JSON.stringify(Object.keys(blue.entity.effects))}`)
  await sleep(600)
  if (blue.entity.effects[REGENERATION] != null) throw new Error('the golden apple of The Bridge gave regeneration')
  log(`guard: a golden apple eaten, absorption and no regeneration (health ${blue.health})`)

  // the arrow: shot away from the map, back in the last slot of the hotbar about 3.5 s later
  await hold(blue, 'bow')
  await blue.lookAt(blue.entity.position.offset(0, 9, 20), true)
  blue.activateItem()
  await sleep(1100)
  blue.deactivateItem()
  const shot = Date.now()
  await waitFor(() => countOf(blue, 'arrow') === 0, 2000, `the arrow shot (hotbar ${JSON.stringify(hotbar(blue))})`)
  await waitFor(() => hotbarItem(blue, 8)?.name === 'arrow', ARROW_MILLIS + 3000,
    `the arrow back in the last slot (hotbar ${JSON.stringify(hotbar(blue))})`)
  const back = Date.now() - shot
  if (back < ARROW_MILLIS - 500) throw new Error(`the arrow came back after ${back} ms, before ${ARROW_MILLIS}`)
  if (countOf(blue, 'arrow') !== 1) throw new Error(`${countOf(blue, 'arrow')} arrows after it came back`)
  log(`guard: the arrow shot came back in the last slot after ${back} ms`)

  await walkTo(blue, BRIDGE_HALF + 3.5, 5.5, 'aside')
}

/** The runner takes the blue flag at the back of the blue base. */
async function take (red, blue, what) {
  const from = [mark(red), mark(blue)]
  await runTo(red, FLAG_X + 0.5, () => red.chats.slice(from[0]).some((line) => /took the Blue Flag!/.test(line)),
    `the blue flag taken (${what})`)
  await chatLine(blue, new RegExp(`${red.username} took the Blue Flag!`), 5000, from[1])
  await waitFor(() => red.inventory.slots[5]?.name === 'banner', 5000,
    `the banner on the head of ${red.username} (helmet ${red.inventory.slots[5]?.name})`)
  await expectFlagLine(blue, 'B Blue: 0 Taken YOU', what)
  if (red.chats.slice(from[0]).some((line) => /Your flag must be at your base/.test(line))) {
    throw new Error('the runner was told its flag was away, its flag at home')
  }
}

async function main () {
  const one = await arrive('RagoCtfUn')
  const two = await arrive('RagoCtfDeux')
  Vec3 = one.entity.position.constructor

  const { red, blue } = await start(one, two)
  log(`CTF The Bridge 1v1: ${red.username} red, ${blue.username} blue`)
  await checkStart(red, blue)
  await guard(blue)

  // 3. the blue flag taken, then into the void with it: back to its base, the runner back on its own
  await take(red, blue, 'the first time')
  log(`runner: ${red.username} took the blue flag, the banner on its head, Taken in the sidebar`)
  let from = [mark(red), mark(blue)]
  await runTo(red, -FLAG_X, () => red.entity.position.x < BRIDGE_HALF / 2, 'back on the bridge with the flag')
  await red.lookAt(red.entity.position.offset(0, 1.62, 20), true)
  red.setControlState('forward', true)
  try {
    await waitFor(() => red.entity.position.y < FLOOR - 2, 5000, `${red.username} off the bridge (at ${where(red)})`)
  } finally {
    red.clearControlStates()
  }
  await chatLine(red, /The Blue Flag went back to its base\./, 10000, from[0])
  await chatLine(blue, /The Blue Flag went back to its base\./, 5000, from[1])
  await waitFor(() => Math.abs(red.entity.position.x - (-SPAWN_X + 0.5)) < 1.5 && Math.abs(red.entity.position.y - (FLOOR + 1)) < 0.5,
    5000, `${red.username} back at once on its spawn (at ${where(red)})`)
  await expectKit(red, 'Red', 'after its death')
  await expectFlagLine(blue, 'B Blue: 0 Safe YOU', 'the flag back')
  await waitFor(() => red.blockAt(new Vec3(FLAG_X, FLOOR + 1, 0))?.name === 'standing_banner', 5000, 'the blue banner back at its base')
  log(`runner: into the void with the flag, the flag back at its base; ${red.username} back at once with its kit`)

  // 4. three captures, the third wins
  for (let capture = 1; capture <= 3; capture++) {
    await take(red, blue, `capture ${capture}`)
    from = [mark(red), mark(blue)]
    await runTo(red, -FLAG_X + 0.5, () => red.chats.slice(from[0]).some((line) => /captured the Blue Flag!/.test(line)),
      `capture ${capture}`)
    for (const [i, bot] of [red, blue].entries()) {
      await chatLine(bot, new RegExp(`${red.username} captured the Blue Flag! (Red ${capture} - 0 Blue|Blue 0 - ${capture} Red)`),
        5000, from[i])
    }
    if (capture < 3) {
      await waitFor(() => red.inventory.slots[5] == null, 5000, `no banner on the head after the capture (${red.inventory.slots[5]?.name})`)
      await expectFlagLine(red, `R Red: ${capture} Safe YOU`, `capture ${capture}`)
      await expectFlagLine(red, `Captures: ${capture}`, `capture ${capture}`)
      await expectFlagLine(blue, 'B Blue: 0 Safe YOU', `capture ${capture}`)
    }
    log(`runner: capture ${capture}, Red ${capture} - 0 Blue`)
  }

  // 5. the end
  for (const [i, bot] of [red, blue].entries()) {
    await chatLine(bot, new RegExp(`Winner: Red - ${red.username}`), 10000, from[i])
    await chatLine(bot, new RegExp(`You: 0 kills, ${bot === red ? 3 : 0} captures`), 5000, from[i])
  }
  await expectInLobby(red, 'after the game')
  await expectInLobby(blue, 'after the game')
  log('end: red won at its third capture, both back in the lobby')

  finishing = true
  log('PASSED: CTF The Bridge 1v1, its kit, its bridge that breaks, the golden apple, the arrow that comes back,'
    + ' a flag taken and back to its base from the void, three captures and the win')
  one.quit()
  two.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
