package com.bene.jump.core.systems

import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.model.GameInput
import com.bene.jump.core.model.World

class PhysicsSystem(private val config: GameConfig) {
    fun applyInput(
        world: World,
        input: GameInput,
        dt: Float,
    ) {
        val player = world.player
        val targetAcceleration = input.tilt * config.horizontalAcceleration
        player.velocity.x += targetAcceleration * dt
        val friction = config.horizontalFriction * dt
        player.velocity.x -= player.velocity.x * friction
        if (player.velocity.x > config.maxHorizontalSpeed) player.velocity.x = config.maxHorizontalSpeed
        if (player.velocity.x < -config.maxHorizontalSpeed) player.velocity.x = -config.maxHorizontalSpeed
    }

    fun integrate(
        world: World,
        dt: Float,
    ) {
        val player = world.player
        player.lastPosition.set(player.position)
        player.velocity.y += config.gravity * dt
        player.position.add(player.velocity.x * dt, player.velocity.y * dt)
        val halfWidth = config.worldWidth * 0.5f
        if (player.position.x < -halfWidth) {
            player.position.x += config.worldWidth
            player.lastPosition.x += config.worldWidth
        }
        if (player.position.x > halfWidth) {
            player.position.x -= config.worldWidth
            player.lastPosition.x -= config.worldWidth
        }
        player.syncBounds()
        world.camera.follow(player.position.y - config.worldHeightVisible * 0.3f, 0.1f)
    }
}
