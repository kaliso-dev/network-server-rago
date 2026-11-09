// Lobby and sumo test bots for RagoLobby and RagoPractice (see tools/lobby-check/README.md).
//
// Two bots join a local 1.8.8 test server that runs RagoLobby, RagoPractice and RagoCombatEngine
// and play the way a player does:
//   1. both arrive in the lobby: adventure mode, the selector in the first hotbar slot, the kit
//      editor book in the second, the sidebar; the Duels NPC is a Steve holding a sword, out of
//      the tab list, and a click on it opens Duels; RagoUn opens the kit editor with the book and
//      moves the stick of the sumo kit (run-lobby-test.sh gives the sumo one) from the first
//      hotbar slot to the fourth, saves, and finds it there when it opens the editor again;
//   2. RagoUn opens the selector with a right click, opens the Duels category (Daily Streak: 0 on
//      the sumo, nobody in its top yet), then clicks the sumo duel; RagoDeux types /queue sumo;
//   3. the duel starts after a 3, 2, 1 countdown in titles, each bot gets the stick where it put
//      it (RagoUn: fourth slot, RagoDeux: the kit's first), the sidebar shows the opponent and both
//      pings: RagoUn walks up to RagoDeux and hits it once (knockback, no damage), then
//      RagoDeux walks off the platform three times, so RagoUn wins 3 - 0;
//   4. both come back to the lobby, and read their own stats above the stats NPC and on the
//      leaderboard wall (RagoUn first with one win, and three kills); a click on the stats NPC opens
//      the stats menu; the XP bar shows the Network level; clicks on the main leaderboard go from the
//      Elo to the kills (a left click: the kills of the week) and to the Network level; the sumo of
//      the Duels NPC shows RagoUn first in the top win streaks of the day;
//   5. a second duel, which RagoUn leaves with /spawn: RagoDeux wins by forfeit;
//   6. RagoUn challenges RagoDeux with /duel, RagoDeux accepts, then leaves with /spawn;
//   7. a game of Ranked Bedwars 1v1 on the demo map of RagoBedwars: both type /queue ranked-1v1,
//      start on their islands with a wooden sword and leather armour, the sidebar lists the teams;
//      after "Fight!" they pick up the iron of their generator standing on their spawn, RagoUn opens
//      the item shop villager (under its floating name, ITEM SHOP) and buys wool from its Quick Buy, cannot place it near its spawn,
//      places it by its bed and breaks it again, cannot break its own bed, buys a fireball and
//      jumps on it at its feet (the blast lifts it and takes a heart, not its life); RagoDeux walks off its
//      island, dies in the void and respawns (its bed stands), then leaves with /spawn: RagoUn
//      wins, and both are back in the lobby;
//   8. a First Rush, a real 1v1: RagoUn asks for a map the mode does not have, then for bw-demo,
//      RagoDeux for any map; they meet on bw-demo, one red and one blue, with the events of a real
//      game in the sidebar; RagoDeux leaves with /spawn, RagoUn wins and both are back in the lobby;
//   9. a party: RagoUn invites RagoDeux with /p, RagoDeux accepts, /party list and /pc; RagoDeux
//      cannot queue (only the leader does); RagoUn queues the sumo: a private game between them,
//      without stats or XP; RagoUn queues Ranked Bedwars 2v2: the party waits in one team, and
//      leaves the queue with RagoDeux; RagoUn disbands the party.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 7 * 60 * 1000
// A new lobby world has a platform at y = 64: players stand at y = 65, at the centre.
const LOBBY_SPAWN = { x: 0.5, y: 65, z: 0.5 }
// The bundled config.yml has three categories, one slot apart in the middle row: Duels is the
// first, on the left, in slot 11. Sumo is the first mode of Duels: slot 10 of its menu.
const DUELS_SLOT = 11
const SUMO_SLOT = 10
const POINTS_TO_WIN = 3
// run-lobby-test.sh places the NPC of the Duels category (point attackdefenseversionrbw), a stats
// NPC (statsjoueur1), a wall of leaderboards (leaderboardstats), the main leaderboard
// (leaderboardprincipal) and the kills leaderboard (leaderboardplayer) around the spawn.
const DUELS_NPC = { x: 3.5, y: 65, z: 0.5 }
const STATS_NPC = { x: -3.5, y: 65, z: 0.5 }
// The kit editor: the book in the second hotbar slot, the Save button of its chest of 27 slots,
// whose window shows the player's hotbar from slot 54 (27 of the chest, 27 of the inventory).
const KIT_EDITOR_SLOT = 1
const KIT_SAVE = 11
/** The Hypixel buttons in the last row of the book: import, then the imported shop. */
const HYPIXEL_IMPORT = 3
const HYPIXEL_SHOP = 5
const EDITOR_HOTBAR = 54
const STICK_SLOT = 3

let finishing = false

