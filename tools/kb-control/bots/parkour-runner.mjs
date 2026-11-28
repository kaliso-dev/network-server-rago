// A bot that runs a parkour course of blocks, for the test of the Parkour Duel (fun-bots.mjs).
//
// It knows nothing of the course: from the blocks it stands on (its platform), the next one is the
// nearest block ahead (+x), at most a block higher or lower, with room above it. Before each jump
// it tries, with mineflayer's own physics (prismarine-physics) on the blocks it sees, every way of
// making it from where it stands: ticks of run before the jump, sprint or not, ticks holding
// forward in the air, ticks of braking after landing. It keeps the way whose neighbours (a tick
// earlier or later) land too, then plays it tick by tick: the same physics, so the same jump.

import physicsModule from 'prismarine-physics'
import vec3Module from 'vec3'

const { Physics, PlayerState } = physicsModule
const { Vec3 } = vec3Module

const KEYS = ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak']
/** Ticks the bot must stay on the block it landed on, its controls released, for a jump to count (or stand still). */
const STAY_TICKS = 30
/** How far ahead the next block may be (blocks of air, plus one). */
const AHEAD = 5

function noControls () {
  return { forward: false, back: false, left: false, right: false, jump: false, sprint: false, sneak: false }
}

/** True if the block at x, y, z is a whole block to stand on (not air, not a plate). */
export function solidAt (world, x, y, z) {
  const block = world.getBlock(new Vec3(x, y, z))
  return block != null && block.boundingBox === 'block'
}

function key (x, y, z) {
  return `${x},${y},${z}`
}

/** The block a player at p stands on: the one under their middle, else one under their sides; null in the air. */
export function standsOn (world, p) {
  const y = Math.floor(p.y - 0.01)
  if (solidAt(world, Math.floor(p.x), y, Math.floor(p.z))) return { x: Math.floor(p.x), y, z: Math.floor(p.z) }
  for (const dx of [-0.299, 0.299]) {
    for (const dz of [-0.299, 0.299]) {
      const b = { x: Math.floor(p.x + dx), y, z: Math.floor(p.z + dz) }
      if (solidAt(world, b.x, b.y, b.z)) return b
    }
  }
  return null
}

/** The blocks joined to this one at its height, within the lane (zMin to zMax): a pad, a strip, a block. */
export function platformOf (world, block, lane) {
  const seen = new Map([[key(block.x, block.y, block.z), block]])
  const todo = [block]
  while (todo.length > 0 && seen.size < 80) {
    const b = todo.pop()
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const n = { x: b.x + dx, y: b.y, z: b.z + dz }
      if (n.z < lane.zMin || n.z > lane.zMax || seen.has(key(n.x, n.y, n.z)) || !solidAt(world, n.x, n.y, n.z)) continue
      seen.set(key(n.x, n.y, n.z), n)
      todo.push(n)
    }
  }
  return seen
}

/** The next block of the course after this platform: the nearest ahead, then the nearest to the middle of the lane. */
export function nextTarget (world, platform, lane) {
  let ex = -Infinity
  let y0 = 0
  for (const b of platform.values()) {
    ex = Math.max(ex, b.x)
    y0 = b.y
  }
  const middle = (lane.zMin + lane.zMax) / 2
  const zs = []
  for (let z = lane.zMin; z <= lane.zMax; z++) zs.push(z)
  zs.sort((a, b) => Math.abs(a + 0.5 - middle - 0.5) - Math.abs(b + 0.5 - middle - 0.5))
  for (let x = ex + 1; x <= ex + AHEAD; x++) {
    for (const y of [y0 + 1, y0, y0 - 1]) {
      for (const z of zs) {
        if (solidAt(world, x, y, z) && !solidAt(world, x, y + 1, z) && !solidAt(world, x, y + 2, z) &&
            world.getBlock(new Vec3(x, y + 1, z)) != null) {
          return { x, y, z }
        }
      }
    }
  }
  return null
}

