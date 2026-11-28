// Arena reset test bots for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Two bots play the block modes several times in a row on a local 1.8.8 test server, and check at
// the start of each game that its zone is exactly as the mode built it: no block left by the game
// before, none missing. Without the owner's maps (the test server has none), each mode builds its
// own arena in the lowest free zone of its row, so that one game after another plays in the same
// zone (zone 0); a zone seen for the first time is only built, and proves nothing.
//   1. Block placement 1v1 (block-fight-1v1, first to 10 in run-arena-test.sh: a fall into the
//      void ends it): each game, both bots place wool beside their platform, the west one a ladder
//      and end stone too, then breaks the wool under the ladder (the ladder falls with it, the
//      wool drops on the ground); in the first game, the west bot closes itself in but for the
//      block above its head, which is refused (the anti full box); the games end in every way a
//      game ends: a fall into the void (a win, the end screen), /spawn
//      (a forfeit), a bot leaving the server, /playagain from the end screen (the next game goes
//      to another zone while the end screen holds the first), and /l;
//   2. Block edit 1v1 (block-edit-duel, first to 2): each game, holes in the wall and wool put back
//      in it; a forfeit with the wall open, a bot leaving the server, and clean edits (break, hit
//      through the hole, put the block back: a point, and the wall whole for the next round);
//   3. Block edit, the training against the bot (block-edit): holes in the wall and wool put back,
//      then /spawn, twice.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 10 * 60 * 1000
// Shapes.RED, Shapes.BLUE, Shapes.WHITE: the data of the wool and the clay of each colour.
const RED = 14
const BLUE = 11
const WHITE = 0

let finishing = false
/** The vector class of mineflayer's positions (from the first bot): new Vec3(x, y, z). */
let Vec3 = null

function abort (error) {
  console.error(`[arena-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[arena-bots] ${text}`)
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
  await sleep(100)
}

function blockText (block) {
  return block == null ? 'not loaded' : `${block.name}:${block.metadata}`
}

/** Places the item in hand against this face of the block at `against`; the new block must be `name` at `where`. */
async function place (bot, against, face, where, name) {
  await waitFor(() => bot.blockAt(against) != null && bot.blockAt(against).name !== 'air', 5000,
    `${bot.username}: a block at ${against} to place against (${blockText(bot.blockAt(against))})`)
  await bot.placeBlock(bot.blockAt(against), face).catch(() => {})
  await waitFor(() => bot.blockAt(where)?.name === name, 5000,
    `${bot.username} to place ${name} at ${where} (there: ${blockText(bot.blockAt(where))})`)
}

/** Breaks the block at `where`, which must be air after. */
async function dig (bot, where) {
  await waitFor(() => bot.blockAt(where) != null, 5000, `${bot.username}: the block at ${where} loaded`)
  await bot.dig(bot.blockAt(where), true)
  await waitFor(() => bot.blockAt(where)?.name === 'air', 5000,
    `${bot.username} to break the block at ${where} (there: ${blockText(bot.blockAt(where))})`)
}

/** Walks the way it looks (yaw) until `done`, then stops. */
async function walk (bot, yaw, done, what) {
  await bot.look(yaw, 0, true)
  bot.setControlState('forward', true)
  try {
    await waitFor(done, 5000, what)
  } finally {
    bot.clearControlStates()
  }
  await sleep(200)
}

/**
 * The blocks of the zone around `centre`, in this box (from the centre), against what the mode
 * built (expected: name and data at each place, air elsewhere); throws with every difference.
 */
async function checkZone (bot, centre, box, expected, what) {
  const [x1, x2, y1, y2, z1, z2] = box
  await waitFor(() => {
    for (const [dx, dz] of [[x1, z1], [x1, z2], [x2, z1], [x2, z2]]) {
      if (bot.blockAt(new Vec3(centre.x + dx, centre.y, centre.z + dz)) == null) return false
    }
    return true
  }, 10000, `${bot.username}: the chunks of the zone at ${JSON.stringify(centre)}`)
  const wrong = []
  for (let dx = x1; dx <= x2; dx++) {
    for (let dy = y1; dy <= y2; dy++) {
      for (let dz = z1; dz <= z2; dz++) {
        const block = bot.blockAt(new Vec3(centre.x + dx, centre.y + dy, centre.z + dz))
        const want = expected.get(`${dx},${dy},${dz}`) ?? ['air', 0]
        if (block == null || block.name !== want[0] || (want[0] !== 'air' && block.metadata !== want[1])) {
          wrong.push(`(${dx}, ${dy}, ${dz}) ${blockText(block)} instead of ${want[0]}:${want[1]}`)
        }
      }
    }
  }
  if (wrong.length > 0) {
    throw new Error(`${what}: the zone at ${JSON.stringify(centre)} is not as built, ${wrong.length} blocks differ: ${wrong.slice(0, 40).join('; ')}`)
  }
}

