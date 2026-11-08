// Lobby and sumo test bots for RagoLobby and RagoPractice (see tools/lobby-check/README.md).
//
// Two bots join a local 1.8.8 test server that runs RagoLobby, RagoPractice and RagoCombatEngine
// and play the way a player does:
//   1. both arrive in the lobby: adventure mode, the selector in the first hotbar slot, the sidebar;
//      the Duels NPC is a Steve holding a sword, out of the tab list, and a click on it opens Duels;
//   2. RagoUn opens the selector with a right click, opens the Duels category (Daily Streak: 0 on
//      the sumo, nobody in its top yet), then clicks the sumo duel; RagoDeux types /queue sumo;
//   3. the duel starts after a 3, 2, 1 countdown in titles, the sidebar shows the opponent and both
//      pings: RagoUn walks up to RagoDeux and hits it once (knockback, no damage), then
//      RagoDeux walks off the platform three times, so RagoUn wins 3 - 0;
//   4. both come back to the lobby, and read their own stats above the stats NPC and on the
//      leaderboard wall (RagoUn first with one win, and three kills); a click on the stats NPC opens
//      the stats menu; the XP bar shows the Network level; clicks on the main leaderboard go from the
//      Elo to the kills (a left click: the kills of the week) and to the Network level; the sumo of
//      the Duels NPC shows RagoUn first in the top win streaks of the day;
//   5. a second duel, which RagoUn leaves with /spawn: RagoDeux wins by forfeit;
//   6. RagoUn challenges RagoDeux with /duel, RagoDeux accepts, then leaves with /spawn.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 5 * 60 * 1000
// A new lobby world has a platform at y = 64: players stand at y = 65, at the centre.
const LOBBY_SPAWN = { x: 0.5, y: 65, z: 0.5 }
// The bundled config.yml has three categories, centred in the middle row: Duels is the first, in
// slot 12. Sumo is the first mode of Duels: slot 10 of its menu.
const DUELS_SLOT = 12
const SUMO_SLOT = 10
const POINTS_TO_WIN = 3
// run-lobby-test.sh places the NPC of the Duels category (point attackdefenseversionrbw), a stats
// NPC (statsjoueur1), a wall of leaderboards (leaderboardstats), the main leaderboard
// (leaderboardprincipal) and the kills leaderboard (leaderboardplayer) around the spawn.
const DUELS_NPC = { x: 3.5, y: 65, z: 0.5 }
const STATS_NPC = { x: -3.5, y: 65, z: 0.5 }

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
  // Network XP: 50 for the win and 5 a kill (65 of the 100 of level 1), 15 for the game lost.
  await expectLevel(one, 1, 0.65)
  await expectLevel(two, 1, 0.15)
  await chatLine(one, /\+50 Network XP \(win\)/, 5000)
  await chatLine(two, /\+15 Network XP \(game played\)/, 5000)
  log('Network level: RagoUn 65 XP, RagoDeux 15 XP, in the XP bar; three kills on the boards')
  await checkMainLeaderboard(one)
  await checkTopStreak(two)
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
  await waitFor(() => (one.scoreboard?.sidebar?.title ?? '').includes('RAGO PRACTICE'), 5000,
    `the lobby sidebar; sidebar: ${JSON.stringify(one.scoreboard?.sidebar?.title)}`)
  log('lobby: adventure mode, selector, visibility item and sidebar are there')
  await checkDuelsNpc(one)

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
  log('the duel started')
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

  finishing = true
  one.quit()
  two.quit()
  await sleep(500)
  log('PASSED')
  process.exit(0)
}

main().catch(abort)