/** The controls of this tick of a jump (and the look, on e), as the plan says. */
function decide (e, tick, combo, aim, mem) {
  if (mem.landed < 0) e.yaw = Math.atan2(-(aim.x - e.position.x), -(aim.z - e.position.z))
  const c = noControls()
  if (mem.jumped < 0) {
    c.forward = true
    c.sprint = combo.sprint
    if (tick >= combo.run) {
      c.jump = true
      mem.jumped = tick
    }
  } else if (mem.landed < 0) {
    c.forward = tick - mem.jumped < combo.air
    c.sprint = combo.sprint
  } else {
    c.back = tick - mem.landed < combo.brake
  }
  return c
}

/**
 * What a tick just played means: 'go' on, 'fail', or 'done' (landed on the platform and stayed
 * there). Sets mem.landed when the player touches the ground after the jump.
 */
function judge (world, player, tick, mem, target, platform) {
  const e = player.entity
  if (mem.jumped < 0 || tick === mem.jumped) return e.onGround || tick === mem.jumped ? 'go' : 'fail'
  if (mem.landed < 0) {
    if (tick > mem.jumped + 1 && e.onGround) {
      const on = standsOn(world, e.position)
      if (on == null || !platform.has(key(on.x, on.y, on.z))) return 'fail'
      mem.landed = tick + 1
      return 'go'
    }
    return e.position.y < target.y - 3 ? 'fail' : 'go'
  }
  if (e.position.y < target.y + 0.9) return 'fail'
  const still = e.onGround && e.velocity.x === 0 && e.velocity.z === 0 && tick - mem.landed >= mem.brake + 2
  if (still || tick - mem.landed + 1 >= STAY_TICKS) {
    const on = standsOn(world, e.position)
    return on != null && platform.has(key(on.x, on.y, on.z)) ? 'done' : 'fail'
  }
  return 'go'
}

/** A copy of what the physics reads of the bot, to try a jump on. */
function copyOf (bot) {
  const e = bot.entity
  return {
    entity: {
      position: e.position.clone(),
      velocity: e.velocity.clone(),
      onGround: e.onGround,
      isInWater: false,
      isInLava: false,
      isInWeb: false,
      isCollidedHorizontally: e.isCollidedHorizontally,
      isCollidedVertically: e.isCollidedVertically,
      elytraFlying: false,
      yaw: e.yaw,
      pitch: e.pitch,
      effects: e.effects,
      attributes: e.attributes
    },
    jumpTicks: bot.jumpTicks,
    jumpQueued: bot.jumpQueued,
    fireworkRocketDuration: 0,
    version: bot.version,
    inventory: bot.inventory
  }
}

export function createRunner (registry, world) {
  const physics = Physics(registry, world)

  /** True if this way of jumping, from the bot as it stands, lands on the platform of the target and stays there. */
  function lands (bot, combo, target, platform) {
    const player = copyOf(bot)
    const aim = { x: target.x + 0.5, z: target.z + 0.5 }
    const mem = { jumped: -1, landed: -1, brake: combo.brake }
    for (let tick = 0; tick < 120; tick++) {
      const controls = decide(player.entity, tick, combo, aim, mem)
      physics.simulatePlayer(new PlayerState(player, controls), world).apply(player)
      const verdict = judge(world, player, tick, mem, target, platform)
      if (verdict !== 'go') return verdict === 'done'
    }
    return false
  }

  /** The surest way to jump from where the bot stands onto the target's platform, or null. */
  function plan (bot, target, platform) {
    let best = null
    for (const brake of [0, 3]) {
      for (const sprint of [true, false]) {
        const ok = []
        for (let run = 0; run <= 12; run++) {
          ok.push([])
          for (let air = 0; air <= 14; air++) ok[run].push(lands(bot, { run, sprint, air, brake }, target, platform))
        }
        for (let run = 0; run <= 12; run++) {
          for (let air = 0; air <= 14; air++) {
            if (!ok[run][air]) continue
            let around = 0
            for (let dr = -1; dr <= 1; dr++) {
              for (let da = -1; da <= 1; da++) {
                if ((dr !== 0 || da !== 0) && ok[run + dr]?.[air + da]) around++
              }
            }
            if (best == null || around > best.around) best = { run, sprint, air, brake, around }
          }
        }
        if (best != null && best.around >= 5) return best
      }
    }
    return best
  }

  return { physics, lands, plan }
}