/** The zones each mode has used so far: a zone seen again must be exactly as built. */
const seen = new Map()
let reusedChecks = 0

async function checkStart (bot, mode, centre, box, expected, what) {
  const key = `${centre.x},${centre.y},${centre.z}`
  const zones = seen.get(mode) ?? new Set()
  seen.set(mode, zones)
  const again = zones.has(key)
  zones.add(key)
  await checkZone(bot, centre, box, expected, what)
  if (again) reusedChecks++
  log(`${what}: zone at ${key} ${again ? 'played before and exactly as built' : 'used for the first time, as built'}`)
}

// --- Block placement 1v1 -------------------------------------------------------------------------

/** BlockFight#build without a map: two floors of smooth sandstone, the clay of each side under its start. */
function blockFightDesign () {
  const design = new Map()
  for (const [from, to] of [[-9, -7], [7, 9]]) {
    for (let x = from; x <= to; x++) {
      for (let z = -1; z <= 1; z++) design.set(`${x},0,${z}`, ['sandstone', 2])
    }
  }
  design.set('-8,-1,0', ['stained_hardened_clay', RED])
  design.set('8,-1,0', ['stained_hardened_clay', BLUE])
  return design
}

const BLOCK_FIGHT_BOX = [-13, 13, -4, 6, -5, 5]

/** Both bots in a Block placement 1v1: the west one (side 0, at x - 7.5) and the east one, and the centre. */
async function startBlockFight (one, two) {
  const fromOne = mark(one)
  const fromTwo = mark(two)
  one.chat('/queue block-fight-1v1')
  two.chat('/queue block-fight-1v1')
  await chatLine(one, /Block fight against RagoDeux/, 15000, fromOne)
  await chatLine(two, /Block fight against RagoUn/, 15000, fromTwo)
  return sides(one, two, 7.5, 8.5, fromOne, fromTwo)
}

/** The sides of a duel whose starts are at (centre - west, centre + east): who is where, and the centre. */
async function sides (one, two, west, east, fromOne, fromTwo) {
  await waitFor(() => one.game.gameMode === 'survival' && two.game.gameMode === 'survival', 5000, 'survival mode in the duel')
  await sleep(500)
  const [w, e] = one.entity.position.x < two.entity.position.x ? [one, two] : [two, one]
  const p = w.entity.position
  const centre = { x: Math.round(p.x + west), y: Math.floor(p.y) - 1, z: Math.floor(p.z) }
  const q = e.entity.position
  if (Math.abs(q.x - (centre.x + east)) > 0.6 || Math.abs(q.z - (centre.z + 0.5)) > 0.6) {
    throw new Error(`the starts do not fit: west ${p}, east ${q}, centre ${JSON.stringify(centre)}`)
  }
  return { west: w, east: e, centre, from: new Map([[one, fromOne], [two, fromTwo]]) }
}

async function fight (game) {
  await chatLine(game.west, /Fight!/, 10000, game.from.get(game.west))
  await chatLine(game.east, /Fight!/, 10000, game.from.get(game.east))
}

