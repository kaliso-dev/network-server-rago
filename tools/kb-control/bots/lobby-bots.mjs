// Lobby and sumo test bots for RagoLobby and RagoPractice (see tools/lobby-check/README.md).
//
// Two bots join a local 1.8.8 test server that runs RagoLobby, RagoPractice and RagoCombatEngine
// and play the way a player does:
//   1. both arrive in the lobby for the first time: "What do you want to do?" opens a moment
//      after the welcome (four ways to play, the book of Getting started), and they close it;
//      adventure mode, the selector in the first hotbar slot, the kit
//      editor book in the second, the sidebar; the Duels NPC is a Steve holding a sword, out of
//      the tab list, and a click on it opens Duels; RagoUn opens the kit editor with the book and
//      moves the stick of the sumo kit (run-lobby-test.sh gives the sumo one) from the first
//      hotbar slot to the fourth, saves, and finds it there when it opens the editor again;
//   2. RagoUn opens the selector with a right click, opens the Duels category (Daily Streak: 0 on
//      the sumo, nobody in its top yet, nobody in its queue), then clicks the sumo duel: the line
//      under the Duels NPC goes from 0 to 1 in queue; RagoDeux types /queue sumo;
//   3. the duel starts after a 3, 2, 1 countdown in titles, each bot gets the stick where it put
//      it (RagoUn: fourth slot, RagoDeux: the kit's first), the sidebar shows the opponent and both
//      pings: RagoUn walks up to RagoDeux and hits it once (knockback, no damage), then
//      RagoDeux walks off the platform three times, so RagoUn wins 3 - 0;
//   4. both come back to the lobby (0 in queue again under the Duels NPC), and read their own stats above the stats NPC and on the
//      leaderboard wall (RagoUn first with one win, and three kills); a click on the stats NPC opens
//      the stats menu (its categories: the Duels hold the sumo's win, a click shows the sumo, Back
//      returns); the XP bar shows the Network level; clicks on the main leaderboard go from the
//      Network level to the Elo, the FKDR, the kills (a click on their period line: the kills of the
//      week) and the games played; the sumo of the Duels NPC shows RagoUn first in the top win
//      streaks of the day;
//   5. a second duel, which RagoUn leaves with /spawn: RagoDeux wins by forfeit;
//   6. RagoUn challenges RagoDeux with /duel, RagoDeux accepts, then leaves with /spawn;
//   7. a game of Ranked Bedwars 1v1 on the demo map of RagoBedwars: both type /queue ranked-1v1,
//      start on their islands with a wooden sword and leather armour, the sidebar lists the teams;
//      after "Fight!" they pick up the iron of their generator standing on their spawn, RagoUn opens
//      the item shop villager (under its floating name, ITEM SHOP) and buys wool from its Quick Buy, cannot place it near its spawn,
//      places it by its bed and breaks it again, cannot break its own bed, buys a fireball and
//      jumps on it at its feet (the blast lifts it and takes a heart, not its life); RagoDeux walks off its
//      island, dies in the void and respawns (its bed stands), then leaves with /spawn: RagoUn
//      wins, and both are back in the lobby; RagoDeux left a ranked game it still played, so the
//      ranked queues are closed to it for 5 minutes;
//   8. a First Rush, a real 1v1: RagoUn asks for a map the mode does not have, then for bw-demo,
//      RagoDeux for any map; they meet on bw-demo, one red and one blue, with the events of a real
//      game in the sidebar; RagoDeux leaves with /spawn, RagoUn wins and both are back in the lobby;
//   9. a party: RagoUn invites RagoDeux with /p, RagoDeux accepts, /party list and /pc; RagoDeux
//      cannot queue (only the leader does); RagoUn queues the sumo: a private game between them,
//      without stats or XP; RagoUn queues Ranked Bedwars 2v2: refused, RagoDeux left a ranked game
//      a moment ago; RagoUn queues First Rush duo (2v2, unranked): the party waits in one team, and
//      leaves the queue with RagoDeux; RagoUn disbands the party; in between, RagoUn watches the
//      replay of the sumo duel it won 3 - 0 (/replay, the menu of its games), hears its countdown
//      (the sounds of the game come back in the replay), and brings RagoDeux into it with /p warp:
//      RagoDeux comes beside RagoUn, at the same moment of the replay, with only the players and
//      leave in its hotbar (the leader runs it), hears the countdown of a later round, and goes back
//      to the lobby when RagoUn leaves;
//  10. a member leaves the server in a party: RagoUn is told, and RagoDeux, back on the server,
//      sees RagoUn's name tag (its team) again;
//  11. an event of /host (plan 4.2): RagoTrois (an operator, run-lobby-test.sh) hosts a Sumo
//      event; the whole server reads its announcement with a [CLICK TO JOIN] that runs /join, and
//      RagoTrois, in it, reads "You are in!" and has the red dye "Cancel the event" in its last
//      slot; RagoDeux, playing the Block clutch, joins and stays in its training, RagoUn joins from
//      the lobby and stays there, with the red dye "Leave the event": a right click takes it out (its
//      profile comes back), and it joins again; the announcement comes back 20 seconds later; at
//      the end of the wait (25 seconds, run-lobby-test.sh), RagoDeux leaves its training for it and
//      the three go to it; RagoTrois and RagoDeux leave the fight with /spawn, RagoUn wins, and the
//      server is told; then RagoTrois hosts another one and calls it off with its red dye.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 7 * 60 * 1000
// A new lobby world has a platform at y = 64: players stand at y = 65, at the centre.
const LOBBY_SPAWN = { x: 0.5, y: 65, z: 0.5 }
// The bundled config.yml has five categories, side by side in the middle row (one slot apart up to
// four, ModeMenu.categorySlot): Practice on the left (slot 11), RBW Modes, Duels in slot 13, the
// Arenas, FUN PRACTICE on the right. The top row of the menus is "Popular now" (the modes with
// players right now). The sumo is the slime ball of the Duels menu, under that row (Bed Fight comes
// first).
const DUELS_SLOT = 13
const POINTS_TO_WIN = 3
// run-lobby-test.sh places the NPC of the Duels category (point duels), a stats NPC
// (profil), a wall of leaderboards (leaderboards), the main leaderboard
// (top) and the kills leaderboard (joueurs) around the spawn. The points
// are at y = 65; each NPC stands a block higher, on the block put at its point (its bloc: in the
// bundled config.yml: quartz under the Duels NPC, iron under the stats NPC).
const DUELS_NPC = { x: 3.5, y: 66, z: 0.5 }
const STATS_NPC = { x: -3.5, y: 66, z: 0.5 }
// qRago (point qrago), a person of the thank-you room, on obsidian
const PERSON_NPC = { x: -3.5, y: 66, z: -3.5 }
const MAIN_BOARD = { x: 0.5, z: 5.5 }
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
    // the sounds the server sends (1.8: their names), for the sounds of the replays
    bot.sounds = []
    bot._client.on('named_sound_effect', (packet) => bot.sounds.push(packet.soundName))
    bot.titles = []
    bot.titleTimes = []
    bot.on('title', (text, type) => { if (type === 'title') bot.titles.push(plainText(text)) })
    bot.on('title_times', (fadeIn, stay, fadeOut) => bot.titleTimes.push([fadeIn, stay, fadeOut]))
    // the menu of a first arrival, whenever it comes
    bot.firstMenu = null
    bot.on('windowOpen', (window) => { if (windowTitle(window).includes('What do you want to do?')) bot.firstMenu = window })
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.on('messagestr', (message) => {
      chats.get(username).push(message)
      log(`${username} < ${message}`)
    })
    // the chat lines as sent, for their click events
    bot.jsonChats = []
    bot.on('message', (message) => bot.jsonChats.push(message.json))
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
        if (!finishing && !bot.leaving) abort(new Error(`${username} disconnected: ${reason}`))
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

