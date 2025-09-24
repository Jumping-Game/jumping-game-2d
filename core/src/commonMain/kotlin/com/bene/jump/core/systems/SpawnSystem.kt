package com.bene.jump.core.systems

import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.model.World
import kotlin.math.max

class SpawnSystem(private val config: GameConfig) {
    fun update(world: World) {
        val cameraBottom = world.camera.position.y - config.worldHeightVisible
        var index = 0
        while (index < world.activePlatforms.size) {
            val platform = world.activePlatforms[index]
            if (platform.position.y < cameraBottom) {
                world.activePlatforms.removeAt(index)
                world.recycle(platform)
                continue
            }
            index++
        }

        val targetTop = world.camera.position.y + config.worldHeightVisible * 1.5f
        var highest = world.highestPlatformY
        if (world.activePlatforms.isEmpty()) {
            highest = world.player.position.y
        }
        val halfWidth = config.worldWidth * 0.5f
        while (highest < targetTop) {
            val spacing = world.random.nextRange(config.platformSpacingMin, config.platformSpacingMax)
            val newY = highest + spacing
            val platform = world.obtainPlatform()
            val x = world.random.nextRange(-halfWidth + config.platformWidth * 0.5f, halfWidth - config.platformWidth * 0.5f)
            platform.position.set(x, newY)
            platform.bounds.center.set(platform.position)
            platform.bounds.halfWidth = config.platformWidth * 0.5f
            platform.bounds.halfHeight = 0.2f
            platform.spawnedTick = world.tick
            world.activePlatforms.add(platform)
            highest = newY
        }
        world.highestPlatformY = max(world.highestPlatformY, highest)
    }
}
