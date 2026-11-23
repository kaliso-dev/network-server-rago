// Private game test bots for RagoLobby and RagoBedwars (see tools/lobby-check/README.md,
// run-arena-test.sh): Bedwars Normal, the private game whose players choose their teams (the
// owner's answers of 22/11).
//
// Three bots make a party. The test server has no map of 4 or 8 teams, so the menu of Bedwars
// Normal shows its two rows, 4s and 2s, with no map; and its 4s cannot be queued alone. The rest
// is played through test-equipes, a mode that run-arena-test.sh adds to the lobby's config with the
// same room (parties-privees-equipes) on Fast Bedwars 2v2, whose map here is the demo map (two
// teams of two, red and blue), and the settings of the leader (reglages-prives, which the script
// gives Fast Bedwars 2v2 too):
//   0. the leader sets the game from the map menu: Speed II (three clicks, then a right-click back),
//      Max Diamond, Max Emerald and Break the Map on; then those of Hypixel: Double Health, Low
//      Gravity, Respawn Time 1 second, Event Times 2x (two clicks), Bed Instabreak, and One Hit One
//      Kill, whose click opens its weapons, each on or off: the Wooden Sword on, the Bow on then off
//      again; another player cannot (/pg settings);
//   1. only the leader starts it; its menu shows a row "2v2" with bw-demo; a click starts the team
//      choice for the whole party, the team menu opening for each with the red and the blue wool;
//   2. the leader and the second bot click red; the third clicks red too: full; it closes its menu
//      and /pg team opens it again, with the two of red written under it;
//   3. the countdown (8 seconds here) ends: the game starts by itself with red the two who chose it
//      and blue the third, who chose none (the emptiest team), in no stats;
//   4. the game is a Fast Bedwars 2v2 on bw-demo with those teams: the two red bots on the red
//      island, the third on the blue one; with the settings: Speed II on the leader, Protection IV
//      on its armour and Sharpness on its sword, the emerald generator at Tier III and no Emerald
//      event in the sidebar, a block of the island broken by hand; 20 hearts and Jump Boost III on
//      the leader, the end of the beds at half its time; the leader walks into the void and is back
//      a second later, its 20 hearts full; the third leaves, red wins, all back in the lobby. Bed
//      Instabreak and One Hit One Kill are not played here (no bot goes to the other island): only
//      their unit tests;
//   5. FFA Archway (the owner's choice of 23/11), the FFA of the party alone: its game is no queue,
//      only the leader starts it; the three in the FFA (on the demo map here), each for themselves,
//      the end in the sidebar; the third walks into the void and comes back at once; after its
//      minutes (1 here: ffa-privee-minutes in run-arena-test.sh, 30 on the server) "Time is up.",
//      nobody with a kill: a draw, and the kills leaderboard of the three, level, by name, the
//      third with its death; all back in the lobby.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 5 * 60 * 1000
/** DemoMap: the spawns of the two islands. */
const RED_SPAWN_X = -32.5
const BLUE_SPAWN_X = 33.5
/** TeamRooms.teamSlot: two teams, two slots apart in the middle row. */
const RED_SLOT = 10
const BLUE_SLOT = 12
/** The countdown of test-equipes in run-arena-test.sh. */
const COUNTDOWN = 8
/** TeamRooms.settingSlot: ten settings, seven in the second row, three in the third; the back arrow under them. */
const SETTING_SLOTS = {
  speed: 10,
  maxDiamond: 11,
  maxEmerald: 12,
  breakMap: 13,
  healthBuff: 14,
  lowGravity: 15,
  respawnTime: 16,
  eventTimes: 19,
  bedInstabreak: 20,
  oneHitOneKill: 21
}
const SETTINGS_BACK = 31
/** The weapons of One Hit One Kill in its menu (TeamRooms.settingSlot of eight), by their items; the back arrow under them. */
const WEAPON_SLOTS = [[10, 'wooden_sword'], [11, 'stone_sword'], [12, 'iron_sword'], [13, 'diamond_sword'], [14, 'stick'],
  [15, 'bow'], [16, 'fire_charge'], [19, 'tnt']]
