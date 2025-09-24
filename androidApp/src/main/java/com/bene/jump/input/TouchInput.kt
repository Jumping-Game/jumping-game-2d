package com.bene.jump.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TouchInput {
    private val mutablePressed = MutableStateFlow(false)
    val pressed: StateFlow<Boolean> = mutablePressed

    fun onTouch(down: Boolean) {
        mutablePressed.value = down
    }
}
