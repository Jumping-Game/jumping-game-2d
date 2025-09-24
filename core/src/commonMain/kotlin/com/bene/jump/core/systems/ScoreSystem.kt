package com.bene.jump.core.systems

import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.model.World
import kotlin.math.max

class ScoreSystem(private val config: GameConfig) {
    fun update(world: World): Boolean {
        val playerTop = world.player.position.y
        world.bestHeight = max(world.bestHeight, playerTop)
        world.score = world.bestHeight
        val failHeight = world.camera.position.y - config.deathHeight
        val playerBottom = world.player.position.y - world.player.bounds.halfHeight
        return playerBottom >= failHeight
    }
}
