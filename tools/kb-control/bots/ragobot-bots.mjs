// RagoBot test bots for RagoLobby (see tools/lobby-check/README.md, run-arena-test.sh).
//
// RagoBot is a player of the server that a party leader invites with /p invite RagoBot (the
// owner's choice of 22/11: only in a party, never ranked, never to fill a random queue; Top Bridge
// 1v1 and 2v2 and the Sumo 1v1 first, then Boxing and Lobby Fight 1v1, step 1 of the owner's order
// of 22/11, 17h54, then Block placement 1v1, 2v2 and its Red Rover, step 2: it places blocks to
// save itself, and breaks those of the other; then Block edit 1v1 and its Red Rover, step 3: it
// opens the wall, hits through the hole, closes it). Test bots, with the bundled settings:
//   1. RagoLeader invites RagoBot: it joins the party at once and stands next to it; a second
//      invite finds it there already; RagoBot refuses a ranked queue and a mode it does not play;
//      /duel RagoBot from another player points to the party;
//   2. Sumo 1v1 (sumo, RagoPractice): RagoLeader and RagoBot, a game of the party that counts in
//      the stats; RagoLeader stands still: RagoBot pushes it off the platform, with at least one
//      hit in the recap: RagoLeader loses. RagoLeader must see RagoBot come to it and get its
//      knockback, as a player does (checked at the end, with what its client saw of the game);
//   3. Boxing (boxing, RagoPractice, first to 10 hits in run-arena-test.sh): RagoLeader stands
//      still, RagoBot lands its 10 hits first: RagoLeader loses 10 - 0;
//   4. Top Bridge 1v1 (top-bridge-1v1, RagoBedwars, on its demo map): RagoBot crosses the bridge
//      and hits RagoLeader with the sword of the kit, until it wins a round; RagoLeader leaves;
//   5. Lobby Fight 1v1 (lobby-fight-1v1, RagoBedwars): RagoMapOp, an operator (ops.json of
//      run-arena-test.sh), makes the middle island of the demo map its waiting lobby (/bw
//      lobbyfight), as the owner did on Archway FFA;
//      RagoBot hits RagoLeader with the stone sword of the kit until it wins a round; RagoLeader
//      leaves;
//   6. Block placement 1v1 (block-fight-1v1, RagoPractice, first to 10 in run-arena-test.sh), on
//      the two platforms the mode builds without a map, 16 blocks apart over the void (RagoBot
//      bridges at step 4 only): RagoArbitre, an operator, moves the players with /tp.
//      The clutch: RagoBot over the void beside its platform places wool under itself and stands
//      on it. The block under the other: RagoLeader on a block of its wool alone over the void,
//      RagoBot on the platform of RagoLeader, out of its hits: RagoBot breaks the wool, RagoLeader
//      falls, +10 for Blue: RagoLeader loses;
//   7. Red Rover Block Placement (red-rover-block-placement): RagoAutreUn joins the party, the
//      three play 1v1 in turn; RagoBot fights the one in its 1v1 (RagoArbitre brings them onto its
//      platform) and wins it; the two others leave, RagoAutreUn leaves the party;
//   8. Block edit 1v1 (block-edit-duel, first to 2 in run-arena-test.sh), step 3, at the wall of
//      wool: the defence, RagoLeader opens the wall and hits RagoBot through the hole out of its
//      hits, then comes to the wall: RagoBot hits it back, closes the wall and scores; the
//      attack, RagoLeader stands at the wall: RagoBot opens it, hits RagoLeader through the hole,
//      closes it and scores again: RagoLeader loses;
//   9. Red Rover Block Edit (red-rover-block-edit): RagoAutreUn joins the party again, the three
//      play 1v1 in turn; the one in the 1v1 of RagoBot stands at the wall: RagoBot wins its 1v1
//      with a clean edit; the two others leave, RagoAutreUn leaves the party;
//  10. Top Bridge 2v2: the party of RagoLeader and RagoBot queues as one team, two players alone
//      join the queue: the four play, RagoBot hits one of the two others; they leave;
//  11. /p kick RagoBot: RagoBot leaves the server; invited again, then the party disbanded: it
//      leaves again.
// run-arena-test.sh also checks the server log: no game of these was counted as a private game of
// a party (those count in no stats), and no error.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 11 * 60 * 1000
const BOT = 'RagoBot'

let finishing = false
/** The vector class of mineflayer's positions (from the first bot): new Vec3(x, y, z). */
let Vec3 = null

function abort (error) {
  console.error(`[ragobot-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[ragobot-bots] ${text}`)
}

/** Waits until `condition` holds; `what` (a text, or a function for a text of the moment it failed) says what for. */
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
        if (!finishing && !bot.leaving) abort(new Error(`${username} disconnected: ${reason}`))
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

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 20000,
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

/** RagoBot as this bot sees it: its entity, or null. */
function ragoBot (bot) {
  return bot.players[BOT]?.entity ?? null
}

/**
 * Keeps a column of air where this bot is while it may be knocked into the void, until the returned
 * function is called: mineflayer takes the empty chunks of the void on 1.8 for chunks gone, and a
 * bot in one stops moving (knocked off the floor into the next chunk, it stayed in the air at y 56.9,
 * never falling far enough for the server to see it fall).
 */
function fallThroughEmptyChunks (bot) {
  const timer = setInterval(() => {
    const p = bot.entity?.position
    const loaded = bot.world.getColumns()[0]
    if (p == null || loaded == null || bot.blockAt(p) != null) return
    const air = new loaded.column.constructor({ minY: bot.game.minY, worldHeight: bot.game.height })
    bot.world.setColumn(Math.floor(p.x / 16), Math.floor(p.z / 16), air)
  }, 50)
  return () => clearInterval(timer)
}

function where (entity) {
  const p = entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
}

/**
 * Watches the game from this bot's client, packet by packet: the entity of RagoBot (its spawns,
 * its moves, its swings), and what reaches this bot (its hurts, its knockbacks, its teleports,
 * its world changes), with the block under its feet and where it went. Its summary says all
 * of it, for the log and for a failure.
 */
