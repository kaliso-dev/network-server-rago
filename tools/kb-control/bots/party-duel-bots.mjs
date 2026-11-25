// Party duel test bots for RagoLobby (see tools/lobby-check/README.md, run-arena-test.sh): the
// duels between parties (the owner's ask of 25/11: from the Party category, or /duel as the leader
// of a party of two or more, the parties of the server, challenged in a mode of the Duels by the
// number of players). Four bots make two parties of two: RagoDuoUn leads RagoDuoDeux, RagoDuoTrois
// leads RagoDuoQuatre.
//   1. RagoDuoDeux, not a leader: /duel tells it that only the leader challenges another party;
//   2. RagoDuoUn opens the Party category of the compass (/play party): Party Duels in its last
//      slot; a click opens the parties of the server: the other one, its leader's head with its two
//      players; a click on it opens the modes of the Duels for two parties of two: Top Bridge 2v2
//      among them, only 2v2s; a click on Top Bridge 2v2 sends the challenge: RagoDuoTrois reads it
//      with [Accept] [Deny], the two others are told; /duel deny RagoDuoUn refuses it;
//   3. RagoDuoUn challenges again, from /duel this time; /duel accept RagoDuoUn: the four read the
//      party duel, and play Top Bridge 2v2, each party in a team of its own; the party of
//      RagoDuoTrois leaves with /spawn; all four back in the lobby.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 3 * 60 * 1000

let finishing = false

