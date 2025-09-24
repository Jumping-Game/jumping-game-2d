package com.bene.jump.core.model

import com.bene.jump.core.math.Vec2
import com.bene.jump.core.rng.WorldRandom

class World(val config: GameConfig, seed: Long) {
    val player = Player(config.playerSize)
    val camera = Camera(position = Vec2(0f, 0f))
    val platforms: MutableList<Platform> = MutableList(config.platformBuffer) { Platform() }
    private val pool: ArrayDeque<Platform> = ArrayDeque()
    val activePlatforms: MutableList<Platform> = mutableListOf()
    val powerUps: MutableList<PowerUp> = mutableListOf()
    var score: Float = 0f
    var bestHeight: Float = 0f
    var tick: Long = 0
    var seed: Long = seed
    val random: WorldRandom = WorldRandom(seed)
    var highestPlatformY: Float = 0f

    init {
        pool.addAll(platforms)
        reset(seed)
    }

    fun obtainPlatform(): Platform =
        if (pool.isEmpty()) {
            val platform = Platform()
            platforms.add(platform)
            platform
        } else {
            pool.removeFirst()
        }

    fun recycle(platform: Platform) {
        pool.addLast(platform)
    }

    fun reset(newSeed: Long = seed) {
        seed = newSeed
        random.reseed(newSeed)
        tick = 0
        score = 0f
        bestHeight = 0f
        highestPlatformY = 0f
        powerUps.clear()
        camera.position.set(0f, 0f)
        camera.minY = 0f
        player.reset(0f, 1f)
        activePlatforms.forEach { recycle(it) }
        activePlatforms.clear()
        val basePlatform = obtainPlatform()
        basePlatform.position.set(0f, 0f)
        basePlatform.bounds.center.set(basePlatform.position)
        basePlatform.bounds.halfWidth = config.platformWidth * 0.5f
        basePlatform.bounds.halfHeight = 0.2f
        basePlatform.spawnedTick = 0
        activePlatforms.add(basePlatform)
        highestPlatformY = 0f
    }
}