/**
 * Plays a planned jump on the bot, tick by tick (the controls of each tick set right after the
 * physics of the one before). Resolves 'done', 'fail' (landed elsewhere), or 'moved' (the server
 * put the bot somewhere else: a fall, the end of the game).
 */
export function playJump (bot, world, combo, target, platform) {
  return new Promise((resolve) => {
    const aim = { x: target.x + 0.5, z: target.z + 0.5 }
    const mem = { jumped: -1, landed: -1, brake: combo.brake }
    let tick = 0
    const apply = () => {
      const controls = decide(bot.entity, tick, combo, aim, mem)
      for (const name of KEYS) bot.setControlState(name, controls[name])
    }
    const end = (verdict) => {
      bot.removeListener('physicsTick', onTick)
      bot.removeListener('forcedMove', onMoved)
      bot.clearControlStates()
      resolve(verdict)
    }
    const onTick = () => {
      const verdict = judge(world, bot, tick, mem, target, platform)
      if (verdict !== 'go' || tick > 160) {
        end(verdict === 'go' ? 'fail' : verdict)
        return
      }
      tick++
      apply()
    }
    const onMoved = () => end('moved')
    apply()
    bot.on('physicsTick', onTick)
    bot.on('forcedMove', onMoved)
  })
}

/** The bot's controls released, until it stands still on the ground (three ticks without moving). */
export function settle (bot, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    bot.clearControlStates()
    let still = 0
    const timer = setTimeout(() => {
      bot.removeListener('physicsTick', onTick)
      reject(new Error(`${bot.username} did not stand still (at ${bot.entity.position})`))
    }, timeoutMs)
    const onTick = () => {
      const v = bot.entity.velocity
      still = bot.entity.onGround && v.x === 0 && v.z === 0 ? still + 1 : 0
      if (still >= 3) {
        clearTimeout(timer)
        bot.removeListener('physicsTick', onTick)
        resolve()
      }
    }
    bot.on('physicsTick', onTick)
  })
}

function sleep (ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * The bot runs the course from where it stands, jump after jump, until `until()` (a checkpoint, the
 * finish). A jump that fails (the bot lands elsewhere, or falls and the server puts it back on its
 * checkpoint) is tried again from there, maxFalls times at most. Resolves the jumps and the misses.
 */
export async function runCourse (bot, world, runner, lane, { until, log = () => {}, maxFalls = 6, maxJumps = 80 }) {
  let jumps = 0
  let misses = 0
  while (!until()) {
    await settle(bot)
    if (until()) break
    const on = standsOn(world, bot.entity.position)
    if (on == null) throw new Error(`${bot.username} stands on nothing at ${bot.entity.position}`)
    const target = nextTarget(world, platformOf(world, on, lane), lane)
    if (target == null) throw new Error(`${bot.username}: no block ahead of ${key(on.x, on.y, on.z)}`)
    const platform = platformOf(world, target, lane)
    const started = Date.now()
    const combo = runner.plan(bot, target, platform)
    if (combo == null) throw new Error(`${bot.username}: no way to jump from ${key(on.x, on.y, on.z)} to ${key(target.x, target.y, target.z)}`)
    // the physics of the bot catches up with the time the plan took (it stands still meanwhile)
    await sleep((Date.now() - started) / 3 + 150)
    const verdict = await playJump(bot, world, combo, target, platform)
    jumps++
    log(`${key(on.x, on.y, on.z)} to ${key(target.x, target.y, target.z)}: run ${combo.run}, ${combo.sprint ? 'sprint' : 'walk'}, ` +
      `air ${combo.air}, brake ${combo.brake} (${combo.around}/8 around): ${verdict}`)
    if (verdict !== 'done' && !until()) {
      misses++
      if (misses > maxFalls) throw new Error(`${bot.username} missed ${misses} jumps`)
    }
    if (jumps > maxJumps) throw new Error(`${bot.username}: ${jumps} jumps and not there yet`)
  }
  return { jumps, misses }
}
