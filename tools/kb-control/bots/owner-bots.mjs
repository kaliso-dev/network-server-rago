// Owner tool test bots for RagoLobby (see tools/lobby-check/README.md, run-arena-test.sh).
//
// The Owner's test tools (outils-owner: of config.yml, plan 7.1): /owner reach and /owner
// scaffold, for the account qRago only, off by default, never in a ranked game, and a game where
// one is on counts for nobody. run-arena-test.sh runs the server in offline mode with
// outils-owner.uuid set to the offline id of qRago, and qRago and RagoOp as operators (rago.admin).
// Test bots:
//   1. RagoCible (not an operator) and RagoOp (an operator, not the Owner's account) read what
//      Spigot answers for a command it does not know, and turn nothing on; qRago reads its tools,
//      all off, and a reach out of 3 to 6 is refused;
//   2. Sumo duel (not ranked) of qRago against RagoCible: qRago turns Reach 6 on in the duel (the
//      game counts for nobody now), swings at RagoCible 6 blocks away: the hit lands (RagoCible gets
//      the knockback, the recap counts it); RagoCible steps off the platform; the server log says
//      the game was not counted;
//   3. Ranked Sumo of the same two, Reach still on: qRago is told it does nothing there, its swings
//      from 6 blocks away push nobody; RagoCible steps off; the game counts (the server log does not
//      say otherwise);
//   4. Block placement 1v1 of the same two, Scaffold on: qRago, wool in hand, walks back off its
//      platform: the wool goes under its feet, block after block, and it does not fall; RagoCible
//      steps into the void; not counted either.
// run-arena-test.sh checks the server log for 2. to 4.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 6 * 60 * 1000

let finishing = false

