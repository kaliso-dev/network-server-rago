// Attacker bot of the kb-recorder end-to-end test (tools/kb-recorder/run-e2e-test.sh).
//
// RagoAttacker hits a real Minecraft 1.8.9 client that runs the kb-recorder mod, in two phases:
// the server's own knockback observed by the engine, then the engine's override with the
// control-alt test profile. Hits alternate between sprint and non-sprint, from random positions
// around the victim. RecorderCheck (tools/kb-control) then compares the client's recording with
// the engine's hit log.
//
// Every attack is written as an "attack" line in the same format as control-bots.mjs, so that
// ControlCheck's readers apply.
//
// RagoAttacker must be an operator: it runs the /kb, /tp and /effect commands.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565), KB_VICTIM (player name of the client, required),
// KB_ATTACKS (attacks.jsonl), KB_HITS_PER_PHASE (12), KB_SEED (189).

import fs from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const VICTIM = process.env.KB_VICTIM
const ATTACKS = process.env.KB_ATTACKS ?? 'attacks.jsonl'
const HITS_PER_PHASE = Number(process.env.KB_HITS_PER_PHASE ?? '12')
const SEED = Number(process.env.KB_SEED ?? '189')

const ATTACKER = 'RagoAttacker'
// Default superflat world: grass top at y = 3, so players stand at y = 4.
const GROUND_Y = 4
// A hit only counts fully 10 ticks after the previous one; a second apart also keeps each
// velocity packet far from the next hit.
const MIN_ATTACK_GAP_MS = 1000
// Between the teleports and the attack: the victim lands, and the client learns the attacker's
// sprint flag (entity metadata), which RecorderCheck compares with the server's.
const SETTLE_MS = 600
// The victim joined before the bot; 60 ticks of spawn invulnerability, plus a margin.
const WAIT_BEFORE_FIRST_HIT_TICKS = 100
const TEST_TIMEOUT_MS = 10 * 60 * 1000

const PHASES = [
  { name: 'observe-vanilla', commands: ['/kb mode observe', '/kb profile vanilla'] },
  { name: 'override-control-alt', commands: ['/kb mode override', '/kb profile control-alt'] }
]

if (!VICTIM) {
  console.error('KB_VICTIM must name the player to hit')
  process.exit(2)
}

const output = fs.createWriteStream(ATTACKS, { flags: 'w' })
let finishing = false

function record (event) {
  output.write(JSON.stringify(event) + '\n')
}

function closeOutput () {
  return new Promise((resolve) => output.end(resolve))
}

/** Deterministic random numbers in [0, 1), so that a run can be replayed with the same seed. */
function mulberry32 (seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6D2B79F5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const random = mulberry32(SEED)

function between (min, max) {
  return min + random() * (max - min)
}

async function waitFor (condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`)
    await sleep(20)
  }
}

let abortHandler = null

function abort (error) {
  if (abortHandler != null) abortHandler(error)
}

function join () {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username: ATTACKER, auth: 'offline', version: '1.8.8' })
    const fail = (reason) => reject(new Error(`${ATTACKER} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot.once('spawn', () => {
      bot.removeListener('error', fail)
      bot.removeListener('kicked', fail)
      bot.on('error', (error) => abort(new Error(`${ATTACKER}: ${error.message}`)))
      bot.on('kicked', (reason) => abort(new Error(`${ATTACKER} was kicked: ${JSON.stringify(reason)}`)))
      bot.on('end', (reason) => {
        if (!finishing) abort(new Error(`${ATTACKER} disconnected: ${reason}`))
      })
      resolve(bot)
    })
  })
}

/** Sends a command and waits for a chat line that matches {@code reply}. */
function command (bot, text, reply, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const seen = []
    const onMessage = (message) => {
      seen.push(message)
      if (reply.test(message)) {
        cleanup()
        resolve(message)
      }
    }
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error(`no reply matching ${reply} to "${text}"; chat: ${JSON.stringify(seen)}`))
    }, timeoutMs)
    const cleanup = () => {
      clearTimeout(timer)
      bot.removeListener('messagestr', onMessage)
    }
    bot.on('messagestr', onMessage)
    bot.chat(text)
  })
}

function victimEntity (bot) {
  return Object.values(bot.entities).find((entity) => entity.type === 'player' && entity.username === VICTIM)
}

// Coordinates always carry a decimal point: /tp adds 0.5 to whole x and z numbers.
function coordinate (value) {
  return value.toFixed(4)
}

function tp (bot, name, x, z, yaw, pitch) {
  bot.chat(`/tp ${name} ${coordinate(x)} ${coordinate(GROUND_Y)} ${coordinate(z)} ${yaw.toFixed(3)} ${pitch.toFixed(3)}`)
}

let lastAttack = 0