const WEAPONS_BACK = 31
/** The settings in the team menu of the leader. */
const TEAM_SETTINGS_SLOT = 26
/** A block of the red island out of the protection of its spawn and its generator (DemoMap, BedwarsGame.near). */
const ISLAND_BLOCK = [-30, 64, 3]
const SPEED = 1
const JUMP_BOOST = 8
/** Double Health: 20 hearts. */
const DOUBLE_HEALTH = 40
/** The end of the beds in Fast Bedwars (fast-bedwars.evenements.fin-des-lits, 15 minutes), at Event Times 2x. */
const BEDS_GONE_AT_2X = 15 * 60 / 2
/** The minutes of FFA Archway on the test server (ffa-privee-minutes in run-arena-test.sh). */
const FFA_MINUTES = 1
/** The last choices of the leader, as the start of the game tells them. */
const CHOSEN = ['Speed: Speed II', 'Max Diamond: On', 'Max Emerald: On', 'Break the Map: On', 'Health Buff: Double Health',
  'Low Gravity: On', 'Respawn Time: 1 second', 'Event Times: 2x - Faster', 'Bed Instabreak: On',
  'One Hit One Kill: Wooden Sword']

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
  // its last row: the settings of the game, Bedwars Normal having them (reglages-prives)
  if (menu.slots[22]?.name !== 'comparator' || !text(menu.slots[22]).includes('Game settings')) {
    throw new Error(`the settings of the game in the last row of Bedwars Normal: ${menu.slots[22]?.name} ${text(menu.slots[22])}`)
  }
  leader.closeWindow(menu)
  const from = mark(leader)
  leader.chat('/queue bedwars-normal-4s')
  await chatLine(leader, /Bedwars Normal 4s is played with your party: its leader clicks Bedwars Normal in the Private games\./, 5000, from)
  log('Bedwars Normal: only the leader starts it; its menu has the row of the 4s, then the row of the 2s, no map of 4 or 8'
    + ' teams on this server, then the settings of the game; its 4s is no queue')
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

/** A click of the leader on a setting, and what the leader is told: "RagoPriveUn set Speed: Speed I." */
async function clickSetting (leader, slot, right, told) {
  const from = mark(leader)
  leader.clickWindow(slot, right ? 1 : 0, 0).catch(() => {})
  await chatLine(leader, new RegExp(`^${leader.username} set ${told}\\.$`), 5000, from)
}

/** 0. The leader sets the game from the map menu; the second cannot. Returns the map menu, open again. */
async function setGame (leader, second, maps) {
  const fromSecond = mark(second)
  second.chat('/pg settings')
  await chatLine(second, /Only the party leader sets the game of a private game\./, 5000, fromSecond)
  const button = maps.slots.find((item) => item?.name === 'comparator')
  if (button == null || !text(button).includes('Game settings') || !text(button).includes('Speed: None')) {
    throw new Error(`the settings of the game in the map menu, all as in a normal game: ${text(button)}`)
  }
  const count = leader.windows.length
  leader.clickWindow(button.slot, 0, 0).catch(() => {})
  const menu = await windowAfter(leader, count, 'Game settings', 'the settings of the game')
  const icons = {
    speed: 'sugar',
    maxDiamond: 'diamond',
    maxEmerald: 'emerald',
    breakMap: 'iron_pickaxe',
    healthBuff: 'golden_apple',
    lowGravity: 'feather',
    respawnTime: 'clock',
    eventTimes: 'experience_bottle',
    bedInstabreak: 'bed',
    oneHitOneKill: 'diamond_sword'
  }
  await waitFor(() => Object.entries(icons).every(([key, name]) => menu.slots[SETTING_SLOTS[key]]?.name === name), 5000,
    () => `the ten settings: ${Object.values(SETTING_SLOTS).map((at) => menu.slots[at]?.name).join(', ')}`)
  for (const told of ['Speed: Speed I', 'Speed: Speed II', 'Speed: Speed III']) await clickSetting(leader, SETTING_SLOTS.speed, false, told)
  await clickSetting(leader, SETTING_SLOTS.speed, true, 'Speed: Speed II')
  await clickSetting(leader, SETTING_SLOTS.maxDiamond, false, 'Max Diamond: On')
  await clickSetting(leader, SETTING_SLOTS.maxEmerald, false, 'Max Emerald: On')
  await clickSetting(leader, SETTING_SLOTS.breakMap, false, 'Break the Map: On')
  await clickSetting(leader, SETTING_SLOTS.healthBuff, false, 'Health Buff: Double Health')
  await clickSetting(leader, SETTING_SLOTS.lowGravity, false, 'Low Gravity: On')
  await clickSetting(leader, SETTING_SLOTS.respawnTime, false, 'Respawn Time: 1 second')
  await clickSetting(leader, SETTING_SLOTS.eventTimes, false, 'Event Times: 0\\.5x - Slower')
  await clickSetting(leader, SETTING_SLOTS.eventTimes, false, 'Event Times: 2x - Faster')
  await clickSetting(leader, SETTING_SLOTS.bedInstabreak, false, 'Bed Instabreak: On')
  await waitFor(() => text(menu.slots[SETTING_SLOTS.speed]).includes('Speed: Speed II'), 5000,
    () => `the menu to show Speed II: ${text(menu.slots[SETTING_SLOTS.speed])}`)
  await oneHitOneKill(leader)
  const back = leader.windows.length
  leader.clickWindow(SETTINGS_BACK, 0, 0).catch(() => {})
  const again = await windowAfter(leader, back, 'Test teams: choose a map', 'the map menu again, from the settings')
  await waitFor(() => again.slots.some((item) => item?.name === 'comparator'), 5000, 'the settings in the map menu again')
  const now = text(again.slots.find((item) => item?.name === 'comparator'))
  for (const set of CHOSEN) {
    if (!now.includes(set)) throw new Error(`the map menu to show ${set}: ${now}`)
  }
  log(`settings: ${second.username} cannot set the game; ${leader.username} set Speed II (three clicks, a right-click back),`
    + ' Max Diamond, Max Emerald, Break the Map, Double Health, Low Gravity, Respawn Time 1 second, Event Times 2x (two'
    + ' clicks), Bed Instabreak and One Hit One Kill with the Wooden Sword from the map menu, and went back to it')
  return again
}