function abort (error) {
  console.error(`[owner-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[owner-bots] ${text}`)
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
    bot.pushes = []
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
      // its knockbacks: the velocities the server sends it, with their time
      bot._client.on('entity_velocity', (packet) => {
        if (bot.entity != null && packet.entityId === bot.entity.id) bot.pushes.push(Date.now())
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
  await sleep(200)
}

/** A command that is not there for this player: Spigot's answer, and nothing else. */
async function refused (bot, command) {
  const from = mark(bot)
  bot.chat(command)
  await chatLine(bot, /Unknown command\. Type "\/help" for help\./, 5000, from)
  await sleep(300)
  const others = bot.chats.slice(from).filter((line) => /^(Owner tools|Reach|Scaffold|Never in a ranked game|This game counts)/.test(line.trim()))
  if (others.length > 0) throw new Error(`${bot.username} read more than an unknown command for ${command}: ${JSON.stringify(others)}`)
}

async function command (bot, text, pattern) {
  const from = mark(bot)
  bot.chat(text)
  return chatLine(bot, pattern, 5000, from)
}

/** qRago aims at the eyes of the other and swings, as a click in the air. */
async function swingAt (owner, other) {
  await owner.lookAt(other.entity.position.offset(0, 1.62, 0), true)
  await sleep(100)
  owner.swingArm()
}

/**
 * Steps back off where it stands (the duels put the players facing each other, the void behind),
 * until the other wins.
 */
async function stepOff (bot, winner, from) {
  bot.setControlState('back', true)
  try {
    await chatLine(winner, /^Victory!/, 10000, from)
  } finally {
    bot.clearControlStates()
  }
}

async function access (owner, target, op) {
  await refused(target, '/owner')
  await refused(target, '/owner reach 6')
  await refused(op, '/owner reach 6')
  await refused(op, '/owner scaffold on')
  await command(owner, '/owner', /Owner tools: Reach off, Scaffold off\./)
  await command(owner, '/owner reach 7', /Reach: from 3 to 6 blocks/)
  await command(owner, '/owner', /Owner tools: Reach off, Scaffold off\./)
  log('access: /owner is an unknown command for RagoCible and RagoOp, qRago reads its tools, all off')
}

async function reachInSumo (owner, target) {
  const from = [mark(owner), mark(target)]
  owner.chat('/queue sumo')
  target.chat('/queue sumo')
  await chatLine(owner, /Sumo duel against RagoCible/, 15000, from[0])
  await command(owner, '/owner reach 6', /This game counts for nobody now \(no stats, no XP, no ELO\)\./)
  await chatLine(owner, /^Fight!/, 10000, from[0])
  await sleep(300)
  const distance = owner.entity.position.distanceTo(target.entity.position)
  if (distance < 5 || distance > 6.3) throw new Error(`the sumo starts are ${distance.toFixed(1)} blocks apart, not about 6`)
  const before = target.pushes.length
  const swung = Date.now()
  await swingAt(owner, target)
  await waitFor(() => target.pushes.length > before, 3000,
    `RagoCible to get the knockback of qRago's hit from ${distance.toFixed(1)} blocks`)
  log(`sumo: qRago's hit from ${distance.toFixed(1)} blocks pushed RagoCible ${target.pushes[before] - swung} ms after the swing`)
  await stepOff(target, owner, from[0])
  const recap = await chatLine(owner, /- qRago: \d+ hits/, 5000, from[0])
  if (Number(/- qRago: (\d+) hits/.exec(recap)[1]) < 1) throw new Error(`the recap counts no hit of qRago: ${recap}`)
  const xp = [owner, target].flatMap((bot, i) => bot.chats.slice(from[i]).filter((line) => /Network XP|Rago Points/.test(line)))
  if (xp.length > 0) throw new Error(`a game with an Owner tool gave XP or points: ${JSON.stringify(xp)}`)
  await expectInLobby(owner, 'after the sumo')
  await expectInLobby(target, 'after the sumo')
  log('sumo: the hit landed, in the recap; no XP, no points')
}

async function reachInRanked (owner, target) {
  const from = [mark(owner), mark(target)]
  owner.chat('/queue ranked-sumo')
  target.chat('/queue ranked-sumo')
  await chatLine(owner, /Sumo duel against RagoCible/, 20000, from[0])
  await command(owner, '/owner reach 6', /Never in a ranked game: it does nothing here\./)
  await chatLine(owner, /^Fight!/, 10000, from[0])
  await sleep(300)
  const distance = owner.entity.position.distanceTo(target.entity.position)
  const before = target.pushes.length
  for (let i = 0; i < 3; i++) {
    await swingAt(owner, target)
    await sleep(500)
  }
  await sleep(500)
  if (target.pushes.length > before) throw new Error(`qRago's Reach pushed RagoCible in a ranked game, from ${distance.toFixed(1)} blocks`)
  log(`ranked sumo: three swings of qRago from ${distance.toFixed(1)} blocks, Reach on: no hit`)
  await stepOff(target, owner, from[0])
  await expectInLobby(owner, 'after the ranked sumo')
  await expectInLobby(target, 'after the ranked sumo')
  await command(owner, '/owner reach off', /Reach off\./)
}

async function scaffoldInBlockFight (owner, target) {
  await command(owner, '/owner scaffold on', /Scaffold \(hold the blocks to place\): on\./)
  const from = [mark(owner), mark(target)]
  owner.chat('/queue block-fight-1v1')
  target.chat('/queue block-fight-1v1')
  await chatLine(owner, /Block fight against RagoCible/, 15000, from[0])
  await waitFor(() => owner.game.gameMode === 'survival', 5000, 'qRago in survival mode in the duel')
  await chatLine(owner, /^Fight!/, 10000, from[0])
  await hold(owner, 'wool')
  const start = owner.entity.position.clone()
  const floor = Math.floor(start.y) - 1
  const wool = () => owner.inventory.items().filter((item) => item.name === 'wool').reduce((n, item) => n + item.count, 0)
  const woolBefore = wool()
  owner.setControlState('back', true)
  try {
    await waitFor(() => owner.entity.position.distanceTo(start) >= 4 || owner.entity.position.y < start.y - 1, 6000,
      `qRago to walk back 4 blocks (at ${owner.entity.position})`)
  } finally {
    owner.clearControlStates()
  }
  await sleep(500)
  const end = owner.entity.position.clone()
  const Vec3 = start.constructor
  const under = []
  const way = end.minus(start)
  for (let along = 0; along <= 1.0001; along += 0.125) {
    const at = start.plus(way.scaled(along))
    const block = owner.blockAt(new Vec3(Math.floor(at.x), floor, Math.floor(at.z)))
    const name = block?.name ?? 'not loaded'
    if (under.length === 0 || under[under.length - 1] !== name) under.push(name)
  }
  const placedWool = under.filter((name) => name === 'wool').length
  log(`block placement: qRago walked back from ${start} to ${end}, under its path: ${under.join(' then ')};`
    + ` wool in its inventory ${woolBefore} then ${wool()}`)
  if (end.y < start.y - 0.5) throw new Error(`qRago fell walking back with Scaffold on: from ${start} to ${end} (under its path: ${under.join(' then ')})`)
  if (end.distanceTo(start) < 3 || placedWool === 0) {
    throw new Error(`Scaffold put no wool under qRago walking back off its platform: from ${start} to ${end}, under its path: ${under.join(' then ')}`)
  }
  await stepOff(target, owner, from[0])
  await expectInLobby(owner, 'after the block placement')
  await expectInLobby(target, 'after the block placement')
  await command(owner, '/owner off', /Owner tools off\./)
}

async function main () {
  const owner = await arrive('qRago')
  const target = await arrive('RagoCible')
  const op = await arrive('RagoOp')
  await access(owner, target, op)
  await reachInSumo(owner, target)
  await reachInRanked(owner, target)
  await scaffoldInBlockFight(owner, target)
  finishing = true
  log('PASSED: /owner for qRago only; Reach lands from 6 blocks in a sumo, which counts for nobody, and does'
    + ' nothing in a ranked sumo; Scaffold puts the wool under qRago walking off its platform')
  for (const bot of [owner, target, op]) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