function abort (error) {
  console.error(`[party-duel-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[party-duel-bots] ${text}`)
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

/** The slots of the chest of a window (not the player's inventory under it) whose item has this text. */
function chestSlots (window, wanted) {
  const found = []
  for (let slot = 0; slot < window.inventoryStart; slot++) {
    if (window.slots[slot] != null && text(window.slots[slot]).includes(wanted)) found.push(slot)
  }
  return found
}

async function party (leader, other) {
  const fromLeader = mark(leader)
  const fromOther = mark(other)
  leader.chat(`/p invite ${other.username}`)
  await chatLine(other, new RegExp(`${leader.username} has invited you to join their party!`), 5000, fromOther)
  other.chat(`/party accept ${leader.username}`)
  await chatLine(leader, new RegExp(`${other.username} joined the party\\.`), 5000, fromLeader)
  log(`party: ${leader.username} with ${other.username}`)
}

/**
 * In the parties menu of the leader: the other party, its leader's head with its two players and a
 * click to choose the duel; a click on it opens its modes: Top Bridge 2v2 among them, only 2v2s.
 * Returns the modes menu.
 */
async function chooseTheOtherParty (leader, menu, other, otherMate) {
  await waitFor(() => chestSlots(menu, `${other.username}'s party`).length === 1, 5000,
    () => `the party of ${other.username} in the parties: ${JSON.stringify(menu.slots.slice(0, menu.inventoryStart).filter(Boolean).map(text))}`)
  const slot = chestSlots(menu, `${other.username}'s party`)[0]
  const icon = menu.slots[slot]
  const lore = text(icon)
  if (icon.name !== 'skull' || !lore.includes('2 players:') || !lore.includes(other.username) || !lore.includes(otherMate.username)
      || !lore.includes('Click to choose the duel')) {
    throw new Error(`the party of ${other.username} in the menu: ${icon.name} ${lore}`)
  }
  if (chestSlots(menu, `${leader.username}'s party`).length !== 0) throw new Error('the leader\'s own party is in the list')
  const count = leader.windows.length
  leader.clickWindow(slot, 0, 0).catch(() => {})
  const modes = await windowAfter(leader, count, `Duel ${other.username}'s party`, 'the modes of the duel')
  await waitFor(() => chestSlots(modes, 'Top Bridge 2v2').length === 1, 5000,
    () => `Top Bridge 2v2 among the modes: ${JSON.stringify(modes.slots.slice(0, modes.inventoryStart).filter(Boolean).map(text))}`)
  // every mode shown is a 2v2 (PartyDuels: "2v2: your party against ..."), but the back arrow
  for (let s = 0; s < modes.inventoryStart; s++) {
    const item = modes.slots[s]
    if (item != null && !text(item).includes('Go back') && !text(item).includes(`2v2: your party against ${other.username}'s`)) {
      throw new Error(`a mode that is no 2v2 for two parties of two, slot ${s}: ${text(item)}`)
    }
  }
  return modes
}

/** The leader clicks Top Bridge 2v2: the challenge, read by the four. */
async function challengeTopBridge (leader, mate, other, otherMate, modes) {
  const from = [leader, mate, other, otherMate].map(mark)
  leader.clickWindow(chestSlots(modes, 'Top Bridge 2v2')[0], 0, 0).catch(() => {})
  await chatLine(leader, new RegExp(`Party duel request sent to ${other.username}'s party in Top Bridge 2v2 \\(2v2\\)\\. They have one minute to accept\\.`), 5000, from[0])
  await chatLine(mate, new RegExp(`${leader.username} challenged ${other.username}'s party to Top Bridge 2v2 \\(2v2\\)\\.`), 5000, from[1])
  await chatLine(other, new RegExp(`${leader.username}'s party \\(2 players\\) challenges your party to Top Bridge 2v2 \\(2v2\\)! Click to answer \\(or /duel accept ${leader.username}\\):`), 5000, from[2])
  await chatLine(other, /\[Accept\]\s+\[Deny\]/, 5000, from[2])
  await chatLine(otherMate, new RegExp(`${leader.username}'s party challenges your party to Top Bridge 2v2 \\(2v2\\): ${other.username} answers\\.`), 5000, from[3])
}

async function main () {
  const one = await arrive('RagoDuoUn')
  const two = await arrive('RagoDuoDeux')
  const three = await arrive('RagoDuoTrois')
  const four = await arrive('RagoDuoQuatre')
  await party(one, two)
  await party(three, four)

  // 1. only the leader
  let from = mark(two)
  two.chat('/duel')
  await chatLine(two, /Only the party leader can challenge another party\./, 5000, from)
  log('/duel of RagoDuoDeux, not a leader: refused')

  // 2. from the Party category of the compass, then denied
  let count = one.windows.length
  one.chat('/play party')
  const category = await windowAfter(one, count, 'Party', 'the Party category')
  const last = category.inventoryStart - 1
  await waitFor(() => category.slots[last] != null, 5000, 'the last slot of the Party category')
  if (category.slots[last].name !== 'iron_sword' || !text(category.slots[last]).includes('Party Duels')
      || !text(category.slots[last]).includes('Click to see the parties')) {
    throw new Error(`Party Duels in the last slot of the Party category: ${category.slots[last].name} ${text(category.slots[last])}`)
  }
  count = one.windows.length
  one.clickWindow(last, 0, 0).catch(() => {})
  let parties = await windowAfter(one, count, 'Party Duels', 'the parties of the server, from the compass')
  let modes = await chooseTheOtherParty(one, parties, three, four)
  await challengeTopBridge(one, two, three, four, modes)
  log('Party category: Party Duels, the party of RagoDuoTrois with its two players, Top Bridge 2v2 and only 2v2s, the challenge read by the four')
  from = mark(one)
  const fromThree = mark(three)
  three.chat(`/duel deny ${one.username}`)
  await chatLine(three, /Party duel request denied\./, 5000, fromThree)
  await chatLine(one, new RegExp(`${three.username} denied your party duel request\\.`), 5000, from)
  log('/duel deny RagoDuoUn: denied, RagoDuoUn told')

  // 3. from /duel, then accepted: the game
  count = one.windows.length
  one.chat('/duel')
  parties = await windowAfter(one, count, 'Party Duels', 'the parties of the server, from /duel')
  modes = await chooseTheOtherParty(one, parties, three, four)
  await challengeTopBridge(one, two, three, four, modes)
  const bots = [one, two, three, four]
  const marks = bots.map(mark)
  three.chat(`/duel accept ${one.username}`)
  for (const [i, bot] of bots.entries()) {
    await chatLine(bot, new RegExp(`Party duel: ${one.username}'s party against ${three.username}'s party in Top Bridge 2v2 \\(2v2\\)\\.`), 5000, marks[i])
  }
  const teams = []
  for (const [i, bot] of bots.entries()) {
    const welcome = await chatLine(bot, /Top Bridge 2v2 on .*: you are in the \S+ team\./, 30000, marks[i])
    teams.push(/you are in the (\S+) team\./.exec(welcome)[1])
  }
  if (teams[0] !== teams[1] || teams[2] !== teams[3] || teams[0] === teams[2]) {
    throw new Error(`the teams are not the parties: ${bots.map((bot, i) => `${bot.username} ${teams[i]}`).join(', ')}`)
  }
  log(`/duel accept RagoDuoUn: Top Bridge 2v2, RagoDuoUn and RagoDuoDeux (${teams[0]}) against RagoDuoTrois and RagoDuoQuatre (${teams[2]})`)
  for (const bot of [three, four]) {
    bot.chat('/spawn')
    await expectInLobby(bot, 'after /spawn in the party duel')
  }
  await expectInLobby(one, 'after the other party left the party duel')
  await expectInLobby(two, 'after the other party left the party duel')
  log('the party of RagoDuoTrois left with /spawn: all four back in the lobby')

  finishing = true
  log('PASSED: party duels: only the leader; Party Duels in the Party category and /duel, the other party with its players,'
    + ' the modes of the Duels for two parties of two (Top Bridge 2v2, only 2v2s), the challenge, denied, then accepted:'
    + ' Top Bridge 2v2, a party a team')
  for (const bot of bots) bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
