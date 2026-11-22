// FUN PRACTICE test bots for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Two bots play the two modes of FUN PRACTICE (plan 6.1) on a local 1.8.8 test server, with the
// bundled settings (one round each):
//   1. Snowball Spleef (snowball-spleef): two floors of snow, both bots on the top one, a diamond
//      shovel each; the west bot breaks a block of snow with it and gets a snowball, throws the
//      snowball on the floor (one block of snow less where it lands), then throws snowballs at the
//      east bot until one reaches it: it stops there, without pushing it nor breaking snow (the
//      players cannot hit each other); then the east bot breaks the snow under its own feet, lands
//      on the second floor (still in the game), breaks it again, falls under it, and the west bot
//      wins;
//   2. Fireball Fight (fireball-knockback): 64 fireballs and the wind gust (a feather) each; the
//      east bot uses the gust looking up (it goes up, a second gust at once must wait, it lands
//      unhurt), the west bot throws a fireball at it (the east bot is pushed away, unhurt, and the
//      west bot's stack stays full), then the east bot walks off the platform: the west bot wins;
//   3. Parkour Duel (parkour-duel): each bot on its lane (red and blue clay), no kit; the blue bot
//      walks off its start and is put back on it (no point), the red bot runs the course
//      (parkour-runner.mjs: each jump planned with the physics of 1.8) to the first checkpoint,
//      walks off it and is put back on it, then runs on through the four checkpoints to the
//      finish: it wins.
// Both back in the lobby after each game.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'
import { createRunner, runCourse, settle } from './parkour-runner.mjs'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 8 * 60 * 1000
/** The bundled radius of the snow floors (snowball-spleef.rayon): the west start is 9 from the middle. */
const SPLEEF_RADIUS = 18
/** The bundled blocks from the top floor down to the second one (snowball-spleef.ecart-etages). */
const SPLEEF_GAP = 6
/** The course of the Parkour Duel (ParkourCourse.java): the start, from the middle of a lane, facing +x. */
const PARKOUR_START = { x: -47.5, y: 1, z: 0.5 }
/** Each lane this far from the middle of the arena, across: red at -5, blue at +5. */
const PARKOUR_LANE_Z = 5
/** The middle of the first checkpoint (x -32 to -30), from the middle of a lane. */
const PARKOUR_CHECKPOINT_1 = { x: -30.5, y: 1, z: 0.5 }

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

/**
 * The pitch (mineflayer's: up is positive) that throws a snowball from a player standing at `from`
 * to the middle of the player standing at `to`, as the server moves it: 1.5 blocks a tick from 0.1
 * under the eyes, then each tick 0.99 of its speed and 0.03 down.
 */
function snowballPitch (from, to) {
  const distance = Math.hypot(to.x - from.x, to.z - from.z)
  const start = from.y + 1.62 - 0.1
  const target = to.y + 0.9
  const heightThere = (pitch) => {
    let along = 0
    let y = start
    let across = 1.5 * Math.cos(pitch)
    let up = 1.5 * Math.sin(pitch)
    for (let tick = 0; tick < 200; tick++) {
      if (along + across >= distance) return y + up * (distance - along) / across
      along += across
      y += up
      across *= 0.99
      up = up * 0.99 - 0.03
    }
    return -Infinity
  }
  let low = -0.5
  let high = 0.8
  for (let i = 0; i < 40; i++) {
    const middle = (low + high) / 2
    if (heightThere(middle) < target) low = middle
    else high = middle
  }
  return (low + high) / 2
}

