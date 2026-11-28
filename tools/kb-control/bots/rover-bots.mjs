// Red Rover test bots for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Three bots play Red Rover on a local 1.8.8 test server, with the bundled settings (points, the
// winner stays, first to 3 1v1 won):
//   1. Red Rover Sumo (red-rover-sumo): the three join the queue alone and play together: two
//      fight, the third watches (spectator);
//   2. three 1v1 in a row: each time the one who is not the first winner walks off the platform;
//      the winner stays on and fights the one who waited, the loser watches;
//   3. after the third 1v1 won, the first winner wins the game, the two others lose, all three read
//      the top 3 (the winner first); all three back in the lobby.
//   4. Red Rover Block Placement (red-rover-block-placement, a duel played as a Red Rover, on the
//      plugin's two platforms): a fighter walks off its platform, the void gives the other 10, the
//      first to 10: the 1v1 won; the next one; the one who waits leaves (/spawn), then a fighter:
//      the last one in wins.
//   5. Red Rover Block Edit (red-rover-block-edit): a fighter leaves during the 1v1, the one who
//      waited comes in against the other; then that one leaves too: the last one in wins.
//   6. Red Rover Top Bridge (red-rover-top-bridge, in RagoBedwars, on its demo map): a fighter
//      walks off the side of the top bridge, the other wins the round, so the 1v1 (one round of one
//      life); the next one; the one who waits leaves, then a fighter: the last one in wins.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 5 * 60 * 1000

let finishing = false
/** The vector class of mineflayer's positions (from the first bot): new Vec3(x, y, z). */
let Vec3 = null