/** Blocks on both sides: wool, a ladder and end stone on the west side (the ladder falls with the wool under it), wool on the east. */
async function buildInBlockFight (game) {
  const { west, east, centre } = game
  const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
  await hold(west, 'wool')
  await place(west, at(-7, 0, 0), new Vec3(1, 0, 0), at(-6, 0, 0), 'wool')
  await place(west, at(-6, 0, 0), new Vec3(1, 0, 0), at(-5, 0, 0), 'wool')
  await hold(west, 'ladder')
  await place(west, at(-6, 0, 0), new Vec3(0, 0, -1), at(-6, 0, -1), 'ladder')
  await hold(west, 'end_stone')
  await place(west, at(-5, 0, 0), new Vec3(0, 1, 0), at(-5, 1, 0), 'end_stone')
  await hold(east, 'wool')
  await place(east, at(7, 0, 0), new Vec3(-1, 0, 0), at(6, 0, 0), 'wool')
  await place(east, at(6, 0, 0), new Vec3(-1, 0, 0), at(5, 0, 0), 'wool')
  await place(east, at(6, 0, 0), new Vec3(0, 1, 0), at(6, 1, 0), 'wool')
  await hold(west, 'shears')
  // a block broken drops on the ground (the owner's ask of 22/11): the wool, for anyone to pick up
  const broken = at(-6, 0, 0)
  // the item of a drop is the item stack of the entity's metadata, read once it is in: the handler
  // only keeps the items that fell near the broken block (getDroppedItem looks at index 8, but in
  // 1.8 the item stack of a dropped item is at index 10, so the stack is found by its fields)
  const near = []
  const onDrop = (entity) => {
    if (entity.position.distanceTo(broken.offset(0.5, 0.5, 0.5)) < 2) near.push(entity)
  }
  const wool = west.registry.itemsByName.wool.id
  const woolDropped = () => {
    for (const entity of near) {
      const stack = Object.values(entity.metadata ?? {}).find((value) => value != null && typeof value === 'object' &&
        typeof value.blockId === 'number' && typeof value.itemCount === 'number')
      if (stack != null && stack.blockId === wool) return { name: 'wool', count: stack.itemCount }
    }
    return null
  }
  let dropped = null
  west.on('itemDrop', onDrop)
  try {
    await dig(west, broken)
    await waitFor(() => west.blockAt(at(-6, 0, -1))?.name === 'air', 3000, 'the ladder to fall with the wool it hung on')
    await waitFor(() => (dropped = woolDropped()) != null, 3000, 'the broken wool to drop on the ground')
  } finally {
    west.removeListener('itemDrop', onDrop)
  }
  if (dropped.count !== 1) throw new Error(`the broken wool dropped ${dropped.count} wool`)
  log('block placement: wool, a ladder and end stone placed, the wool under the ladder broken (the ladder fell with it, the wool dropped on the ground)')
}

/**
 * The anti full box (the owner's choice of 22/11): the west bot closes itself in on its platform,
 * the four sides of its feet and of its head, then a block beside the top; the block above its
 * head, the last of the box, is refused.
 */
async function boxInIsRefused (game) {
  const { west, centre } = game
  const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
  const feet = west.entity.position.floored()
  if (!feet.equals(at(-8, 1, 0))) throw new Error(`the west bot should stand at its start ${at(-8, 1, 0)}, not ${feet}`)
  await hold(west, 'wool')
  const up = new Vec3(0, 1, 0)
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    await place(west, at(-8 + dx, 0, dz), up, at(-8 + dx, 1, dz), 'wool')
    await place(west, at(-8 + dx, 1, dz), up, at(-8 + dx, 2, dz), 'wool')
  }
  await place(west, at(-7, 2, 0), up, at(-7, 3, 0), 'wool')
  const from = mark(west)
  await west.placeBlock(west.blockAt(at(-7, 3, 0)), new Vec3(-1, 0, 0)).catch(() => {})
  await chatLine(west, /You can't box yourself in!/, 5000, from)
  await sleep(500)
  if (west.blockAt(at(-8, 3, 0))?.name !== 'air') {
    throw new Error(`the block above the head of the west bot was placed: ${blockText(west.blockAt(at(-8, 3, 0)))}`)
  }
  log('block placement: the four sides of its feet and of its head placed, the block above its head refused ("You can\'t box yourself in!")')
}

/** The east bot steps back off its platform, into the void: +10, the west side wins (first to 10). */
async function eastFalls (game) {
  const { west, east } = game
  const from = mark(west)
  east.setControlState('back', true)
  try {
    await chatLine(west, /Victory!/, 10000, from)
  } finally {
    east.clearControlStates()
  }
}