async function snowballSpleef (one, two) {
  const game = await startDuel(one, two, 'snowball-spleef', 'Snowball Spleef', 9)
  const { west, east, at } = game
  for (const bot of [west, east]) {
    const under = bot.blockAt(bot.entity.position.offset(0, -0.5, 0))
    if (under?.name !== 'snow') throw new Error(`${bot.username} does not stand on snow: ${blockText(under)} at ${where(bot)}`)
  }
  const floor = snowLeft(west, game)
  if (floor < 300) throw new Error(`the floor of snow has ${floor} blocks only`)
  log(`snowball spleef: both on the snow floor (${floor} blocks), the west bot at ${where(west)}`)

  // 1. the shovel breaks the snow, a snowball for it
  const second = west.blockAt(at(0, -SPLEEF_GAP, 0))
  if (second?.name !== 'snow') throw new Error(`no second floor of snow ${SPLEEF_GAP} blocks under the top one: ${blockText(second)}`)
  log('snowball spleef: a second floor of snow under the top one')

  await hold(west, 'diamond_shovel')
  await dig(west, at(-7, 0, 0))
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

  // 3. no hits: a snowball thrown at the east bot stops on it, without pushing it nor breaking
  // snow. A throw that misses lands on the floor behind it (a block of snow less): again, three
  // times at most.
  await hold(west, 'diamond_shovel')
  for (const dx of [-7, -6, -5]) await dig(west, at(dx, 0, 2))
  await waitFor(() => countOf(west, 'snowball') === 3, 3000, `a snowball for each of the 3 blocks broken (has ${countOf(west, 'snowball')})`)
  await hold(west, 'snowball')
  const standing = east.entity.position.clone()
  let pushes = 0
  const onVelocity = (packet) => { if (packet.entityId === east.entity.id) pushes++ }
  east._client.on('entity_velocity', onVelocity)
  let reached = 0
  try {
    for (let throwNo = 1; throwNo <= 3 && reached === 0; throwNo++) {
      const snow = snowLeft(west, game)
      const w = west.entity.position
      await west.look(Math.atan2(-(standing.x - w.x), -(standing.z - w.z)), snowballPitch(w, standing), true)
      await sleep(200)
      // the snowball: the entity that appears by the west bot's eyes after the throw (mineflayer
      // does not name the objects of 1.8 well), until it goes
      let ball = null
      const gone = new Promise((resolve) => {
        const onSpawn = (entity) => {
          if (ball == null && entity !== west.entity && entity.type !== 'player' && entity.position.distanceTo(w.offset(0, 1.62, 0)) < 3) {
            ball = entity.id
          }
        }
        const onGone = (entity) => {
          if (ball != null && entity.id === ball) {
            west.removeListener('entitySpawn', onSpawn)
            west.removeListener('entityGone', onGone)
            resolve(true)
          }
        }
        west.on('entitySpawn', onSpawn)
        west.on('entityGone', onGone)
      })
      west.activateItem()
      if (!(await Promise.race([gone, sleep(5000).then(() => false)]))) {
        throw new Error(`the snowball thrown at the east bot never landed (${ball == null ? 'never seen' : 'seen, never gone'})`)
      }
      await sleep(800)
      if (snowLeft(west, game) === snow) reached = throwNo
      else log(`snowball spleef: throw ${throwNo} at the east bot missed it (a block of snow less behind it)`)
    }
    await sleep(500)
  } finally {
    east._client.removeListener('entity_velocity', onVelocity)
  }
  if (reached === 0) throw new Error('three snowballs thrown at the east bot, none reached it')
  const moved = Math.hypot(east.entity.position.x - standing.x, east.entity.position.z - standing.z)
  if (pushes > 0 || moved > 0.1) throw new Error(`the snowball pushed the east bot: ${pushes} velocity packets, moved ${moved.toFixed(2)} blocks`)
  if (east.health < 20) throw new Error(`the snowball hurt the east bot: health ${east.health}`)
  log(`snowball spleef: snowball ${reached} reached the east bot and stopped there, without pushing it nor breaking snow`)

  // 4. the east bot breaks the snow under itself, lands on the second floor (still in the game),
  // breaks it again and falls under it: the west bot wins
  const fromWest = mark(west)
  await hold(east, 'diamond_shovel')
  await dig(east, east.entity.position.offset(0, -0.5, 0).floored())
  await waitFor(() => east.entity.onGround && east.entity.position.y < game.centre.y - SPLEEF_GAP + 1.5, 5000,
    `the east bot on the second floor (at ${where(east)})`)
  const below = east.blockAt(east.entity.position.offset(0, -0.5, 0))
  if (below?.name !== 'snow') throw new Error(`the east bot does not stand on the snow of the second floor: ${blockText(below)} at ${where(east)}`)
  await sleep(500)
  if (west.chats.slice(fromWest).some((line) => /Victory!/.test(line))) throw new Error('the game ended on the second floor')
  if (east.health < 20) throw new Error(`the fall to the second floor hurt the east bot: health ${east.health}`)
  log(`snowball spleef: the east bot fell on the second floor (${where(east)}), unhurt and still in the game`)
  await dig(east, east.entity.position.offset(0, -0.5, 0).floored())
  await chatLine(west, /Victory!/, 10000, fromWest)
  log('snowball spleef: the east bot fell under the second floor, the west bot won')
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

/**
 * The bot walks this way (a yaw) off the edge, lets go once in the air, and waits for the server to
 * put it somewhere else (a fall: back on its checkpoint). Towards the gap between the lanes: the
 * chunks of the void around the course are empty, which mineflayer takes on 1.8 for chunks gone,
 * and a bot in one stops moving.
 */
async function walkOff (bot, yaw, what) {
  bot.entity.yaw = yaw
  const moved = new Promise((resolve) => bot.once('forcedMove', resolve))
  const letGo = () => { if (!bot.entity.onGround) bot.setControlState('forward', false) }
  bot.on('physicsTick', letGo)
  bot.setControlState('forward', true)
  try {
    if (!(await Promise.race([moved.then(() => true), sleep(8000).then(() => false)]))) {
      throw new Error(`${bot.username} walked off ${what} and was not put back (at ${where(bot)})`)
    }
  } finally {
    bot.removeListener('physicsTick', letGo)
    bot.clearControlStates()
  }
  await settle(bot)
}

function near (bot, place, what) {
  const p = bot.entity.position
  if (Math.abs(p.x - place.x) > 0.6 || Math.abs(p.z - place.z) > 0.6 || Math.abs(p.y - place.y) > 0.2) {
    throw new Error(`${bot.username} is not ${what}: at ${where(bot)}, not ${place.x}, ${place.y}, ${place.z}`)
  }
}

async function parkourDuel (one, two) {
  const fromOne = mark(one)
  const fromTwo = mark(two)
  one.chat('/queue parkour-duel')
  two.chat('/queue parkour-duel')
  await chatLine(one, new RegExp(`Parkour Duel against ${two.username}`), 15000, fromOne)
  await chatLine(two, new RegExp(`Parkour Duel against ${one.username}`), 15000, fromTwo)
  await waitFor(() => one.game.gameMode === 'survival' && two.game.gameMode === 'survival', 5000, 'survival mode in the duel')
  await sleep(500)
  // red (side 0) on the lane at z -5 from the middle, blue at +5, both at the start
  const [red, blue] = one.entity.position.z < two.entity.position.z ? [one, two] : [two, one]
  const fromRed = red === one ? fromOne : fromTwo
  const fromBlue = blue === one ? fromOne : fromTwo
  const p = red.entity.position
  const centre = { x: Math.round(p.x - PARKOUR_START.x), y: Math.round(p.y - PARKOUR_START.y), z: Math.round(p.z - PARKOUR_START.z + PARKOUR_LANE_Z) }
  const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
  const start = (lane) => ({ x: centre.x + PARKOUR_START.x, y: centre.y + PARKOUR_START.y, z: centre.z + lane + PARKOUR_START.z })
  near(blue, start(PARKOUR_LANE_Z), 'at the start of the blue lane')
  for (const bot of [red, blue]) {
    if (bot.inventory.items().length > 0) throw new Error(`${bot.username} has a kit: ${bot.inventory.items().map((item) => item.name)}`)
  }
  for (const [lane, colour, name] of [[-PARKOUR_LANE_Z, 14, 'red'], [PARKOUR_LANE_Z, 11, 'blue']]) {
    const checks = [[at(-48, 0, lane), 'quartz_block', 0, 'the start'], [at(-50, 0, lane - 2), 'stained_hardened_clay', colour, 'the border of the start'],
      [at(-43, 0, lane), 'quartz_block', 2, 'the first pillar'], [at(-31, 1, lane), 'light_weighted_pressure_plate', 0, 'the plate of the first checkpoint']]
    for (const [place, block, data, what] of checks) {
      const there = red.blockAt(place)
      if (there?.name !== block || there.metadata !== data) {
        throw new Error(`${what} of the ${name} lane at ${place} is ${blockText(there)}, not ${block}:${data}`)
      }
    }
  }
  log(`parkour duel: both at the start of their lane (red and blue clay, quartz pillars), no kit; centre ${JSON.stringify(centre)}`)
  await chatLine(red, /Fight!/, 10000, fromRed)
  await chatLine(blue, /Fight!/, 10000, fromBlue)

  // 1. the blue bot walks off its start (north, into the gap between the lanes): back on its start, no point
  await walkOff(blue, 0, 'its start')
  near(blue, start(PARKOUR_LANE_Z), 'back at its start')
  if (blue.health < 20) throw new Error(`the fall hurt the blue bot: health ${blue.health}`)
  if ([...red.chats.slice(fromRed), ...blue.chats.slice(fromBlue)].some((line) => /Point for|Victory!|Defeat/.test(line))) {
    throw new Error('a fall gave a point')
  }
  log('parkour duel: the blue bot fell off its start and was put back on it, no point')

  // 2. the red bot runs to the first checkpoint, walks off it (south, into the gap): back on it
  const world = { getBlock: (pos) => red.blockAt(pos, false) }
  const runner = createRunner(red.registry, world)
  const lane = { zMin: centre.z - PARKOUR_LANE_Z - 2, zMax: centre.z - PARKOUR_LANE_Z + 2 }
  const seen = (pattern) => () => red.chats.slice(fromRed).some((line) => pattern.test(line))
  const first = await runCourse(red, world, runner, lane, { until: seen(/Checkpoint 1\/4!/), log: (line) => log(`parkour duel: ${line}`) })
  log(`parkour duel: the red bot reached checkpoint 1 in ${first.jumps} jumps (${first.misses} missed)`)
  await walkOff(red, Math.PI, 'the first checkpoint')
  const checkpoint = { x: centre.x + PARKOUR_CHECKPOINT_1.x, y: centre.y + PARKOUR_CHECKPOINT_1.y, z: centre.z - PARKOUR_LANE_Z + PARKOUR_CHECKPOINT_1.z }
  near(red, checkpoint, 'back on the first checkpoint')
  if (red.chats.slice(fromRed).some((line) => /Point for|Victory!|Defeat/.test(line))) throw new Error('a fall gave a point')
  log('parkour duel: the red bot fell off the first checkpoint and was put back on it')

  // 3. on to the finish, through the other checkpoints
  const rest = await runCourse(red, world, runner, lane, { until: seen(/finished the course in/), log: (line) => log(`parkour duel: ${line}`) })
  const lines = red.chats.slice(fromRed)
  let last = 0
  for (let k = 1; k <= 4; k++) {
    const index = lines.findIndex((line) => line.includes(`Checkpoint ${k}/4!`))
    if (index < last) throw new Error(`checkpoint ${k} of 4 not reached in order: ${JSON.stringify(lines)}`)
    last = index
  }
  await chatLine(red, /Victory!/, 10000, fromRed)
  await chatLine(blue, new RegExp(`${red.username} finished the course in`), 5000, fromBlue)
  log(`parkour duel: the red bot reached checkpoints 2, 3 and 4 and the finish in ${rest.jumps} jumps (${rest.misses} missed): it won`)
  await expectInLobby(red, 'after the parkour duel')
  await expectInLobby(blue, 'after the parkour duel')
}

async function main () {
  const one = await arrive('RagoFunUn')
  const two = await arrive('RagoFunDeux')
  Vec3 = one.entity.position.constructor
  await snowballSpleef(one, two)
  await fireballFight(one, two)
  await parkourDuel(one, two)
  finishing = true
  log('PASSED: the Snowball Spleef, the Fireball Fight and the Parkour Duel of FUN PRACTICE')
  one.quit()
  two.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