function watch (bot) {
  const seen = {
    ids: new Set(), spawns: [], destroyed: 0, moves: 0, swings: 0, start: null, farthest: 0,
    hurts: 0, pushes: 0, teleports: 0, worlds: 0, under: new Set(), path: []
  }
  const current = ragoBot(bot)
  if (current != null) seen.ids.add(current.id)
  const onPacket = (packet, meta) => {
    switch (meta.name) {
      case 'named_entity_spawn':
        if (packet.playerUUID === bot.players[BOT]?.uuid) {
          seen.ids.add(packet.entityId)
          seen.spawns.push(`${packet.entityId} at ${(packet.x / 32).toFixed(1)}, ${(packet.y / 32).toFixed(1)}, ${(packet.z / 32).toFixed(1)}`)
        }
        break
      case 'entity_destroy':
        seen.destroyed += packet.entityIds.filter((id) => seen.ids.has(id)).length
        break
      case 'rel_entity_move':
      case 'entity_move_look':
      case 'entity_teleport':
        if (seen.ids.has(packet.entityId)) seen.moves++
        break
      case 'animation':
        if (seen.ids.has(packet.entityId) && packet.animation === 0) seen.swings++
        break
      case 'entity_status':
        if (packet.entityId === bot.entity.id && packet.entityStatus === 2) seen.hurts++
        break
      case 'entity_velocity':
        if (packet.entityId === bot.entity.id) seen.pushes++
        break
      case 'position':
        seen.teleports++
        break
      case 'respawn':
        seen.worlds++
        break
    }
  }
  const onMove = (entity) => {
    if (!seen.ids.has(entity.id)) return
    if (seen.start == null) seen.start = entity.position.clone()
    seen.farthest = Math.max(seen.farthest, entity.position.distanceTo(seen.start))
  }
  let last = null
  const sample = setInterval(() => {
    const p = bot.entity.position
    const block = bot.blockAt(p.offset(0, -0.5, 0))
    seen.under.add(block == null ? 'not loaded' : block.name)
    if ((last == null || last.distanceTo(p) > 0.5) && seen.path.length < 40) {
      seen.path.push(`${p.x.toFixed(1)},${p.y.toFixed(1)},${p.z.toFixed(1)}`)
      last = p.clone()
    }
  }, 100)
  bot._client.on('packet', onPacket)
  bot.on('entityMoved', onMove)
  seen.stop = () => {
    clearInterval(sample)
    bot._client.removeListener('packet', onPacket)
    bot.removeListener('entityMoved', onMove)
  }
  seen.summary = () => `RagoBot as ${bot.username} saw it: entity ${[...seen.ids].join('/') || 'never seen'}`
    + ` (spawned ${seen.spawns.join('; ') || 'never'}, ${seen.destroyed} destroyed), ${seen.moves} moves,`
    + ` ${seen.farthest.toFixed(1)} blocks from where it first moved, ${seen.swings} swings; ${bot.username}:`
    + ` ${seen.hurts} hurts, ${seen.pushes} knockbacks, ${seen.teleports} teleports, ${seen.worlds} world changes,`
    + ` under its feet ${[...seen.under].join('/')}, its path ${seen.path.join(' ')}`
  return seen
}

/** What a player sees of RagoBot in a game: it comes to them, and its hits push them. */
function asAPlayerSeesIt (game, seen, problems, pushed = true) {
  if (seen.moves === 0 || seen.farthest < 1) problems.push(`${game}: RagoLeader did not see RagoBot move (${seen.summary()})`)
  if (pushed && seen.pushes === 0) problems.push(`${game}: no knockback reached RagoLeader (${seen.summary()})`)
}

async function invite (leader) {
  const from = mark(leader)
  leader.chat(`/p invite ${BOT}`)
  await chatLine(leader, /RagoBot joined the party\./, 10000, from)
  await chatLine(leader, /RagoBot plays .*Top Bridge 1v1, Top Bridge 2v2, Sumo duel, Boxing, Lobby Fight 1v1, Block placement 1v1, Block placement 2v2, Red Rover Block Placement, Block edit 1v1 and Red Rover Block Edit.* with your party, never ranked: your games with it count in your stats, never in the ELO\./, 5000, from)
  await waitFor(() => ragoBot(leader) != null, 5000, `${leader.username} to see RagoBot (players: ${Object.keys(leader.players).join(', ')})`)
  const distance = ragoBot(leader).position.distanceTo(leader.entity.position)
  if (distance > 3) throw new Error(`RagoBot joined ${distance.toFixed(1)} blocks away from ${leader.username}, at ${where(ragoBot(leader))}`)
}

async function gone (bot, what) {
  await waitFor(() => bot.players[BOT] == null, 5000, `RagoBot to leave the server (${what})`)
}

async function sumo (leader, problems) {
  const from = mark(leader)
  const seen = watch(leader)
  let hits
  leader.chat('/queue sumo')
  try {
    await chatLine(leader, /Game of your party in Sumo duel: RagoLeader vs RagoBot\. It counts in your stats, never in the ELO\./, 5000, from)
    await chatLine(leader, /Sumo duel against RagoBot/, 15000, from)
    log('sumo: RagoLeader vs RagoBot, RagoLeader stands still')
    const end = await chatLine(leader, /^(Defeat\.|Victory!)/, 90000, from)
    if (!end.startsWith('Defeat.')) throw new Error(`RagoLeader won the sumo: RagoBot fell off on its own (${end}; ${seen.summary()})`)
    const recap = await chatLine(leader, /- RagoBot: \d+ hits?\b/, 5000, from)
    hits = Number(/- RagoBot: (\d+) hits?\b/.exec(recap)[1])
    if (hits < 1) throw new Error(`RagoBot won the sumo without a hit: RagoLeader fell off on its own (${recap}; ${seen.summary()})`)
  } finally {
    seen.stop()
  }
  log(`sumo: RagoBot pushed RagoLeader off the platform and won, ${hits} hits in the recap; ${seen.summary()}`)
  asAPlayerSeesIt('sumo', seen, problems)
  await expectInLobby(leader, 'after the sumo')
}