/**
 * One Hit One Kill: its click opens its weapons, none on; the Wooden Sword on, the Bow on then off
 * again; the arrow goes back to the settings, which show one weapon of eight.
 */
async function oneHitOneKill (leader) {
  const count = leader.windows.length
  leader.clickWindow(SETTING_SLOTS.oneHitOneKill, 0, 0).catch(() => {})
  const weapons = await windowAfter(leader, count, 'One Hit One Kill', 'the weapons of One Hit One Kill')
  await waitFor(() => WEAPON_SLOTS.every(([at, name]) => weapons.slots[at]?.name === name), 5000,
    () => `the eight weapons: ${WEAPON_SLOTS.map(([at]) => weapons.slots[at]?.name).join(', ')}`)
  for (const [at] of WEAPON_SLOTS) {
    if (!text(weapons.slots[at]).includes(': Off')) throw new Error(`a weapon on before any click: ${text(weapons.slots[at])}`)
  }
  await clickSetting(leader, WEAPON_SLOTS[0][0], false, 'One Hit One Kill: Wooden Sword')
  await clickSetting(leader, WEAPON_SLOTS[5][0], false, 'One Hit One Kill: Wooden Sword, Bow')
  await clickSetting(leader, WEAPON_SLOTS[5][0], false, 'One Hit One Kill: Wooden Sword')
  await waitFor(() => text(weapons.slots[WEAPON_SLOTS[0][0]]).includes('Wooden Sword: On')
    && text(weapons.slots[WEAPON_SLOTS[5][0]]).includes('Bow: Off'), 5000,
  () => `the Wooden Sword on and the Bow off: ${text(weapons.slots[WEAPON_SLOTS[0][0]])} ${text(weapons.slots[WEAPON_SLOTS[5][0]])}`)
  const back = leader.windows.length
  leader.clickWindow(WEAPONS_BACK, 0, 0).catch(() => {})
  const settings = await windowAfter(leader, back, 'Game settings', 'the settings again, from the weapons')
  await waitFor(() => text(settings.slots[SETTING_SLOTS.oneHitOneKill]).includes('One Hit One Kill: 1/8'), 5000,
    () => `the settings to show one weapon of eight: ${text(settings.slots[SETTING_SLOTS.oneHitOneKill])}`)
  log(`One Hit One Kill: its eight weapons, none on; ${leader.username} set the Wooden Sword on, the Bow on then off, and`
    + ' went back to the settings (1/8)')
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
  await setGame(leader, second, maps)
  const afterMaps = leader.windows.length
  leader.clickWindow(1, 0, 0).catch(() => {})
  for (const [i, bot] of bots.entries()) {
    await chatLine(bot, new RegExp(`${leader.username} started a private game of Test teams 2v2 on bw-demo: choose your team`
      + ` \\(2 teams of 2\\)\\. The game starts in ${COUNTDOWN} seconds`), 5000, marks[i])
  }
  const menus = []
  for (const [i, bot] of bots.entries()) menus.push(await teamMenu(bot, i === 0 ? afterMaps : counts[i]))
  // the settings in the team menu: the leader's only
  await waitFor(() => menus[0].slots[TEAM_SETTINGS_SLOT]?.name === 'comparator', 5000, 'the settings in the team menu of the leader')
  if (menus[1].slots[TEAM_SETTINGS_SLOT] != null) throw new Error(`the settings in the team menu of ${second.username}`)
  log('team choice: the demo map chosen, the team menu opened for the three, red and blue, the settings in the leader\'s')

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
    await chatLine(bot, new RegExp(`Settings: ${CHOSEN.join(', ').replace(/\./g, '\\.')}$`), 2000, marks[i])
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
  await rules(leader, marks[0])
  await respawnTime(leader)
  const from = mark(leader)
  third.chat('/spawn')
  await expectInLobby(third, 'after /spawn')
  await chatLine(leader, /Winner: Red/, 10000, from)
  for (const bot of [leader, second]) await expectInLobby(bot, 'after the game')
  log(`end: ${third.username} left, red won, all back in the lobby`)
}

