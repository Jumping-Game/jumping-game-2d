package dev.fizcode.jumpinggame

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform