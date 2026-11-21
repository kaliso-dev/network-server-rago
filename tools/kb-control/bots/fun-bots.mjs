// FUN PRACTICE test bots for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Two bots play the two modes of FUN PRACTICE (plan 6.1) on a local 1.8.8 test server, with the
// bundled settings (one round each):
//   1. Snowball Spleef (snowball-spleef): the floor of snow under both, a diamond shovel each; the
//      west bot breaks a block of snow with it and gets a snowball, throws the snowball on the
//      floor (one block of snow less where it lands), then the east bot breaks the snow under its
//      own feet, falls under the floor, and the west bot wins;
//   2. Fireball Fight (fireball-knockback): 64 fireballs and the wind gust (a feather) each; the
//      east bot uses the gust looking up (it goes up, a second gust at once must wait, it lands
//      unhurt), the west bot throws a fireball at it (the east bot is pushed away, unhurt, and the
//      west bot's stack stays full), then the east bot walks off the platform: the west bot wins.
// Both back in the lobby after each game.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 4 * 60 * 1000
/** The bundled radius of the snow floor (snowball-spleef.rayon): the west start is 6 from the middle. */
const SPLEEF_RADIUS = 12

let finishing = false
/** The vector class of mineflayer's positions (from the first bot): new Vec3(x, y, z). */
let Vec3 = null

function abort (error) {
  console.error(`[fun-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[fun-bots] ${text}`)
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
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
}

function countOf (bot, name) {
  return bot.inventory.items().filter((item) => item.name === name).reduce((sum, item) => sum + item.count, 0)
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
  }, 5000, `${bot.username} to have ${name} in its hotbar: ${JSON.stringify([...Array(9).keys()].map((i) => hotbarItem(bot, i)?.name ?? null))}`)
  bot.setQuickBarSlot(slot)
  await sleep(150)
}

function blockText (block) {
  return block == null ? 'not loaded' : `${block.name}:${block.metadata}`
}

function where (bot) {
  const p = bot.entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
}

async function dig (bot, at) {
  await waitFor(() => bot.blockAt(at) != null, 5000, `${bot.username}: the block at ${at} loaded`)
  await bot.dig(bot.blockAt(at), true)
  await waitFor(() => bot.blockAt(at)?.name === 'air', 5000,
    `${bot.username} to break the block at ${at} (there: ${blockText(bot.blockAt(at))})`)
}

/**
 * Both bots in a duel of this mode: the west one (side 0, at centre.x + 0.5 - distance) and the
 * east one (centre.x + 0.5 + distance), and the centre (the block under the middle).
 */
async function startDuel (one, two, mode, title, distance) {
  const fromOne = mark(one)
  const fromTwo = mark(two)
  one.chat(`/queue ${mode}`)
  two.chat(`/queue ${mode}`)
  await chatLine(one, new RegExp(`${title} against ${two.username}`), 15000, fromOne)
  await chatLine(two, new RegExp(`${title} against ${one.username}`), 15000, fromTwo)
  await waitFor(() => one.game.gameMode === 'survival' && two.game.gameMode === 'survival', 5000, 'survival mode in the duel')
  await sleep(500)
  const [west, east] = one.entity.position.x < two.entity.position.x ? [one, two] : [two, one]
  const p = west.entity.position
  const centre = { x: Math.round(p.x - 0.5 + distance), y: Math.floor(p.y) - 1, z: Math.floor(p.z) }
  const q = east.entity.position
  if (Math.abs(q.x - (centre.x + 0.5 + distance)) > 0.6 || Math.abs(q.z - (centre.z + 0.5)) > 0.6) {
    throw new Error(`the starts of ${mode} do not fit: west ${p}, east ${q}, centre ${JSON.stringify(centre)}`)
  }
  await chatLine(west, /Fight!/, 10000, west === one ? fromOne : fromTwo)
  await chatLine(east, /Fight!/, 10000, east === one ? fromOne : fromTwo)
  return { west, east, centre, at: (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz) }
}

/** The blocks of snow of the floor around the centre. */
function snowLeft (bot, game) {
  let snow = 0
  for (let dx = -SPLEEF_RADIUS - 1; dx <= SPLEEF_RADIUS + 1; dx++) {
    for (let dz = -SPLEEF_RADIUS - 1; dz <= SPLEEF_RADIUS + 1; dz++) {
      if (bot.blockAt(game.at(dx, 0, dz))?.name === 'snow') snow++
    }
  }
  return snow
}

async function snowballSpleef (one, two) {
  const game = await startDuel(one, two, 'snowball-spleef', 'Snowball Spleef', 6)
  const { west, east, at } = game
  for (const bot of [west, east]) {
    const under = bot.blockAt(bot.entity.position.offset(0, -0.5, 0))
    if (under?.name !== 'snow') throw new Error(`${bot.username} does not stand on snow: ${blockText(under)} at ${where(bot)}`)
  }
  const floor = snowLeft(west, game)
  if (floor < 300) throw new Error(`the floor of snow has ${floor} blocks only`)
  log(`snowball spleef: both on the snow floor (${floor} blocks), the west bot at ${where(west)}`)

  // 1. the shovel breaks the snow, a snowball for it
  await hold(west, 'diamond_shovel')
  await dig(west, at(-3, 0, 0))
  await waitFor(() => countOf(west, 'snowball') === 1, 3000, `a snowball for the block broken (has ${countOf(west, 'snowball')})`)
  log('snowball spleef: a block of snow broken with the shovel, a snowball for it')

  // 2. the snowball breaks the snow it lands on: thrown north, a little down (no block in reach
  // along the look, so that it is a throw in the air), it lands some six blocks away
  const before = snowLeft(west, game)
  await hold(west, 'snowball')
  await west.look(0, -0.21, true)
  await sleep(200)
  west.activateItem()
  await waitFor(() => countOf(west, 'snowball') === 0, 3000, 'the snowball thrown')
  await waitFor(() => snowLeft(west, game) === before - 1, 5000,
    `one block of snow less where the snowball landed (${before} before, ${snowLeft(west, game)} now)`)
  log('snowball spleef: the snowball broke the block of snow it landed on')

  // 3. the east bot breaks the snow under itself and falls under the floor: the west bot wins
  const fromWest = mark(west)
  await hold(east, 'diamond_shovel')
  const under = east.entity.position.offset(0, -0.5, 0).floored()
  await dig(east, under)
  await chatLine(west, /Victory!/, 10000, fromWest)
  log('snowball spleef: the east bot fell under the floor, the west bot won')
  await expectInLobby(west, 'after the snowball spleef')
  await expectInLobby(east, 'after the snowball spleef')
}

async function fireballFight (one, two) {
  const game = await startDuel(one, two, 'fireball-knockback', 'Fireball Fight', 5)
  const { west, east } = game
  for (const bot of [west, east]) {
    if (countOf(bot, 'fire_charge') !== 64) throw new Error(`${bot.username} has ${countOf(bot, 'fire_charge')} fireballs, not 64`)
    if (countOf(bot, 'feather') !== 1) throw new Error(`${bot.username} has no wind gust (feather)`)
    const under = bot.blockAt(bot.entity.position.offset(0, -0.5, 0))
    if (under?.name !== 'quartz_block') throw new Error(`${bot.username} does not stand on quartz: ${blockText(under)}`)
  }
  log('fireball fight: 64 fireballs and the wind gust each, on the quartz platform')

  // 1. the wind gust, looking up: up it goes; a second one at once must wait; it lands unhurt
  await hold(east, 'feather')
  await east.look(east.entity.yaw, Math.PI / 2, true)
  await sleep(200)
  const ground = east.entity.position.y
  let highest = ground
  const onMove = () => { highest = Math.max(highest, east.entity.position.y) }
  east.on('move', onMove)
  try {
    east.activateItem()
    await waitFor(() => highest > ground + 2, 3000, `the wind gust to lift the east bot (up to ${highest}, from ${ground})`)
    const fromEast = mark(east)
    east.activateItem()
    await chatLine(east, /Wind gust ready in \d+ seconds?\./, 3000, fromEast)
    await waitFor(() => east.entity.onGround && Math.abs(east.entity.position.y - ground) < 0.1, 10000,
      `the east bot back on the platform (at ${where(east)})`)
  } finally {
    east.removeListener('move', onMove)
  }
  await sleep(300)
  if (east.health < 20) throw new Error(`the east bot was hurt by its fall after the gust: health ${east.health}`)
  log(`fireball fight: the wind gust lifted the east bot ${(highest - ground).toFixed(1)} blocks, it landed unhurt`)

  // 2. a fireball at the east bot: pushed away, unhurt; the west bot's stack stays full
  const start = east.entity.position.clone()
  await hold(west, 'fire_charge')
  await west.lookAt(east.entity.position.offset(0, 1, 0), true)
  await sleep(200)
  const fromWest = mark(west)
  west.activateItem()
  let pushed = 0
  await waitFor(() => {
    const p = east.entity.position
    pushed = Math.hypot(p.x - start.x, p.z - start.z)
    return pushed > 2 || west.chats.slice(fromWest).some((line) => /Victory!/.test(line))
  }, 5000, `the fireball to push the east bot (moved ${pushed.toFixed(1)} blocks)`)
  if (east.health < 20) throw new Error(`the fireball hurt the east bot: health ${east.health}`)
  if (!west.chats.slice(fromWest).some((line) => /Victory!/.test(line))) {
    // still in the game (the end of a game takes the kit away): no limit, the stack is full again
    if (countOf(west, 'fire_charge') !== 64) throw new Error(`the stack of fireballs is not full again: ${countOf(west, 'fire_charge')}`)
  }
  log(`fireball fight: the fireball pushed the east bot ${pushed.toFixed(1)} blocks, unhurt (west: ${countOf(west, 'fire_charge')} fireballs)`)

  // 3. the east bot walks off the platform (if the fireball did not throw it off): the west bot wins
  if (!west.chats.slice(fromWest).some((line) => /Victory!/.test(line))) {
    await east.lookAt(east.entity.position.offset(20, 1.62, 0), true)
    east.setControlState('forward', true)
    try {
      await chatLine(west, /Victory!/, 15000, fromWest)
    } finally {
      east.clearControlStates()
    }
  }
  log('fireball fight: the east bot fell off the platform, the west bot won')
  await expectInLobby(west, 'after the fireball fight')
  await expectInLobby(east, 'after the fireball fight')
}

async function main () {
  const one = await arrive('RagoFunUn')
  const two = await arrive('RagoFunDeux')
  Vec3 = one.entity.position.constructor
  await snowballSpleef(one, two)
  await fireballFight(one, two)
  finishing = true
  log('PASSED: the Snowball Spleef and the Fireball Fight of FUN PRACTICE')
  one.quit()
  two.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