/** The lines of the sidebar, top to bottom, without colours. */
function sidebarLines (bot) {
  return (bot.scoreboard?.sidebar?.items ?? []).map((item) => String(item.name).replace(/§./g, ''))
}

/** The names over the entities the bot sees (the texts of the generators), without colours. */
function labels (bot) {
  return Object.values(bot.entities).map((e) => String(e.metadata?.[2] ?? '').replace(/§./g, '')).filter((name) => name.trim() !== '')
}

function enchanted (item, name, level) {
  return (item?.enchants ?? []).some((e) => e.name === name && e.lvl === level)
}

/** 4. The settings of the leader in the game. */
async function rules (leader, from) {
  await chatLine(leader, /Settings of the party leader: Speed II, Max Diamond, Max Emerald, Break the Map, Double Health, Low Gravity,/
    , 5000, from)
  await chatLine(leader, /, Low Gravity, Respawn Time: 1 second, Event Times: 2x - Faster, Bed Instabreak, One Hit One Kill: Wooden Sword$/,
    1000, from)
  await waitFor(() => leader.entity.effects?.[SPEED]?.amplifier === 1, 8000,
    () => `Speed II on ${leader.username}: ${JSON.stringify(leader.entity.effects)}`)
  await waitFor(() => enchanted(leader.inventory.slots[5], 'protection', 4)
    && enchanted(leader.inventory.items().find((item) => item.name.endsWith('_sword')), 'sharpness', 1), 5000,
  () => `Protection IV and Sharpness I (Max Diamond): helmet ${JSON.stringify(leader.inventory.slots[5]?.enchants)}, sword`
    + ` ${JSON.stringify(leader.inventory.items().find((item) => item.name.endsWith('_sword'))?.enchants)}`)
  await waitFor(() => labels(leader).some((name) => name.includes('Tier III Emerald')), 10000,
    () => `the emerald generator at Tier III (Max Emerald): ${JSON.stringify(labels(leader))}`)
  await waitFor(() => sidebarLines(leader).some((line) => /^Bed Gone in \d+:\d\d$/.test(line)), 5000,
    () => `the beds next in the sidebar, no Emerald event (Max Emerald): ${JSON.stringify(sidebarLines(leader))}`)
  if (sidebarLines(leader).some((line) => line.includes('Emerald'))) {
    throw new Error(`an Emerald event in the sidebar with Max Emerald: ${JSON.stringify(sidebarLines(leader))}`)
  }
  // Event Times 2x: the end of the beds at half its time
  const bedsGone = sidebarLines(leader).map((line) => /^Bed Gone in (\d+):(\d\d)$/.exec(line)).find((found) => found != null)
  const left = Number(bedsGone[1]) * 60 + Number(bedsGone[2])
  if (left > BEDS_GONE_AT_2X) throw new Error(`Event Times 2x: the beds gone in ${bedsGone[1]}:${bedsGone[2]}, after ${BEDS_GONE_AT_2X} s`)
  // Health Buff (Double Health) and Low Gravity (Jump Boost III)
  await waitFor(() => leader.health === DOUBLE_HEALTH, 5000, () => `Double Health on ${leader.username}: ${leader.health}`)
  await waitFor(() => leader.entity.effects?.[JUMP_BOOST]?.amplifier === 2, 5000,
    () => `Jump Boost III (Low Gravity) on ${leader.username}: ${JSON.stringify(leader.entity.effects)}`)
  // Break the Map: a block of the island, not placed by a player, breaks
  const Vec3 = leader.entity.position.constructor
  const at = new Vec3(...ISLAND_BLOCK)
  const before = leader.blockAt(at)?.name
  if (before == null || before === 'air') throw new Error(`no block of the island at ${ISLAND_BLOCK}: ${before}`)
  const fromDig = mark(leader)
  await leader.dig(leader.blockAt(at), true)
  await sleep(1000)
  if (leader.blockAt(at)?.name !== 'air') {
    throw new Error(`Break the Map: the ${before} at ${ISLAND_BLOCK} is still there (${leader.blockAt(at)?.name}); chat`
      + ` ${JSON.stringify(leader.chats.slice(fromDig))}`)
  }
  log(`rules: Speed II on ${leader.username}, Protection IV and Sharpness I from the start, the emerald generator at Tier III`
    + ` and the beds next in the sidebar, gone in ${bedsGone[1]}:${bedsGone[2]} (Event Times 2x), ${DOUBLE_HEALTH / 2} hearts`
    + ` and Jump Boost III, the ${before} of the island at ${ISLAND_BLOCK} broken by hand`)
}

