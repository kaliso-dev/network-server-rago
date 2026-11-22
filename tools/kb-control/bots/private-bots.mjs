// Private game test bots for RagoLobby and RagoBedwars (see tools/lobby-check/README.md,
// run-arena-test.sh): Bedwars Normal, the private game whose players choose their teams (the
// owner's answers of 22/11).
//
// Three bots make a party. The test server has no map of 4 or 8 teams, so the menu of Bedwars
// Normal shows its two rows, 4s and 2s, with no map; and its 4s cannot be queued alone. The rest
// is played through test-equipes, a mode that run-arena-test.sh adds to the lobby's config with the
// same room (parties-privees-equipes) on Fast Bedwars 2v2, whose map here is the demo map (two
// teams of two, red and blue):
//   1. only the leader starts it; its menu shows a row "2v2" with bw-demo; a click starts the team
//      choice for the whole party, the team menu opening for each with the red and the blue wool;
//   2. the leader and the second bot click red; the third clicks red too: full; it closes its menu
//      and /pg team opens it again, with the two of red written under it;
//   3. the countdown (8 seconds here) ends: the game starts by itself with red the two who chose it
//      and blue the third, who chose none (the emptiest team), in no stats;
//   4. the game is a Fast Bedwars 2v2 on bw-demo with those teams: the two red bots on the red
//      island, the third on the blue one; it leaves, red wins, all back in the lobby.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 3 * 60 * 1000
/** DemoMap: the spawns of the two islands. */
const RED_SPAWN_X = -32.5
const BLUE_SPAWN_X = 33.5
/** TeamRooms.teamSlot: two teams, two slots apart in the middle row. */
const RED_SLOT = 10
const BLUE_SLOT = 12
/** The countdown of test-equipes in run-arena-test.sh. */
const COUNTDOWN = 8

let finishing = false

