// Block in test bot for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// The enemy of the Block in training stands at one side of the player, as a Bed Wars player would:
// it hits them only through that side open, breaks the block they put there (in the time a 1.8
// player takes, with the cracks), and has to break again a block put back. One bot plays the
// training on the plugin's own island (the test server has no map), from its start on a corner of
// the ring of glass:
//   1. it blocks itself in with wool, at its feet and at its head, on the four sides;
//   2. nothing hits it until a block of one side is broken, with its cracks first, not at once;
//   3. it is then hit through that hole, pushed away from that side;
//   4. it puts the block back, at once, again and again: each time, the enemy breaks it again,
//      at the same side, and does not hit it;
//   5. only the blocks of that side were broken: not the other sides, nor the glass under it.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 3 * 60 * 1000
/** How many times the bot puts the block back, and the enemy breaks it again. */
const PUT_BACK = 3

let finishing = false
/** The vector class of mineflayer's positions: new Vec3(x, y, z). */
let Vec3 = null

function abort (error) {
  console.error(`[blockin-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[blockin-bots] ${text}`)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`)
    await sleep(20)
  }
}

const chats = []

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    bot.firstMenu = null
    bot.on('windowOpen', (window) => {
      const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
      if (title.includes('What do you want to do?')) bot.firstMenu = window
    })
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.on('messagestr', (message) => {
      chats.push(message)
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

async function chatLine (pattern, timeoutMs, from) {
  await waitFor(() => chats.slice(from).some((line) => pattern.test(line)), timeoutMs,
    `to read ${pattern}; chat: ${JSON.stringify(chats.slice(from))}`)
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
}

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 15000,
    `${bot.username} in the lobby (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
}

function key (v) {
  return `${v.x},${v.y},${v.z}`
}

function blockText (block) {
  return block == null ? 'not loaded' : `${block.name}:${block.metadata}`
}

/**
 * What the server tells the bot, from now on, each with its time: its own hurt (the enemy's hit)
 * and push, the cracks on a block, and every block that changed.
 */
function record (bot) {
  const events = []
  const started = Date.now()
  bot._client.on('entity_status', (data) => {
    if (data.entityId === bot.entity.id && data.entityStatus === 2) events.push({ t: Date.now() - started, what: 'hit' })
  })
  bot._client.on('entity_velocity', (data) => {
    if (data.entityId === bot.entity.id) {
      // 1/8000 of a block a tick, as 1.8 sends it
      const v = data.velocity
      events.push({ t: Date.now() - started, what: 'push', x: v.x / 8000, y: v.y / 8000, z: v.z / 8000 })
    }
  })
  bot._client.on('block_break_animation', (data) => {
    events.push({ t: Date.now() - started, what: 'cracks', at: key(data.location), stage: data.destroyStage })
  })
  bot.on('blockUpdate', (before, after) => {
    if (after == null) return
    events.push({ t: Date.now() - started, what: 'block', at: key(after.position), from: before?.name, to: after.name })
  })
  events.now = () => Date.now() - started
  return events
}

function story (events, from = 0) {
  return events.slice(from).map((e) => `${e.t} ms ${e.what}${e.at ? ' ' + e.at : ''}${e.stage != null ? ' ' + e.stage : ''}`
    + `${e.to ? ' ' + e.from + '>' + e.to : ''}${e.what === 'push' ? ` (${e.x.toFixed(2)}, ${e.y.toFixed(2)}, ${e.z.toFixed(2)})` : ''}`).join(', ')
}

/**
 * Places the wool in hand against this face of the block at `against`; wool must then be at
 * `where`. Tried again a few times: the server can drop a placement that comes too soon after
 * another (each try is said in the log).
 */
async function place (bot, against, face, where) {
  await waitFor(() => bot.blockAt(against) != null && bot.blockAt(against).name !== 'air', 3000,
    `a block at ${against} to place against (${blockText(bot.blockAt(against))})`)
  for (let attempt = 1; ; attempt++) {
    // looking at once (the default turns the head over several ticks): the enemy comes 2 s after the first block
    const error = await bot._placeBlockWithOptions(bot.blockAt(against), face, { swingArm: 'right', forceLook: true })
      .then(() => null, (e) => e)
    try {
      await waitFor(() => bot.blockAt(where)?.name === 'wool', 400, `wool at ${where}`)
      return
    } catch {
      const p = bot.entity.position
      const why = `wool not placed at ${where} against ${against} (there: ${blockText(bot.blockAt(where))}; ${error?.message ?? 'no error'}; `
        + `the bot at ${p.x.toFixed(2)}, ${p.y.toFixed(2)}, ${p.z.toFixed(2)}, holding ${bot.heldItem?.name ?? 'nothing'} x${bot.heldItem?.count ?? 0})`
      if (attempt >= 4) throw new Error(why)
      log(`try ${attempt}: ${why}`)
      await sleep(150)
    }
  }
}

/** Wool back at `where`, against any block next to it. */
async function putBack (bot, where) {
  for (const face of [[0, -1, 0], [0, 1, 0], [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1]]) {
    const against = where.offset(face[0], face[1], face[2])
    const block = bot.blockAt(against)
    if (block != null && block.name !== 'air' && !against.equals(bot.entity.position.floored())) {
      await place(bot, against, new Vec3(-face[0], -face[1], -face[2]), where)
      return
    }
  }
  throw new Error(`nothing next to ${where} to put the wool back against`)
}

async function main () {
  const bot = await join('RagoCinq')
  Vec3 = bot.entity.position.constructor
  await waitFor(() => bot.firstMenu != null, 8000, 'the menu of the first arrival')
  bot.closeWindow(bot.firstMenu)
  await sleep(200)
  await expectInLobby(bot, 'on joining')

  const from = chats.length
  bot.chat('/queue block-in')
  await chatLine(/Block in practice - /, 15000, from)
  await waitFor(() => bot.game.gameMode === 'survival', 5000, 'survival mode in the training')
  await sleep(1500)
  const feet = bot.entity.position.floored()
  const below = bot.blockAt(feet.offset(0, -1, 0))
  if (below?.name !== 'glass') throw new Error(`the start is not on the ring of glass: ${blockText(below)} under ${feet}`)
  log(`on the ring of glass at ${feet}`)
  const events = record(bot)

  // 1. blocked in: the sides over the glass first (west, north), then east and south on a block beside
  let slot = -1
  for (let i = 0; i < 9; i++) if (hotbarItem(bot, i)?.name === 'wool') slot = i
  if (slot < 0) throw new Error('no wool in the hotbar')
  bot.setQuickBarSlot(slot)
  await sleep(100)
  const up = new Vec3(0, 1, 0)
  const at = (dx, dy, dz) => feet.offset(dx, dy, dz)
  // a block to place against broken meanwhile by the enemy goes back first
  const build = async (against, face, where) => {
    if (bot.blockAt(against)?.name === 'air') await putBack(bot, against)
    await place(bot, against, face, where)
  }
  await build(at(-1, -1, 0), up, at(-1, 0, 0))
  await build(at(-1, 0, 0), up, at(-1, 1, 0))
  await build(at(0, -1, -1), up, at(0, 0, -1))
  await build(at(0, 0, -1), up, at(0, 1, -1))
  await build(at(0, 0, -1), new Vec3(1, 0, 0), at(1, 0, -1))
  await build(at(1, 0, -1), new Vec3(0, 0, 1), at(1, 0, 0))
  await build(at(1, 0, 0), up, at(1, 1, 0))
  await build(at(-1, 0, 0), new Vec3(0, 0, 1), at(-1, 0, 1))
  await build(at(-1, 0, 1), new Vec3(1, 0, 0), at(0, 0, 1))
  await build(at(0, 0, 1), up, at(0, 1, 1))
  const sides = [[1, 0], [-1, 0], [0, 1], [0, -1]]
  const walls = new Set()
  for (const [dx, dz] of sides) {
    walls.add(key(at(dx, 0, dz)))
    walls.add(key(at(dx, 1, dz)))
  }
  // the enemy comes 2 s after the first block, and may break one while the bot builds: put back
  const wallAt = (wall) => new Vec3(...wall.split(',').map(Number))
  for (let round = 0; ; round++) {
    const open = [...walls].filter((wall) => bot.blockAt(wallAt(wall))?.name !== 'wool')
    if (open.length === 0) break
    if (round >= 5) throw new Error(`cannot block itself in: ${open.join(', ')} still open; ${story(events)}`)
    for (const wall of open) await putBack(bot, wallAt(wall))
  }
  const closed = events.now()
  const fromClosed = events.length
  log(`blocked in, the four sides at the feet and at the head, in ${closed} ms`)

  // 2. its first break: cracks first, then the block goes; no hit before
  const isBreak = (e) => e.what === 'block' && walls.has(e.at) && e.to === 'air'
  await waitFor(() => events.slice(fromClosed).some(isBreak), 6000, `a block of the walls broken; since blocked in: ${story(events, fromClosed)}`)
  const brokenAt = events.slice(fromClosed).find(isBreak)
  const hitsBefore = events.slice(fromClosed).filter((e) => e.what === 'hit' && e.t < brokenAt.t)
  if (hitsBefore.length > 0) throw new Error(`hit while blocked in, before any block broken: ${story(events, fromClosed)}`)
  const cracks = events.slice(fromClosed).filter((e) => e.what === 'cracks' && e.at === brokenAt.at && e.stage >= 0 && e.t <= brokenAt.t)
  if (cracks.length === 0) throw new Error(`no cracks on ${brokenAt.at} before it went: ${story(events, fromClosed)}`)
  const digMs = brokenAt.t - cracks[0].t
  // 5 ticks (250 ms) for wool with shears
  if (digMs < 150) throw new Error(`${brokenAt.at} went ${digMs} ms after its first cracks: not in the time a player takes; ${story(events, fromClosed)}`)
  const [bx, by, bz] = brokenAt.at.split(',').map(Number)
  const side = [bx - feet.x, bz - feet.z]
  const sideBlocks = new Set([key(at(side[0], 0, side[1])), key(at(side[0], 1, side[1]))])
  log(`the enemy, at side (${side}), broke ${brokenAt.at} ${digMs} ms after its first cracks (${cracks.length} stages seen)`)

  // 3. hit through the hole, pushed away from that side
  const hole = new Vec3(bx, by, bz)
  const fromBreak = events.indexOf(brokenAt)
  await waitFor(() => events.slice(fromBreak).some((e) => e.what === 'hit'), 4000, `a hit through the hole; since the break: ${story(events, fromBreak)}`)
  const hit = events.slice(fromBreak).find((e) => e.what === 'hit')
  // the push of the hit can come after its hurt (the velocity goes at the end of the server's
  // tick, the hurt at once): it is waited for, 300 ms at most after the hit
  const pushOf = () => events.slice(fromBreak).find((e) => e.what === 'push' && Math.abs(e.t - hit.t) <= 300 && (e.x !== 0 || e.z !== 0))
  const until = Date.now() + 400
  while (pushOf() == null && Date.now() < until) await sleep(20)
  const push = pushOf()
  if (push == null) throw new Error(`no push with the hit: ${story(events, fromBreak)}`)
  const along = push.x * side[0] + push.z * side[1]
  if (along > -0.2) throw new Error(`the hit did not push away from side (${side}): ${story(events, fromBreak)}`)
  log(`hit through the hole ${hit.t - brokenAt.t} ms after the break, pushed away from it`)

  // 4. the block put back, again and again: broken again at the same side, no hit
  await putBack(bot, hole)
  let fromBack = events.length
  for (let n = 1; n <= PUT_BACK; n++) {
    await waitFor(() => events.slice(fromBack).some(isBreak), 5000, `break ${n} after the block put back; since: ${story(events, fromBack)}`)
    const again = events.slice(fromBack).find(isBreak)
    if (!sideBlocks.has(again.at)) throw new Error(`a block of another side broken: ${again.at}, the enemy at (${side}); ${story(events, fromBack)}`)
    const [ax, ay, az] = again.at.split(',').map(Number)
    await putBack(bot, new Vec3(ax, ay, az))
    log(`put back ${n}: broken again at ${again.at}, put back again`)
    fromBack = events.indexOf(again) + 1
  }
  await sleep(500)
  const lastBack = events.length
  const hitsBack = events.slice(events.indexOf(hit) + 1).filter((e) => e.what === 'hit')
  if (hitsBack.length > 0) throw new Error(`hit while the block was put back each time: ${story(events, events.indexOf(hit))}`)

  // 5. nothing else broken: the walls of the other sides, the glass under the bot
  const others = events.slice(fromClosed).filter((e) => e.what === 'block' && e.to === 'air' && !sideBlocks.has(e.at))
  if (others.length > 0) throw new Error(`blocks broken elsewhere than the enemy's side: ${others.map((e) => e.at).join(', ')}`)
  for (const [dx, dz] of [[0, 0], [-1, 0], [0, -1]]) {
    const glass = bot.blockAt(at(dx, -1, dz))
    if (glass?.name !== 'glass') throw new Error(`the glass at ${at(dx, -1, dz)} is now ${blockText(glass)}`)
  }
  log(`only the side of the enemy broken; ${lastBack} events in all`)

  bot.chat('/spawn')
  await expectInLobby(bot, 'after the training')
  finishing = true
  log('PASSED: the enemy breaks its way in at one side, hits only through the hole, and breaks a block put back again')
  bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
