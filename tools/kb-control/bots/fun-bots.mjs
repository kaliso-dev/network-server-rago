// FUN PRACTICE test bots for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Bots play the modes of FUN PRACTICE (plan 6.1) on a local 1.8.8 test server, with the bundled
// settings (one round each): two of them the duels, three the Fireball Fight:
//   1. Snowball Spleef (snowball-spleef): two floors of snow, both bots on the top one, a diamond
//      shovel each; the west bot breaks a block of snow with it and gets a snowball, throws the
//      snowball on the floor (one block of snow less where it lands), then throws snowballs at the
//      east bot until one reaches it: it pushes it, without hurting it nor breaking snow there;
//      then the east bot breaks the snow under its own feet, lands on the second floor, 9 blocks
//      down (still in the game), throws its snowball up at the top floor (it breaks nothing there:
//      no floor breaks from below), walks towards the middle, breaks the second floor and lands on
//      the narrow third one, 5 blocks down (the owner's ask of 23/11), breaks it too, falls under
//      it, and the west bot wins;
//   2. Fireball Fight (fireball-knockback), three bots each for themselves on the platform of 20
//      blocks: 64 fireballs and 64 wool of their colour each (red, blue, green), no feather, the
//      double jump from the start; the blue bot hits the green one three times (no push, no harm:
//      no hits), the green one double jumps looking up (it goes up, a second one at once must wait,
//      it lands unhurt), the red one places a block of its wool (its stack stays full, the block
//      goes after 3 seconds) and throws a fireball at the green one (pushed away, unhurt, the stack
//      full); then the blue one walks off the platform (out, the others play on), the green one
//      too: the red one, the last one on, wins;
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
const SPLEEF_GAP = 9
/** The bundled narrow third floor (snowball-spleef.etage-etroit-rayon and etage-etroit-ecart). */
const SPLEEF_NARROW_RADIUS = 9
const SPLEEF_NARROW_GAP = 5
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
 * Breaks every block of snow this bot stands on: its feet, 0.6 wide, rest on up to four blocks
 * (stopped at x 6.1, on the blocks 5 and 6, it stayed on the 5 once the 6 was broken).
 */
