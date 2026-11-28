// Block Pattern Practice test bot for RagoPractice (see tools/lobby-check/README.md, run-arena-test.sh).
//
// The owner's ask of 27/11: the techniques of his video Block Method, RagoBot running at the
// player. One bot plays the training:
//   1. #1 Block in front: it places the block in front of it (the light blue dust shows it), the
//      training says the pattern is built; RagoBot comes, and the bot hits it first: done, and #2
//      comes by itself;
//   2. #2 Pillar: it places nothing; RagoBot runs at it and hits it first: #2 again;
//   3. #10 Tunnel (the red dye, from #1 back to the last): the tunnel is there around the bot.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565).

import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const TEST_TIMEOUT_MS = 3 * 60 * 1000

let finishing = false
let Vec3 = null

function abort (error) {
  console.error(`[pattern-bots] FAILED: ${error.message}`)
  process.exit(1)
}

setTimeout(() => abort(new Error('the test took too long')), TEST_TIMEOUT_MS).unref()

function log (text) {
  console.log(`[pattern-bots] ${text}`)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`)
    await sleep(20)
  }
}

const chats = []

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    bot.firstMenu = null
    bot.on('windowOpen', (window) => {
      const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
      if (title.includes('What do you want to do?')) bot.firstMenu = window
    })
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.on('messagestr', (message) => {
      chats.push(message)
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

async function chatLine (pattern, timeoutMs, from) {
  await waitFor(() => chats.slice(from).some((line) => pattern.test(line)), timeoutMs,
    `to read ${pattern}; chat: ${JSON.stringify(chats.slice(from))}`)
}

function hotbarItem (bot, slot) {
  return bot.inventory.slots[bot.inventory.hotbarStart + slot]
}

function hotbarSlot (bot, name, metadata) {
  for (let i = 0; i < 9; i++) {
    const item = hotbarItem(bot, i)
    if (item?.name === name && (metadata == null || item.metadata === metadata)) return i
  }
  return -1
}

async function expectInLobby (bot, what) {
  await waitFor(() => bot.game.gameMode === 'adventure' && hotbarItem(bot, 0)?.name === 'compass', 15000,
    `${bot.username} in the lobby (${what}): mode ${bot.game.gameMode}, slot 0 ${hotbarItem(bot, 0)?.name}`)
}

function blockText (block) {
  return block == null ? 'not loaded' : `${block.name}:${block.metadata}`
}

/** RagoBot: the zombie of the training nearest to the bot, or null. */
function ragoBot (bot) {
  let best = null
  for (const entity of Object.values(bot.entities)) {
    if (entity === bot.entity || !/zombie/i.test(entity.name ?? entity.displayName ?? '')) continue
    if (best == null || entity.position.distanceTo(bot.entity.position) < best.position.distanceTo(bot.entity.position)) best = entity
  }
  return best
}

async function main () {
  const bot = await join('RagoMotif')
  Vec3 = bot.entity.position.constructor
  await waitFor(() => bot.firstMenu != null, 8000, 'the menu of the first arrival')
  bot.closeWindow(bot.firstMenu)
  await sleep(200)
  await expectInLobby(bot, 'on joining')

  // 1. #1 Block in front: the block, then the first hit
  let from = chats.length
  bot.chat('/queue block-pattern-practice')
  await chatLine(/#1 Block in front: place a block in front of you/, 15000, from)
  await waitFor(() => bot.game.gameMode === 'survival', 5000, 'survival mode in the training')
  await sleep(300)
  const feet = bot.entity.position.floored()
  log(`at ${feet}, facing yaw ${bot.entity.yaw.toFixed(2)}`)
  const wool = hotbarSlot(bot, 'wool', 3)
  if (wool < 0) throw new Error('no light blue wool in the hotbar')
  bot.setQuickBarSlot(wool)
  await sleep(100)
  // forward is +z (south): the block two ahead, on the floor
  const target = feet.offset(0, 0, 2)
  const floor = feet.offset(0, -1, 2)
  if (bot.blockAt(floor)?.name !== 'stained_hardened_clay') throw new Error(`no floor at ${floor}: ${blockText(bot.blockAt(floor))}`)
  from = chats.length
  await bot._placeBlockWithOptions(bot.blockAt(floor), new Vec3(0, 1, 0), { swingArm: 'right', forceLook: true })
  await waitFor(() => bot.blockAt(target)?.name === 'wool', 1500, `wool at ${target} (${blockText(bot.blockAt(target))})`)
  await chatLine(/Pattern built!/, 2000, from)
  log('pattern #1 built')
  const sword = hotbarSlot(bot, 'stone_sword')
  if (sword >= 0) bot.setQuickBarSlot(sword)
  // RagoBot comes: hit it once in reach
  let hits = 0
  await waitFor(() => {
    const rago = ragoBot(bot)
    if (rago != null && rago.position.distanceTo(bot.entity.position.offset(0, 0, 0)) < 3.2) {
      bot.lookAt(rago.position.offset(0, 1.4, 0), true)
      bot.attack(rago)
      hits++
    }
    return chats.slice(from).some((line) => /#1 Block in front done!|RagoBot hit you first/.test(line))
  }, 12000, 'RagoBot within reach, then hit first')
  if (chats.slice(from).some((line) => /RagoBot hit you first/.test(line))) {
    throw new Error(`RagoBot hit first in #1, after ${hits} clicks of the bot; chat: ${JSON.stringify(chats.slice(from))}`)
  }
  log(`#1 done after ${hits} clicks`)
  await chatLine(/#2 Pillar: a pillar of two blocks/, 4000, from)
  log('#2 came by itself')

  // 2. #2 Pillar, nothing placed: RagoBot runs at the bot and hits it first
  from = chats.length
  const before = ragoBot(bot)?.position?.clone()
  await chatLine(/RagoBot hit you first\. #2 Pillar again\./, 10000, from)
  log(`RagoBot came from ${before} and hit first: #2 again`)
  await chatLine(/#2 Pillar: a pillar of two blocks/, 3000, from)

  // 3. back to #1, then back again to the last, #10 Tunnel: its tunnel is there
  for (const expected of [/#1 Block in front: place a block/, /#10 Tunnel: in a tunnel/]) {
    from = chats.length
    const red = hotbarSlot(bot, 'dye', 1)
    if (red < 0) throw new Error('no red dye in the hotbar')
    bot.setQuickBarSlot(red)
    await sleep(100)
    bot.activateItem()
    await sleep(50)
    bot.deactivateItem()
    await chatLine(expected, 4000, from)
  }
  await sleep(300)
  const here = bot.entity.position.floored()
  const walls = [here.offset(1, 0, 4), here.offset(-1, 1, 4), here.offset(0, 2, 4)].map((v) => blockText(bot.blockAt(v)))
  if (walls.some((b) => !b.startsWith('stained_hardened_clay'))) throw new Error(`no tunnel around the bot at ${here}: ${walls}`)
  log(`the tunnel of #10 is there: ${walls}`)

  finishing = true
  log('PASSED: the pattern built then the first hit, the next technique by itself, RagoBot's run and hit, the tunnel')
  bot.quit()
  await sleep(500)
  process.exit(0)
}

main().catch(abort)