function abort (error) {
  console.error(`[private-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[private-bots] ${text}`)
}

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
    bot.windows = []
    bot.on('windowOpen', (window) => {
      if (windowTitle(window).includes('What do you want to do?')) bot.firstMenu = window
      bot.windows.push(window)
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

function windowTitle (window) {
  return typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
}

/** The name and lore of an item, without colours. */
function text (item) {
  return JSON.stringify(item?.nbt ?? null).replace(/\\u00a7.|§./g, '')
}

function mark (bot) {
  return bot.chats.length
}

async function chatLine (bot, pattern, timeoutMs, from) {
  await waitFor(() => bot.chats.slice(from).some((line) => pattern.test(line)), timeoutMs,
    () => `${bot.username} to read ${pattern}; chat: ${JSON.stringify(bot.chats.slice(from))}`)
  return bot.chats.slice(from).find((line) => pattern.test(line))
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
}

function where (bot) {
  const p = bot.entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
}

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 15000,
    () => `${bot.username} in the lobby (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
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

/** The next window the bot opens whose title has this text, after this many windows. */
async function windowAfter (bot, count, title, what) {
  await waitFor(() => bot.windows.slice(count).some((window) => windowTitle(window).includes(title)), 5000,
    () => `${bot.username}: ${what} (${title}); opened: ${JSON.stringify(bot.windows.slice(count).map(windowTitle))}`)
  return bot.windows.slice(count).reverse().find((window) => windowTitle(window).includes(title))
}

async function party (leader, others) {
  for (const other of others) {
    const fromLeader = mark(leader)
    const fromOther = mark(other)
    leader.chat(`/p invite ${other.username}`)
    await chatLine(other, new RegExp(`${leader.username} has invited you to join their party!`), 5000, fromOther)
    other.chat(`/party accept ${leader.username}`)
    await chatLine(leader, new RegExp(`${other.username} joined the party\\.`), 5000, fromLeader)
  }
  log(`party: ${leader.username} with ${others.map((bot) => bot.username).join(' and ')}`)
}

/** Bedwars Normal itself: its rows of 4s and 2s, empty on this server; its 4s not a queue. */
async function bedwarsNormal (leader, other) {
  const fromOther = mark(other)
  other.chat('/queue bedwars-normal')
  await chatLine(other, /Only the party leader can start a private game\./, 5000, fromOther)
  const count = leader.windows.length
  leader.chat('/queue bedwars-normal')
  const menu = await windowAfter(leader, count, 'Bedwars Normal: choose a map', 'the maps of Bedwars Normal')
  await waitFor(() => menu.slots[0] != null && menu.slots[9] != null, 5000, 'the two rows of the menu')
  const fours = text(menu.slots[0])
  const twos = text(menu.slots[9])
  if (!fours.includes('4s (4 teams of 4)') || !fours.includes('No map of this size on the server yet.')) {
    throw new Error(`the first row is not the 4s, with no map here: ${fours}`)
  }
  if (!twos.includes('2s (8 teams of 2)') || !twos.includes('No map of this size on the server yet.')) {
    throw new Error(`the second row is not the 2s, with no map here: ${twos}`)
  }
  for (const slot of [1, 10]) {
    if (menu.slots[slot] != null) throw new Error(`a map in the menu, slot ${slot}: ${text(menu.slots[slot])}: no map of 4 or 8 teams here`)
  }
  leader.closeWindow(menu)
  const from = mark(leader)
  leader.chat('/queue bedwars-normal-4s')
  await chatLine(leader, /Bedwars Normal 4s is played with your party: its leader clicks Bedwars Normal in the Private games\./, 5000, from)
  log('Bedwars Normal: only the leader starts it; its menu has the row of the 4s, then the row of the 2s, no map of 4 or 8'
    + ' teams on this server; its 4s is no queue')
}

/** The team menu that just opened for this bot, with the red wool and the blue wool. */
async function teamMenu (bot, count) {
  const menu = await windowAfter(bot, count, 'Choose your team', 'the team menu')
  await waitFor(() => menu.slots[RED_SLOT]?.name === 'wool' && menu.slots[BLUE_SLOT]?.name === 'wool', 5000,
    () => `the red and blue wool of the team menu: ${menu.slots[RED_SLOT]?.name}, ${menu.slots[BLUE_SLOT]?.name}`)
  if (menu.slots[RED_SLOT].metadata !== 14 || menu.slots[BLUE_SLOT].metadata !== 11) {
    throw new Error(`the colours of the teams: ${menu.slots[RED_SLOT].metadata}, ${menu.slots[BLUE_SLOT].metadata}, not 14 (red) and 11 (blue)`)
  }
  return menu
}

async function chooseTeams (leader, second, third) {
  const bots = [leader, second, third]
  const marks = bots.map(mark)
  const counts = bots.map((bot) => bot.windows.length)
  leader.chat('/queue test-equipes')
  const maps = await windowAfter(leader, counts[0], 'Test teams: choose a map', 'the maps of test-equipes')
  await waitFor(() => maps.slots[1]?.name === 'map' || maps.slots[1]?.name === 'empty_map', 5000,
    () => `the demo map in the menu: ${maps.slots[1]?.name}`)
  if (!text(maps.slots[0]).includes('2v2 (2 teams of 2)')) throw new Error(`the row of Fast Bedwars 2v2: ${text(maps.slots[0])}`)
  if (!text(maps.slots[1]).includes('bw-demo')) throw new Error(`the map of the row: ${text(maps.slots[1])}`)
  const afterMaps = leader.windows.length
  leader.clickWindow(1, 0, 0).catch(() => {})
  for (const [i, bot] of bots.entries()) {
    await chatLine(bot, new RegExp(`${leader.username} started a private game of Test teams 2v2 on bw-demo: choose your team`
      + ` \\(2 teams of 2\\)\\. The game starts in ${COUNTDOWN} seconds`), 5000, marks[i])
  }
  const menus = []
  for (const [i, bot] of bots.entries()) menus.push(await teamMenu(bot, i === 0 ? afterMaps : counts[i]))
  log('team choice: the demo map chosen, the team menu opened for the three, red and blue')

  // 2. red for the leader and the second; full for the third
  leader.clickWindow(RED_SLOT, 0, 0).catch(() => {})
  await chatLine(second, new RegExp(`${leader.username} joined Red\\.`), 5000, marks[1])
  second.clickWindow(RED_SLOT, 0, 0).catch(() => {})
  await chatLine(leader, new RegExp(`${second.username} joined Red\\.`), 5000, marks[0])
  const fromThird = mark(third)
  third.clickWindow(RED_SLOT, 0, 0).catch(() => {})
  await chatLine(third, /^Red is full\.$/, 5000, fromThird)
  await waitFor(() => menus[2].slots[RED_SLOT]?.count === 2 && text(menus[2].slots[RED_SLOT]).includes(second.username), 5000,
    () => `the open menu of ${third.username} to show the two of red: ${menus[2].slots[RED_SLOT]?.count} ${text(menus[2].slots[RED_SLOT])}`)
  third.closeWindow(menus[2])
  const reopened = third.windows.length
  third.chat('/pg team')
  const again = await teamMenu(third, reopened)
  const red = text(again.slots[RED_SLOT])
  if (!red.includes('2/2 players') || !red.includes(leader.username) || !red.includes(second.username) || !red.includes('Full.')) {
    throw new Error(`/pg team: red with its two players, full: ${red}`)
  }
  if (!text(again.slots[BLUE_SLOT]).includes('0/2 players')) throw new Error(`/pg team: blue empty: ${text(again.slots[BLUE_SLOT])}`)
  third.closeWindow(again)
  log(`team choice: ${leader.username} and ${second.username} in red, red full for ${third.username}; /pg team opened the menu again`)

  // 3. the countdown, then the game with the teams chosen
  for (const [i, bot] of bots.entries()) {
    await chatLine(bot, /The game starts in 3 seconds\.\.\./, (COUNTDOWN + 2) * 1000, marks[i])
    await chatLine(bot, /Private game of your party in Test teams 2v2 on bw-demo\. It counts in no stats\./, 8000, marks[i])
    await chatLine(bot, new RegExp(`Red: ${leader.username}, ${second.username}$`), 2000, marks[i])
    await chatLine(bot, new RegExp(`Blue: ${third.username}$`), 2000, marks[i])
  }
  log(`countdown: the game started by itself, red ${leader.username} and ${second.username}, blue ${third.username}`
    + ' (who chose none, in the emptiest team), in no stats')
  return marks
}

async function play (leader, second, third, marks) {
  const teams = [[leader, 'Red', RED_SPAWN_X], [second, 'Red', RED_SPAWN_X], [third, 'Blue', BLUE_SPAWN_X]]
  for (const [i, [bot, team]] of teams.entries()) {
    await chatLine(bot, new RegExp(`Fast Bedwars 2v2 on bw-demo: you are in the ${team} team\\.`), 30000, marks[i])
  }
  for (const [bot, team, spawnX] of teams) {
    await waitFor(() => bot.game.gameMode === 'survival' && Math.abs(bot.entity.position.x - spawnX) < 3, 10000,
      () => `${bot.username} (${team}) on its island, near x ${spawnX}: ${bot.game.gameMode} at ${where(bot)}`)
  }
  log('game: Fast Bedwars 2v2 on bw-demo with the teams chosen, the two red bots on the red island, the third on the blue one')
  const from = mark(leader)
  third.chat('/spawn')
  await expectInLobby(third, 'after /spawn')
  await chatLine(leader, /Winner: Red/, 10000, from)
  for (const bot of [leader, second]) await expectInLobby(bot, 'after the game')
  log(`end: ${third.username} left, red won, all back in the lobby`)
}

async function main () {
  const leader = await arrive('RagoPriveUn')
  const second = await arrive('RagoPriveDeux')
  const third = await arrive('RagoPriveTrois')
  await party(leader, [second, third])
  await bedwarsNormal(leader, second)
  const marks = await chooseTeams(leader, second, third)
  await play(leader, second, third, marks)

  finishing = true
  log('PASSED: Bedwars Normal of the Private games, its rows of 4s and 2s; the team choice of its party: the map, the team'
    + ' menu, a team full, /pg team, the countdown, who chose none in the emptiest team, the game with the teams chosen')
  for (const bot of [leader, second, third]) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
