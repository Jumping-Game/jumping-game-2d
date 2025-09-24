package com.bene.jump.core.model

data class GameInput(
    var tilt: Float = 0f,
    var touchDown: Boolean = false,
    var pauseRequested: Boolean = false,
)