/** Respawn Time 1 second: the leader walks off its island into the void, back a second later with its hearts full. */
async function respawnTime (leader) {
  const Vec3 = leader.entity.position.constructor
  const from = mark(leader)
  const p = leader.entity.position
  // west, along the middle of the island: the floor of its generator, then the void
  await leader.lookAt(new Vec3(p.x - 30, p.y + 1.62, p.z), true)
  leader.setControlState('forward', true)
  const fell = new RegExp(`${leader.username} fell into the void\\.`)
  await waitFor(() => leader.chats.slice(from).some((line) => fell.test(line)), 15000,
    () => `${leader.username} to fall into the void, at ${where(leader)}; chat: ${JSON.stringify(leader.chats.slice(from))}`)
  const died = Date.now()
  leader.setControlState('forward', false)
  await chatLine(leader, /You have respawned!/, 8000, from)
  const took = Date.now() - died
  if (took > 2500) throw new Error(`${leader.username} respawned after ${took} ms, not about 1 second (Respawn Time)`)
  await waitFor(() => Math.abs(leader.entity.position.x - RED_SPAWN_X) < 3 && leader.health === DOUBLE_HEALTH, 5000,
    () => `${leader.username} on its island with ${DOUBLE_HEALTH / 2} hearts after its respawn: ${where(leader)}, ${leader.health}`)
  log(`respawn: ${leader.username} fell into the void and respawned ${took} ms later (Respawn Time 1 second),`
    + ` ${DOUBLE_HEALTH / 2} hearts again`)
}

/** A death: the bot walks off its island of the demo map (west from red, east from blue) into the void, back at once. */
async function fall (bot) {
  const Vec3 = bot.entity.position.constructor
  const from = mark(bot)
  const p = bot.entity.position
  await bot.lookAt(new Vec3(p.x + (p.x < 0 ? -30 : 30), p.y + 1.62, p.z), true)
  bot.setControlState('forward', true)
  const fell = new RegExp(`${bot.username} fell into the void\\.`)
  await waitFor(() => bot.chats.slice(from).some((line) => fell.test(line)), 15000,
    () => `${bot.username} to fall into the void, at ${where(bot)}; chat: ${JSON.stringify(bot.chats.slice(from))}`)
  bot.setControlState('forward', false)
  await waitFor(() => bot.game.gameMode === 'survival' && bot.entity.position.y > 60, 8000,
    () => `${bot.username} back in the FFA after its fall: ${bot.game.gameMode} at ${where(bot)}`)
}

