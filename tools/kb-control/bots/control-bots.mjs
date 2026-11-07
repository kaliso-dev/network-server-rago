// Control-test bots for RagoCombatEngine (see ../README.md).
//
// RagoAttacker hits RagoVictim on a local 1.8.8 test server in four phases: the server's own
// knockback observed by the engine, the engine's override with the vanilla profile, the override
// with the control-alt test profile, and the vanilla profile with the ping simulation at 150 ms,
// which holds each knockback packet back (both bots have about 0 ms of ping here). Each phase mixes sprint and non-sprint hits, bare
// hand and Knockback I/II sticks, and a victim standing, walking or in the middle of a jump.
//
// Both bots write to a JSON-lines file every velocity packet the server sends them for their own
// entity, plus one "attack" line per attack. ControlCheck (tools/kb-control) pairs those lines
// with the engine's hit log and checks them against rago-combat-model.
//
// RagoAttacker must be an operator: it runs the /kb, /tp, /effect and /replaceitem commands.
//
// Environment: KB_HOST (127.0.0.1), KB_PORT (25565), KB_RECEIVED (received.jsonl),
// KB_HITS_PER_PHASE (36), KB_SEED (188).

import fs from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'
import mineflayer from 'mineflayer'

const HOST = process.env.KB_HOST ?? '127.0.0.1'
const PORT = Number(process.env.KB_PORT ?? '25565')
const RECEIVED = process.env.KB_RECEIVED ?? 'received.jsonl'
const HITS_PER_PHASE = Number(process.env.KB_HITS_PER_PHASE ?? '36')
const SEED = Number(process.env.KB_SEED ?? '188')

const ATTACKER = 'RagoAttacker'
const VICTIM = 'RagoVictim'
// Default superflat world: grass top at y = 3, so players stand at y = 4.
const GROUND_Y = 4
// A hit only counts fully 10 ticks after the previous one. Keeping attacks a second apart also
// keeps each velocity packet far from the next hit, so pairing packets with hits is unambiguous.
const MIN_ATTACK_GAP_MS = 1000
// A player who just joined cannot be hurt for 60 ticks (EntityPlayer.invulnerableTicks in 1.8.8):
// an attack in that window gives no hit at all, so the first phase waits for it to end. The
// server sends the world age on join and then every 20 ticks.
const SPAWN_INVULNERABILITY_TICKS = 60
const TIME_UPDATE_TICKS = 20
const TEST_TIMEOUT_MS = 15 * 60 * 1000

const PHASES = [
  { name: 'observe-vanilla', commands: ['/kb mode observe', '/kb profile vanilla'] },
  { name: 'override-vanilla', commands: ['/kb mode override', '/kb profile vanilla'] },
  { name: 'override-control-alt', commands: ['/kb mode override', '/kb profile control-alt'] },
  { name: 'override-ping-simulation', commands: ['/kb mode override', '/kb profile vanilla', '/kb pingsim 150'] }
]

// Hotbar slots: 0 is left empty (bare hand), 1 and 2 hold Knockback I and II sticks.
const KNOCKBACK_STICKS = [
  { slot: 1, level: 1 },
  { slot: 2, level: 2 }
]

// The first hits of every phase cover each case once, whatever the random draw gives afterwards.
const FIXED_SCENARIOS = [
  { sprint: false, enchantment: 0, victim: 'stand' },
  { sprint: true, enchantment: 0, victim: 'stand' },
  { sprint: false, enchantment: 1, victim: 'jump' },
  { sprint: true, enchantment: 2, victim: 'jump' },
  { sprint: false, enchantment: 0, victim: 'walk' },
  { sprint: true, enchantment: 1, victim: 'walk' }
]

const output = fs.createWriteStream(RECEIVED, { flags: 'w' })
let recording = false
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