async function digUnder (bot) {
  const p = bot.entity.position
  const done = new Set()
  for (const dx of [-0.299, 0.299]) {
    for (const dz of [-0.299, 0.299]) {
      const at = new Vec3(Math.floor(p.x + dx), Math.floor(p.y - 0.5), Math.floor(p.z + dz))
      if (done.has(at.toString())) continue
      done.add(at.toString())
      if (bot.blockAt(at)?.name === 'snow') await dig(bot, at)
    }
  }
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

  // 3. a snowball thrown at the east bot hits it: it pushes it, without hurting it nor breaking snow
  // there. A throw that misses lands on the floor behind it (a block of snow less): again, three
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
  if (pushes === 0 || moved < 0.2) throw new Error(`the snowball did not push the east bot: ${pushes} velocity packets, moved ${moved.toFixed(2)} blocks`)
  if (east.health < 20) throw new Error(`the snowball hurt the east bot: health ${east.health}`)
  log(`snowball spleef: snowball ${reached} hit the east bot: pushed ${moved.toFixed(2)} blocks, unhurt, no snow broken there`)

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

  // 5. from the second floor, a snowball thrown up at the top floor, three blocks aside from the
  // hole: it lands on it and breaks nothing
  await waitFor(() => countOf(east, 'snowball') === 1, 3000, `a snowball for the block the east bot broke (has ${countOf(east, 'snowball')})`)
  const top = snowLeft(east, game)
  await hold(east, 'snowball')
  const e = east.entity.position
  const aim = new Vec3(Math.floor(e.x) + 3.5, game.centre.y, Math.floor(e.z) + 0.5)
  await east.lookAt(aim, true)
  await sleep(200)
  // The server sends a snowball's place every 10 ticks only: one that hits the floor above in fewer
  // is never seen moving. Its flight time tells where it went: the ticks of 1.8 snowball physics
  // from the eyes to the underside of the top floor along the look (a block in its way at once
  // would take it away in a tick or two, a miss through the hole would fly for a second and more).
  const flightTicks = ticksToCeiling(e.offset(0, 1.62, 0), aim, game.centre.y)
  let ball = null
  let thrownAt = null
  let goneAt = null
  const onSpawn = (entity) => {
    if (ball == null && entity !== east.entity && entity.type !== 'player' && entity.position.distanceTo(e.offset(0, 1.62, 0)) < 3) {
      ball = entity.id
      thrownAt = Date.now()
    }
  }
  const onGone = (entity) => { if (ball != null && entity.id === ball) goneAt = Date.now() }
  east.on('entitySpawn', onSpawn)
  east.on('entityGone', onGone)
  try {
    east.activateItem()
    await waitFor(() => goneAt != null, 5000, `the snowball thrown up to land (${ball == null ? 'never seen' : 'seen, never gone'})`)
  } finally {
    east.removeListener('entitySpawn', onSpawn)
    east.removeListener('entityGone', onGone)
  }
  await sleep(800)
  const flight = goneAt - thrownAt
  if (flightTicks == null) throw new Error('the aim of the snowball misses the top floor')
  if (flight < (flightTicks - 2) * 50 || flight > (flightTicks + 10) * 50) {
    throw new Error(`the snowball thrown up did not land on the top floor: gone after ${flight} ms, the floor ${flightTicks} ticks away`)
  }
  if (snowLeft(east, game) !== top) throw new Error(`a snowball thrown from the second floor broke the top floor: ${top} blocks before, ${snowLeft(east, game)} now`)
  log(`snowball spleef: from the second floor, a snowball thrown up landed on the top floor (after ${flight} ms, ${flightTicks} ticks away) and broke nothing there`)
  // 6. towards the middle, over the narrow floor (the snowball pushed it away), then down on it
  const middle = new Vec3(game.centre.x + 0.5, east.entity.position.y + 1.62, game.centre.z + 0.5)
  const overNarrow = () => Math.hypot(east.entity.position.x - middle.x, east.entity.position.z - middle.z) < SPLEEF_NARROW_RADIUS - 3
  if (!overNarrow()) {
    await east.lookAt(middle, true)
    east.setControlState('forward', true)
    try {
      await waitFor(overNarrow, 8000, () => `the east bot over the narrow floor, walking to the middle (at ${where(east)})`)
    } finally {
      east.setControlState('forward', false)
    }
    await waitFor(() => east.entity.onGround, 3000, 'the east bot to stop')
    await sleep(300)
  }
  await hold(east, 'diamond_shovel')
  await digUnder(east)
  const narrowY = game.centre.y - SPLEEF_GAP - SPLEEF_NARROW_GAP
  await waitFor(() => east.entity.onGround && east.entity.position.y < narrowY + 1.5, 5000,
    `the east bot on the narrow third floor, ${SPLEEF_NARROW_GAP} blocks under the second (at ${where(east)})`)
  const narrow = east.blockAt(east.entity.position.offset(0, -0.5, 0))
  if (narrow?.name !== 'snow') throw new Error(`the east bot does not stand on the snow of the narrow floor: ${blockText(narrow)} at ${where(east)}`)
  // narrow: no snow a block past its radius, along x from the middle
  const past = east.blockAt(new Vec3(game.centre.x + SPLEEF_NARROW_RADIUS + 1, narrowY, game.centre.z))
  if (past?.name === 'snow') throw new Error(`the third floor is not narrow: snow ${SPLEEF_NARROW_RADIUS + 1} blocks from the middle`)
  await sleep(500)
  if (west.chats.slice(fromWest).some((line) => /Victory!/.test(line))) throw new Error('the game ended on the narrow floor')
  log(`snowball spleef: the east bot walked to the middle, broke the second floor and landed on the narrow third one (${where(east)}), still in the game`)
  await digUnder(east)
  await chatLine(west, /Victory!/, 10000, fromWest)
  log('snowball spleef: the east bot fell under the narrow third floor, the west bot won')
  await expectInLobby(west, 'after the snowball spleef')
  await expectInLobby(east, 'after the snowball spleef')
}

/**
 * The ticks a snowball thrown from these eyes towards this point takes to reach the underside of
 * the floor at this y, by the physics of 1.8 (EntityProjectile: 1.5 blocks a tick along the look,
 * then each tick it moves, slows to 0.99 and falls 0.03); null if it never gets there.
 */
function ticksToCeiling (eyes, aim, floorY) {
  const dx = aim.x - eyes.x
  const dy = aim.y - eyes.y
  const dz = aim.z - eyes.z
  const length = Math.hypot(dx, dy, dz)
  let x = eyes.x
  let y = eyes.y - 0.1
  let z = eyes.z
  let vx = dx / length * 1.5
  let vy = dy / length * 1.5
  let vz = dz / length * 1.5
  for (let tick = 1; tick <= 100; tick++) {
    x += vx
    y += vy
    z += vz
    if (y >= floorY) return tick
    vx *= 0.99
    vy = vy * 0.99 - 0.03
    vz *= 0.99
  }
  return null
}

