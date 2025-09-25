package com.bene.jump.core.net

/**
 * Known protocol error codes as documented in NETWORK_PROTOCOL.md v1.2.
 * The enum preserves the raw server string for mapping while allowing
 * clients to handle unknown codes gracefully.
 */
enum class NetErrorCode(val raw: String) {
    BAD_VERSION("BAD_VERSION"),
    ROOM_NOT_FOUND("ROOM_NOT_FOUND"),
    ROOM_FULL("ROOM_FULL"),
    NAME_TAKEN("NAME_TAKEN"),
    INVALID_STATE("INVALID_STATE"),
    INVALID_TICK("INVALID_TICK"),
    RATE_LIMITED("RATE_LIMITED"),
    UNAUTHORIZED("UNAUTHORIZED"),
    SLOW_CONSUMER("SLOW_CONSUMER"),
    ROOM_CLOSED("ROOM_CLOSED"),
    INTERNAL("INTERNAL"),
    NOT_MASTER("NOT_MASTER"),
    ROOM_STATE_INVALID("ROOM_STATE_INVALID"),
    ROOM_NOT_READY("ROOM_NOT_READY"),
    START_ALREADY("START_ALREADY"),
    COUNTDOWN_ACTIVE("COUNTDOWN_ACTIVE"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): NetErrorCode? {
            if (value == null) return null
            return entries.firstOrNull { it.raw == value }
        }
    }
}

fun NetErrorCode?.orUnknown(): NetErrorCode = this ?: NetErrorCode.UNKNOWN