async function boxing (leader, problems) {
  const from = mark(leader)
  const seen = watch(leader)
  let score
  leader.chat('/queue boxing')
  try {
    await chatLine(leader, /Game of your party in Boxing: RagoLeader vs RagoBot\. It counts in your stats, never in the ELO\./, 5000, from)
    await chatLine(leader, /Boxing against RagoBot/, 15000, from)
    log('boxing: RagoLeader vs RagoBot, RagoLeader stands still')
    const end = await chatLine(leader, /^(Defeat\.|Victory!)/, 90000, from)
    if (!end.startsWith('Defeat.')) throw new Error(`RagoLeader won the boxing without a hit (${end}; ${seen.summary()})`)
    score = /\((\d+) - (\d+) hits/.exec(end)
    if (score == null || Number(score[1]) < 10 || Number(score[2]) !== 0) {
      throw new Error(`the boxing should end 10 - 0 for RagoBot: ${end} (${seen.summary()})`)
    }
  } finally {
    seen.stop()
  }
  log(`boxing: RagoBot landed its ${score[1]} hits first (${score[1]} - ${score[2]}); ${seen.summary()}`)
  asAPlayerSeesIt('boxing', seen, problems)
  await expectInLobby(leader, 'after the boxing')
}

/**
 * RagoMapOp, an operator (ops.json of run-arena-test.sh), makes the middle island of the demo map
 * (its floor at y 64, 11 blocks square) the waiting lobby of the map, as the owner did on Archway
 * FFA with /bw lobbyfight: Lobby Fight is then played there. It leaves the server after.
 */
async function waitingLobbyOnTheDemoMap () {
  const op = await arrive('RagoMapOp')
  let from = mark(op)
  op.chat('/bw edit bw-demo')
  await chatLine(op, /You are on bw-demo\./, 20000, from)
  op.chat('/tp 0.5 65 0.5')
  await waitFor(() => op.entity.position.distanceTo({ x: 0.5, y: 65, z: 0.5 }) < 0.5 && op.entity.onGround, 10000,
    `RagoMapOp on the middle island of bw-demo (at ${where(op.entity)})`)
  from = mark(op)
  op.chat('/bw lobbyfight')
  await chatLine(op, /Waiting lobby of bw-demo: floor at y 64, .* \(121 blocks of floor\)\./, 5000, from)
  op.leaving = true
  op.quit()
  log('lobby fight: RagoMapOp made the middle island of bw-demo its waiting lobby (121 blocks), and left')
}

async function lobbyFight1v1 (leader, problems) {
  await waitingLobbyOnTheDemoMap()
  const from = mark(leader)
  const seen = watch(leader)
  // RagoBot comes back from the game before after its end screen: until then, the party waits for it
  const deadline = Date.now() + 20000
  for (let asked = mark(leader); ; asked = mark(leader)) {
    leader.chat('/queue lobby-fight-1v1')
    const answer = await chatLine(leader, /Game of your party in Lobby Fight 1v1: RagoLeader vs RagoBot\.|RagoBot is not in the lobby: your party cannot join a queue\./, 5000, asked)
    if (answer.startsWith('Game of your party')) break
    if (Date.now() > deadline) throw new Error(`RagoBot never came back to the lobby after the top bridge 1v1 (${answer})`)
    await sleep(1000)
  }
  try {
    // "Lobby Fight 1v1 on bw-demo: you are Red. Rounds of one life in the waiting lobby of the map: ..."
    const welcome = await chatLine(leader, /Lobby Fight 1v1 on bw-demo: you are \S+\./, 20000, from)
    const team = /you are (\S+)\./.exec(welcome)[1]
    log(`lobby fight 1v1: RagoLeader is ${team}, against RagoBot, on the middle island of bw-demo`)
    await waitFor(() => leader.health < 20, 60000,
      `RagoBot to hit RagoLeader with its sword (health ${leader.health}; ${seen.summary()})`)
    log(`lobby fight 1v1: RagoBot hit RagoLeader (health ${leader.health.toFixed(1)})`)
    const round = await chatLine(leader, /\S+ wins round 1!/, 60000, from).catch((error) => {
      const bot = ragoBot(leader)
      throw new Error(`${error.message}; RagoLeader at ${where(leader.entity)}, RagoBot at ${bot ? where(bot) : 'out of sight'}; ${seen.summary()}`)
    })
    if (round.startsWith(team)) throw new Error(`RagoLeader's team won the round: RagoBot fell off on its own (${round}; ${seen.summary()})`)
  } finally {
    seen.stop()
  }
  log(`lobby fight 1v1: RagoBot won the first round; ${seen.summary()}`)
  asAPlayerSeesIt('lobby fight 1v1', seen, problems)
  leader.chat('/spawn')
  await expectInLobby(leader, 'after /spawn in the lobby fight')
  log('lobby fight 1v1: RagoLeader left, back in the lobby with RagoBot')
}

async function topBridge1v1 (leader, problems) {
  let from = mark(leader)
  const seen = watch(leader)
  leader.chat('/queue top-bridge-1v1')
  try {
    await chatLine(leader, /Game of your party in Top Bridge 1v1: RagoLeader vs RagoBot\. It counts in your stats, never in the ELO\./, 5000, from)
    const welcome = await chatLine(leader, /Top Bridge 1v1 on .*: you are in the \S+ team\./, 20000, from)
    const team = /you are in the (\S+) team\./.exec(welcome)[1]
    log(`top bridge 1v1: RagoLeader in the ${team} team, against RagoBot`)
    await waitFor(() => leader.health < 20, 60000,
      `RagoBot to hit RagoLeader with its sword (health ${leader.health}; ${seen.summary()})`)
    log(`top bridge 1v1: RagoBot hit RagoLeader (health ${leader.health.toFixed(1)})`)
    const round = await chatLine(leader, /\S+ wins round 1!/, 60000, from).catch((error) => {
      const bot = ragoBot(leader)
      throw new Error(`${error.message}; RagoLeader at ${where(leader.entity)}, RagoBot at ${bot ? where(bot) : 'out of sight'}; ${seen.summary()}`)
    })
    if (round.startsWith(team)) throw new Error(`RagoLeader's team won the round: RagoBot fell off on its own (${round}; ${seen.summary()})`)
  } finally {
    seen.stop()
  }
  log(`top bridge 1v1: RagoBot won the first round; ${seen.summary()}`)
  asAPlayerSeesIt('top bridge 1v1', seen, problems)
  from = mark(leader)
  leader.chat('/spawn')
  await expectInLobby(leader, 'after /spawn in the top bridge')
  log('top bridge 1v1: RagoLeader left, back in the lobby with RagoBot')
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

/** Places the item in hand against this face of the block at `against`; the new block must be `name` at `at`. */
async function place (bot, against, face, at, name) {
  await waitFor(() => bot.blockAt(against) != null && bot.blockAt(against).name !== 'air', 5000,
    `${bot.username}: a block at ${against} to place against (${blockText(bot.blockAt(against))})`)
  await bot.placeBlock(bot.blockAt(against), face).catch(() => {})
  await waitFor(() => bot.blockAt(at)?.name === name, 5000,
    `${bot.username} to place ${name} at ${at} (there: ${blockText(bot.blockAt(at))})`)
}

/** Breaks the block at `at`, which must be air after. */
async function dig (bot, at) {
  await waitFor(() => bot.blockAt(at) != null, 5000, `${bot.username}: the block at ${at} loaded`)
  await bot.dig(bot.blockAt(at), true)
  await waitFor(() => bot.blockAt(at)?.name === 'air', 5000,
    `${bot.username} to break the block at ${at} (there: ${blockText(bot.blockAt(at))})`)
}

/**
 * The leader joins the queue of this mode once RagoBot is back in the lobby from the game before
 * (after its end screen): until then, the party waits for it, as for any player of the party.
 */
async function queueWhenBack (leader, mode, started, after) {
  const deadline = Date.now() + 20000
  for (let asked = mark(leader); ; asked = mark(leader)) {
    leader.chat(`/queue ${mode}`)
    const answer = await chatLine(leader, new RegExp(`${started.source}|RagoBot is not in the lobby: your party cannot join a queue\\.`), 5000, asked)
    if (started.test(answer)) return
    if (Date.now() > deadline) throw new Error(`RagoBot never came back to the lobby after ${after} (${answer})`)
    await sleep(1000)
  }
}

/** RagoArbitre, an operator, moves a player of the game: /tp <name> <x> <y> <z>. */
function teleport (op, name, x, y, z) {
  op.chat(`/tp ${name} ${x.toFixed(2)} ${y.toFixed(2)} ${z.toFixed(2)}`)
}

/**
 * Block placement 1v1, on the arena the mode builds without a map (the test server has none):
 * two floors of 3 by 3 at x -9..-7 and 7..9 from its centre, 16 blocks apart over the void,
 * RagoLeader starting on the west one (the first team of the party), RagoBot on the east one.
 * Nothing joins the two (RagoBot bridges at step 4 only): RagoArbitre, an operator, moves them.
 *   1. the clutch: RagoBot over the void south of its floor, its feet 2 blocks over it: it places
 *      wool under itself against the side of its floor, stands on it, and no one gets the 10 of
 *      a fall;
 *   2. the block under the other: RagoLeader places three blocks of wool out of its floor, east,
 *      and breaks the first two: the third is alone over the void. RagoLeader on it, RagoBot on
 *      the floor of RagoLeader, out of its hits: RagoBot breaks the wool under RagoLeader (with
 *      no hit), RagoLeader falls, +10 for Blue, the 10 of the game: RagoLeader loses.
 */
async function blockPlacement1v1 (leader, op, problems) {
  const from = mark(leader)
  const seen = watch(leader)
  try {
    await queueWhenBack(leader, 'block-fight-1v1',
      /Game of your party in Block placement 1v1: RagoLeader vs RagoBot\. It counts in your stats, never in the ELO\./, 'the Lobby Fight 1v1')
    await chatLine(leader, /Block fight against RagoBot/, 15000, from)
    await waitFor(() => leader.game.gameMode === 'survival' && ragoBot(leader) != null, 5000,
      `RagoLeader in survival mode and RagoBot in sight in the block placement (mode ${leader.game.gameMode})`)
    await sleep(500)
    const p = leader.entity.position
    const centre = { x: Math.round(p.x + 7.5), y: Math.floor(p.y) - 1, z: Math.floor(p.z) }
    const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
    const start = ragoBot(leader).position
    if (Math.abs(start.x - (centre.x + 8.5)) > 0.6 || Math.abs(start.z - (centre.z + 0.5)) > 0.6) {
      throw new Error(`RagoLeader should start on the west floor and RagoBot on the east one: RagoLeader at ${where(leader.entity)}, RagoBot at ${where(ragoBot(leader))}`)
    }
    await chatLine(leader, /Fight!/, 10000, from)
    log(`block placement 1v1: RagoLeader vs RagoBot, the floors 16 blocks apart over the void (centre ${JSON.stringify(centre)})`)

    // RagoLeader: three blocks of wool out of its floor, east, the first two broken
    const east = new Vec3(1, 0, 0)
    await hold(leader, 'wool')
    await place(leader, at(-7, 0, 0), east, at(-6, 0, 0), 'wool')
    await place(leader, at(-6, 0, 0), east, at(-5, 0, 0), 'wool')
    await place(leader, at(-5, 0, 0), east, at(-4, 0, 0), 'wool')
    await hold(leader, 'shears')
    await dig(leader, at(-6, 0, 0))
    await dig(leader, at(-5, 0, 0))
    log('block placement 1v1: RagoLeader placed three blocks of wool out of its floor and broke the first two')

    // 1. the clutch
    let since = mark(leader)
    const clutch = at(8, 0, 2)
    teleport(op, BOT, centre.x + 8.5, centre.y + 3, centre.z + 2.7)
    await waitFor(() => leader.blockAt(clutch)?.name === 'wool', 5000, () => {
      const bot = ragoBot(leader)
      return `RagoBot over the void to place wool under itself at ${clutch} (there: ${blockText(leader.blockAt(clutch))};`
        + ` RagoBot at ${bot ? where(bot) : 'out of sight'}; RagoArbitre read ${JSON.stringify(op.chats.slice(-3))})`
    })
    await sleep(2000)
    const fall = leader.chats.slice(since).find((line) => /\+10 for|Victory!|Defeat\./.test(line))
    if (fall != null) throw new Error(`RagoBot fell into the void after its clutch: ${fall} (${seen.summary()})`)
    const after = ragoBot(leader)
    if (after == null || after.position.y < centre.y + 0.9) {
      throw new Error(`RagoBot does not stand on its floor nor its wool after the clutch: ${after ? where(after) : 'out of sight'}`)
    }
    log(`block placement 1v1: the clutch, RagoBot placed wool under itself at ${clutch} and stands at ${where(after)}`)

    // 2. the block under the other
    teleport(op, 'RagoLeader', centre.x - 3.5, centre.y + 1, centre.z + 0.5)
    await waitFor(() => leader.entity.position.distanceTo(at(-3.5, 1, 0.5)) < 0.3 && leader.entity.onGround, 5000,
      () => `RagoLeader on its wool alone over the void (at ${where(leader.entity)}; RagoArbitre read ${JSON.stringify(op.chats.slice(-3))})`)
    since = mark(leader)
    const hurts = seen.hurts
    const under = at(-4, 0, 0)
    let fellAt = null
    teleport(op, BOT, centre.x - 8.5, centre.y + 1, centre.z + 0.5)
    await waitFor(() => {
      if (leader.blockAt(under)?.name !== 'air') return false
      fellAt = leader.entity.position.clone()
      return true
    }, 15000, () => {
      const bot = ragoBot(leader)
      return `RagoBot to break the wool under RagoLeader at ${under} (there: ${blockText(leader.blockAt(under))};`
        + ` RagoLeader at ${where(leader.entity)}, RagoBot at ${bot ? where(bot) : 'out of sight'}; ${seen.summary()})`
    })
    if (fellAt.y < centre.y + 0.9) throw new Error(`RagoLeader fell before its wool went: at ${fellAt} (${seen.summary()})`)
    if (seen.hurts !== hurts) throw new Error(`RagoBot hit RagoLeader on its wool: ${seen.hurts - hurts} hurts (${seen.summary()})`)
    const bot = ragoBot(leader)
    log(`block placement 1v1: RagoBot broke the wool under RagoLeader from ${bot ? where(bot) : 'out of sight'}, out of its hits`)
    await chatLine(leader, /\+10 for Blue/, 10000, since)
    const end = await chatLine(leader, /^(Defeat\.|Victory!)/, 10000, since)
    if (!end.startsWith('Defeat.')) throw new Error(`RagoLeader won the block placement: ${end} (${seen.summary()})`)
  } finally {
    seen.stop()
  }
  log(`block placement 1v1: RagoLeader fell with its wool, +10 for Blue: RagoBot won; ${seen.summary()}`)
  asAPlayerSeesIt('block placement 1v1', seen, problems, false)
  await expectInLobby(leader, 'after the block placement')
}

/** The bot walks away from the other one until it falls off its floor. */
async function walkOff (bot, other) {
  const p = bot.entity.position
  const q = other.entity.position
  let dx = p.x - q.x
  let dz = p.z - q.z
  const length = Math.hypot(dx, dz) || 1
  dx /= length
  dz /= length
  await bot.lookAt(p.offset(dx * 20, 1.62, dz * 20), true)
  bot.setControlState('forward', true)
}

/**
 * Red Rover Block Placement (red-rover-block-placement, 1v1 in turn, first to 10 points wins the
 * 1v1, the void 10): RagoAutreUn joins the party of RagoLeader and RagoBot, the three play (the
 * party of three joins the queue of the mode and fills its match at once). If the
 * first 1v1 is that of the two others, one walks off its floor: the other wins it, and meets
 * RagoBot. In the 1v1 of RagoBot, RagoArbitre brings the other onto RagoBot (/tp), who stands still:
 * RagoBot hits them, or knocks them into the void, and wins the 1v1. Then the two others leave
 * the game, RagoAutreUn leaves the party.
 */
async function redRoverBlockPlacement (leader, other, op) {
  const from = mark(leader)
  let fromOther = mark(other)
  leader.chat(`/p invite ${other.username}`)
  await chatLine(other, /RagoLeader has invited you to join their party!/, 5000, fromOther)
  other.chat('/party accept RagoLeader')
  await chatLine(leader, new RegExp(`${other.username} joined the party\\.`), 5000, from)
  const humans = [leader, other]
  const marks = humans.map(mark)
  await queueWhenBack(leader, 'red-rover-block-placement',
    /Your party joined the queue for Red Rover Block Placement \(1v1v1, 3 waiting\)\./, 'the block placement')
  for (const [i, bot] of humans.entries()) await chatLine(bot, /Red Rover Block Placement with /, 20000, marks[i])
  await waitFor(() => humans.every((bot) => ['survival', 'spectator'].includes(bot.game.gameMode)) &&
    humans.some((bot) => bot.game.gameMode === 'survival'), 8000,
  () => `the first 1v1 of the Red Rover (${humans.map((bot) => `${bot.username} ${bot.game.gameMode}`).join(', ')})`)
  await sleep(300)
  const fighting = humans.filter((bot) => bot.game.gameMode === 'survival')
  let opponent
  if (fighting.length === 2) {
    // RagoBot waits: the first walks off its floor, the other wins the 1v1, then meets RagoBot
    const [a, b] = fighting
    await chatLine(a, /Fight!/, 10000, marks[humans.indexOf(a)])
    log(`red rover block placement: ${a.username} vs ${b.username}, RagoBot waits`)
    const before = mark(b)
    await walkOff(a, b)
    try {
      await chatLine(b, new RegExp(`${b.username} wins the 1v1 \\(1/3\\)`), 15000, before)
    } finally {
      a.clearControlStates()
    }
    await chatLine(b, new RegExp(`Next: ${b.username} vs RagoBot \\(${a.username} waits\\)`), 5000, before)
    await waitFor(() => b.game.gameMode === 'survival' && a.game.gameMode === 'spectator', 8000,
      () => `${b.username} against RagoBot, ${a.username} watching (${a.game.gameMode}, ${b.game.gameMode})`)
    await chatLine(b, /Fight!/, 15000, before)
    opponent = b
  } else {
    opponent = fighting[0]
    await chatLine(opponent, /Fight!/, 10000, marks[humans.indexOf(opponent)])
  }
  log(`red rover block placement: RagoBot vs ${opponent.username}, RagoArbitre brings ${opponent.username} onto RagoBot`)
  const before = mark(opponent)
  const health = opponent.health
  const falling = fallThroughEmptyChunks(opponent)
  op.chat(`/tp ${opponent.username} ${BOT}`)
  await chatLine(opponent, /RagoBot wins the 1v1 \(1\/3\)/, 30000, before).catch((error) => {
    const bot = ragoBot(opponent)
    throw new Error(`${error.message}; ${opponent.username} at ${where(opponent.entity)} (health ${opponent.health}),`
      + ` RagoBot at ${bot ? where(bot) : 'out of sight'}; RagoArbitre read ${JSON.stringify(op.chats.slice(-3))}`)
  }).finally(falling)
  log(`red rover block placement: RagoBot won its 1v1 against ${opponent.username} (health ${health} then ${opponent.health})`)
  // the two others leave the game: RagoBot, the last one in, wins
  for (const bot of [other, leader]) {
    bot.chat('/spawn')
    await expectInLobby(bot, 'after /spawn in the Red Rover')
  }
  fromOther = mark(other)
  other.chat('/party leave')
  await chatLine(other, /You left the party\./, 5000, fromOther)
  log(`red rover block placement: RagoLeader and ${other.username} left the game, ${other.username} left the party`)
}

// --- Block edit (step 3) -----------------------------------------------------------------------

/**
 * The centre of the station of the block edit (EditStation: the wall of wool at x 0 from it, z -2
 * to 2, y 1 to 3; the west start at x -2.5, the east one at 3.5), from where this fighter stands at
 * its start, west or east of the other one.
 */
function editCentre (fighter, other) {
  const p = fighter.position
  const west = p.x < other.position.x
  const centre = { x: Math.round(p.x + (west ? 2.5 : -3.5)), y: Math.floor(p.y) - 1, z: Math.floor(p.z) }
  if (Math.abs(p.z - (centre.z + 0.5)) > 0.4 || Math.abs(p.x - (centre.x + (west ? -2.5 : 3.5))) > 0.4) {
    throw new Error(`not at its start in the block edit: at ${where(fighter)}, the other at ${where(other)}`)
  }
  return centre
}

/** The bot walks to the wall of the block edit and keeps pressing against it, as a player standing there. */
async function toTheWall (bot, centre) {
  await bot.look(bot.entity.position.x < centre.x + 0.5 ? -Math.PI / 2 : Math.PI / 2, 0, true)
  bot.setControlState('forward', true)
}

/** True once this entity stands against the wall of the block edit, on its side. */
function atTheWall (entity, centre) {
  return entity != null && Math.abs(entity.position.x - (centre.x + 0.5)) < 1.0
}

/**
 * Block edit 1v1 (block-edit-duel, first to 2 in run-arena-test.sh), RagoLeader against RagoBot:
 *   1. the defence: at the Fight!, RagoLeader breaks the block of the wall at the height of the
 *      head in front of RagoBot from its start, and hits RagoBot through that hole once RagoBot
 *      stands at the wall, from out of its hits (3.4 blocks: the server takes the hits of a player
 *      up to 6 blocks away through a hole); then RagoLeader walks to the wall and stands there:
 *      RagoBot hits it back (its point gone), breaks no other block, closes the wall: a clean edit;
 *   2. the attack: RagoLeader at the wall again: RagoBot opens the wall in front of it, hits it
 *      through the hole, closes it: its second clean edit, RagoLeader loses.
 */
async function blockEdit1v1 (leader, problems) {
  const from = mark(leader)
  const seen = watch(leader)
  try {
    await queueWhenBack(leader, 'block-edit-duel',
      /Game of your party in Block edit 1v1: RagoLeader vs RagoBot\. It counts in your stats, never in the ELO\./,
      'the Red Rover Block Placement')
    await chatLine(leader, /Block edit against RagoBot/, 15000, from)
    await waitFor(() => leader.game.gameMode === 'survival' && ragoBot(leader) != null, 5000,
      `RagoLeader in survival mode and RagoBot in sight in the block edit (mode ${leader.game.gameMode})`)
    await sleep(500)
    const centre = editCentre(leader.entity, ragoBot(leader))
    const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
    await chatLine(leader, /Fight!/, 10000, from)
    log(`block edit 1v1: RagoLeader vs RagoBot at the wall of wool (centre ${JSON.stringify(centre)})`)

    // 1. the defence
    let since = mark(leader)
    await hold(leader, 'shears')
    await dig(leader, at(0, 2, 0))
    await waitFor(() => atTheWall(ragoBot(leader), centre), 8000, () => {
      const bot = ragoBot(leader)
      return `RagoBot at the wall in front of the hole (RagoBot at ${bot ? where(bot) : 'out of sight'}; ${seen.summary()})`
    })
    await sleep(300)
    const holes = [at(0, 1, 0), at(0, 3, 0), at(0, 2, -1), at(0, 2, 1)].filter((block) => leader.blockAt(block)?.name !== 'wool')
    if (holes.length > 0) throw new Error(`RagoBot broke another block of the wall: ${holes.join(', ')}`)
    const target = ragoBot(leader)
    await leader.lookAt(target.position.offset(0, 1.62, 0), true)
    leader.attack(target)
    await chatLine(leader, /Hit! Put a block back in the wall before they hit you\./, 3000, since)
    log(`block edit 1v1: RagoLeader opened the wall and hit RagoBot through the hole from ${where(leader.entity)}`)
    await toTheWall(leader, centre)
    try {
      await chatLine(leader, /Hit back: your point is gone\./, 15000, since).catch((error) => {
        const bot = ragoBot(leader)
        throw new Error(`${error.message}; RagoLeader at ${where(leader.entity)}, RagoBot at ${bot ? where(bot) : 'out of sight'}; ${seen.summary()}`)
      })
      log('block edit 1v1: RagoBot hit RagoLeader back through the hole')
      await chatLine(leader, /RagoBot made a clean edit\./, 10000, since)
      log('block edit 1v1: RagoBot closed the wall: a clean edit, its first point')

      // 2. the attack: RagoLeader keeps pressing against the wall from its start
      since = mark(leader)
      await chatLine(leader, /Fight!/, 10000, since)
      await toTheWall(leader, centre)
      // a clean edit: its block in the wall after its hit, the last (the game scores nothing else)
      await chatLine(leader, /RagoBot made a clean edit\./, 20000, since).catch((error) => {
        const bot = ragoBot(leader)
        throw new Error(`${error.message}; RagoLeader at ${where(leader.entity)}, RagoBot at ${bot ? where(bot) : 'out of sight'};`
          + ` the wall in front of RagoLeader: ${blockText(leader.blockAt(at(0, 2, 0)))}; ${seen.summary()}`)
      })
      const end = await chatLine(leader, /^(Defeat\.|Victory!)/, 10000, since)
      if (!end.startsWith('Defeat.')) throw new Error(`RagoLeader won the block edit: ${end} (${seen.summary()})`)
    } finally {
      leader.clearControlStates()
    }
  } finally {
    seen.stop()
  }
  log(`block edit 1v1: RagoBot opened the wall, hit RagoLeader through the hole and closed it: 2 - 0; ${seen.summary()}`)
  asAPlayerSeesIt('block edit 1v1', seen, problems)
  await expectInLobby(leader, 'after the block edit')
}

/** A clean edit of the west fighter against the east one, both at the wall: a hole, a hit through it, the block back. */
async function cleanEdit (west, east, centre) {
  const at = (dx, dy, dz) => new Vec3(centre.x + dx, centre.y + dy, centre.z + dz)
  await toTheWall(west, centre)
  await toTheWall(east, centre)
  try {
    await waitFor(() => atTheWall(west.entity, centre) && atTheWall(east.entity, centre), 5000,
      `${west.username} and ${east.username} at the wall (${where(west.entity)}, ${where(east.entity)})`)
  } finally {
    west.clearControlStates()
    east.clearControlStates()
  }
  await hold(west, 'shears')
  await dig(west, at(0, 1, 0))
  await dig(west, at(0, 2, 0))
  const target = Object.values(west.entities).find((e) => e.type === 'player' && e.username === east.username)
  if (target == null) throw new Error(`${west.username} does not see ${east.username}`)
  const from = mark(west)
  await west.lookAt(target.position.offset(0, 1.5, 0), true)
  west.attack(target)
  await chatLine(west, /Hit! Put a block back in the wall/, 3000, from)
  await hold(west, 'wool')
  await place(west, at(0, 0, 0), new Vec3(0, 1, 0), at(0, 1, 0), 'wool')
}

/**
 * Red Rover Block Edit (red-rover-block-edit, 1v1 in turn, a clean edit wins the 1v1): RagoAutreUn
 * joins the party of RagoLeader and RagoBot again, the three play. If the first 1v1 is that of the
 * two others, the west one makes a clean edit: it wins the 1v1, and meets RagoBot. In the 1v1 of
 * RagoBot, the other stands at the wall: RagoBot opens it, hits them through the hole, closes it
 * and wins the 1v1. Then the two others leave the game, RagoAutreUn leaves the party.
 */
async function redRoverBlockEdit (leader, other) {
  const from = mark(leader)
  let fromOther = mark(other)
  leader.chat(`/p invite ${other.username}`)
  await chatLine(other, /RagoLeader has invited you to join their party!/, 5000, fromOther)
  other.chat('/party accept RagoLeader')
  await chatLine(leader, new RegExp(`${other.username} joined the party\\.`), 5000, from)
  const humans = [leader, other]
  const marks = humans.map(mark)
  await queueWhenBack(leader, 'red-rover-block-edit',
    /Your party joined the queue for Red Rover Block Edit \(1v1v1, 3 waiting\)\./, 'the block edit 1v1')
  for (const [i, bot] of humans.entries()) await chatLine(bot, /Red Rover Block Edit with /, 20000, marks[i])
  await waitFor(() => humans.every((bot) => ['survival', 'spectator'].includes(bot.game.gameMode)) &&
    humans.some((bot) => bot.game.gameMode === 'survival'), 8000,
  () => `the first 1v1 of the Red Rover (${humans.map((bot) => `${bot.username} ${bot.game.gameMode}`).join(', ')})`)
  await sleep(500)
  const fighting = humans.filter((bot) => bot.game.gameMode === 'survival')
  let opponent
  if (fighting.length === 2) {
    // RagoBot waits: the west one makes a clean edit, wins the 1v1, then meets RagoBot
    const [a, b] = fighting
    await chatLine(a, /Fight!/, 10000, marks[humans.indexOf(a)])
    const centre = editCentre(a.entity, b.entity)
    const [west, east] = a.entity.position.x < b.entity.position.x ? [a, b] : [b, a]
    log(`red rover block edit: ${west.username} vs ${east.username}, RagoBot waits`)
    const before = mark(west)
    await cleanEdit(west, east, centre)
    await chatLine(west, new RegExp(`${west.username} wins the 1v1 \\(1/3\\)`), 10000, before)
    await chatLine(west, new RegExp(`Next: ${west.username} vs RagoBot \\(${east.username} waits\\)`), 5000, before)
    await waitFor(() => west.game.gameMode === 'survival' && east.game.gameMode === 'spectator', 8000,
      () => `${west.username} against RagoBot, ${east.username} watching (${west.game.gameMode}, ${east.game.gameMode})`)
    await chatLine(west, /Fight!/, 15000, before)
    opponent = west
  } else {
    opponent = fighting[0]
    await chatLine(opponent, /Fight!/, 10000, marks[humans.indexOf(opponent)])
  }
  await waitFor(() => ragoBot(opponent) != null, 5000, `${opponent.username} to see RagoBot in its 1v1`)
  const centre = editCentre(opponent.entity, ragoBot(opponent))
  log(`red rover block edit: RagoBot vs ${opponent.username}, who stands at the wall (centre ${JSON.stringify(centre)})`)
  const before = mark(opponent)
  await toTheWall(opponent, centre)
  try {
    await chatLine(opponent, /RagoBot made a clean edit\./, 20000, before).catch((error) => {
      const bot = ragoBot(opponent)
      throw new Error(`${error.message}; ${opponent.username} at ${where(opponent.entity)}, RagoBot at ${bot ? where(bot) : 'out of sight'}`)
    })
    await chatLine(opponent, /RagoBot wins the 1v1 \(1\/3\)/, 5000, before)
  } finally {
    opponent.clearControlStates()
  }
  log(`red rover block edit: RagoBot won its 1v1 against ${opponent.username} with a clean edit`)
  // the two others leave the game: RagoBot, the last one in, wins
  for (const bot of [other, leader]) {
    bot.chat('/spawn')
    await expectInLobby(bot, 'after /spawn in the Red Rover Block Edit')
  }
  fromOther = mark(other)
  other.chat('/party leave')
  await chatLine(other, /You left the party\./, 5000, fromOther)
  log(`red rover block edit: RagoLeader and ${other.username} left the game, ${other.username} left the party`)
}

async function topBridge2v2 (leader, others) {
  const from = [leader, ...others].map(mark)
  // RagoBot comes back from the game RagoLeader left after its end screen: until then, the party
  // waits for it, as for any player of the party
  const queued = /Your party joined the queue for .*Top Bridge 2v2.*You will play in the same team\./
  const deadline = Date.now() + 20000
  for (let asked = mark(leader); ; asked = mark(leader)) {
    leader.chat('/queue top-bridge-2v2')
    const answer = await chatLine(leader, new RegExp(`${queued.source}|RagoBot is not in the lobby: your party cannot join a queue\\.`), 5000, asked)
    if (queued.test(answer)) break
    if (Date.now() > deadline) throw new Error(`RagoBot never came back to the lobby after the Lobby Fight 1v1 (${answer})`)
    await sleep(1000)
  }
  for (const other of others) other.chat('/queue top-bridge-2v2')
  const welcome = await chatLine(leader, /Top Bridge 2v2 on .*: you are in the \S+ team\./, 30000, from[0])
  const team = /you are in the (\S+) team\./.exec(welcome)[1]
  for (const [i, other] of others.entries()) {
    const theirs = await chatLine(other, /Top Bridge 2v2 on .*: you are in the \S+ team\./, 10000, from[i + 1])
    if (/you are in the (\S+) team\./.exec(theirs)[1] === team) {
      throw new Error(`${other.username} plays in the team of RagoLeader and RagoBot (${team})`)
    }
  }
  log(`top bridge 2v2: RagoLeader and RagoBot (${team}) against ${others.map((o) => o.username).join(' and ')}`)
  await waitFor(() => others.some((other) => other.health < 20), 60000,
    `RagoBot to hit one of the other team (${others.map((o) => `${o.username} ${o.health}`).join(', ')})`)
  log(`top bridge 2v2: RagoBot hit ${others.filter((o) => o.health < 20).map((o) => o.username).join(' and ')}`)
  for (const other of others) {
    other.chat('/spawn')
    await expectInLobby(other, 'after /spawn in the top bridge 2v2')
  }
  await expectInLobby(leader, 'after the others left the top bridge 2v2')
  log('top bridge 2v2: the other team left, RagoLeader and RagoBot back in the lobby')
}

async function main () {
  const leader = await arrive('RagoLeader')
  Vec3 = leader.entity.position.constructor
  const others = [await arrive('RagoAutreUn'), await arrive('RagoAutreDeux')]
  // an operator (ops.json of run-arena-test.sh), who moves the players of the block placement with /tp
  const op = await arrive('RagoArbitre')

  // 1. the invite, and what it refuses
  await invite(leader)
  let from = mark(leader)
  leader.chat(`/p invite ${BOT}`)
  await chatLine(leader, /RagoBot is already in the party\./, 5000, from)
  from = mark(leader)
  leader.chat('/queue ranked-1v1')
  await chatLine(leader, /RagoBot never plays ranked: \/p kick RagoBot first\./, 5000, from)
  from = mark(leader)
  leader.chat('/queue snowball-spleef')
  await chatLine(leader, /RagoBot plays .*Red Rover Block Edit only: \/p kick RagoBot to play /, 5000, from)
  const fromOther = mark(others[0])
  others[0].chat(`/duel ${BOT}`)
  await chatLine(others[0], /RagoBot plays with your party: \/p invite RagoBot, then pick its mode as the leader\./, 5000, fromOther)
  log('invite: RagoBot joined the party next to RagoLeader; it refuses a second invite, ranked, a mode it does not play, a /duel')

  // 2. to 8. its games; what a player sees of RagoBot in them is checked at the end, after all
  const problems = []
  await sumo(leader, problems)
  await boxing(leader, problems)
  await topBridge1v1(leader, problems)
  await lobbyFight1v1(leader, problems)
  await blockPlacement1v1(leader, op, problems)
  await redRoverBlockPlacement(leader, others[0], op)
  await blockEdit1v1(leader, problems)
  await redRoverBlockEdit(leader, others[0])
  await topBridge2v2(leader, others)

  // 9. it leaves once out of the party
  from = mark(leader)
  leader.chat(`/p kick ${BOT}`)
  await chatLine(leader, /RagoBot has been removed from the party\./, 5000, from)
  await gone(leader, '/p kick')
  log('/p kick RagoBot: it left the server')
  await invite(leader)
  from = mark(leader)
  leader.chat('/party disband')
  await gone(leader, 'the party disbanded')
  log('invited again, then the party disbanded: it left the server')
  if (problems.length > 0) throw new Error(problems.join('; '))

  finishing = true
  log('PASSED: RagoBot joins a party by /p invite, refuses ranked and the other modes, wins a Sumo duel, a Boxing,'
    + ' a round of Top Bridge 1v1 and a round of Lobby Fight 1v1 against a player who stands still; in Block placement'
    + ' 1v1 it clutches over the void and breaks the wool under the other, who falls; it wins its 1v1 of a Red Rover'
    + ' Block Placement; in Block edit 1v1 it hits back through the hole of the other and closes the wall, then opens'
    + ' it, hits through the hole and closes it, and wins; it wins its 1v1 of a Red Rover Block Edit with a clean edit;'
    + ' it plays Top Bridge 2v2 in its party\'s team, and leaves the server once out of the party')
  for (const bot of [leader, ...others, op]) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