function abort (error) {
  console.error(`[lobby-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[lobby-bots] ${text}`)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`)
    await sleep(50)
  }
}

/** The plain text of a chat component, given as JSON or as text, without colour codes. */
function plainText (text) {
  const flatten = (part) => {
    if (typeof part === 'string') return part
    if (part == null || typeof part !== 'object') return ''
    if (Array.isArray(part)) return part.map(flatten).join('')
    return (part.text ?? '') + (part.extra ?? []).map(flatten).join('')
  }
  let value = text
  try {
    value = flatten(JSON.parse(text))
  } catch {}
  return String(value).replace(/§./g, '')
}

/** Every chat line a bot received, so that a test can wait for a line sent a moment ago. */
const chats = new Map()

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    chats.set(username, [])
    bot.velocities = []
    bot.titles = []
    bot.titleTimes = []
    bot.on('title', (text, type) => { if (type === 'title') bot.titles.push(plainText(text)) })
    bot.on('title_times', (fadeIn, stay, fadeOut) => bot.titleTimes.push([fadeIn, stay, fadeOut]))
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.on('messagestr', (message) => {
      chats.get(username).push(message)
      log(`${username} < ${message}`)
    })
    bot._client.on('entity_velocity', (packet) => {
      if (bot.entity != null && packet.entityId === bot.entity.id) {
        bot.velocities.push({ time: Date.now(), velocity: packet.velocity })
      }
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

/** Waits for a chat line matching the pattern, received after the line number `from`. */
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

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 10000,
    `${bot.username} in the lobby state (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
  const p = bot.entity.position
  const distance = Math.hypot(p.x - LOBBY_SPAWN.x, p.y - LOBBY_SPAWN.y, p.z - LOBBY_SPAWN.z)
  if (distance > 1.5) throw new Error(`${bot.username} is not at the lobby spawn (${what}): ${p}`)
}

function playerEntity (bot, other) {
  return Object.values(bot.entities).find((e) => e.type === 'player' && e.username === other.username)
}

async function openSelectorAndJoinSumo (bot) {
  bot.setQuickBarSlot(0)
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  await sleep(250)
  bot.activateItem()
  const window = await Promise.race([opened, sleep(5000).then(() => null)])
  if (window == null) throw new Error(`${bot.username}: the selector did not open on a right click`)
  const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
  if (!title.includes('Game modes')) throw new Error(`${bot.username}: unexpected window title ${title}`)
  if (window.slots[DUELS_SLOT]?.name !== 'iron_sword') {
    throw new Error(`${bot.username}: slot ${DUELS_SLOT} holds ${window.slots[DUELS_SLOT]?.name}, not the Duels category`)
  }
  const duelsOpened = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.clickWindow(DUELS_SLOT, 0, 0).catch(() => {})
  const duels = await Promise.race([duelsOpened, sleep(5000).then(() => null)])
  if (duels == null) throw new Error(`${bot.username}: the Duels category did not open`)
  const duelsTitle = typeof duels.title === 'string' ? duels.title : JSON.stringify(duels.title)
  if (!duelsTitle.includes('Duels')) throw new Error(`${bot.username}: unexpected category title ${duelsTitle}`)
  const icon = duels.slots[SUMO_SLOT]
  if (icon?.name !== 'slime_ball') throw new Error(`${bot.username}: slot ${SUMO_SLOT} holds ${icon?.name}, not the sumo duel`)
  const lore = JSON.stringify(icon.nbt ?? null)
  if (!lore.includes('§eDaily Streak: 0') || lore.includes(' 1. ')) {
    throw new Error(`${bot.username}: the sumo should show Daily Streak: 0 and nobody in its top: ${lore}`)
  }
  const from = mark(bot)
  // The server cancels the click (the menu cannot be emptied): do not wait for its acceptance.
  bot.clickWindow(SUMO_SLOT, 0, 0).catch(() => {})
  await chatLine(bot, /You joined the queue for .*Sumo duel/, 5000, from)
  log(`${bot.username} joined the sumo queue from the selector`)
}

function windowTitle (window) {
  return typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
}

/** The book of the hotbar, then the sumo in its list of modes: the editor of the sumo kit. */
async function openSumoKit (bot) {
  bot.setQuickBarSlot(KIT_EDITOR_SLOT)
  await sleep(250)
  const listOpened = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.activateItem()
  const list = await Promise.race([listOpened, sleep(5000).then(() => null)])
  if (list == null) throw new Error(`${bot.username}: the kit editor did not open on a right click on the book`)
  if (!windowTitle(list).includes('Kit editor')) throw new Error(`${bot.username}: the book opened ${windowTitle(list)}`)
  const sumo = list.slots.findIndex((item, slot) => slot < list.inventoryStart &&
    JSON.stringify(item?.nbt ?? null).includes('Sumo duel'))
  if (sumo < 0) throw new Error(`${bot.username}: no sumo in the kit editor: ${JSON.stringify(list.slots.map((item) => item?.name))}`)
  const editorOpened = new Promise((resolve) => bot.once('windowOpen', resolve))
  // The server cancels the click (the list cannot be emptied): do not wait for its acceptance.
  bot.clickWindow(sumo, 0, 0).catch(() => {})
  const editor = await Promise.race([editorOpened, sleep(5000).then(() => null)])
  if (editor == null) throw new Error(`${bot.username}: a click on the sumo did not open its kit`)
  if (!windowTitle(editor).includes('Kit: Sumo duel')) throw new Error(`${bot.username}: the sumo opened ${windowTitle(editor)}`)
  return editor
}

/** The stick of the sumo kit goes from the first hotbar slot to the fourth, and stays there. */
async function arrangeSumoKit (bot) {
  const editor = await openSumoKit(bot)
  await waitFor(() => editor.slots[EDITOR_HOTBAR]?.name === 'stick', 5000,
    `${bot.username} to get the stick of the sumo kit in the first hotbar slot: ${JSON.stringify(editor.slots.slice(EDITOR_HOTBAR).map((item) => item?.name))}`)
  await bot.clickWindow(EDITOR_HOTBAR, 0, 0)
  await bot.clickWindow(EDITOR_HOTBAR + STICK_SLOT, 0, 0)
  let from = mark(bot)
  bot.clickWindow(KIT_SAVE, 0, 0).catch(() => {})
  await chatLine(bot, /Kit saved for Sumo duel/, 5000, from)
  await waitFor(() => bot.currentWindow == null && hotbarItem(bot, 0)?.name === 'compass' &&
    hotbarItem(bot, KIT_EDITOR_SLOT)?.name === 'book', 5000, `${bot.username} to get the lobby hotbar back after saving`)
  log(`${bot.username} moved the stick of the sumo kit to hotbar slot ${STICK_SLOT + 1} and saved`)

  const again = await openSumoKit(bot)
  await waitFor(() => again.slots[EDITOR_HOTBAR + STICK_SLOT]?.name === 'stick' && again.slots[EDITOR_HOTBAR] == null, 5000,
    `${bot.username} to find the stick where it put it: ${JSON.stringify(again.slots.slice(EDITOR_HOTBAR).map((item) => item?.name))}`)
  from = mark(bot)
  // Closing the editor saves too.
  bot.closeWindow(again)
  await chatLine(bot, /Kit saved for Sumo duel/, 5000, from)
  await expectInLobby(bot, 'after the kit editor')
  bot.setQuickBarSlot(0)
  log(`${bot.username} finds the stick in slot ${STICK_SLOT + 1} when it opens the editor again`)
}

/**
 * The test server has no Hypixel API key (hypixel.cle-api): the emerald of the book and
 * /hypixel import say so, and /hypixel shop that nothing was imported.
 */
async function checkHypixelImport (bot) {
  bot.setQuickBarSlot(KIT_EDITOR_SLOT)
  await sleep(250)
  const listOpened = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.activateItem()
  const list = await Promise.race([listOpened, sleep(5000).then(() => null)])
  if (list == null) throw new Error(`${bot.username}: the kit editor did not open on a right click on the book`)
  const lastRow = list.inventoryStart - 9
  const buttons = [list.slots[lastRow + HYPIXEL_IMPORT]?.name, list.slots[lastRow + HYPIXEL_SHOP]?.name]
  if (buttons[0] !== 'emerald' || buttons[1] !== 'chest') {
    throw new Error(`${bot.username}: no Hypixel buttons in the last row of the book: ${JSON.stringify(buttons)}`)
  }
  let from = mark(bot)
  bot.clickWindow(lastRow + HYPIXEL_IMPORT, 0, 0).catch(() => {})
  await chatLine(bot, /The Hypixel import is not set up on this server yet/, 5000, from)
  await waitFor(() => bot.currentWindow == null, 5000, `${bot.username} to see the book close after a click on the emerald`)
  bot.setQuickBarSlot(0)
  from = mark(bot)
  bot.chat('/hypixel import Notch')
  await chatLine(bot, /The Hypixel import is not set up on this server yet/, 5000, from)
  from = mark(bot)
  bot.chat('/hypixel shop')
  await chatLine(bot, /You have not imported a Hypixel shop yet/, 5000, from)
  log(`${bot.username}: the emerald of the book and /hypixel import say the import needs an API key, /hypixel shop that nothing was imported`)
}

/** The NPC body standing on a point: a player whose name starts with a colour code. */
function npcAt (bot, where) {
  return Object.values(bot.entities).find((e) => e.type === 'player' && e.username?.startsWith('§8pnj') &&
    Math.hypot(e.position.x - where.x, e.position.y - where.y, e.position.z - where.z) < 0.5)
}

/** The Duels NPC: a Steve with the category's sword, out of the tab list, name tag hidden; a click opens Duels. */
async function checkDuelsNpc (bot) {
  await waitFor(() => npcAt(bot, DUELS_NPC) != null, 10000,
    `the Duels NPC at ${JSON.stringify(DUELS_NPC)}; players: ${JSON.stringify(Object.values(bot.entities)
      .filter((e) => e.type === 'player').map((e) => [e.username, e.position]))}`)
  const npc = npcAt(bot, DUELS_NPC)
  await waitFor(() => npc.heldItem?.name === 'iron_sword', 5000, `the Duels NPC holding a sword (holds ${npc.heldItem?.name})`)
  const team = bot.teamMap?.[npc.username]
  if (team?.team !== 'pnj' || team.nameTagVisibility !== 'never') {
    throw new Error(`the name tag of ${JSON.stringify(npc.username)} is not hidden: team ${team?.team}, ${team?.nameTagVisibility}`)
  }
  await waitFor(() => bot.players[npc.username] == null, 5000, `${JSON.stringify(npc.username)} to leave the tab list`)
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.activateEntity(npc)
  const window = await Promise.race([opened, sleep(5000).then(() => null)])
  if (window == null) throw new Error('a click on the Duels NPC did not open a menu')
  const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
  if (!title.includes('Duels')) throw new Error(`the Duels NPC opened ${title}`)
  bot.closeWindow(window)
  log('the Duels NPC is a Steve holding a sword, out of the tab list, and its click opens Duels')
}

/** Every line of floating text this bot sees: the names of the armor stands, without colours. */
function floatingTexts (bot) {
  return Object.values(bot.entities)
    .map((e) => e.metadata?.[2])
    .filter((name) => typeof name === 'string' && name !== '')
    .map((name) => name.replace(/§./g, ''))
}

async function expectFloating (bot, text, what) {
  await waitFor(() => floatingTexts(bot).includes(text), 5000,
    `${bot.username} to read "${text}" (${what}); floating texts: ${JSON.stringify(floatingTexts(bot))}`)
}

/** The armor stand showing this line of floating text, without colours. */
function floatingEntity (bot, text) {
  return Object.values(bot.entities).find((e) => typeof e.metadata?.[2] === 'string' &&
    e.metadata[2].replace(/§./g, '') === text)
}

/** The XP bar: the Network level, and how far towards the next one. */
async function expectLevel (bot, level, progress) {
  await waitFor(() => bot.experience?.level === level && Math.abs((bot.experience?.progress ?? -1) - progress) < 0.02,
    5000, `${bot.username} to see level ${level} at ${progress} in the XP bar: ${JSON.stringify(bot.experience)}`)
}

/**
 * The main leaderboard (one board, three stats): a right click on it goes from the Elo to the
 * kills, a left click changes their period, a right click goes on to the Network level.
 */
async function checkMainLeaderboard (bot) {
  await expectFloating(bot, 'Ranked Elo', 'the main leaderboard')
  const title = floatingEntity(bot, 'Ranked Elo')
  const text = () => (title.metadata?.[2] ?? '').replace(/§./g, '')
  bot.activateEntity(title)
  await waitFor(() => text() === 'Kills', 5000, `a right click to turn the main leaderboard to the kills: ${text()}`)
  await expectFloating(bot, '1. RagoUn - 3', 'the kills of the day on the main leaderboard')
  await sleep(500)
  bot.attack(title)
  await expectFloating(bot, 'All modes · Weekly', 'a left click: the kills of the week')
  await sleep(500)
  bot.activateEntity(title)
  await waitFor(() => text() === 'Network level', 5000, `a right click to turn the main leaderboard to the level: ${text()}`)
  log('the main leaderboard goes from the Elo to the kills (left click: the week) and to the Network level')
}

/** The lines of the sidebar, top to bottom, without colours. */
function sidebarLines (bot) {
  return (bot.scoreboard?.sidebar?.items ?? []).map((item) => String(item.name).replace(/§./g, ''))
}

/** In a 1v1, the sidebar shows the opponent, the player's ping and theirs. */
async function expectDuelSidebar (bot, opponent) {
  await waitFor(() => {
    const lines = sidebarLines(bot)
    return lines.includes(`Opponent: ${opponent}`) && lines.some((line) => /^Your ping: \d+ ms$/.test(line)) &&
      lines.some((line) => /^Their ping: \d+ ms$/.test(line))
  }, 5000, `${bot.username} to see ${opponent} and both pings in the sidebar: ${JSON.stringify(sidebarLines(bot))}`)
}

/** The Duels NPC opens Duels: hovering the sumo shows the top win streaks of the day, as on Minemen. */
async function checkTopStreak (bot) {
  const npc = npcAt(bot, DUELS_NPC)
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.activateEntity(npc)
  const window = await Promise.race([opened, sleep(5000).then(() => null)])
  if (window == null) throw new Error('a click on the Duels NPC did not open a menu')
  const lore = JSON.stringify(window.slots[SUMO_SLOT]?.nbt ?? null)
  if (!lore.includes('§eDaily Streak: 0') || !lore.includes('§e 1. §fRagoUn§e - 1')) {
    throw new Error(`${bot.username}: the sumo of the Duels NPC does not show RagoUn's streak: ${lore}`)
  }
  bot.closeWindow(window)
  log('the sumo shows Daily Streak: 0 for RagoDeux and RagoUn first of the day with 1')
}

/**
 * After the 3 - 0: each bot reads its own profile above the stats NPC and the same leaderboard,
 * and a click on the stats NPC opens the stats menu.
 */
async function checkStats (one, two) {
  await expectFloating(one, 'Wins: 1', 'the profile of the winner')
  await expectFloating(one, 'Losses: 0', 'the profile of the winner')
  await expectFloating(two, 'Wins: 0', 'the profile of the loser')
  await expectFloating(two, 'Losses: 1', 'the profile of the loser')
  await expectFloating(one, '1. RagoUn - 1', 'the wins of the day')
  await expectFloating(two, '1. RagoUn - 1', 'the wins of the day')
  await expectFloating(one, 'You: #1 - 1', 'the place of the winner')
  await expectFloating(two, 'You: not ranked yet', 'the place of the loser')
  const npc = npcAt(one, STATS_NPC)
  if (npc == null) throw new Error('RagoUn does not see the stats NPC')
  const opened = new Promise((resolve) => one.once('windowOpen', resolve))
  one.activateEntity(npc)
  const window = await Promise.race([opened, sleep(5000).then(() => null)])
  if (window == null) throw new Error('a click on the stats NPC did not open a menu')
  const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
  if (!title.includes('Your stats')) throw new Error(`the stats NPC opened ${title}`)
  const sumo = window.slots.find((item) => item?.name === 'slime_ball')
  const lore = JSON.stringify(sumo?.nbt ?? null)
  if (!lore.includes('Wins: §a1')) throw new Error(`no sumo win in the stats menu: ${lore}`)
  if (!lore.includes('Kills: §a3')) throw new Error(`no sumo kills in the stats menu: ${lore}`)
  one.closeWindow(window)
  log('stats: each bot reads its own profile, the leaderboard has RagoUn first, the menu opens')
  // Three points: three kills for RagoUn, three deaths for RagoDeux.
  await expectFloating(one, 'Kills: 3', 'the kills of the winner')
  await expectFloating(two, 'Kills: 0', 'the kills of the loser')
  await expectFloating(two, '1. RagoUn - 3', 'the kills of the day on leaderboardplayer')
  // leaderboardplayer: the best win streaks on the left of the kills, the Network level on the right.
  await waitFor(() => floatingTexts(two).filter((text) => text === 'Best win streak').length === 2 &&
    floatingTexts(two).includes('Network level'), 5000,
  `the streaks and the level next to the kills on leaderboardplayer: ${JSON.stringify(floatingTexts(two))}`)
  // Network XP: 50 for the win and 5 a kill (65 of the 100 of level 1), 15 for the game lost.
  await expectLevel(one, 1, 0.65)
  await expectLevel(two, 1, 0.15)
  await chatLine(one, /\+50 Network XP \(win\)/, 5000)
  await chatLine(two, /\+15 Network XP \(game played\)/, 5000)
  log('Network level: RagoUn 65 XP, RagoDeux 15 XP, in the XP bar; three kills on the boards')
  await checkMainLeaderboard(one)
  await checkTopStreak(two)
}

// The demo map of RagoBedwars (bw-demo): the red island in the west, the blue one in the east,
// mirrored. Each team's spawn is at x = 32.5 on its side (33.5 for blue), its generator one block
// behind, its bed (foot, then head) at x = 27 and 28, the item shop villager at z = 4.5.
const BEDWARS_FLOOR = 64

/** -1 on the red island (west), +1 on the blue one. */
function islandSide (bot) {
  return bot.entity.position.x < 0 ? -1 : 1
}

function countOf (bot, name) {
  return bot.inventory.items().filter((item) => item.name === name).reduce((sum, item) => sum + item.count, 0)
}

/** The start of a Bed Wars game: on the island, the wooden sword, the leather armour, the teams in the sidebar. */
async function expectBedwarsStart (bot, from, mode = 'Ranked Bedwars 1v1') {
  const line = await chatLine(bot, new RegExp(`${mode} on bw-demo: you are in the (Red|Blue) team\\. Protect your bed`), 15000, from)
  await waitFor(() => bot.game.gameMode === 'survival' && countOf(bot, 'wooden_sword') === 1 &&
    bot.inventory.slots[5]?.name === 'leather_helmet', 5000,
  `${bot.username} to start with a wooden sword and leather armour: ${bot.game.gameMode}, ${JSON.stringify(bot.inventory.items().map((item) => item.name))}, helmet ${bot.inventory.slots[5]?.name}`)
  const team = line.includes('Red team') ? 'Red' : 'Blue'
  if ((team === 'Red') !== (islandSide(bot) < 0)) throw new Error(`${bot.username} is in the ${team} team at ${bot.entity.position}`)
  await waitFor(() => {
    const lines = sidebarLines(bot)
    return lines.some((text) => text === `${team[0]} ${team}: ✔ YOU`) && lines.includes('Kills: 0') &&
      lines.includes('Final Kills: 0') && lines.includes('Beds Broken: 0')
  }, 5000, `${bot.username} to see the teams and its kills in the sidebar: ${JSON.stringify(sidebarLines(bot))}`)
  return team
}

/** The item shop villager of the bot's island: under its name, ITEM SHOP, which floats above it as on Hypixel. */
function shopVillager (bot) {
  const label = Object.values(bot.entities).find((e) => String(e.metadata?.[2] ?? '').includes('ITEM SHOP') &&
    Math.sign(e.position.x) === islandSide(bot))
  if (label == null) return null
  return Object.values(bot.entities).find((e) => /villager/i.test(e.name ?? '') &&
    Math.abs(e.position.x - label.position.x) < 0.6 && Math.abs(e.position.z - label.position.z) < 0.6 &&
    e.position.y < label.position.y)
}

/** RagoUn: the shop opens on a click, wool from the Quick Buy, placed by the bed and broken again. */
async function shopAndBuild (bot) {
  const V = bot.entity.position.constructor
  const side = islandSide(bot)
  await waitFor(() => countOf(bot, 'iron_ingot') >= 4, 15000,
    `${bot.username} to pick up 4 iron on its spawn (has ${countOf(bot, 'iron_ingot')})`)
  await waitFor(() => shopVillager(bot) != null, 5000, `${bot.username} to see its item shop villager`)
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.activateEntity(shopVillager(bot))
  const shop = await Promise.race([opened, sleep(5000).then(() => null)])
  if (shop == null) throw new Error(`${bot.username}: a click on the item shop villager did not open the shop`)
  if (!windowTitle(shop).includes('Item Shop')) throw new Error(`${bot.username}: the villager opened ${windowTitle(shop)}`)
  // The default Quick Buy (nothing imported from Hypixel): wool in the first slot of the first row.
  if (shop.slots[19]?.name !== 'wool') {
    throw new Error(`${bot.username}: the Quick Buy starts with ${shop.slots[19]?.name}, not wool`)
  }
  let from = mark(bot)
  bot.clickWindow(19, 0, 0).catch(() => {})
  await chatLine(bot, /You purchased Wool/, 5000, from)
  bot.closeWindow(shop)
  await waitFor(() => countOf(bot, 'wool') === 16, 5000, `${bot.username} to get 16 wool (has ${countOf(bot, 'wool')})`)
  log(`${bot.username} picked up iron on its spawn and bought wool in the item shop`)

  await bot.equip(bot.inventory.items().find((item) => item.name === 'wool'), 'hand')
  const up = new V(0, 1, 0)
  // Two blocks from the spawn: protected.
  from = mark(bot)
  await bot.placeBlock(bot.blockAt(new V(side * 31, BEDWARS_FLOOR, 0)), up).catch(() => {})
  await chatLine(bot, /You can't place blocks here!/, 5000, from)
  // Next to the head of the bed: a defence.
  const defence = new V(side * 29, BEDWARS_FLOOR + 1, 0)
  await bot.placeBlock(bot.blockAt(new V(side * 29, BEDWARS_FLOOR, 0)), up)
  await waitFor(() => bot.blockAt(defence)?.name === 'wool', 5000, `${bot.username} to see its wool by the bed`)
  await bot.dig(bot.blockAt(defence))
  await waitFor(() => bot.blockAt(defence)?.name === 'air', 5000, `${bot.username} to break its wool again`)
  log(`${bot.username}: no block near the spawn; wool placed by the bed and broken again`)
  // Its own bed cannot be broken.
  from = mark(bot)
  bot.dig(bot.blockAt(new V(side * 28, BEDWARS_FLOOR + 1, 0))).catch(() => {})
  await chatLine(bot, /You can't destroy your own bed!/, 5000, from)
  await waitFor(() => bot.blockAt(new V(side * 28, BEDWARS_FLOOR + 1, 0))?.name === 'bed', 5000, `${bot.username} to see its bed stand`)
  log(`${bot.username} cannot break its own bed`)
  await fireballJump(bot)
}

/**
 * A fireball thrown at its own feet from the spawn: the blast lifts the bot and takes about a
 * heart, not the 20 hearts of a vanilla blast at point blank (the fall back costs a few more).
 * run-lobby-test.sh makes the iron generators four times faster, so that the 40 iron come in time.
 */
async function fireballJump (bot) {
  await waitFor(() => countOf(bot, 'iron_ingot') >= 40, 30000,
    `${bot.username} to pick up 40 iron for a fireball (has ${countOf(bot, 'iron_ingot')})`)
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.activateEntity(shopVillager(bot))
  const shop = await Promise.race([opened, sleep(5000).then(() => null)])
  if (shop == null) throw new Error(`${bot.username}: the item shop did not open again`)
  // The default Quick Buy: the fireball is the third item of the third row (slot 39).
  if (shop.slots[39]?.name !== 'fire_charge') {
    throw new Error(`${bot.username}: slot 39 of the Quick Buy holds ${shop.slots[39]?.name}, not the fireball`)
  }
  let from = mark(bot)
  bot.clickWindow(39, 0, 0).catch(() => {})
  await chatLine(bot, /You purchased Fireball/, 5000, from)
  bot.closeWindow(shop)
  await waitFor(() => countOf(bot, 'fire_charge') === 1, 5000, `${bot.username} to get its fireball`)
  await bot.equip(bot.inventory.items().find((item) => item.name === 'fire_charge'), 'hand')
  const ground = bot.blockAt(bot.entity.position.offset(0, -0.5, 0))
  if (ground == null || ground.name === 'air') throw new Error(`${bot.username} is not standing on a block`)
  await bot.lookAt(ground.position.offset(0.5, 1, 0.5), true)
  await sleep(200)
  const floor = bot.entity.position.y
  const before = bot.health
  const healths = []
  let highest = floor
  const onHealth = () => healths.push(bot.health)
  const onMove = () => { highest = Math.max(highest, bot.entity.position.y) }
  bot.on('health', onHealth)
  bot.on('move', onMove)
  from = mark(bot)
  // A right click on the floor, as a player does: the use packet in the air that activateItem sends
  // is dropped by the server when the player looks at a block just after placing one (the wool).
  bot.activateBlock(ground).catch(() => {})
  try {
    await waitFor(() => healths.some((health) => health < before), 5000,
      `${bot.username} to be hurt by the blast of its fireball (health ${bot.health})`)
    // the bot goes up and lands again: the fall can hurt too
    await sleep(3000)
  } finally {
    bot.removeListener('health', onHealth)
    bot.removeListener('move', onMove)
  }
  const blast = before - healths.find((health) => health < before)
  if (blast > 3) throw new Error(`${bot.username} lost ${blast} health to the blast of its own fireball`)
  const died = chats.get(bot.username).slice(from).some((line) => /RagoUn (died|fell into the void)/.test(line))
  // A jump straight up climbs some ten blocks: the fall back costs about three hearts more.
  if (died || bot.game.gameMode !== 'survival' || bot.health < 8) {
    throw new Error(`${bot.username} did not survive its fireball jump: ${bot.game.gameMode}, health ${bot.health}, ` +
      `${blast} lost to the blast, up ${(highest - floor).toFixed(1)} blocks`)
  }
  if (highest < floor + 1.5) throw new Error(`the fireball did not lift ${bot.username}: up to ${highest}, from ${floor}`)
  if (countOf(bot, 'fire_charge') !== 0) throw new Error(`${bot.username} still holds its fireball`)
  log(`${bot.username} jumped on its fireball: up ${(highest - floor).toFixed(1)} blocks, ${blast.toFixed(1)} health lost to the blast, ${bot.health.toFixed(1)} left`)
}

/** RagoDeux walks off the back of its island: the void kills it, and it respawns since its bed stands. */
async function voidAndRespawn (bot, other) {
  const V = bot.entity.position.constructor
  const fromBot = mark(bot)
  const fromOther = mark(other)
  await bot.lookAt(new V(0, BEDWARS_FLOOR + 1.6, 0.5), true)
  bot.setControlState('back', true)
  await chatLine(bot, /RagoDeux fell into the void\./, 10000, fromBot)
  stop(bot)
  await chatLine(other, /RagoDeux fell into the void\./, 5000, fromOther)
  await waitFor(() => bot.game.gameMode === 'spectator', 5000, `${bot.username} to watch as a spectator while it waits`)
  await chatLine(bot, /You have respawned!/, 10000, fromBot)
  await waitFor(() => bot.game.gameMode === 'survival' && countOf(bot, 'wooden_sword') === 1 &&
    Math.abs(bot.entity.position.y - (BEDWARS_FLOOR + 1)) < 0.5, 5000,
  `${bot.username} back on its island with its sword: ${bot.game.gameMode} at ${bot.entity.position}`)
  log(`${bot.username} fell into the void and respawned`)
}

/** Ranked Bedwars 1v1 on the demo map, ending with RagoDeux leaving: RagoUn wins. */
async function playBedwars (one, two) {
  const fromOne = mark(one)
  const fromTwo = mark(two)
  one.chat('/queue ranked-1v1')
  two.chat('/queue ranked-1v1')
  await expectBedwarsStart(one, fromOne)
  await expectBedwarsStart(two, fromTwo)
  log('Bed Wars: both on their islands, with a wooden sword, leather armour and the teams in the sidebar')
  await chatLine(one, /Fight!/, 10000, fromOne)
  await shopAndBuild(one)
  await voidAndRespawn(two, one)
  const fromEnd = mark(one)
  two.chat('/spawn')
  await chatLine(one, /RagoDeux left the game\./, 5000, fromEnd)
  await chatLine(one, /Winner: (Red|Blue) - RagoUn/, 5000, fromEnd)
  await expectInLobby(one, 'after the Bed Wars game')
  await expectInLobby(two, 'after /spawn in the Bed Wars game')
  log('Bed Wars: RagoDeux left, RagoUn won, both are back in the lobby')
}

/** First Rush: a real 1v1 on two neighbouring islands, on the map chosen in the queue. */
async function playFirstRush (one, two) {
  let fromOne = mark(one)
  one.chat('/queue first-rush Nowhere')
  await chatLine(one, /This mode has no choice of map\./, 5000, fromOne)
  fromOne = mark(one)
  const fromTwo = mark(two)
  one.chat('/queue first-rush bw-demo')
  two.chat('/queue first-rush random')
  await chatLine(one, /You joined the queue for First rush solo on bw-demo/, 5000, fromOne)
  const teamOne = await expectBedwarsStart(one, fromOne, 'First rush solo')
  const teamTwo = await expectBedwarsStart(two, fromTwo, 'First rush solo')
  // the demo map has two islands only, about 60 blocks apart (out of sight): they are the neighbours
  if (teamOne === teamTwo) throw new Error(`both bots are in the ${teamOne} team of the First Rush`)
  await chatLine(one, /Fight!/, 10000, fromOne)
  // no clock of a rush: the events of a real game
  await waitFor(() => sidebarLines(one).some((text) => /^Diamond II in \d+:\d\d$/.test(text)), 5000,
    `the next event in the sidebar of the First Rush: ${JSON.stringify(sidebarLines(one))}`)
  log(`First Rush: RagoUn (${teamOne}) against RagoDeux (${teamTwo}) on bw-demo, a real game`)
  const fromEnd = mark(one)
  two.chat('/spawn')
  await chatLine(one, /RagoDeux left the game\./, 5000, fromEnd)
  await chatLine(one, /Winner: (Red|Blue) - RagoUn/, 5000, fromEnd)
  await expectInLobby(one, 'after the First Rush')
  await expectInLobby(two, 'after /spawn in the First Rush')
  log('First Rush: RagoDeux left, RagoUn won, both are back in the lobby')
}

async function playParty (one, two) {
  let fromOne = mark(one)
  let fromTwo = mark(two)
  one.chat('/p RagoDeux')
  await chatLine(one, /invited .*RagoDeux to the party! They have 60 seconds to accept\./, 5000, fromOne)
  await chatLine(two, /RagoUn has invited you to join their party!/, 5000, fromTwo)
  await chatLine(two, /Click here to join!/, 5000, fromTwo)
  two.chat('/party accept RagoUn')
  await chatLine(two, /You have joined .*RagoUn's party!/, 5000, fromTwo)
  await chatLine(one, /RagoDeux joined the party\./, 5000, fromOne)
  one.chat('/party list')
  await chatLine(one, /Party Members \(2\)/, 5000, fromOne)
  two.chat('/pc gg')
  await chatLine(one, /Party > .*RagoDeux: gg/, 5000, fromOne)
  two.chat('/queue sumo')
  await chatLine(two, /Only the party leader can join a queue\./, 5000, fromTwo)
  log('party: /p invite, the clickable invite, /party accept, /party list, /pc; only the leader queues')

  // two in a 1v1 mode: a private game between them, counted nowhere
  fromOne = mark(one)
  fromTwo = mark(two)
  one.chat('/queue sumo')
  await chatLine(one, /Private game of your party in .*: RagoUn vs RagoDeux\. It counts in no stats\./, 5000, fromOne)
  await chatLine(two, /Private game of your party in .*: RagoUn vs RagoDeux\./, 5000, fromTwo)
  await chatLine(one, /Sumo duel against RagoDeux/, 10000, fromOne)
  await chatLine(two, /Sumo duel against RagoUn/, 10000, fromTwo)
  two.chat('/spawn')
  await chatLine(one, /Victory!.*RagoDeux gave up/, 5000, fromOne)
  await expectInLobby(one, 'after the private game')
  await expectInLobby(two, 'after /spawn in the private game')
  await sleep(1000)
  const xp = chats.get(one.username).slice(fromOne).find((line) => /Network XP/.test(line))
  if (xp != null) throw new Error(`the private game of the party was counted: ${xp}`)
  log('party: two in a 1v1 mode play a private game against each other, without stats')

  // two in a 2v2 mode: the queue, in one team
  fromOne = mark(one)
  fromTwo = mark(two)
  one.chat('/queue ranked-2v2')
  await chatLine(one, /Your party joined the queue for .*You will play in the same team\./, 5000, fromOne)
  await chatLine(two, /Your party joined the queue for .*You will play in the same team\./, 5000, fromTwo)
  two.chat('/queue leave')
  await chatLine(two, /You left the queue: your party left the queue for/, 5000, fromTwo)
  await chatLine(one, /RagoDeux left the queue: your party left the queue for/, 5000, fromOne)
  one.chat('/party disband')
  await chatLine(two, /RagoUn has disbanded the party!/, 5000, fromTwo)
  log('party: in a 2v2 mode the party queues together, leaves together, then is disbanded')
}

async function fallOff (bot) {
  bot.setControlState('back', true)
  bot.setControlState('jump', true)
}

function stop (bot) {
  bot.clearControlStates()
}

async function main () {
  const one = await join('RagoUn')
  const two = await join('RagoDeux')
  log('both bots joined')

  // 1. The lobby.
  await expectInLobby(one, 'on joining')
  await expectInLobby(two, 'on joining')
  if (hotbarItem(one, 8)?.name !== 'dye') throw new Error(`no visibility item in slot 8: ${hotbarItem(one, 8)?.name}`)
  if (hotbarItem(one, KIT_EDITOR_SLOT)?.name !== 'book') {
    throw new Error(`no kit editor in slot ${KIT_EDITOR_SLOT}: ${hotbarItem(one, KIT_EDITOR_SLOT)?.name}`)
  }
  await waitFor(() => (one.scoreboard?.sidebar?.title ?? '').includes('RAGO PRACTICE'), 5000,
    `the lobby sidebar; sidebar: ${JSON.stringify(one.scoreboard?.sidebar?.title)}`)
  log('lobby: adventure mode, selector, visibility item and sidebar are there')
  await checkDuelsNpc(one)
  await arrangeSumoKit(one)
  await checkHypixelImport(two)

  // 2. The queue.
  await openSelectorAndJoinSumo(one)
  let fromOne = mark(one)
  let fromTwo = mark(two)
  two.chat('/queue sumo')

  // 3. The duel.
  await chatLine(one, /Sumo duel against RagoDeux/, 10000, fromOne)
  await chatLine(two, /Sumo duel against RagoUn/, 10000, fromTwo)
  await waitFor(() => one.game.gameMode === 'survival' && two.game.gameMode === 'survival', 5000, 'survival mode in the duel')
  if (hotbarItem(one, 0) != null) throw new Error('the lobby items are still there in the duel')
  await waitFor(() => hotbarItem(one, STICK_SLOT)?.name === 'stick' && hotbarItem(two, 0)?.name === 'stick', 5000,
    `the sumo kit as each bot arranged it: RagoUn slot ${STICK_SLOT} ${hotbarItem(one, STICK_SLOT)?.name}, RagoDeux slot 0 ${hotbarItem(two, 0)?.name}`)
  log('the duel started, with the stick where each bot put it')
  await expectDuelSidebar(one, 'RagoDeux')
  await expectDuelSidebar(two, 'RagoUn')
  log(`sidebar in the duel: ${JSON.stringify(sidebarLines(one))}`)

  for (let round = 1; round <= POINTS_TO_WIN; round++) {
    await chatLine(two, /Fight!/, 10000, fromTwo)
    fromOne = mark(one)
    fromTwo = mark(two)
    if (round === 1) {
      // The countdown is also shown as a big title: 3, 2, 1, Fight!, each one at once.
      await waitFor(() => two.titles.includes('Fight!'), 2000, `the countdown titles; titles: ${JSON.stringify(two.titles)}`)
      const countdown = two.titles.slice(-4)
      if (JSON.stringify(countdown) !== JSON.stringify(['3', '2', '1', 'Fight!'])) {
        throw new Error(`unexpected countdown titles: ${JSON.stringify(two.titles)}`)
      }
      if (!two.titleTimes.some(([fadeIn]) => fadeIn === 0)) {
        throw new Error(`the countdown titles do not show at once; timings: ${JSON.stringify(two.titleTimes)}`)
      }
      log('countdown: titles 3, 2, 1, Fight!')
      // RagoUn walks to RagoDeux and hits it once: knockback, but no damage.
      const target = playerEntity(one, two)
      if (target == null) throw new Error('RagoUn does not see RagoDeux in the duel')
      await one.lookAt(target.position.offset(0, 1.6, 0), true)
      one.setControlState('forward', true)
      await waitFor(() => one.entity.position.distanceTo(target.position) < 2.8, 5000, 'RagoUn close to RagoDeux')
      stop(one)
      const before = two.velocities.length
      await one.lookAt(target.position.offset(0, 1.6, 0), true)
      one.attack(target)
      await waitFor(() => two.velocities.length > before, 2000, 'a knockback packet for RagoDeux after the hit')
      await sleep(500)
      if (two.health < 20) throw new Error(`RagoDeux took damage in the sumo duel (health ${two.health})`)
      log('hit: RagoDeux got knockback and no damage')
    }
    await fallOff(two)
    await chatLine(two, /Point for RagoUn/, 10000, fromTwo)
    stop(two)
    await chatLine(one, /Your point/, 5000, fromOne)
    log(`round ${round}: RagoDeux fell, point for RagoUn`)
  }
  await chatLine(one, /Victory!.*3 - 0/, 5000, fromOne)
  await chatLine(two, /Defeat.*0 - 3/, 5000, fromTwo)
  log('RagoUn won 3 - 0')

  // 4. Back to the lobby.
  await expectInLobby(one, 'after the duel')
  await expectInLobby(two, 'after the duel')
  log('both are back in the lobby')
  await checkStats(one, two)

  // 5. A forfeit.
  fromOne = mark(one)
  fromTwo = mark(two)
  one.chat('/queue sumo')
  // The French name of the first version still works.
  two.chat('/file sumo')
  await chatLine(one, /Sumo duel against RagoDeux/, 10000, fromOne)
  await chatLine(two, /Sumo duel against RagoUn/, 10000, fromTwo)
  one.chat('/spawn')
  await expectInLobby(one, 'after /spawn in a duel')
  await chatLine(two, /Victory!.*RagoUn gave up/, 5000, fromTwo)
  await expectInLobby(two, 'after winning by forfeit')
  log('/spawn in a duel: RagoUn is back in the lobby, RagoDeux wins by forfeit')

  // 6. A friendly challenge: /duel, then /duel accept (the clickable button runs the same).
  fromOne = mark(one)
  fromTwo = mark(two)
  one.chat('/duel RagoDeux sumo')
  await chatLine(two, /RagoUn challenges you to .*Sumo duel/, 5000, fromTwo)
  two.chat('/duel accept RagoUn')
  await chatLine(one, /Sumo duel against RagoDeux/, 10000, fromOne)
  await chatLine(two, /Sumo duel against RagoUn/, 10000, fromTwo)
  two.chat('/spawn')
  await chatLine(one, /Victory!.*RagoDeux gave up/, 5000, fromOne)
  await expectInLobby(one, 'after the /duel match')
  await expectInLobby(two, 'after /spawn in the /duel match')
  log('/duel: RagoDeux accepted, the duel started without the queue')

  // 7. Ranked Bedwars 1v1 (RagoBedwars), on its demo map.
  await playBedwars(one, two)

  // 8. First Rush: a real game on two islands side by side.
  await playFirstRush(one, two)

  // 9. A party: a private game in a 1v1 mode, the queue in one team in a 2v2 mode.
  await playParty(one, two)

  finishing = true
  one.quit()
  two.quit()
  await sleep(500)
  log('PASSED')
  process.exit(0)
}

main().catch(abort)
