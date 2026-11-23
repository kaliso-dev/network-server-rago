// Fast Bedwars test bots for RagoBedwars (see tools/lobby-check/README.md, run-arena-test.sh).
//
// Two bots play Fast Bedwars 1v1 (fast-bedwars-1v1) with the bundled settings. None of its maps (the
// 20 of the RBW queue) is on the test server: it plays on the demo map, bw-demo (red in the west,
// blue in the east, the spawns at x = -32.5 and 33.5, the item shop at z = 4.5).
//   1. both start on their islands with the start of Bed Wars (a wooden sword, the leather armour)
//      and the two lines of its welcome: the faster forges, the smaller shop, no team upgrades, the
//      beds gone at 15:00, the end at 22:30;
//   2. no Diamond II in the sidebar (no diamond generator without the upgrades): Emerald II, at 6
//      minutes; the emerald generator of the middle, no diamond one; the item shop, no upgrades NPC;
//   3. its forge, twice a Hypixel one: the red bot picks up 7 gold or more in 12 seconds on its
//      spawn (24 a minute, twice as many in the first 30 seconds; 4 or 5 with a forge of Bed Wars);
//   4. its shop: the default Quick Buy, whose 21st slot stays empty (no "Empty slot!" pane), and the
//      Blocks with the four of the shop alone (wool, blast-proof glass, end stone, oak planks: no
//      hardened clay, no ladder, no obsidian); wool bought;
//   5. blue leaves: red wins, both back in the lobby.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 4 * 60 * 1000
/** DemoMap: the spawns of the two islands. */
const RED_SPAWN_X = -32.5
const BLUE_SPAWN_X = 33.5
/** ShopMenu: the 21 item slots, three rows of seven, and the Blocks category on the top row. */
const ITEM_SLOTS = [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]
const BLOCKS_SLOT = 1

let finishing = false