/** The wool of each side of the Fireball Fight, by its colour (FireballKnockback.SIDE_WOOL): red, blue, green. */
const FIREBALL_WOOL = { 14: 'red', 11: 'blue', 5: 'green' }
/** The bundled radius of the Fireball Fight platform (fireball-knockback.rayon) and the start, half of it from the middle. */
const FIREBALL_RADIUS = 20
const FIREBALL_START = 10
/** The bundled life of a block placed in the Fireball Fight (fireball-knockback.blocs-secondes). */
const FIREBALL_BLOCK_SECONDS = 3

/** The metadata of the wool in the bot's inventory (null without any). */
function woolColour (bot) {
  return bot.inventory.items().find((item) => item.name === 'wool')?.metadata ?? null
}

/** Jump pressed twice in the air, as the 1.8 client says it: "I fly now" (the abilities, the flying flag). */
function doubleJump (bot) {
  bot._client.write('abilities', { flags: 2, flyingSpeed: 0.05, walkingSpeed: 0.1 })
}

/**
 * The bot walks away from the middle of the platform until it falls off (letting go once in the air,
 * so that it falls by the edge, in the chunks of the platform: mineflayer takes the empty chunks of
 * the void on 1.8 for chunks gone, and a bot in one stops moving): its fall is told to the others.
 */
async function walkOffPlatform (bot, centre, others) {
  const p = bot.entity.position
  const away = Math.atan2(p.x - (centre.x + 0.5), p.z - (centre.z + 0.5))
  await bot.lookAt(p.offset(Math.sin(away) * 30, 1.62, Math.cos(away) * 30), true)
  const from = others.map(mark)
  const letGo = () => { if (!bot.entity.onGround) bot.setControlState('forward', false) }
  bot.on('physicsTick', letGo)
  bot.setControlState('forward', true)
  try {
    await waitFor(() => others.every((o, i) => o.chats.slice(from[i]).some((line) => line.includes(`${bot.username} fell into the void`)
      || line.includes(`${bot.username} was knocked into the void`))), 15000, `${bot.username} to fall off the platform (at ${where(bot)})`)
  } finally {
    bot.removeListener('physicsTick', letGo)
    bot.clearControlStates()
  }
}

