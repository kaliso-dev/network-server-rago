// RagoBot test bots for RagoLobby (see tools/lobby-check/README.md, run-arena-test.sh).
//
// RagoBot is a player of the server that a party leader invites with /p invite RagoBot (the
// owner's choice of 22/11: only in a party, never ranked, never to fill a random queue; Top Bridge
// 1v1 and 2v2 and the Sumo 1v1 first). Test bots, with the bundled settings:
//   1. RagoLeader invites RagoBot: it joins the party at once and stands next to it; a second
//      invite finds it there already; RagoBot refuses a ranked queue and a mode it does not play;
//      /duel RagoBot from another player points to the party;
//   2. Sumo 1v1 (sumo, RagoPractice): RagoLeader and RagoBot, a game of the party that counts in
//      the stats; RagoLeader stands still: RagoBot comes to it, swings and pushes it (knockback),
//      and pushes it off the platform: RagoLeader loses;
//   3. Top Bridge 1v1 (top-bridge-1v1, RagoBedwars, on its demo map): RagoBot crosses the bridge
//      and hits RagoLeader with the sword of the kit, until it wins a round; RagoLeader leaves;
//   4. Top Bridge 2v2: the party of RagoLeader and RagoBot queues as one team, two players alone
//      join the queue: the four play, RagoBot hits one of the two others; they leave;
//   5. /p kick RagoBot: RagoBot leaves the server; invited again, then the party disbanded: it
//      leaves again.
// run-arena-test.sh also checks the server log: no game of these was counted as a private game of
// a party (those count in no stats), and no error.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 7 * 60 * 1000
const BOT = 'RagoBot'

let finishing = false