function abort (error) {
  console.error(`[fast-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[fast-bots] ${text}`)
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
      if (windowTitle(window).includes('What do you want to do?')) bot.firstMenu = window
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

function countOf (bot, name) {
  return bot.inventory.items().filter((item) => item.name === name).reduce((sum, item) => sum + item.count, 0)
}

/** The lines of the sidebar, top to bottom, without colours. */
function sidebarLines (bot) {
  return (bot.scoreboard?.sidebar?.items ?? []).map((item) => String(item.name).replace(/§./g, ''))
}

/** The names floating over the entities the bot sees (the NPCs, the generators), without colours. */
function labels (bot) {
  return Object.values(bot.entities).map((e) => String(e.metadata?.[2] ?? '').replace(/§./g, '')).filter((name) => name.trim() !== '')
}

function where (bot) {
  const p = bot.entity.position
  return `${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)}`
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

/** The item shop villager of the bot's island: under its name, ITEM SHOP, which floats above it as on Hypixel. */
function shopVillager (bot) {
  const side = Math.sign(bot.entity.position.x)
  const label = Object.values(bot.entities).find((e) => String(e.metadata?.[2] ?? '').includes('ITEM SHOP') &&
    Math.sign(e.position.x) === side)
  if (label == null) return null
  return Object.values(bot.entities).find((e) => /villager/i.test(e.name ?? '') &&
    Math.abs(e.position.x - label.position.x) < 0.6 && Math.abs(e.position.z - label.position.z) < 0.6 &&
    e.position.y < label.position.y)
}

/** 1. Both in the queue of Fast Bedwars 1v1: the red bot and the blue one, once the fight is on. */
async function start (one, two) {
  const from = [mark(one), mark(two)]
  one.chat('/queue fast-bedwars-1v1')
  two.chat('/queue fast-bedwars-1v1')
  const teams = {}
  for (const [i, bot] of [one, two].entries()) {
    const line = await chatLine(bot,
      /2017 Bedwars 1v1 on bw-demo: you are in the (Red|Blue) team\. Protect your bed and destroy the others'!/, 30000, from[i])
    teams[line.includes('Red team') ? 'Red' : 'Blue'] = bot
  }
  if (teams.Red == null || teams.Blue == null) throw new Error('the two bots are not in two teams')
  for (const [i, bot] of [one, two].entries()) {
    await chatLine(bot,
      /^Faster forges, a smaller shop, no team upgrades\. The beds go at 15:00, the game ends at 22:30\.$/, 5000, from[i])
    await chatLine(bot, /Fight!/, 15000, from[i])
  }
  return { red: teams.Red, blue: teams.Blue }
}

async function checkStart (red, blue) {
  for (const [bot, team, spawnX] of [[red, 'Red', RED_SPAWN_X], [blue, 'Blue', BLUE_SPAWN_X]]) {
    await waitFor(() => bot.game.gameMode === 'survival' && countOf(bot, 'wooden_sword') === 1 &&
      bot.inventory.slots[5]?.name === 'leather_helmet', 5000,
    `${bot.username} to start as in Bed Wars: ${bot.game.gameMode}, ${JSON.stringify(bot.inventory.items().map((item) => item.name))},`
      + ` helmet ${bot.inventory.slots[5]?.name}`)
    const p = bot.entity.position
    if (Math.abs(p.x - spawnX) > 1.5 || Math.abs(p.z - 0.5) > 1.5) {
      throw new Error(`${bot.username} (${team}) is not on its spawn (${spawnX}, 0.5): at ${where(bot)}`)
    }
    // 2. Emerald II next, at 6 minutes: no Diamond II
    await waitFor(() => sidebarLines(bot).some((line) => /^Emerald II in [56]:\d\d$/.test(line)), 5000,
      `${bot.username} to see Emerald II next in the sidebar: ${JSON.stringify(sidebarLines(bot))}`)
    if (sidebarLines(bot).some((line) => line.includes('Diamond'))) {
      throw new Error(`${bot.username}: a diamond event in the sidebar: ${JSON.stringify(sidebarLines(bot))}`)
    }
    await waitFor(() => sidebarLines(bot).some((line) => line === `${team[0]} ${team}: ✔ YOU`), 5000,
      `${bot.username} to see its team with its bed in the sidebar: ${JSON.stringify(sidebarLines(bot))}`)
    await waitFor(() => shopVillager(bot) != null, 5000, `${bot.username} to see its item shop villager`)
    await waitFor(() => labels(bot).some((name) => name.includes('Emerald')), 10000,
      `${bot.username} to see the emerald generator of the middle: ${JSON.stringify(labels(bot))}`)
    const wrong = labels(bot).filter((name) => name.includes('Diamond') || name.includes('UPGRADES'))
    if (wrong.length > 0) throw new Error(`${bot.username}: no diamond generator, no upgrades NPC, but ${JSON.stringify(wrong)}`)
  }
  log('start: both on their islands with the start of Bed Wars; Emerald II next in the sidebar, no Diamond II;'
    + ' the emerald generator, no diamond one; the item shop, no upgrades NPC')
}

/** 3. Twice the gold of a Bed Wars forge: 7 or more in 12 seconds on the spawn. */
async function forge (red, startedAt) {
  const left = startedAt + 12000 - Date.now()
  if (left > 0) await sleep(left)
  const gold = countOf(red, 'gold_ingot')
  if (gold < 7) throw new Error(`${red.username} picked up ${gold} gold in 12 seconds on its spawn, not 7 or more (a fast forge)`)
  log(`forge: ${red.username} picked up ${gold} gold and ${countOf(red, 'iron_ingot')} iron in its first 12 seconds`)
}

/** 4. The shop: the Quick Buy, the Blocks of the shop alone, wool bought. */
async function shop (red) {
  const opened = new Promise((resolve) => red.once('windowOpen', resolve))
  red.activateEntity(shopVillager(red))
  const window = await Promise.race([opened, sleep(5000).then(() => null)])
  if (window == null) throw new Error(`${red.username}: a click on the item shop villager did not open the shop`)
  if (!windowTitle(window).includes('Item Shop')) throw new Error(`${red.username}: the villager opened ${windowTitle(window)}`)
  // the default Quick Buy: wool first, the fireball third in the third row, its 21st slot empty
  await waitFor(() => window.slots[19]?.name === 'wool', 5000, `the Quick Buy to start with wool, not ${window.slots[19]?.name}`)
  if (window.slots[39]?.name !== 'fire_charge') throw new Error(`slot 39 of the Quick Buy holds ${window.slots[39]?.name}, not the fireball`)
  if (window.slots[43] != null) throw new Error(`the last slot of the Quick Buy holds ${window.slots[43]?.name}: no "Empty slot!" pane in a smaller shop`)
  // the Blocks: wool, blast-proof glass, end stone, oak planks, nothing else
  red.clickWindow(BLOCKS_SLOT, 0, 0).catch(() => {})
  const blocks = ['wool', 'stained_glass', 'end_stone', 'planks']
  const shown = () => ITEM_SLOTS.map((slot) => window.slots[slot]?.name ?? null).filter((name) => name != null)
  await waitFor(() => JSON.stringify(shown()) === JSON.stringify(blocks), 5000,
    `the Blocks of the shop to be ${JSON.stringify(blocks)}: ${JSON.stringify(shown())}`)
  const from = mark(red)
  red.clickWindow(19, 0, 0).catch(() => {})
  await chatLine(red, /You purchased Wool/, 5000, from)
  red.closeWindow(window)
  await waitFor(() => countOf(red, 'wool') === 16, 5000, `${red.username} to get 16 wool (has ${countOf(red, 'wool')})`)
  log(`shop: the default Quick Buy without its "Empty slot!" pane; the Blocks: ${blocks.join(', ')}; wool bought`)
}

async function main () {
  const one = await arrive('RagoFastUn')
  const two = await arrive('RagoFastDeux')

  const { red, blue } = await start(one, two)
  const startedAt = Date.now()
  log(`Fast Bedwars 1v1: ${red.username} red, ${blue.username} blue`)
  await checkStart(red, blue)
  await forge(red, startedAt)
  await shop(red)

  // 5. blue leaves: red wins
  const from = mark(red)
  blue.chat('/spawn')
  await expectInLobby(blue, 'after /spawn')
  await chatLine(red, new RegExp(`Winner: Red - ${red.username}`), 10000, from)
  await expectInLobby(red, 'after the game')
  log('end: blue left the game, red won, both back in the lobby')

  finishing = true
  log('PASSED: Fast Bedwars 1v1, its welcome, its events (no Diamond II), the emerald generator alone, no upgrades NPC,'
    + ' its fast forge, its smaller shop')
  for (const bot of [one, two]) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
