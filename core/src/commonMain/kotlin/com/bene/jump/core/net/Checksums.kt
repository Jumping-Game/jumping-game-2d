package com.bene.jump.core.net

import com.bene.jump.core.model.World

object Checksums {
    fun world(world: World): Long {
        var hash = 17L
        hash = hash * 31 + world.player.position.x.toBits()
        hash = hash * 31 + world.player.position.y.toBits()
        hash = hash * 31 + world.player.velocity.x.toBits()
        hash = hash * 31 + world.player.velocity.y.toBits()
        for (platform in world.activePlatforms) {
            hash = hash * 31 + platform.position.x.toBits()
            hash = hash * 31 + platform.position.y.toBits()
        }
        hash = hash * 31 + world.tick
        return hash
    }

    private fun Float.toBits(): Long = this.toBits().toLong()
}