/**
 * A first arrival: "What do you want to do?" opens a moment after the welcome, with four ways to
 * play (the block clutch, Ranked Bedwars, the Duels, the FFA) and the book of Getting started.
 * The bot closes it, as Esc does.
 */
async function closeFirstMenu (bot) {
  await waitFor(() => bot.firstMenu != null, 8000, `${bot.username}: the menu of the first arrival`)
  const icons = [10, 12, 14, 16, 31].map((slot) => bot.firstMenu.slots[slot]?.name ?? null)
  if (JSON.stringify(icons) !== JSON.stringify(['ladder', 'bed', 'iron_sword', 'diamond_sword', 'book'])) {
    throw new Error(`${bot.username}: the menu of the first arrival holds ${JSON.stringify(icons)}`)
  }
  bot.closeWindow(bot.firstMenu)
  await sleep(200)
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

/**
 * A right click with the item of this hotbar slot, looking at the ground: a right click aimed at a
 * leaderboard goes to the board instead, and the spawn faces the main leaderboard.
 */
async function useHotbarItem (bot, slot) {
  bot.setQuickBarSlot(slot)
  await bot.look(bot.entity.yaw, -Math.PI / 2, true)
  await sleep(250)
  bot.activateItem()
}

async function openSelectorAndJoinSumo (bot) {
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  await useHotbarItem(bot, 0)
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
  const sumo = sumoSlot(duels)
  if (sumo < 0) throw new Error(`${bot.username}: no sumo in Duels: ${JSON.stringify(duels.slots.slice(0, duels.inventoryStart).map((item) => item?.name))}`)
  const icon = duels.slots[sumo]
  const lore = JSON.stringify(icon.nbt ?? null)
  if (!lore.includes('§cDaily Streak: 0') || lore.includes(' 1. ')) {
    throw new Error(`${bot.username}: the sumo should show Daily Streak: 0 and nobody in its top: ${lore}`)
  }
  if (!lore.includes('§7In queue: §f0')) throw new Error(`${bot.username}: the sumo should show nobody in its queue: ${lore}`)
  const from = mark(bot)
  // The server cancels the click (the menu cannot be emptied): do not wait for its acceptance.
  bot.clickWindow(sumo, 0, 0).catch(() => {})
  await chatLine(bot, /You joined the queue for .*Sumo duel/, 5000, from)
  log(`${bot.username} joined the sumo queue from the selector`)
}

/** The slot of the sumo in the Duels menu: its slime ball under the top row ("Popular now"), or -1. */
function sumoSlot (window) {
  return window.slots.findIndex((item, slot) => slot >= 9 && slot < window.inventoryStart && item?.name === 'slime_ball')
}

function windowTitle (window) {
  return typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
}

/** The book of the hotbar, then the sumo in its list of modes: the editor of the sumo kit. */
async function openSumoKit (bot) {
  const listOpened = new Promise((resolve) => bot.once('windowOpen', resolve))
  await useHotbarItem(bot, KIT_EDITOR_SLOT)
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
  // Save goes back to the list of kits, with the lobby hotbar under it
  const listAgain = new Promise((resolve) => bot.once('windowOpen', resolve))
  bot.clickWindow(KIT_SAVE, 0, 0).catch(() => {})
  await chatLine(bot, /Kit saved for Sumo duel/, 5000, from)
  const list = await Promise.race([listAgain, sleep(5000).then(() => null)])
  if (list == null || !windowTitle(list).includes('Kit editor')) {
    throw new Error(`${bot.username}: Save did not go back to the list of kits: ${list == null ? 'nothing opened' : windowTitle(list)}`)
  }
  await waitFor(() => hotbarItem(bot, 0)?.name === 'compass' && hotbarItem(bot, KIT_EDITOR_SLOT)?.name === 'book', 5000,
    `${bot.username} to get the lobby hotbar back after saving`)
  bot.closeWindow(list)
  log(`${bot.username} moved the stick of the sumo kit to hotbar slot ${STICK_SLOT + 1}, saved, and is back in the list of kits`)

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
  const listOpened = new Promise((resolve) => bot.once('windowOpen', resolve))
  await useHotbarItem(bot, KIT_EDITOR_SLOT)
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
  // plan 5.3: an NPC to play stands on quartz, the stats NPC on iron (bloc: of the bundled config)
  const V = bot.entity.position.constructor
  const under = (where) => bot.blockAt(new V(where.x, where.y - 1, where.z))?.name
  await waitFor(() => under(DUELS_NPC) === 'quartz_block' && under(STATS_NPC) === 'iron_block', 5000,
    `the Duels NPC on quartz and the stats NPC on iron (they stand on ${under(DUELS_NPC)} and ${under(STATS_NPC)})`)
  log('the Duels NPC stands on quartz, the stats NPC on iron')
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
  await checkPersonNpc(bot)
}

/** qRago (plan 5.6): on obsidian, "[OWNER] qRago" above the head, empty hands; a click opens nothing. */
async function checkPersonNpc (bot) {
  await waitFor(() => npcAt(bot, PERSON_NPC) != null, 10000, `the NPC qRago at ${JSON.stringify(PERSON_NPC)}`)
  const npc = npcAt(bot, PERSON_NPC)
  const V = bot.entity.position.constructor
  const under = () => bot.blockAt(new V(PERSON_NPC.x, PERSON_NPC.y - 1, PERSON_NPC.z))?.name
  const line = () => Object.values(bot.entities).find((e) => typeof e.metadata?.[2] === 'string' &&
    e.metadata[2].includes('qRago') && Math.hypot(e.position.x - PERSON_NPC.x, e.position.z - PERSON_NPC.z) < 1)
    ?.metadata[2].replace(/§./g, '')
  await waitFor(() => under() === 'obsidian' && line() === '[OWNER] qRago', 5000,
    `qRago on obsidian under "[OWNER] qRago" (stands on ${under()}, reads ${JSON.stringify(line())})`)
  if (npc.heldItem != null) throw new Error(`qRago holds ${npc.heldItem.name}`)
  let opened = null
  const onOpen = (window) => { opened = window }
  bot.on('windowOpen', onOpen)
  bot.activateEntity(npc)
  await sleep(1500)
  bot.removeListener('windowOpen', onOpen)
  if (opened != null) {
    bot.closeWindow(opened)
    throw new Error('a click on qRago opened a menu')
  }
  log('qRago stands on obsidian under "[OWNER] qRago", empty-handed, and a click on the NPC opens nothing')
}

/** The queue line under the Duels NPC ("1 in queue · Click to play"), without colours, or undefined. */
function duelsQueueLine (bot) {
  const line = Object.values(bot.entities).find((e) => typeof e.metadata?.[2] === 'string' &&
    e.metadata[2].includes('in queue') && Math.hypot(e.position.x - DUELS_NPC.x, e.position.z - DUELS_NPC.z) < 1)
  return line?.metadata[2].replace(/§./g, '')
}

/** The Duels NPC counts the players in the queues of its modes; the line changes within a second or two. */
async function expectDuelsQueue (bot, count, what) {
  const expected = `${count} in queue · Click to play`
  await waitFor(() => duelsQueueLine(bot) === expected, 5000,
    `${bot.username} to read "${expected}" under the Duels NPC (${what}): ${JSON.stringify(duelsQueueLine(bot))}`)
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

/** The XP bar: the Network level, and how far towards the next one. */
async function expectLevel (bot, level, progress) {
  await waitFor(() => bot.experience?.level === level && Math.abs((bot.experience?.progress ?? -1) - progress) < 0.02,
    5000, `${bot.username} to see level ${level} at ${progress} in the XP bar: ${JSON.stringify(bot.experience)}`)
}

/**
 * The main leaderboard (one board, five stats): the Network level first; a click on it goes on to
 * the Elo, the FKDR, the kills and the games played, and a click on its line "change the period"
 * changes the period of the kills.
 */
async function checkMainLeaderboard (bot) {
  await waitFor(() => mainBoardLine(bot, 'Network level') != null, 5000,
    `${bot.username} to read "Network level" on the main leaderboard; floating texts: ${JSON.stringify(floatingTexts(bot))}`)
  const title = mainBoardLine(bot, 'Network level')
  const text = () => (title.metadata?.[2] ?? '').replace(/§./g, '')
  for (const next of ['Ranked Elo', 'FKDR', 'Kills']) {
    await clickBoardLine(bot, title, false)
    await waitFor(() => text() === next, 5000, `a right click to turn the main leaderboard to ${next}: ${text()}`)
  }
  await expectFloating(bot, '1. RagoUn - 3', 'the kills of the day on the main leaderboard')
  await clickBoardLine(bot, mainBoardLine(bot, '» Click here: change the period'), true)
  await expectFloating(bot, 'All modes · Weekly', 'a left click on "change the period": the kills of the week')
  await clickBoardLine(bot, title, false)
  await waitFor(() => text() === 'Games played', 5000, `a right click to turn the main leaderboard to the games played: ${text()}`)
  log('the main leaderboard goes from the Network level to the Elo, the FKDR, the kills (their period line: the week) and the games played')
}

/**
 * A click on a line of a leaderboard: the server follows the look to the line aimed at, so the bot
 * looks at its text (a block above the armor stand) first. Clicks are half a second apart at least.
 */
async function clickBoardLine (bot, line, left) {
  await sleep(500)
  await bot.lookAt(line.position.offset(0, 1, 0), true)
  await sleep(200)
  if (left) bot.attack(line)
  else bot.activateEntity(line)
}

/** The line of the main leaderboard (top) showing this text, or undefined. */
function mainBoardLine (bot, text) {
  return Object.values(bot.entities).find((e) => typeof e.metadata?.[2] === 'string' &&
    e.metadata[2].replace(/§./g, '') === text &&
    Math.hypot(e.position.x - MAIN_BOARD.x, e.position.z - MAIN_BOARD.z) < 1)
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
  const lore = JSON.stringify(window.slots[sumoSlot(window)]?.nbt ?? null)
  if (!lore.includes('§cDaily Streak: 0') || !lore.includes('§c 1. §fRagoUn§c - 1')) {
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
  // Two stats a line in the profile.
  await expectFloating(one, 'Wins: 1 · Losses: 0', 'the profile of the winner')
  await expectFloating(two, 'Wins: 0 · Losses: 1', 'the profile of the loser')
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
  const title = windowTitle(window)
  if (!title.includes('Your stats')) throw new Error(`the stats NPC opened ${title}`)
  // the first page: the categories of the selector, the Duels (an iron sword) with the sumo's win and kills
  const duelsSlot = window.slots.findIndex((item, slot) => slot < window.inventoryStart && item?.name === 'iron_sword')
  const duels = JSON.stringify(window.slots[duelsSlot]?.nbt ?? null)
  if (duelsSlot < 0 || !duels.includes('Wins: §a1') || !duels.includes('Kills: §a3')) {
    throw new Error(`no Duels category with the sumo's win and kills in the stats menu: ${duels}`)
  }
  // a click on it: its modes, the sumo (its slime ball) with its stats
  const modesOpened = new Promise((resolve) => one.once('windowOpen', resolve))
  one.clickWindow(duelsSlot, 0, 0).catch(() => {})
  const modes = await Promise.race([modesOpened, sleep(5000).then(() => null)])
  if (modes == null || !windowTitle(modes).includes('Your stats: Duels')) {
    throw new Error(`a click on the Duels of the stats menu opened ${modes == null ? 'nothing' : windowTitle(modes)}`)
  }
  const sumo = modes.slots.find((item, slot) => slot < modes.inventoryStart && item?.name === 'slime_ball')
  const lore = JSON.stringify(sumo?.nbt ?? null)
  if (!lore.includes('Wins: §a1')) throw new Error(`no sumo win in the stats menu: ${lore}`)
  if (!lore.includes('Kills: §a3')) throw new Error(`no sumo kills in the stats menu: ${lore}`)
  // Back: the categories again
  const backOpened = new Promise((resolve) => one.once('windowOpen', resolve))
  one.clickWindow(0, 0, 0).catch(() => {})
  const back = await Promise.race([backOpened, sleep(5000).then(() => null)])
  if (back == null || windowTitle(back).includes(':') ||
    !back.slots.some((item, slot) => slot < back.inventoryStart && item?.name === 'iron_sword')) {
    throw new Error(`Back in the stats menu opened ${back == null ? 'nothing' : windowTitle(back)}`)
  }
  one.closeWindow(back)
  log('stats: each bot reads its own profile, the leaderboard has RagoUn first, the menu opens on the categories, ' +
    'the Duels show the sumo, Back returns')
  // Three points: three kills for RagoUn, three deaths for RagoDeux.
  await expectFloating(one, 'Kills: 3 · Final kills: 0', 'the kills of the winner')
  await expectFloating(two, 'Kills: 0 · Final kills: 0', 'the kills of the loser')
  await expectFloating(two, '1. RagoUn - 3', 'the kills of the day on joueurs')
  // joueurs: the best win streaks on the left of the kills, the Network level on the right.
  await waitFor(() => floatingTexts(two).filter((text) => text === 'Best win streak').length === 2 &&
    floatingTexts(two).includes('Network level'), 5000,
  `the streaks and the level next to the kills on joueurs: ${JSON.stringify(floatingTexts(two))}`)
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
  const fromLeft = mark(two)
  two.chat('/spawn')
  await chatLine(one, /RagoDeux left the game\./, 5000, fromEnd)
  await chatLine(one, /Winner: (Red|Blue) - RagoUn/, 5000, fromEnd)
  // RagoDeux left a ranked game it still played (its bed stands): no ranked queue for 5 minutes
  await chatLine(two, /^You left a ranked game: the ranked queues are closed to you for 5 minutes\.$/, 5000, fromLeft)
  await expectInLobby(one, 'after the Bed Wars game')
  await expectInLobby(two, 'after /spawn in the Bed Wars game')
  log('Bed Wars: RagoDeux left, RagoUn won, both are back in the lobby; no ranked queue for RagoDeux for 5 minutes')
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
  await partyReplay(one, two)

  // two in a 2v2 mode: no ranked queue, RagoDeux left a ranked game a moment ago (playBedwars);
  // the queue of an unranked one, in one team
  fromOne = mark(one)
  fromTwo = mark(two)
  one.chat('/queue ranked-2v2')
  await chatLine(one, /^RagoDeux left a ranked game: your party can join a ranked queue again in [0-5]:\d\d\.$/, 5000, fromOne)
  fromOne = mark(one)
  one.chat('/queue first-rush-duo')
  await chatLine(one, /Your party joined the queue for .*You will play in the same team\./, 5000, fromOne)
  await chatLine(two, /Your party joined the queue for .*You will play in the same team\./, 5000, fromTwo)
  two.chat('/queue leave')
  await chatLine(two, /You left the queue: your party left the queue for/, 5000, fromTwo)
  await chatLine(one, /RagoDeux left the queue: your party left the queue for/, 5000, fromOne)
  one.chat('/party disband')
  await chatLine(two, /RagoUn has disbanded the party!/, 5000, fromTwo)
  log('party: no ranked queue after RagoDeux left a ranked game; in a 2v2 mode the party queues together, leaves together, then is disbanded')
}

/** The elapsed and total seconds of the replay in the sidebar ("▶ 0:05 / 0:12"), or null. */
function replayClock (bot) {
  for (const line of sidebarLines(bot)) {
    const clock = /(\d+):(\d\d) \/ (\d+):(\d\d)/.exec(line)
    if (clock != null) {
      return { at: Number(clock[1]) * 60 + Number(clock[2]), length: Number(clock[3]) * 60 + Number(clock[4]) }
    }
  }
  return null
}

/**
 * Plan 4.1: RagoUn, leader of the party, watches the replay of its 3 - 0 sumo duel from /replay,
 * then /p warp brings RagoDeux into it: beside RagoUn, at the same moment, with the players and
 * leave only (the leader runs the replay). RagoUn leaves: RagoDeux goes back to the lobby with it.
 * Plan 4.1b: both hear the notes of the duel's countdowns, recorded with the game.
 */
async function partyReplay (one, two) {
  let fromOne = mark(one)
  let fromTwo = mark(two)
  const heardOne = one.sounds.length
  const opened = new Promise((resolve) => one.once('windowOpen', resolve))
  one.chat('/replay')
  const menu = await Promise.race([opened, sleep(5000).then(() => null)])
  if (menu == null) throw new Error('/replay did not open the menu of the replays')
  if (!windowTitle(menu).includes('Your replays')) throw new Error(`/replay opened ${windowTitle(menu)}`)
  // the duel RagoUn won 3 - 0: three rounds, each after a countdown
  const sumo = menu.slots.findIndex((item, slot) => slot < 45 && item != null && /Sumo/i.test(JSON.stringify(item.nbt ?? null)) &&
    JSON.stringify(item.nbt ?? null).includes('RagoUn won (3 - 0)'))
  if (sumo < 0) {
    throw new Error(`no 3 - 0 sumo duel in RagoUn's replays: ${JSON.stringify(menu.slots.slice(0, 45).filter((item) => item != null).map((item) => item.nbt))}`)
  }
  one.clickWindow(sumo, 0, 0).catch(() => {})
  await chatLine(one, /^Replay of .*Sumo.*long\./, 15000, fromOne)
  await chatLine(one, /Your party can watch it with you: \/p warp/, 5000, fromOne)
  await waitFor(() => hotbarItem(one, 3)?.name === 'dye', 5000,
    `the pause button of the replay in RagoUn's hotbar: ${hotbarItem(one, 3)?.name}`)
  log('party replay: RagoUn, the leader, watches the replay of a sumo duel, and is told /p warp brings the party')
  await waitFor(() => one.sounds.slice(heardOne).some((name) => /^note\./.test(name)), 10000,
    `RagoUn to hear the countdown in the replay; sounds: ${JSON.stringify(one.sounds.slice(heardOne))}`)
  log(`party replay: RagoUn hears the sounds of the duel (${JSON.stringify([...new Set(one.sounds.slice(heardOne))])})`)

  fromOne = mark(one)
  one.chat('/p warp')
  await chatLine(one, /You summoned 1 player to your replay\./, 5000, fromOne)
  await chatLine(two, /RagoUn warped you into the replay of .*Sumo/, 5000, fromTwo)
  await waitFor(() => hotbarItem(two, 0)?.name === 'compass' && hotbarItem(two, 8)?.name === 'bed', 5000,
    `the players and leave in RagoDeux's hotbar: ${hotbarItem(two, 0)?.name}, ${hotbarItem(two, 8)?.name}`)
  for (const slot of [2, 3, 4, 6]) {
    if (hotbarItem(two, slot) != null) throw new Error(`RagoDeux has ${hotbarItem(two, slot).name} in slot ${slot}: the leader runs the replay`)
  }
  const distance = one.entity.position.distanceTo(two.entity.position)
  if (distance > 2) throw new Error(`RagoDeux is ${distance.toFixed(1)} blocks from RagoUn in the replay`)
  await waitFor(() => {
    const a = replayClock(one)
    const b = replayClock(two)
    return a != null && b != null && a.length === b.length && Math.abs(a.at - b.at) <= 1
  }, 5000, `the same moment of the replay in both sidebars: ${JSON.stringify(sidebarLines(one))} / ${JSON.stringify(sidebarLines(two))}`)
  log(`party replay: /p warp brought RagoDeux beside RagoUn, same moment (${JSON.stringify(replayClock(two))}), players and leave only`)
  const heardTwo = two.sounds.length
  await waitFor(() => two.sounds.slice(heardTwo).some((name) => /^note\./.test(name)), 30000,
    `RagoDeux to hear a countdown of the replay; sounds: ${JSON.stringify(two.sounds.slice(heardTwo))}`)
  log(`party replay: RagoDeux hears the sounds of the replay too (${JSON.stringify([...new Set(two.sounds.slice(heardTwo))])})`)

  fromTwo = mark(two)
  one.chat('/replay leave')
  await chatLine(two, /RagoUn left the replay\./, 5000, fromTwo)
  await expectInLobby(one, 'after leaving the replay')
  await expectInLobby(two, 'after the leader left the replay')
  log('party replay: RagoUn left, and RagoDeux went back to the lobby with it')
}

/**
 * RagoDeux leaves the server while in RagoUn's party, then comes back: RagoUn is told, and the
 * lobby forgot RagoDeux whole (an error there left its old scoreboard: no name tags at all).
 * Returns the RagoDeux of now.
 */
async function quitInParty (one, two) {
  let fromOne = mark(one)
  const fromTwo = mark(two)
  one.chat('/p RagoDeux')
  await chatLine(two, /RagoUn has invited you to join their party!/, 5000, fromTwo)
  two.chat('/party accept RagoUn')
  await chatLine(one, /RagoDeux joined the party\./, 5000, fromOne)
  fromOne = mark(one)
  two.leaving = true
  two.quit()
  await chatLine(one, /RagoDeux left the server and the party\./, 5000, fromOne)
  const back = await join('RagoDeux')
  await expectInLobby(back, 'back on the server')
  await waitFor(() => back.teamMap?.RagoUn != null, 5000,
    `RagoDeux, back, to see the name tag of RagoUn: teams ${JSON.stringify(Object.keys(back.teamMap ?? {}))}`)
  log('party: RagoDeux left the server in the party, RagoUn was told, and back it sees the name tags')
  return back
}

/** The commands a click on this chat line runs (its run_command click events, at any depth). */
function clickCommands (json) {
  const found = []
  const walk = (part) => {
    if (part == null || typeof part !== 'object') return
    if (Array.isArray(part)) { part.forEach(walk); return }
    if (part.clickEvent?.action === 'run_command') found.push(part.clickEvent.value)
    walk(part.extra)
  }
  walk(json)
  return found
}

/** The red dye of an event in the last slot of the lobby hotbar, with this name (Leave or Cancel the event). */
function eventDye (bot, name) {
  const item = hotbarItem(bot, 8)
  return item?.name === 'dye' && item.metadata === 1 && JSON.stringify(item.nbt ?? null).includes(name)
}

/**
 * A right click on the floor with the item of this slot, as a player does: the use packet in the air
 * that activateItem sends is dropped by the server when the player looks at a block after placing
 * one (RagoUn placed its wool in Bed Wars), as in fireballJump.
 */
async function useOnFloor (bot, slot) {
  bot.setQuickBarSlot(slot)
  await sleep(250)
  bot.activateBlock(bot.blockAt(bot.entity.position.offset(0, -1, 0))).catch(() => {})
}

/**
 * An event of /host (plan 4.2): announced to the whole server every 20 seconds with a [CLICK TO
 * JOIN] until it starts; joining does not move a player, who keeps playing, and the red dye of the
 * lobby hotbar takes them out (its host's calls it off); at the end of the wait, those still in a
 * game leave it, as with /spawn, and all its players go to it.
 */
async function playEvent (one, two, three) {
  // RagoDeux plays the Block clutch training: it stays in it until the event starts
  let fromTwo = mark(two)
  two.chat('/queue clutch')
  await chatLine(two, /Block clutch - /, 15000, fromTwo)
  await waitFor(() => two.game.gameMode === 'survival', 5000, 'RagoDeux in survival mode in the Block clutch')
  const inTraining = (what) => {
    if (two.game.gameMode !== 'survival' || hotbarItem(two, 0)?.name === 'compass') {
      throw new Error(`RagoDeux left its Block clutch (${what}): mode ${two.game.gameMode}, slot 0 ${hotbarItem(two, 0)?.name}`)
    }
  }
  log('event: RagoDeux plays the Block clutch')

  // where each one stands: joining moves nobody
  const stayed = (bot, at, what) => {
    if (bot.game.gameMode !== 'adventure' || hotbarItem(bot, 0)?.name !== 'compass' || bot.entity.position.distanceTo(at) > 1) {
      throw new Error(`${bot.username} moved (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}, ` +
        `at ${bot.entity.position}, was at ${at}`)
    }
  }
  const oneAt = one.entity.position.clone()
  const threeAt = three.entity.position.clone()
  let fromOne = mark(one)
  fromTwo = mark(two)
  let fromThree = mark(three)
  const jsonFrom = one.jsonChats.length
  three.chat('/host sumo')
  const hosting = /SUMO EVENT[\s\S]*Hosted by RagoTrois[\s\S]*Knock your opponent off the platform\./
  await chatLine(one, hosting, 5000, fromOne)
  const firstAt = Date.now()
  await chatLine(two, hosting, 5000, fromTwo)
  await chatLine(three, /SUMO EVENT[\s\S]*Starts in 25s\s+•\s+1\/40 players[\s\S]*You are in!/, 5000, fromThree)
  await chatLine(one, /Starts in 25s\s+•\s+1\/40 players[\s\S]*\[CLICK TO JOIN\]/, 5000, fromOne)
  const button = one.jsonChats.slice(jsonFrom).find((json) => clickCommands(json).length > 0)
  if (button == null || JSON.stringify(clickCommands(button)) !== JSON.stringify(['/join'])) {
    throw new Error(`the announcement of the event has no [CLICK TO JOIN] that runs /join: ${JSON.stringify(one.jsonChats.slice(jsonFrom))}`)
  }
  // a player in a game reads it too, with the same button
  await chatLine(two, /Starts in 25s\s+•\s+1\/40 players[\s\S]*\[CLICK TO JOIN\]/, 5000, fromTwo)
  await waitFor(() => eventDye(three, 'Cancel the event'), 3000,
    `the red dye Cancel the event in the last slot of RagoTrois: ${JSON.stringify(hotbarItem(three, 8) ?? null)}`)
  log('event: RagoTrois hosts a Sumo event, the whole server reads it with a [CLICK TO JOIN] that runs /join; RagoTrois has the red dye Cancel the event')

  // joining moves nobody: RagoDeux from its training, RagoUn from the lobby (the button runs /join)
  fromTwo = mark(two)
  two.chat('/join')
  await chatLine(two, /You joined the Sumo event of RagoTrois \(2\/40 players \(1 in a game\), starts in \d+s\)\. Keep playing/, 5000, fromTwo)
  fromOne = mark(one)
  one.chat('/join')
  await chatLine(one, /You joined the Sumo event of RagoTrois \(3\/40 players \(1 in a game\), starts in \d+s\)\. Keep playing/, 5000, fromOne)
  // the red dye of RagoUn takes it out of the event, its profile comes back; it joins again
  await waitFor(() => eventDye(one, 'Leave the event'), 3000,
    `the red dye Leave the event in the last slot of RagoUn: ${JSON.stringify(hotbarItem(one, 8) ?? null)}`)
  fromOne = mark(one)
  await useOnFloor(one, 8)
  await chatLine(one, /You left the Sumo event\./, 3000, fromOne)
  await waitFor(() => hotbarItem(one, 8)?.name === 'skull', 3000,
    `the profile back in the last slot of RagoUn: ${JSON.stringify(hotbarItem(one, 8) ?? null)}`)
  one.setQuickBarSlot(0)
  fromOne = mark(one)
  one.chat('/join')
  await chatLine(one, /You joined the Sumo event of RagoTrois \(3\/40 players \(1 in a game\), starts in \d+s\)\. Keep playing/, 5000, fromOne)
  await waitFor(() => eventDye(one, 'Leave the event'), 3000, 'the red dye of RagoUn again')
  log('event: RagoUn left the event with its red dye (its profile came back) and joined again')
  await sleep(1000)
  inTraining('after /join')
  stayed(one, oneAt, 'after /join')
  stayed(three, threeAt, 'after /host')
  log('event: RagoDeux joined from its Block clutch and stays in it, RagoUn joined and stays in the lobby')

  // the message again, 20 seconds after the first, with where the event stands
  fromOne = mark(one)
  fromTwo = mark(two)
  await chatLine(one, hosting, 25000, fromOne)
  const again = (Date.now() - firstAt) / 1000
  if (again < 18 || again > 22) throw new Error(`the announcement came back after ${again.toFixed(1)} s, not 20`)
  await chatLine(one, /Starts in 5s\s+•\s+3\/40 players[\s\S]*You are in!/, 2000, fromOne)
  await chatLine(two, /Starts in 5s\s+•\s+3\/40 players[\s\S]*You are in!/, 2000, fromTwo)
  inTraining('at the second announcement')
  stayed(one, oneAt, 'at the second announcement')
  log(`event: the announcement came back ${again.toFixed(1)} s after the first, RagoUn and RagoDeux read they are in`)

  // the end of the wait: RagoDeux leaves its Block clutch for it, as with /spawn; the three go to it
  fromOne = mark(one)
  fromTwo = mark(two)
  fromThree = mark(three)
  const bots = { RagoUn: one, RagoDeux: two, RagoTrois: three }
  const titlesFrom = Object.fromEntries(Object.entries(bots).map(([name, bot]) => [name, bot.titles.length]))
  await chatLine(two, /The Sumo event starts: you leave your game for it\./, 10000, fromTwo)
  await chatLine(two, /You joined the Sumo event of RagoTrois\. /, 5000, fromTwo)
  await chatLine(one, /You joined the Sumo event of RagoTrois\. /, 5000, fromOne)
  await chatLine(three, /You joined the Sumo event of RagoTrois\. /, 5000, fromThree)
  await chatLine(one, /The Sumo event starts with 3 players!/, 12000, fromOne)
  const vs = (await chatLine(one, /(RagoUn|RagoDeux|RagoTrois) vs (RagoUn|RagoDeux|RagoTrois)/, 5000, fromOne))
    .match(/(RagoUn|RagoDeux|RagoTrois) vs (RagoUn|RagoDeux|RagoTrois)/)
  log(`event: RagoDeux left its Block clutch for it at the end of the wait, the three went to it; first fight ${vs[1]} vs ${vs[2]}`)

  const fighter = bots[vs[1]]
  await waitFor(() => fighter.titles.slice(titlesFrom[vs[1]]).includes('Fight!'), 10000,
    `the fight of the event; titles of ${vs[1]}: ${JSON.stringify(fighter.titles.slice(titlesFrom[vs[1]]))}`)
  fromOne = mark(one)
  fromTwo = mark(two)
  fromThree = mark(three)
  three.chat('/spawn')
  await expectInLobby(three, 'after /spawn in the event')
  two.chat('/spawn')
  await expectInLobby(two, 'after /spawn in the event')
  await chatLine(one, /RagoUn won the Sumo event of RagoTrois!/, 10000, fromOne)
  await chatLine(two, /RagoUn won the Sumo event of RagoTrois!/, 5000, fromTwo)
  await chatLine(three, /RagoUn won the Sumo event of RagoTrois!/, 5000, fromThree)
  await expectInLobby(one, 'after winning the event')
  log('event: RagoTrois and RagoDeux left it, RagoUn won and the server was told; the three are back in the lobby')

  // another one, called off by its host with its red dye; nobody keeps a red dye
  fromOne = mark(one)
  fromThree = mark(three)
  three.chat('/host sumo')
  await chatLine(one, hosting, 5000, fromOne)
  await waitFor(() => eventDye(three, 'Cancel the event'), 3000, 'the red dye Cancel the event of RagoTrois')
  await useOnFloor(three, 8)
  await chatLine(one, /The Sumo event of RagoTrois is called off: called off by its host\./, 3000, fromOne)
  await chatLine(three, /The Sumo event of RagoTrois is called off: called off by its host\./, 3000, fromThree)
  await waitFor(() => hotbarItem(three, 8)?.name === 'skull', 3000,
    `the profile back in the last slot of RagoTrois: ${JSON.stringify(hotbarItem(three, 8) ?? null)}`)
  three.setQuickBarSlot(0)
  log('event: RagoTrois hosted another one and called it off with its red dye, the server was told')
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
  await closeFirstMenu(one)
  await closeFirstMenu(two)
  log('the menu of the first arrival: four ways to play and Getting started, then closed')
  await expectInLobby(one, 'on joining')
  await expectInLobby(two, 'on joining')
  // the fireball in the fifth slot, a free slot, the slot of the leave or play again item (empty out
  // of a queue before any game), the shop and the profile at the end
  const layout = [4, 5, 6, 7, 8].map((slot) => hotbarItem(one, slot)?.name ?? null)
  if (JSON.stringify(layout) !== JSON.stringify(['fire_charge', null, null, 'emerald', 'skull'])) {
    throw new Error(`the lobby hotbar, slots 4 to 8: ${JSON.stringify(layout)}`)
  }
  if (hotbarItem(one, KIT_EDITOR_SLOT)?.name !== 'book') {
    throw new Error(`no kit editor in slot ${KIT_EDITOR_SLOT}: ${hotbarItem(one, KIT_EDITOR_SLOT)?.name}`)
  }
  await waitFor(() => (one.scoreboard?.sidebar?.title ?? '').includes('RAGO PRACTICE'), 5000,
    `the lobby sidebar; sidebar: ${JSON.stringify(one.scoreboard?.sidebar?.title)}`)
  log('lobby: adventure mode, selector, fireball, shop, profile and sidebar are there')
  await checkDuelsNpc(one)
  await arrangeSumoKit(one)
  await checkHypixelImport(two)

  // 2. The queue, and its count under the Duels NPC (plan 3.8).
  await expectDuelsQueue(two, 0, 'nobody waits yet')
  await openSelectorAndJoinSumo(one)
  await expectDuelsQueue(two, 1, 'RagoUn waits for a sumo duel')
  log('the Duels NPC counts RagoUn in its queue')
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
  await expectDuelsQueue(two, 0, 'the duel took both out of the queue')
  log('the Duels NPC counts nobody in its queue again')
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

  // 10. A member leaves the server in a party, then comes back.
  const back = await quitInParty(one, two)

  // 11. An event of /host, hosted by an operator.
  const three = await join('RagoTrois')
  await closeFirstMenu(three)
  await expectInLobby(three, 'on joining')
  await playEvent(one, back, three)

  finishing = true
  one.quit()
  back.quit()
  three.quit()
  await sleep(500)
  log('PASSED')
  process.exit(0)
}

main().catch(abort)
