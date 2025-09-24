package com.bene.jump.core.systems

import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.model.World

class CollisionSystem(private val config: GameConfig) {
    fun resolve(world: World) {
        val player = world.player
        if (player.velocity.y >= 0f) return
        val playerRect = player.bounds
        val previousBottom = player.lastPosition.y - playerRect.halfHeight
        for (platform in world.activePlatforms) {
            val platformTop = platform.bounds.top()
            if (previousBottom >= platformTop) {
                val playerBottom = playerRect.bottom()
                val overlapsHorizontally =
                    playerRect.right() >= platform.bounds.left() &&
                        playerRect.left() <= platform.bounds.right()
                if (playerBottom <= platformTop && overlapsHorizontally) {
                    player.position.y = platformTop + playerRect.halfHeight
                    player.velocity.y = config.jumpVelocity
                    player.isJumping = true
                    player.syncBounds()
                    break
                }
            }
        }
    }
}