async function fireballFight (one, two, three) {
  const bots = [one, two, three]
  const from = bots.map(mark)
  for (const bot of bots) {
    bot.lastAbilities = null
    bot._client.on('abilities', (packet) => { bot.lastAbilities = packet })
    bot.chat('/queue fireball-knockback')
  }
  for (const [i, bot] of bots.entries()) {
    const others = bots.filter((other) => other !== bot)
    await chatLine(bot, new RegExp(`Fireball Fight against .*(${others[0].username}.*${others[1].username}|${others[1].username}.*${others[0].username})`),
      15000, from[i])
  }
  await waitFor(() => bots.every((bot) => bot.game.gameMode === 'survival'), 5000, 'survival mode in the Fireball Fight')
  await sleep(500)
  // the sides by the colour of their wool, the middle of the platform between the three starts
  const side = {}
  for (const bot of bots) {
    const colour = FIREBALL_WOOL[woolColour(bot)]
    if (colour == null || side[colour] != null) throw new Error(`${bot.username} has no wool of its own colour: ${woolColour(bot)}`)
    side[colour] = bot
  }
  const { red, blue, green } = side
  const mid = bots.reduce((sum, bot) => ({ x: sum.x + bot.entity.position.x / 3, z: sum.z + bot.entity.position.z / 3 }), { x: 0, z: 0 })
  const centre = { x: Math.floor(mid.x), y: Math.floor(red.entity.position.y) - 1, z: Math.floor(mid.z) }
  for (const bot of bots) {
    const p = bot.entity.position
    const distance = Math.hypot(p.x - (centre.x + 0.5), p.z - (centre.z + 0.5))
    if (Math.abs(distance - FIREBALL_START) > 0.6) throw new Error(`${bot.username} starts ${distance.toFixed(2)} from the middle, not ${FIREBALL_START}`)
    if (countOf(bot, 'fire_charge') !== 64) throw new Error(`${bot.username} has ${countOf(bot, 'fire_charge')} fireballs, not 64`)
    if (countOf(bot, 'wool') !== 64) throw new Error(`${bot.username} has ${countOf(bot, 'wool')} wool, not 64`)
    if (countOf(bot, 'feather') !== 0) throw new Error(`${bot.username} still has the feather of the wind gust`)
    const under = bot.blockAt(p.offset(0, -0.5, 0))
    if (under?.name !== 'quartz_block') throw new Error(`${bot.username} does not stand on quartz: ${blockText(under)}`)
    if (bot.lastAbilities == null || (bot.lastAbilities.flags & 4) === 0) {
      throw new Error(`${bot.username} may not double jump from the start (abilities: ${JSON.stringify(bot.lastAbilities)})`)
    }
  }
  const inside = red.blockAt(new Vec3(centre.x - FIREBALL_RADIUS, centre.y, centre.z))
  const beyond = red.blockAt(new Vec3(centre.x - FIREBALL_RADIUS - 1, centre.y, centre.z))
  if (inside?.name !== 'quartz_block' || beyond?.name === 'quartz_block') {
    throw new Error(`the platform does not end ${FIREBALL_RADIUS} blocks from its middle: ${blockText(inside)}, then ${blockText(beyond)}`)
  }
  for (const [i, bot] of bots.entries()) await chatLine(bot, /Fight!/, 10000, from[i])
  log(`fireball fight: red ${red.username}, blue ${blue.username}, green ${green.username}, ${FIREBALL_START} blocks from the middle of a`
    + ` platform of ${FIREBALL_RADIUS}; 64 fireballs and 64 wool of their colour each, no feather, the double jump from the start`)

  // 1. no hits: the blue bot walks up to the green one and hits it, which neither pushes nor hurts it
  await blue.lookAt(green.entity.position.offset(0, 1.62, 0), true)
  blue.setControlState('forward', true)
  try {
    await waitFor(() => blue.entity.position.distanceTo(green.entity.position) < 2.5, 10000,
      `the blue bot to walk up to the green one (${blue.entity.position.distanceTo(green.entity.position).toFixed(1)} apart)`)
  } finally {
    blue.clearControlStates()
  }
  await sleep(500)
  const still = green.entity.position.clone()
  let pushes = 0
  const onVelocity = (packet) => { if (packet.entityId === green.entity.id) pushes++ }
  green._client.on('entity_velocity', onVelocity)
  try {
    for (let hit = 0; hit < 3; hit++) {
      await blue.lookAt(green.entity.position.offset(0, 1.5, 0), true)
      blue.attack(blue.entities[green.entity.id] ?? Object.values(blue.entities).find((e) => e.username === green.username))
      await sleep(400)
    }
    await sleep(500)
  } finally {
    green._client.removeListener('entity_velocity', onVelocity)
  }
  const moved = green.entity.position.distanceTo(still)
  if (pushes > 0 || moved > 0.1) throw new Error(`a hit pushed the green bot: ${pushes} velocity packets, moved ${moved.toFixed(2)} blocks`)
  if (green.health < 20) throw new Error(`a hit hurt the green bot: health ${green.health}`)
  log('fireball fight: three hits of the blue bot on the green one, neither pushed nor hurt it')
  // the blue bot steps back towards the middle, out of the blast of the fireball to come
  await blue.lookAt(new Vec3(centre.x + 0.5, blue.entity.position.y + 1.62, centre.z + 0.5), true)
  blue.setControlState('forward', true)
  await sleep(1500)
  blue.clearControlStates()

  // 2. the double jump, looking up: up it goes; a second one at once must wait; it lands unhurt
  await green.look(green.entity.yaw, Math.PI / 2, true)
  await sleep(200)
  const ground = green.entity.position.y
  let highest = ground
  const onMove = () => { highest = Math.max(highest, green.entity.position.y) }
  green.on('move', onMove)
  try {
    green.setControlState('jump', true)
    await waitFor(() => !green.entity.onGround, 2000, 'the green bot to jump')
    green.setControlState('jump', false)
    doubleJump(green)
    await waitFor(() => highest > ground + 2, 3000, `the double jump to lift the green bot (up to ${highest}, from ${ground})`)
    const fromGreen = mark(green)
    doubleJump(green)
    await chatLine(green, /Double jump ready in \d+ seconds?\./, 3000, fromGreen)
    await waitFor(() => green.entity.onGround && Math.abs(green.entity.position.y - ground) < 0.1, 10000,
      `the green bot back on the platform (at ${where(green)})`)
  } finally {
    green.removeListener('move', onMove)
    green.clearControlStates()
  }
  await sleep(300)
  if (green.health < 20) throw new Error(`the green bot was hurt by its fall after the double jump: health ${green.health}`)
  if (green.game.gameMode !== 'survival') throw new Error(`the green bot is not in the fight after its double jump: ${green.game.gameMode}`)
  log(`fireball fight: the double jump lifted the green bot ${(highest - ground).toFixed(1)} blocks, a second one at once had to wait,`
    + ' it landed unhurt')

  // 3. the wool: a block placed on the platform, of the bot's colour; the stack stays full; the block
  // goes after its seconds
  await hold(red, 'wool')
  const feet = red.entity.position.floored()
  const placedAt = new Vec3(feet.x, feet.y, feet.z + 1)
  const placedWhen = Date.now()
  await red.placeBlock(red.blockAt(placedAt.offset(0, -1, 0)), new Vec3(0, 1, 0)).catch(() => {})
  await waitFor(() => red.blockAt(placedAt)?.name === 'wool', 5000, `the red bot to place wool at ${placedAt} (there: ${blockText(red.blockAt(placedAt))})`)
  if (red.blockAt(placedAt).metadata !== 14) throw new Error(`the red bot's wool is not red: ${blockText(red.blockAt(placedAt))}`)
  await waitFor(() => countOf(red, 'wool') === 64, 3000, `the stack of wool full again (has ${countOf(red, 'wool')})`)
  await waitFor(() => red.blockAt(placedAt)?.name === 'air', (FIREBALL_BLOCK_SECONDS + 3) * 1000,
    `the block of wool to go after ${FIREBALL_BLOCK_SECONDS} seconds (there: ${blockText(red.blockAt(placedAt))})`)
  const lasted = (Date.now() - placedWhen) / 1000
  if (lasted < FIREBALL_BLOCK_SECONDS - 1) throw new Error(`the block of wool went after ${lasted.toFixed(1)} seconds only`)
  log(`fireball fight: a block of red wool placed, the stack still full, the block gone after ${lasted.toFixed(1)} seconds`)

  // 4. a fireball of the red bot at the green one: pushed away, unhurt; the red bot's stack stays full
  const start = green.entity.position.clone()
  await hold(red, 'fire_charge')
  await red.lookAt(green.entity.position.offset(0, 1, 0), true)
  await sleep(200)
  const fromRed = mark(red)
  red.activateItem()
  let pushed = 0
  await waitFor(() => {
    const p = green.entity.position
    pushed = Math.hypot(p.x - start.x, p.z - start.z)
    return pushed > 2 || green.game.gameMode === 'spectator'
  }, 5000, `the fireball to push the green bot (moved ${pushed.toFixed(1)} blocks)`)
  if (green.health < 20) throw new Error(`the fireball hurt the green bot: health ${green.health}`)
  if (red.chats.slice(fromRed).some((line) => /Victory!/.test(line))) throw new Error('the game ended with two players still on the platform')
  if (countOf(red, 'fire_charge') !== 64) throw new Error(`the stack of fireballs is not full again: ${countOf(red, 'fire_charge')}`)
  log(`fireball fight: the fireball pushed the green bot ${pushed.toFixed(1)} blocks, unhurt (red: ${countOf(red, 'fire_charge')} fireballs)`)
  await sleep(1500)

  // 5. the blue bot walks off the platform: out, the two others play on; then the green bot (if the
  // fireball did not throw it off already): the red bot, the last one on, wins
  const greenOut = green.game.gameMode === 'spectator'
  if (greenOut) log('fireball fight: the fireball threw the green bot off the platform: it watches, the two others play on')
  await walkOffPlatform(blue, centre, [red, green])
  if (!greenOut) {
    await waitFor(() => blue.game.gameMode === 'spectator', 5000, 'the blue bot to watch, out of the round')
    if (red.chats.slice(fromRed).some((line) => /Victory!/.test(line))) throw new Error('the game ended with two players still on the platform')
    log('fireball fight: the blue bot fell off the platform and watches, the two others play on')
    await walkOffPlatform(green, centre, [red, blue])
  }
  await chatLine(red, /Victory!/, 10000, fromRed)
  for (const bot of [blue, green]) await chatLine(bot, /Defeat/, 10000, from[bots.indexOf(bot)])
  log('fireball fight: the red bot, the last one on the platform, won')
  for (const bot of bots) await expectInLobby(bot, 'after the fireball fight')
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
  const three = await arrive('RagoFunTrois')
  Vec3 = one.entity.position.constructor
  await snowballSpleef(one, two)
  await fireballFight(one, two, three)
  await parkourDuel(one, two)
  finishing = true
  log('PASSED: the Snowball Spleef, the Fireball Fight (three players) and the Parkour Duel of FUN PRACTICE')
  one.quit()
  two.quit()
  three.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
