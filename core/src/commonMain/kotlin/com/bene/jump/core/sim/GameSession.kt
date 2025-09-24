package com.bene.jump.core.sim

import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.model.GameInput
import com.bene.jump.core.model.SessionPhase
import com.bene.jump.core.model.World
import com.bene.jump.core.systems.CollisionSystem
import com.bene.jump.core.systems.PhysicsSystem
import com.bene.jump.core.systems.ScoreSystem
import com.bene.jump.core.systems.SpawnSystem

class GameSession(
    val config: GameConfig = GameConfig(),
    seed: Long = 0L,
) {
    val world: World = World(config, seed)
    private val physics = PhysicsSystem(config)
    private val collisions = CollisionSystem(config)
    private val spawns = SpawnSystem(config)
    private val score = ScoreSystem(config)
    var phase: SessionPhase = SessionPhase.Running
        private set

    fun step(
        input: GameInput,
        dt: Float,
    ) {
        if (phase == SessionPhase.Paused) {
            if (input.pauseRequested) phase = SessionPhase.Running
            return
        }
        if (phase != SessionPhase.Running) return
        if (input.pauseRequested) {
            phase = SessionPhase.Paused
            return
        }
        physics.applyInput(world, input, dt)
        physics.integrate(world, dt)
        collisions.resolve(world)
        spawns.update(world)
        val alive = score.update(world)
        if (!alive) {
            phase = SessionPhase.GameOver
        }
        world.tick += 1
    }

    fun restart(newSeed: Long = world.seed) {
        world.reset(newSeed)
        phase = SessionPhase.Running
    }
}
