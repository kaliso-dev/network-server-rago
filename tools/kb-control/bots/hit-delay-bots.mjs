// Hit delay test bots for RagoCombatEngine (see ../README.md).
//
// RagoAttacker hits RagoVictim twice per tick for a few seconds, a "burst", with the engine's
// hit delay set by /kb hitdelay: the vanilla 10 ticks, then 9, then 9 again after the victim died
// and respawned (vanilla makes a new player entity then, with the default cooldown; the engine
// sets the cooldown again whatever the server does). HitDelayCheck (tools/kb-control) reads the bursts and the engine's
// hit log and checks that the hits of a burst are exactly the delay apart, in server ticks.
//
// The engine runs in override mode with the "still" test profile, which pushes the victim by
// nothing, so the victim stays in reach for the whole burst.
//
// RagoAttacker must be an operator. Environment: KB_HOST (127.0.0.1), KB_PORT (25565),
// KB_BURSTS (bursts.jsonl), KB_BURST_MS (3000).

import fs from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const BURSTS = process.env.KB_BURSTS ?? 'bursts.jsonl'
const BURST_MS = Number(process.env.KB_BURST_MS ?? '3000')

const ATTACKER = 'RagoAttacker'
const VICTIM = 'RagoVictim'
const GROUND_Y = 4
// Twice per tick, so that every tick of the burst has an attack to count.
const ATTACK_EVERY_MS = 25
// A player cannot be hurt for 60 ticks after joining or respawning (1.8.8); wait a little more.
const INVULNERABLE_WAIT_MS = 4000
const TEST_TIMEOUT_MS = 5 * 60 * 1000

const BURST_PLAN = [
  { name: 'vanilla-delay', delayTicks: 10, respawnFirst: false },
  { name: 'delay-9', delayTicks: 9, respawnFirst: false },
  { name: 'delay-9-after-respawn', delayTicks: 9, respawnFirst: true }
]

const output = fs.createWriteStream(BURSTS, { flags: 'w' })
let finishing = false

function record (event) {
  output.write(JSON.stringify(event) + '\n')
}

function closeOutput () {
  return new Promise((resolve) => output.end(resolve))
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

function join (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, auth: 'offline', version: '1.8.8' })
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
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

async function teleport (commander, bot, x, z, yaw) {
  commander.chat(`/tp ${bot.username} ${x.toFixed(4)} ${GROUND_Y.toFixed(4)} ${z.toFixed(4)} ${yaw.toFixed(3)} 0.000`)
  await waitFor(() => {
    const position = bot.entity.position
    return Math.abs(position.x - x) < 1e-3 && Math.abs(position.y - GROUND_Y) < 1e-3 && Math.abs(position.z - z) < 1e-3
  }, 3000, `${bot.username} to reach ${x} ${GROUND_Y} ${z}`)
}

/** Resistance V: the victim takes no damage, and a hit still needs the usual cooldown. */
async function protect (attacker, victim) {
  attacker.chat(`/effect ${VICTIM} 11 1000000 4 true`)
  await waitFor(() => victim.entity.effects?.[11] != null, 5000, 'the resistance effect on the victim')
}

async function placeFacing (attacker, victim) {
  // Victim at the origin, attacker 2 blocks north looking south at it.
  await teleport(attacker, victim, 0.5, 2.5, 180)
  await teleport(attacker, attacker, 0.5, 0.5, 0)
  await sleep(200)
}

async function respawnVictim (attacker, victim) {
  const respawned = new Promise((resolve) => victim.once('spawn', resolve))
  attacker.chat(`/kill ${VICTIM}`)
  // mineflayer answers the death screen with a respawn request by itself.
  await Promise.race([respawned, sleep(10000).then(() => { throw new Error('the victim did not respawn') })])
  // The world spawn of a flat world can be far from the attacker, out of its tracking range:
  // bring the victim back first.
  await sleep(500)
  await placeFacing(attacker, victim)
  await waitFor(() => attacker.entities[victim.entity.id] != null, 10000, 'the attacker to see the respawned victim')
  await sleep(INVULNERABLE_WAIT_MS)
  await protect(attacker, victim)
}

async function burst (attacker, victim, plan) {
  await command(attacker, `/kb hitdelay ${plan.delayTicks}`, /^OK : /)
  await placeFacing(attacker, victim)
  const target = attacker.entities[victim.entity.id]
  if (target == null) throw new Error('the attacker does not see the victim entity')
  const start = Date.now()
  while (Date.now() - start < BURST_MS) {
    // No arm swing: the server does not need it to count the attack, and it halves the packets.
    attacker.attack(target, false)
    await sleep(ATTACK_EVERY_MS)
  }
  const end = Date.now()
  record({
    type: 'burst',
    name: plan.name,
    delayTicks: plan.delayTicks,
    victimEntityId: victim.entity.id,
    start,
    end,
    afterRespawn: plan.respawnFirst
  })
  console.log(`burst ${plan.name}: ${plan.delayTicks} ticks, ${end - start} ms`)
  // Let the cooldown of the last hit run out before the next burst.
  await sleep(1500)
}

async function main () {
  const failed = new Promise((resolve, reject) => { abortHandler = reject })
  failed.catch(() => {})
  const watchdog = setTimeout(() => abort(new Error('hit delay test took longer than 5 minutes')), TEST_TIMEOUT_MS)

  const run = async () => {
    const attacker = await join(ATTACKER)
    const victim = await join(VICTIM)
    console.log(`joined: ${ATTACKER} entity ${attacker.entity.id}, ${VICTIM} entity ${victim.entity.id}`)
    await waitFor(() => attacker.entities[victim.entity.id] != null, 10000, 'the attacker to see the victim')
    await protect(attacker, victim)
    await sleep(INVULNERABLE_WAIT_MS)
    await command(attacker, '/kb mode override', /^OK : /)
    await command(attacker, '/kb profile still', /^OK : /)
    for (const plan of BURST_PLAN) {
      if (plan.respawnFirst) await respawnVictim(attacker, victim)
      await burst(attacker, victim, plan)
    }
    record({ type: 'done', time: Date.now() })
    finishing = true
    attacker.quit()
    victim.quit()
  }

  try {
    await Promise.race([run(), failed])
  } finally {
    clearTimeout(watchdog)
  }
}

main().then(async () => {
  await closeOutput()
  console.log(`hit delay bots finished, bursts written to ${BURSTS}`)
  process.exit(0)
}, async (error) => {
  console.error(`hit delay bots failed: ${error.stack ?? error}`)
  record({ type: 'error', time: Date.now(), message: String(error.message ?? error) })
  await closeOutput()
  process.exit(1)
})