async function blockPlacementGames (state) {
  const design = blockFightDesign()
  const endings = ['void', 'spawn', 'quit', 'playagain', 'l', 'last']
  for (let n = 0; n < endings.length; n++) {
    const ending = endings[n]
    const what = `block placement game ${n + 1} (${ending})`
    const game = await startBlockFight(state.one, state.two)
    await checkStart(game.west, 'block-fight-1v1', game.centre, BLOCK_FIGHT_BOX, design, what)
    await fight(game)
    await buildInBlockFight(game)
    if (n === 0) await boxInIsRefused(game)
    if (ending === 'void') {
      await eastFalls(game)
    } else if (ending === 'spawn' || ending === 'last') {
      game.west.chat('/spawn')
      await chatLine(game.east, /Victory!/, 10000, game.from.get(game.east))
    } else if (ending === 'l') {
      game.east.chat('/l')
      await chatLine(game.west, /Victory!/, 10000, game.from.get(game.west))
    } else if (ending === 'quit') {
      await quitAndComeBack(state, game.west, game.east)
    } else if (ending === 'playagain') {
      // from the end screen, the next game at once: its zone is another one, the first is still held
      await eastFalls(game)
      const next = mark(state.one)
      const nextTwo = mark(state.two)
      state.one.chat('/playagain')
      state.two.chat('/playagain')
      await chatLine(state.one, /Block fight against RagoDeux/, 15000, next)
      await chatLine(state.two, /Block fight against RagoUn/, 15000, nextTwo)
      const again = await sides(state.one, state.two, 7.5, 8.5, next, nextTwo)
      await checkStart(again.west, 'block-fight-1v1', again.centre, BLOCK_FIGHT_BOX, design, `${what}, the game after /playagain`)
      await fight(again)
      await buildInBlockFight(again)
      again.west.chat('/spawn')
      await chatLine(again.east, /Victory!/, 10000, again.from.get(again.east))
    }
    await expectInLobby(state.one, `after ${what}`)
    await expectInLobby(state.two, `after ${what}`)
    log(`${what}: over, both back in the lobby`)
  }
}

/** The bot leaves the server in the middle of the game, the other wins; the bot comes back. */
async function quitAndComeBack (state, leaver, winner) {
  const from = mark(winner)
  leaver.leaving = true
  leaver.quit()
  await chatLine(winner, /Victory!/, 10000, from)
  await sleep(1000)
  const back = await join(leaver.username)
  if (leaver === state.one) state.one = back
  else state.two = back
  await expectInLobby(back, 'back on the server')
  log(`${leaver.username} left the server in the game, and came back`)
}

// --- Block edit ----------------------------------------------------------------------------------

/** EditStation#build, block for block: the case of barrier blocks, the grass floor, the wall of this wool, the bot's room of wood. */
function editDesign (duel, wool) {
  const design = new Map()
  const build = (x, y, z, name, data = 0) => design.set(`${x},${y},${z}`, [name, data])
  const west = -6
  const east = (duel ? 5 : 2) + 1
  const side = 3
  for (let x = west; x <= east; x++) {
    for (let y = 0; y <= 5; y++) {
      for (let z = -side; z <= side; z++) {
        if (x === west || x === east || Math.abs(z) === side || y === 0 || y === 5) build(x, y, z, 'barrier')
      }
    }
  }
  for (let x = west + 1; x <= east - 1; x++) {
    for (let z = -2; z <= 2; z++) build(x, 0, z, 'grass')
  }
  for (let z = -2; z <= 2; z++) {
    build(0, 0, z, 'dirt')
    build(0, 4, z, 'log')
    for (let y = 1; y <= 3; y++) build(0, y, z, 'wool', wool)
  }
  if (duel) return design
  for (let x = 0; x <= east; x++) {
    for (let y = 1; y <= 3; y++) {
      const wood = x === 0 || x === east ? 'log' : 'planks'
      build(x, y, -side, wood)
      build(x, y, side, wood)
    }
    for (let z = -2; z <= 2; z++) {
      if (x === east) {
        for (let y = 1; y <= 3; y++) build(x, y, z, 'planks')
      }
      if (x > 0) build(x, 4, z, 'planks')
    }
  }
  return design
}

const EDIT_BOX = [-8, 8, -2, 7, -5, 5]

async function startEditDuel (one, two) {
  const fromOne = mark(one)
  const fromTwo = mark(two)
  one.chat('/queue block-edit-duel')
  two.chat('/queue block-edit-duel')
  await chatLine(one, /Block edit against RagoDeux/, 15000, fromOne)
  await chatLine(two, /Block edit against RagoUn/, 15000, fromTwo)
  return sides(one, two, 2.5, 3.5, fromOne, fromTwo)
}

/** Holes in the wall, and wool put back in some of them (without a hit first: no point). */
async function editTheWall (bot, other, centre) {
  const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
  await hold(bot, 'shears')
  await dig(bot, at(0, 1, 0))
  await dig(bot, at(0, 2, 0))
  await dig(bot, at(0, 1, 1))
  await dig(bot, at(0, 3, -2))
  if (other != null) {
    await hold(other, 'wool')
    await place(other, at(0, 0, 0), new Vec3(0, 1, 0), at(0, 1, 0), 'wool')
  }
  await hold(bot, 'wool')
  await place(bot, at(0, 0, 1), new Vec3(0, 1, 0), at(0, 1, 1), 'wool')
  log(`block edit: four holes in the wall, wool put back in ${other == null ? 'one' : 'two'} of them`)
}