function abort (error) {
  console.error(`[ragobot-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[ragobot-bots] ${text}`)
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
    bot.pushes = 0
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
      // its own knockback: the velocity the server sends it
      bot._client.on('entity_velocity', (packet) => {
        if (bot.entity != null && packet.entityId === bot.entity.id) bot.pushes++
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

function where (entity) {
  const p = entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
}

/** Watches RagoBot from this bot: its swings, and how far it went from where it was first seen. */
function watch (bot) {
  const seen = { swings: 0, start: null, farthest: 0 }
  const onSwing = (entity) => {
    if (entity.username === BOT) seen.swings++
  }
  const onMove = (entity) => {
    if (entity.username !== BOT) return
    if (seen.start == null) seen.start = entity.position.clone()
    seen.farthest = Math.max(seen.farthest, entity.position.distanceTo(seen.start))
  }
  bot.on('entitySwingArm', onSwing)
  bot.on('entityMoved', onMove)
  seen.stop = () => {
    bot.removeListener('entitySwingArm', onSwing)
    bot.removeListener('entityMoved', onMove)
  }
  return seen
}

async function invite (leader) {
  const from = mark(leader)
  leader.chat(`/p invite ${BOT}`)
  await chatLine(leader, /RagoBot joined the party\./, 10000, from)
  await chatLine(leader, /RagoBot plays .*Top Bridge 1v1, Top Bridge 2v2 and Sumo duel.* with your party, never ranked: your games with it count in your stats, never in the ELO\./, 5000, from)
  await waitFor(() => ragoBot(leader) != null, 5000, `${leader.username} to see RagoBot (players: ${Object.keys(leader.players).join(', ')})`)
  const distance = ragoBot(leader).position.distanceTo(leader.entity.position)
  if (distance > 3) throw new Error(`RagoBot joined ${distance.toFixed(1)} blocks away from ${leader.username}, at ${where(ragoBot(leader))}`)
}

async function gone (bot, what) {
  await waitFor(() => bot.players[BOT] == null, 5000, `RagoBot to leave the server (${what})`)
}

async function sumo (leader) {
  let from = mark(leader)
  const pushesBefore = leader.pushes
  const seen = watch(leader)
  leader.chat('/queue sumo')
  await chatLine(leader, /Game of your party in Sumo duel: RagoLeader vs RagoBot\. It counts in your stats, never in the ELO\./, 5000, from)
  await chatLine(leader, /Sumo duel against RagoBot/, 15000, from)
  log(`sumo: RagoLeader vs RagoBot, RagoLeader stands still`)
  from = mark(leader)
  try {
    await waitFor(() => leader.pushes - pushesBefore >= 3 && seen.swings >= 3, 45000,
      `RagoBot to swing at RagoLeader and push it (${seen.swings} swings seen, ${leader.pushes - pushesBefore} pushes,`
      + ` RagoBot ${ragoBot(leader) == null ? 'not seen' : `at ${where(ragoBot(leader))}, ${seen.farthest.toFixed(1)} blocks from its start`},`
      + ` RagoLeader at ${where(leader.entity)})`)
    log(`sumo: RagoBot came ${seen.farthest.toFixed(1)} blocks, swung ${seen.swings} times, pushed RagoLeader ${leader.pushes - pushesBefore} times`)
    const end = await chatLine(leader, /^(Defeat\.|Victory!)/, 90000, from)
    if (!end.startsWith('Defeat.')) throw new Error(`RagoLeader won the sumo: RagoBot fell off on its own (${end})`)
  } finally {
    seen.stop()
  }
  await expectInLobby(leader, 'after the sumo')
  log(`sumo: RagoBot pushed RagoLeader off the platform and won, after ${seen.swings} swings`)
}

async function topBridge1v1 (leader) {
  let from = mark(leader)
  const seen = watch(leader)
  leader.chat('/queue top-bridge-1v1')
  await chatLine(leader, /Game of your party in Top Bridge 1v1: RagoLeader vs RagoBot\. It counts in your stats, never in the ELO\./, 5000, from)
  const welcome = await chatLine(leader, /Top Bridge 1v1 on .*: you are in the \S+ team\./, 20000, from)
  const team = /you are in the (\S+) team\./.exec(welcome)[1]
  log(`top bridge 1v1: RagoLeader in the ${team} team, against RagoBot`)
  try {
    await waitFor(() => leader.health < 20, 60000,
      `RagoBot to hit RagoLeader with its sword (health ${leader.health}, ${seen.swings} swings seen, RagoBot`
      + ` ${ragoBot(leader) == null ? 'not seen' : `at ${where(ragoBot(leader))}, ${seen.farthest.toFixed(1)} blocks from its start`},`
      + ` RagoLeader at ${where(leader.entity)})`)
    log(`top bridge 1v1: RagoBot crossed ${seen.farthest.toFixed(1)} blocks and hit RagoLeader (health ${leader.health.toFixed(1)})`)
    const round = await chatLine(leader, /\S+ wins round 1!/, 60000, from)
    if (round.startsWith(team)) throw new Error(`RagoLeader's team won the round: RagoBot fell off on its own (${round})`)
  } finally {
    seen.stop()
  }
  log('top bridge 1v1: RagoBot won the first round')
  from = mark(leader)
  leader.chat('/spawn')
  await expectInLobby(leader, 'after /spawn in the top bridge')
  log('top bridge 1v1: RagoLeader left, back in the lobby with RagoBot')
}

async function topBridge2v2 (leader, others) {
  const from = [leader, ...others].map(mark)
  leader.chat('/queue top-bridge-2v2')
  await chatLine(leader, /Your party joined the queue for .*Top Bridge 2v2.*You will play in the same team\./, 5000, from[0])
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
  const others = [await arrive('RagoAutreUn'), await arrive('RagoAutreDeux')]

  // 1. the invite, and what it refuses
  await invite(leader)
  let from = mark(leader)
  leader.chat(`/p invite ${BOT}`)
  await chatLine(leader, /RagoBot is already in the party\./, 5000, from)
  from = mark(leader)
  leader.chat('/queue ranked-1v1')
  await chatLine(leader, /RagoBot never plays ranked: \/p kick RagoBot first\./, 5000, from)
  from = mark(leader)
  leader.chat('/queue block-fight-1v1')
  await chatLine(leader, /RagoBot plays .*Sumo duel only: \/p kick RagoBot to play /, 5000, from)
  const fromOther = mark(others[0])
  others[0].chat(`/duel ${BOT}`)
  await chatLine(others[0], /RagoBot plays with your party: \/p invite RagoBot, then pick its mode as the leader\./, 5000, fromOther)
  log('invite: RagoBot joined the party next to RagoLeader; it refuses a second invite, ranked, a mode it does not play, a /duel')

  // 2. to 4. its games
  await sumo(leader)
  await topBridge1v1(leader)
  await topBridge2v2(leader, others)

  // 5. it leaves once out of the party
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

  finishing = true
  log('PASSED: RagoBot joins a party by /p invite, refuses ranked and the other modes, wins a Sumo duel and a round'
    + ' of Top Bridge 1v1 against a player who stands still, plays Top Bridge 2v2 in its party\'s team, and'
    + ' leaves the server once out of the party')
  for (const bot of [leader, ...others]) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