function pick (weighted) {
  let roll = random() * weighted.reduce((sum, [, weight]) => sum + weight, 0)
  for (const [value, weight] of weighted) {
    roll -= weight
    if (roll < 0) return value
  }
  return weighted[weighted.length - 1][0]
}

function scenario (index) {
  if (index < FIXED_SCENARIOS.length) return FIXED_SCENARIOS[index]
  return {
    sprint: random() < 0.5,
    enchantment: pick([[0, 6], [1, 2], [2, 2]]),
    victim: pick([['stand', 4], ['walk', 2], ['jump', 4]])
  }
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
    const fail = (reason) => reject(new Error(`${username} could not join: ${reason?.message ?? JSON.stringify(reason)}`))
    bot.once('error', fail)
    bot.once('kicked', fail)
    bot._client.on('entity_velocity', (packet) => {
      if (bot.entity == null || packet.entityId !== bot.entity.id) return
      record({
        // Packets received before the first phase are kept for the record but not checked.
        type: recording ? 'velocity' : 'velocity-before-test',
        time: Date.now(),
        bot: bot.username,
        entityId: packet.entityId,
        packet: [packet.velocity.x, packet.velocity.y, packet.velocity.z]
      })
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

// Coordinates always carry a decimal point: /tp adds 0.5 to whole x and z numbers.
function coordinate (value) {
  return value.toFixed(4)
}

async function teleport (commander, bot, x, z, yaw, pitch) {
  commander.chat(`/tp ${bot.username} ${coordinate(x)} ${coordinate(GROUND_Y)} ${coordinate(z)} ` +
    `${yaw.toFixed(3)} ${pitch.toFixed(3)}`)
  await waitFor(() => {
    const position = bot.entity.position
    return Math.abs(position.x - x) < 1e-3 && Math.abs(position.y - GROUND_Y) < 1e-3 && Math.abs(position.z - z) < 1e-3
  }, 3000, `${bot.username} to reach ${coordinate(x)} ${GROUND_Y} ${coordinate(z)}`)
}

async function prepare (attacker, victim) {
  // Resistance V: the victim takes no damage, so it never dies during the test. Damage does not
  // change knockback, and a hit still needs the usual cooldown.
  attacker.chat(`/effect ${VICTIM} 11 1000000 4 true`)
  await waitFor(() => victim.entity.effects?.[11] != null, 5000, 'the resistance effect on the victim')
  for (const stick of KNOCKBACK_STICKS) {
    attacker.chat(`/replaceitem entity ${ATTACKER} slot.hotbar.${stick.slot} minecraft:stick 1 0 ` +
      `{ench:[{id:19,lvl:${stick.level}}]}`)
  }
  await waitFor(() => KNOCKBACK_STICKS.every((stick) => {
    const item = attacker.inventory.slots[attacker.inventory.hotbarStart + stick.slot]
    return item?.name === 'stick' && item.enchants.some((e) => e.name === 'knockback' && e.lvl === stick.level)
  }), 5000, 'the Knockback sticks in the attacker hotbar')
  if (attacker.inventory.slots[attacker.inventory.hotbarStart] != null) {
    throw new Error('hotbar slot 0 of the attacker must be empty (bare hand hits)')
  }
}

let lastAttack = 0

async function hit (attacker, victim, phase, index) {
  const s = scenario(index)
  const victimX = 0.5 + between(-4, 4)
  const victimZ = 0.5 + between(-4, 4)
  const angle = between(0, 2 * Math.PI)
  const distance = between(0.8, 2.9)
  const attackerX = victimX + Math.cos(angle) * distance
  const attackerZ = victimZ + Math.sin(angle) * distance
  // Minecraft yaw: 0 looks towards +z, 90 towards -x. Look roughly at the victim, up to 50 degrees off,
  // so the sprint push is not always in line with the base push.
  const towardsVictim = Math.atan2(-(victimX - attackerX), victimZ - attackerZ) * 180 / Math.PI
  const attackerYaw = towardsVictim + between(-50, 50)
  const attackerPitch = between(-30, 30)
  const victimYaw = between(-180, 180)

  await teleport(attacker, victim, victimX, victimZ, victimYaw, 0)
  await teleport(attacker, attacker, attackerX, attackerZ, attackerYaw, attackerPitch)
  // Let both clients confirm the teleport and land: three ticks.
  await sleep(150)

  attacker.setQuickBarSlot(s.enchantment === 0 ? 0 : KNOCKBACK_STICKS[s.enchantment - 1].slot)
  // The server ends a sprint on every sprint hit without telling the client, so a sprint hit
  // always sends a fresh start_sprinting.
  attacker.setControlState('sprint', false)
  if (s.sprint) attacker.setControlState('sprint', true)

  let airTicks = 0
  if (s.victim === 'walk') {
    victim.setControlState('forward', true)
    await sleep(250)
  } else if (s.victim === 'jump') {
    await waitFor(() => victim.entity.onGround, 2000, 'the victim to stand on the ground before jumping')
    victim.setControlState('jump', true)
    airTicks = 2 + Math.floor(random() * 8)
    await sleep(airTicks * 50)
  } else {
    await sleep(100)
  }

  const wait = lastAttack + MIN_ATTACK_GAP_MS - Date.now()
  if (wait > 0) await sleep(wait)
  const target = attacker.entities[victim.entity.id]
  if (target == null) throw new Error('the attacker does not see the victim entity')
  attacker.attack(target)
  lastAttack = Date.now()
  record({
    type: 'attack',
    time: lastAttack,
    phase: phase.name,
    index,
    sprint: s.sprint,
    enchantment: s.enchantment,
    victim: s.victim,
    airTicks,
    distance: Number(distance.toFixed(4))
  })

  await sleep(100)
  victim.clearControlStates()
}

let abortHandler = null

function abort (error) {
  if (abortHandler != null) abortHandler(error)
}

async function main () {
  const failed = new Promise((resolve, reject) => { abortHandler = reject })
  failed.catch(() => {})
  const watchdog = setTimeout(() => abort(new Error('control test took longer than 15 minutes')), TEST_TIMEOUT_MS)

  const run = async () => {
    const attacker = await join(ATTACKER)
    const victim = await join(VICTIM)
    console.log(`joined: ${ATTACKER} entity ${attacker.entity.id}, ${VICTIM} entity ${victim.entity.id}`)
    await waitFor(() => victim.time.age != null, 5000, 'the world age')
    const victimJoinAge = victim.time.age
    await waitFor(() => attacker.entities[victim.entity.id] != null, 10000, 'the attacker to see the victim')
    await prepare(attacker, victim)
    // One more time update as a margin, since the known world age is refreshed every 20 ticks.
    await waitFor(() => victim.time.age >= victimJoinAge + SPAWN_INVULNERABILITY_TICKS + TIME_UPDATE_TICKS,
      15000, 'the end of the victim spawn invulnerability')

    recording = true
    for (const phase of PHASES) {
      for (const text of phase.commands) {
        await command(attacker, text, /^OK : /)
      }
      record({ type: 'phase', time: Date.now(), name: phase.name })
      console.log(`phase ${phase.name}: ${HITS_PER_PHASE} hits`)
      for (let index = 0; index < HITS_PER_PHASE; index++) {
        await hit(attacker, victim, phase, index)
      }
    }
    // The hit delay bots that run next expect knockback without the ping simulation.
    await command(attacker, '/kb pingsim 0', /^OK : /)
    // Leave time for the last velocity packet to arrive.
    await sleep(1000)
    recording = false
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
  console.log(`control bots finished, packets written to ${RECEIVED}`)
  process.exit(0)
}, async (error) => {
  console.error(`control bots failed: ${error.stack ?? error}`)
  record({ type: 'error', time: Date.now(), message: String(error.message ?? error) })
  await closeOutput()
  process.exit(1)
})