function abort (error) {
  console.error(`[rover-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[rover-bots] ${text}`)
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


function survivors (bots) {
  return bots.filter((bot) => bot.game.gameMode === 'survival')
}

/** The two fighters of the 1v1 in survival, the others watching in the spectator mode. */
async function fighters (bots, what) {
  await waitFor(() => survivors(bots).length === 2 && bots.every((bot) => ['survival', 'spectator'].includes(bot.game.gameMode)),
    8000, `${what}: two fighters and the others watching (${bots.map((bot) => `${bot.username} ${bot.game.gameMode}`).join(', ')})`)
  await sleep(300)
  return survivors(bots)
}

/** The bot walks away from the other one until it falls off the platform. */
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

/** The bot walks across the line to the other one, off the side of the top bridge. */
async function walkSideways (bot, other) {
  const p = bot.entity.position
  const q = other.entity.position
  const length = Math.hypot(q.x - p.x, q.z - p.z) || 1
  const dx = -(q.z - p.z) / length
  const dz = (q.x - p.x) / length
  await bot.lookAt(p.offset(dx * 20, 1.62, dz * 20), true)
  bot.setControlState('forward', true)
}

/** The three in the queue of this Red Rover, together: its two fighters once the fight is on. */
async function start (bots, mode, name) {
  const from = bots.map(mark)
  for (const bot of bots) bot.chat(`/queue ${mode}`)
  for (let i = 0; i < bots.length; i++) await chatLine(bots[i], new RegExp(`${name} with `), 20000, from[i])
  const fight = await fighters(bots, `the first 1v1 of the ${name}`)
  for (const bot of fight) await chatLine(bot, /Fight!/, 10000, from[bots.indexOf(bot)])
  log(`${name}: ${fight[0].username} vs ${fight[1].username}, ${bots.find((bot) => !fight.includes(bot)).username} watches`)
  return fight
}

/** This bot leaves the game (/spawn): the others read it. */
async function leaves (bot, others) {
  const from = others.map(mark)
  bot.chat('/spawn')
  await expectInLobby(bot, 'after /spawn')
  return from
}

async function blockPlacement (bots) {
  const [a, b] = await start(bots, 'red-rover-block-placement', 'Red Rover Block Placement')
  const waiting = bots.find((bot) => bot !== a && bot !== b)
  // a off its platform: +10 for b, who wins the 1v1 at 10; b stays on against the one who waited
  let from = bots.map(mark)
  await walkOff(a, b)
  try {
    await chatLine(b, new RegExp(`${b.username} wins the 1v1 \\(1/3\\)`), 15000, from[bots.indexOf(b)])
  } finally {
    a.clearControlStates()
  }
  for (let i = 0; i < bots.length; i++) {
    await chatLine(bots[i], new RegExp(`Next: ${b.username} vs ${waiting.username} \\(${a.username} waits\\)`), 5000, from[i])
  }
  const [c, d] = await fighters(bots, 'the second 1v1 of the block placement')
  if (![c, d].includes(b) || ![c, d].includes(waiting)) {
    throw new Error(`block placement, 1v1 2: ${c.username} vs ${d.username}, not ${b.username} vs ${waiting.username}`)
  }
  if (a.game.gameMode !== 'spectator') throw new Error(`${a.username} lost and does not watch: ${a.game.gameMode}`)
  log(`block placement: ${b.username} won the 1v1 by the void, stays on against ${waiting.username}`)
  // the one who waits leaves, then a fighter: the last one in wins
  from = await leaves(a, [b, waiting])
  await chatLine(b, new RegExp(`${a.username} left the game`), 5000, from[0])
  from = await leaves(waiting, [b])
  await chatLine(b, /Victory!.*gave up/, 10000, from[0])
  await expectInLobby(b, 'after the block placement')
  log(`block placement: ${a.username} who waited left, then ${waiting.username}: ${b.username} wins, the last one in`)
}

async function blockEdit (bots) {
  const [a, b] = await start(bots, 'red-rover-block-edit', 'Red Rover Block Edit')
  const waiting = bots.find((bot) => bot !== a && bot !== b)
  // a fighter leaves: the one who waited comes in against the other at once
  let from = await leaves(a, [b, waiting])
  for (const [i, bot] of [b, waiting].entries()) {
    await chatLine(bot, new RegExp(`${a.username} left the game`), 5000, from[i])
    await chatLine(bot, new RegExp(`Next: (${b.username} vs ${waiting.username}|${waiting.username} vs ${b.username})`), 5000, from[i])
  }
  await fighters([b, waiting], 'the block edit after one left')
  log(`block edit: ${a.username} left the 1v1, ${waiting.username} came in against ${b.username}`)
  from = await leaves(b, [waiting])
  await chatLine(waiting, /Victory!.*gave up/, 10000, from[0])
  await expectInLobby(waiting, 'after the block edit')
  log(`block edit: ${b.username} left too, ${waiting.username} wins`)
}

async function topBridge (bots) {
  const [a, b] = await start(bots, 'red-rover-top-bridge', 'Red Rover Top Bridge')
  const waiting = bots.find((bot) => bot !== a && bot !== b)
  // a off the side of the bridge: b wins the round, so the 1v1; b stays on against the one who waited
  let from = bots.map(mark)
  await walkSideways(a, b)
  try {
    await chatLine(b, new RegExp(`${b.username} wins the 1v1 \\(1/3\\)`), 15000, from[bots.indexOf(b)])
  } finally {
    a.clearControlStates()
  }
  for (let i = 0; i < bots.length; i++) {
    await chatLine(bots[i], new RegExp(`Next: ${b.username} vs ${waiting.username} \\(${a.username} waits\\)`), 5000, from[i])
  }
  const [c, d] = await fighters(bots, 'the second 1v1 of the top bridge')
  if (![c, d].includes(b) || ![c, d].includes(waiting)) {
    throw new Error(`top bridge, 1v1 2: ${c.username} vs ${d.username}, not ${b.username} vs ${waiting.username}`)
  }
  if (a.game.gameMode !== 'spectator') throw new Error(`${a.username} lost and does not watch: ${a.game.gameMode}`)
  log(`top bridge: ${b.username} won the 1v1, ${a.username} off the bridge; ${b.username} stays on against ${waiting.username}`)
  // the one who waits leaves, then a fighter: the last one in wins
  from = await leaves(a, [b, waiting])
  await chatLine(b, new RegExp(`${a.username} left the game`), 5000, from[0])
  from = await leaves(waiting, [b])
  await chatLine(b, new RegExp(`${waiting.username} gave up`), 10000, from[0])
  await chatLine(b, new RegExp(`Winner: .* - ${b.username}`), 10000, from[0])
  await expectInLobby(b, 'after the top bridge')
  log(`top bridge: ${a.username} who waited left, then ${waiting.username}: ${b.username} wins, the last one in`)
}

async function main () {
  const bots = []
  for (const name of ['RagoRoverUn', 'RagoRoverDeux', 'RagoRoverTrois']) bots.push(await arrive(name))
  Vec3 = bots[0].entity.position.constructor

  // 1. the queue alone, the three together
  let from = bots.map(mark)
  for (const bot of bots) bot.chat('/queue red-rover-sumo')
  for (let i = 0; i < bots.length; i++) await chatLine(bots[i], /Red Rover Sumo with /, 20000, from[i])
  let [a, b] = await fighters(bots, 'the first 1v1')
  for (let i = 0; i < bots.length; i++) await chatLine(bots[i], /Fight!/, 10000, from[i])
  // the first winner: the fighter at the lower x
  const champion = a.entity.position.x < b.entity.position.x ? a : b
  log(`red rover: ${a.username} vs ${b.username}, ${bots.find((bot) => bot !== a && bot !== b).username} watches`)

  // 2. three 1v1: the other fighter walks off each time
  for (let round = 1; round <= 3; round++) {
    const loser = a === champion ? b : a
    from = bots.map(mark)
    await walkOff(loser, champion)
    try {
      await chatLine(champion, new RegExp(`${champion.username} wins the 1v1 \\(${round}/3\\)`), 10000, from[bots.indexOf(champion)])
    } finally {
      loser.clearControlStates()
    }
    if (round === 3) break
    const waited = bots.find((bot) => bot !== champion && bot !== loser)
    for (let i = 0; i < bots.length; i++) {
      await chatLine(bots[i], new RegExp(`Next: ${champion.username} vs ${waited.username} \\(${loser.username} waits\\)`), 5000, from[i])
    }
    ;[a, b] = await fighters(bots, `1v1 ${round + 1}`)
    if (![a, b].includes(champion) || ![a, b].includes(waited)) {
      throw new Error(`1v1 ${round + 1}: ${a.username} vs ${b.username}, not ${champion.username} vs ${waited.username}`)
    }
    if (loser.game.gameMode !== 'spectator') throw new Error(`${loser.username} lost and does not watch: ${loser.game.gameMode}`)
    await chatLine(champion, /Fight!/, 10000, from[bots.indexOf(champion)])
    log(`red rover: ${champion.username} won 1v1 ${round}, stays on against ${waited.username}; ${loser.username} watches`)
  }

  // 3. the game won by the champion
  for (let i = 0; i < bots.length; i++) {
    await chatLine(bots[i], bots[i] === champion ? /Victory!/ : /Defeat\./, 10000, from[i])
  }
  log(`red rover: ${champion.username} won the game, first to 3`)
  // the top 3 of the end (the owner's ask of 22/11): the champion first with its 3 1v1 won, the others 0
  const others = bots.filter((bot) => bot !== champion).map((bot) => bot.username).join('|')
  for (let i = 0; i < bots.length; i++) {
    await chatLine(bots[i], /^Top 3$/, 5000, from[i])
    await chatLine(bots[i], new RegExp(`1st - ${champion.username} - 3 1v1 won`), 5000, from[i])
    await chatLine(bots[i], new RegExp(`2nd - (${others}) - 0 1v1 won`), 5000, from[i])
    await chatLine(bots[i], new RegExp(`3rd - (${others}) - 0 1v1 won`), 5000, from[i])
  }
  log(`red rover: the top 3 of the end, ${champion.username} first`)
  for (const bot of bots) await expectInLobby(bot, 'after the Red Rover')

  await blockPlacement(bots)
  await blockEdit(bots)
  await topBridge(bots)

  finishing = true
  log('PASSED: Red Rover Sumo, three 1v1 in turn, the winner staying on; Red Rover Block Placement and Block Edit,'
    + ' a 1v1 won by the void, the next one, and those who leave; Red Rover Top Bridge, a 1v1 won off the bridge,'
    + ' the next one, and those who leave')
  for (const bot of bots) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
