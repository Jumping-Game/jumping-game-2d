package com.bene.jump.core

import com.bene.jump.core.engine.FixedTimestepLoop
import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.model.GameInput
import com.bene.jump.core.sim.GameSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameSessionDeterminismTest {
    @Test
    fun sessionsWithSameInputMatch() {
        val config = GameConfig()
        val sessionA = GameSession(config, seed = 1234L)
        val sessionB = GameSession(config, seed = 1234L)
        val input = GameInput(tilt = 0.1f)
        repeat(240) {
            sessionA.step(input, 1f / 60f)
            sessionB.step(input, 1f / 60f)
        }
        assertEquals(sessionA.world.player.position.x, sessionB.world.player.position.x, 1e-4f)
        assertEquals(sessionA.world.player.position.y, sessionB.world.player.position.y, 1e-4f)
        assertEquals(sessionA.world.tick, sessionB.world.tick)
    }

    @Test
    fun fixedLoopClampsDelta() {
        val loop = FixedTimestepLoop(step = 1f / 60f, maxFrameDelta = 0.1f)
        var steps = 0
        loop.advance(1f) { steps++ }
        assertEquals(6, steps)
    }
}
