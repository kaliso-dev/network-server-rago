// Red Rover test bots for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Three bots play Red Rover Sumo (red-rover-sumo) on a local 1.8.8 test server, with the bundled
// settings (points, the winner stays, first to 3 1v1 won):
//   1. the three join the queue alone and play together: two fight, the third watches (spectator);
//   2. three 1v1 in a row: each time the one who is not the first winner walks off the platform;
//      the winner stays on and fights the one who waited, the loser watches;
//   3. after the third 1v1 won, the first winner wins the game, the two others lose; all three back
//      in the lobby.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 4 * 60 * 1000

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
  for (const bot of bots) await expectInLobby(bot, 'after the Red Rover')

  finishing = true
  log('PASSED: Red Rover Sumo, three 1v1 in turn, the winner staying on')
  for (const bot of bots) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
