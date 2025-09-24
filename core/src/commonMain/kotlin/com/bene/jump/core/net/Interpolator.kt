package com.bene.jump.core.net

import com.bene.jump.core.model.World

class Interpolator {
    fun applyRemoteState(
        world: World,
        snapshot: List<NetPlayer>,
    ) {
        val player = world.player
        val local = snapshot.firstOrNull() ?: return
        player.position.set(local.x, local.y)
        player.velocity.set(local.velocityX, local.velocityY)
        player.syncBounds()
    }
}
