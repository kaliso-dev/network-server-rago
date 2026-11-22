// Spectator test bots for RagoLobby and RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// A game of the practice must take at least 10 spectators at once, the Block clutch first. One bot
// plays the Block clutch training; ten others come one after the other and watch it with the
// lobby's /spectate:
//   1. each is told it spectates, in the spectator mode, near the player, in the training's world;
//   2. the player is told of each of them, in red and bold with a ding, and keeps playing (still in survival);
//   3. a few seconds later, all ten still watch, near the player;
//   4. /spawn takes each of them back to the lobby, then the player too.
// The test server has the 50 places of the local server's server.properties (max-players): it had
// 8, too few for a game and ten spectators.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 3 * 60 * 1000
/** How many watch the game at once: the plan asks for 10 at least. */
const WATCHERS = 10
/** How far from the player a spectator may be: the zone of the training, which holds them near it. */
const NEAR = 64

let finishing = false

function abort (error) {
  console.error(`[spectate-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[spectate-bots] ${text}`)
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
    // the same lines with their colours (§ codes), and the sounds heard: the spectator's line is red
    // and bold, with a ding
    bot.coloured = []
    bot.sounds = []
    bot.on('message', (message) => bot.coloured.push(message.toMotd()))
    bot.on('soundEffectHeard', (name) => bot.sounds.push(name))
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

async function chatLine (bot, pattern, timeoutMs, from) {
  await waitFor(() => bot.chats.slice(from).some((line) => pattern.test(line)), timeoutMs,
    `${bot.username} to read ${pattern}; chat: ${JSON.stringify(bot.chats.slice(from))}`)
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
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

function distance (a, b) {
  return a.entity.position.distanceTo(b.entity.position)
}

function where (bot) {
  const p = bot.entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
}

async function main () {
  const player = await arrive('RagoClutch')
  let from = player.chats.length
  player.chat('/queue clutch')
  await chatLine(player, /Block clutch - /, 15000, from)
  await waitFor(() => player.game.gameMode === 'survival', 5000, 'survival mode in the training')
  await sleep(1000)
  log(`RagoClutch plays the Block clutch at ${where(player)} (${player.game.dimension})`)

  // 1. ten spectators, one after the other
  const watchers = []
  const told = player.chats.length
  const toldColoured = player.coloured.length
  const heard = player.sounds.length
  for (let i = 1; i <= WATCHERS; i++) {
    const name = `RagoVue${String(i).padStart(2, '0')}`
    const watcher = await arrive(name)
    from = watcher.chats.length
    watcher.chat('/spectate RagoClutch')
    await chatLine(watcher, /Spectating RagoClutch/, 10000, from)
    await waitFor(() => watcher.game.gameMode === 'spectator', 5000, `${name} in the spectator mode (now ${watcher.game.gameMode})`)
    await waitFor(() => watcher.game.dimension === player.game.dimension && distance(watcher, player) <= NEAR, 5000,
      `${name} near the player: at ${where(watcher)}, the player at ${where(player)}`)
    watchers.push(watcher)
    log(`${name} spectates, ${distance(watcher, player).toFixed(1)} blocks from the player (${watchers.length} watching)`)
  }

  // 2. the player told of each, still playing
  await waitFor(() => {
    const lines = player.chats.slice(told).filter((line) => /RagoVue\d+.* is now spectating your game/.test(line))
    return lines.length >= WATCHERS
  }, 5000, `the player told of the ${WATCHERS} spectators; chat: ${JSON.stringify(player.chats.slice(told))}`)
  if (player.game.gameMode !== 'survival') throw new Error(`the player left the training: mode ${player.game.gameMode}`)
  // in red and bold, each with a ding (the owner's ask of 22/11)
  const red = player.coloured.slice(toldColoured).filter((line) => /RagoVue\d+.* is now spectating your game/.test(line))
  const notRed = red.filter((line) => !/^(§[rcl])*§c§l(§[rcl])*RagoVue\d+(§[rcl])* is now spectating your game/.test(line))
  if (red.length < WATCHERS || notRed.length > 0) throw new Error(`the spectators' lines should be red and bold: ${JSON.stringify(red)}`)
  const dings = player.sounds.slice(heard).filter((name) => name === 'random.orb').length
  if (dings < WATCHERS) throw new Error(`the player heard ${dings} dings for ${WATCHERS} spectators: ${JSON.stringify(player.sounds.slice(heard))}`)
  log(`the player read each spectator in red and bold, with a ding (${dings} heard)`)

  // 3. all still there a few seconds later
  await sleep(4000)
  for (const watcher of watchers) {
    if (watcher.game.gameMode !== 'spectator') throw new Error(`${watcher.username} is no longer a spectator: ${watcher.game.gameMode}`)
    if (distance(watcher, player) > NEAR) throw new Error(`${watcher.username} went away: at ${where(watcher)}, the player at ${where(player)}`)
  }
  if (player.game.gameMode !== 'survival') throw new Error(`the player left the training: mode ${player.game.gameMode}`)
  log(`${WATCHERS} spectators at once, the player still in the training`)

  // 4. back to the lobby
  for (const watcher of watchers) {
    watcher.chat('/spawn')
    await expectInLobby(watcher, 'after spectating')
  }
  player.chat('/spawn')
  await expectInLobby(player, 'after the training')

  finishing = true
  log(`PASSED: ${WATCHERS} spectators watched the Block clutch at once, and went back to the lobby`)
  for (const bot of [player, ...watchers]) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