/** 5. FFA Archway, the FFA of the party alone, until the end of its minutes and its kills leaderboard. */
async function ffaArchway (leader, second, third) {
  const bots = [leader, second, third]
  let from = mark(leader)
  leader.chat('/queue ffa-archway-partie')
  await chatLine(leader, /FFA Archway is played with your party: its leader clicks FFA Archway in the Private games\./, 5000, from)
  from = mark(second)
  second.chat('/queue ffa-archway')
  await chatLine(second, /Only the party leader can start a private game\./, 5000, from)
  const marks = bots.map(mark)
  leader.chat('/queue ffa-archway')
  const started = Date.now()
  for (const [i, bot] of bots.entries()) {
    await chatLine(bot, new RegExp(`Private game of your party in FFA Archway: ${leader.username} vs ${second.username}`
      + ` vs ${third.username}\\. It counts in no stats\\.`), 5000, marks[i])
    await chatLine(bot, /FFA Archway on bw-demo: everyone for themselves\./, 30000, marks[i])
  }
  for (const bot of bots) {
    await waitFor(() => bot.game.gameMode === 'survival' && sidebarLines(bot).includes('Players: 3')
      && sidebarLines(bot).some((line) => /^Ends in: \d+:\d\d$/.test(line)), 10000,
    () => `${bot.username} in the FFA of the party, the end in its sidebar: ${bot.game.gameMode}, ${JSON.stringify(sidebarLines(bot))}`)
  }
  log(`FFA Archway: its game is no queue, only the leader starts it; the three in the FFA of the party on bw-demo, the end`
    + ` in the sidebar (${sidebarLines(leader).find((line) => line.startsWith('Ends in: '))})`)
  await fall(third)
  log(`FFA Archway: ${third.username} fell into the void and came back at once`)
  for (const [i, bot] of bots.entries()) {
    await chatLine(bot, /Time is up\./, (FFA_MINUTES * 60 + 30) * 1000, marks[i])
    await chatLine(bot, /Draw: no team won\./, 2000, marks[i])
    await chatLine(bot, /Kills leaderboard/, 2000, marks[i])
  }
  const took = Math.round((Date.now() - started) / 1000)
  // level in kills (none): the same place, by name; the third with its death
  const expected = [`1. ${second.username} - 0 kills, 0 deaths`, `1. ${third.username} - 0 kills, 1 death`,
    `1. ${leader.username} - 0 kills, 0 deaths`]
  const board = leader.chats.slice(marks[0]).map((line) => line.trim()).filter((line) => /^\d+\. /.test(line))
  if (JSON.stringify(board) !== JSON.stringify(expected)) {
    throw new Error(`the kills leaderboard: ${JSON.stringify(board)}, not ${JSON.stringify(expected)}`)
  }
  await chatLine(third, /You: 0 kills, 1 death$/, 2000, marks[2])
  for (const bot of bots) await expectInLobby(bot, 'after the FFA of the party')
  log(`FFA Archway: "Time is up." ${took} s after the start (${FFA_MINUTES} minute here), a draw, the kills leaderboard`
    + ` ${JSON.stringify(board)}, all back in the lobby`)
}

async function main () {
  const leader = await arrive('RagoPriveUn')
  const second = await arrive('RagoPriveDeux')
  const third = await arrive('RagoPriveTrois')
  await party(leader, [second, third])
  await bedwarsNormal(leader, second)
  const marks = await chooseTeams(leader, second, third)
  await play(leader, second, third, marks)
  await ffaArchway(leader, second, third)

  finishing = true
  log('PASSED: Bedwars Normal of the Private games, its rows of 4s and 2s; the settings of the leader (Speed, Max Diamond,'
    + ' Max Emerald, Break the Map, Health Buff, Low Gravity, Respawn Time, Event Times; Bed Instabreak and One Hit One Kill'
    + ' in the menu); the team choice of its party: the map, the team menu, a team full, /pg team, the'
    + ' countdown, who chose none in the emptiest team, the game with the teams chosen and the settings; FFA Archway, the'
    + ' FFA of the party alone, until its end and its kills leaderboard')
  for (const bot of [leader, second, third]) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