async function hit (bot, phase, index) {
  const sprint = index % 2 === 1
  const victimX = 0.5 + between(-3, 3)
  const victimZ = 0.5 + between(-3, 3)
  const angle = between(0, 2 * Math.PI)
  const distance = between(1.5, 2.8)
  const attackerX = victimX + Math.cos(angle) * distance
  const attackerZ = victimZ + Math.sin(angle) * distance
  // Minecraft yaw: 0 looks towards +z, 90 towards -x. Look roughly at the victim, up to 30 degrees off.
  const towardsVictim = Math.atan2(-(victimX - attackerX), victimZ - attackerZ) * 180 / Math.PI
  const attackerYaw = towardsVictim + between(-30, 30)

  tp(bot, VICTIM, victimX, victimZ, between(-180, 180), 0)
  // Entity positions travel in 1/32 block.
  await waitFor(() => {
    const victim = victimEntity(bot)
    return victim != null && Math.abs(victim.position.x - victimX) < 0.05 &&
      Math.abs(victim.position.y - GROUND_Y) < 0.05 && Math.abs(victim.position.z - victimZ) < 0.05
  }, 5000, `${VICTIM} to reach ${coordinate(victimX)} ${GROUND_Y} ${coordinate(victimZ)}`)
  tp(bot, ATTACKER, attackerX, attackerZ, attackerYaw, between(-20, 20))
  await waitFor(() => {
    const position = bot.entity.position
    return Math.abs(position.x - attackerX) < 1e-3 && Math.abs(position.y - GROUND_Y) < 1e-3 &&
      Math.abs(position.z - attackerZ) < 1e-3
  }, 3000, `${ATTACKER} to reach ${coordinate(attackerX)} ${GROUND_Y} ${coordinate(attackerZ)}`)

  // The server ends a sprint on every sprint hit without telling the bot, so a sprint hit always
  // sends a fresh start_sprinting.
  bot.setControlState('sprint', false)
  if (sprint) bot.setControlState('sprint', true)
  await sleep(SETTLE_MS)

  const wait = lastAttack + MIN_ATTACK_GAP_MS - Date.now()
  if (wait > 0) await sleep(wait)
  const target = victimEntity(bot)
  if (target == null) throw new Error(`the attacker does not see ${VICTIM}`)
  bot.attack(target)
  lastAttack = Date.now()
  record({
    type: 'attack',
    time: lastAttack,
    phase: phase.name,
    index,
    sprint,
    enchantment: 0,
    victim: 'stand',
    distance: Number(distance.toFixed(4))
  })
  await sleep(200)
}

async function main () {
  const failed = new Promise((resolve, reject) => { abortHandler = reject })
  failed.catch(() => {})
  const watchdog = setTimeout(() => abort(new Error('the attacker took longer than 10 minutes')), TEST_TIMEOUT_MS)

  const run = async () => {
    const bot = await join()
    console.log(`joined: ${ATTACKER} entity ${bot.entity.id}`)
    await waitFor(() => bot.players[VICTIM] != null, 10000, `${VICTIM} in the player list`)
    await waitFor(() => bot.time.age != null, 5000, 'the world age')
    const joinAge = bot.time.age

    // Resistance V: the victim takes no damage, so it never dies during the test. Damage does
    // not change knockback, and a hit still needs the usual cooldown.
    await command(bot, `/effect ${VICTIM} 11 1000000 4 true`, /Given|Resistance|commands\.effect/)
    tp(bot, ATTACKER, 3.5, 0.5, 90, 0)
    await waitFor(() => victimEntity(bot) != null, 10000, `the attacker to see ${VICTIM}`)
    await waitFor(() => bot.time.age >= joinAge + WAIT_BEFORE_FIRST_HIT_TICKS, 15000,
      'the end of the victim spawn invulnerability')

    record({ type: 'start', time: Date.now(), victim: VICTIM, attacker: ATTACKER })
    for (const phase of PHASES) {
      for (const text of phase.commands) {
        await command(bot, text, /^OK : /)
      }
      record({ type: 'phase', time: Date.now(), name: phase.name })
      console.log(`phase ${phase.name}: ${HITS_PER_PHASE} hits`)
      for (let index = 0; index < HITS_PER_PHASE; index++) {
        await hit(bot, phase, index)
      }
    }
    // Leave time for the last packets to reach the client.
    await sleep(1000)
    record({ type: 'done', time: Date.now() })
    finishing = true
    bot.quit()
  }

  try {
    await Promise.race([run(), failed])
  } finally {
    clearTimeout(watchdog)
  }
}

main().then(async () => {
  await closeOutput()
  console.log(`attacker finished, attacks written to ${ATTACKS}`)
  process.exit(0)
}, async (error) => {
  console.error(`attacker failed: ${error.stack ?? error}`)
  record({ type: 'error', time: Date.now(), message: String(error.message ?? error) })
  await closeOutput()
  process.exit(1)
})