/** A clean edit of the west bot: up to the wall, a hole, a hit through it, the block back: a point. */
async function cleanEdit (game, points) {
  const { west, east, centre } = game
  const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
  await walk(west, -Math.PI / 2, () => west.entity.position.x > centre.x - 0.9, 'the west bot at the wall')
  await walk(east, Math.PI / 2, () => east.entity.position.x < centre.x + 1.9, 'the east bot at the wall')
  await hold(west, 'shears')
  await dig(west, at(0, 1, 0))
  await dig(west, at(0, 2, 0))
  const target = Object.values(west.entities).find((e) => e.type === 'player' && e.username === east.username)
  if (target == null) throw new Error('the west bot does not see the east one')
  const from = mark(west)
  await west.lookAt(target.position.offset(0, 1.5, 0), true)
  west.attack(target)
  await chatLine(west, /Hit! Put a block back in the wall/, 3000, from)
  await hold(west, 'wool')
  const fromPoint = mark(west)
  await place(west, at(0, 0, 0), new Vec3(0, 1, 0), at(0, 1, 0), 'wool')
  await chatLine(west, /made a clean edit/, 3000, fromPoint)
  log(`block edit: a clean edit of the west bot, point ${points}`)
  return fromPoint
}

async function blockEditGames (state) {
  const design = editDesign(true, WHITE)
  const endings = ['spawn', 'quit', 'points', 'last']
  for (let n = 0; n < endings.length; n++) {
    const ending = endings[n]
    const what = `block edit game ${n + 1} (${ending})`
    const game = await startEditDuel(state.one, state.two)
    await checkStart(game.west, 'block-edit-duel', game.centre, EDIT_BOX, design, what)
    await fight(game)
    if (ending === 'points') {
      // first to 2 in run-arena-test.sh: after the first point, the next round starts on the wall as built
      const from = await cleanEdit(game, 1)
      await chatLine(game.west, /Fight!/, 10000, from)
      await checkZone(game.west, game.centre, EDIT_BOX, design, `${what}, the round after a point`)
      log(`${what}: the wall is whole again for the next round`)
      game.from.set(game.west, mark(game.west))
      game.from.set(game.east, mark(game.east))
      const fromWin = mark(game.west)
      await cleanEdit(game, 2)
      await chatLine(game.west, /Victory!/, 5000, fromWin)
    } else {
      await editTheWall(game.west, game.east, game.centre)
      if (ending === 'quit') {
        await quitAndComeBack(state, game.east, game.west)
      } else {
        game.west.chat('/spawn')
        await chatLine(game.east, /Victory!/, 10000, game.from.get(game.east))
      }
    }
    await expectInLobby(state.one, `after ${what}`)
    await expectInLobby(state.two, `after ${what}`)
    log(`${what}: over, both back in the lobby`)
  }
}

async function blockEditTrainings (state) {
  const design = editDesign(false, RED)
  const bot = state.one
  for (let n = 1; n <= 3; n++) {
    const what = `block edit training ${n}`
    const from = mark(bot)
    bot.chat('/queue block-edit')
    await chatLine(bot, /Block edit - /, 15000, from)
    await waitFor(() => bot.game.gameMode === 'survival', 5000, 'survival mode in the training')
    await sleep(500)
    const p = bot.entity.position
    const centre = { x: Math.round(p.x + 2.5), y: Math.floor(p.y) - 1, z: Math.floor(p.z) }
    // the bot of the training stands in its room: no block of the station is checked through it
    await checkStart(bot, 'block-edit', centre, EDIT_BOX, design, what)
    if (n < 3) await editTheWall(bot, null, centre)
    bot.chat('/spawn')
    await expectInLobby(bot, `after ${what}`)
    log(`${what}: /spawn, back in the lobby`)
  }
}

async function main () {
  const state = { one: await join('RagoUn'), two: await join('RagoDeux') }
  Vec3 = state.one.entity.position.constructor
  await closeFirstMenu(state.one)
  await closeFirstMenu(state.two)
  await expectInLobby(state.one, 'on joining')
  await expectInLobby(state.two, 'on joining')
  log('both bots joined the lobby')

  await blockPlacementGames(state)
  await blockEditGames(state)
  await blockEditTrainings(state)

  if (reusedChecks < 8) throw new Error(`only ${reusedChecks} games played in a zone used before: the test proves too little`)
  finishing = true
  state.one.quit()
  state.two.quit()
  await sleep(500)
  log(`PASSED: ${reusedChecks} games started in a zone played before, each exactly as built`)
  process.exit(0)
}

main().catch(abort)
