package com.bene.jump.net.api

import com.bene.jump.BuildConfig
import com.bene.jump.core.net.NetErrorCode
import com.bene.jump.core.net.NetworkJson
import com.bene.jump.core.net.Role
import com.bene.jump.core.net.RoomState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Thin REST client for the matchmaking + lobby endpoints defined in NETWORK_PROTOCOL.md v1.2.
 * Uses OkHttp + kotlinx-serialization (strict) to keep behaviour consistent with the realtime layer.
 */
class RoomsApi(
    private val client: OkHttpClient,
    private val json: Json = NetworkJson,
    private val baseUrl: String = BuildConfig.API_BASE,
) {
    suspend fun createRoom(request: CreateRoomRequest): CreateRoomResponse =
        execute(
            method = "POST",
            path = "/v1/rooms",
            body = request,
            accept = setOf(201),
        )

    suspend fun joinRoom(
        roomId: String,
        request: JoinRoomRequest,
    ): JoinRoomResponse =
        execute(
            method = "POST",
            path = "/v1/rooms/${'$'}roomId/join",
            body = request,
            accept = setOf(200),
        )

    suspend fun leaveRoom(roomId: String) {
        execute<Unit, Unit>(
            method = "POST",
            path = "/v1/rooms/${'$'}roomId/leave",
            body = Unit,
            accept = setOf(204),
        )
    }

    suspend fun setReady(
        roomId: String,
        request: ReadyRequest,
    ) {
        execute<ReadyRequest, Unit>(
            method = "POST",
            path = "/v1/rooms/${'$'}roomId/ready",
            body = request,
            accept = setOf(204),
        )
    }

    suspend fun setCharacter(
        roomId: String,
        request: CharacterRequest,
    ) {
        execute<CharacterRequest, Unit>(
            method = "POST",
            path = "/v1/rooms/${'$'}roomId/character",
            body = request,
            accept = setOf(204),
        )
    }

    suspend fun startRoom(
        roomId: String,
        request: StartRoomRequest,
    ): StartRoomResponse =
        execute(
            method = "POST",
            path = "/v1/rooms/${'$'}roomId/start",
            body = request,
            accept = setOf(202),
        )

    suspend fun getStatus(): StatusResponse =
        execute(
            method = "GET",
            path = "/v1/status",
            body = null,
            accept = setOf(200),
        )

    private suspend inline fun <reified Req : Any?, reified Res> execute(
        method: String,
        path: String,
        body: Req,
        accept: Set<Int>,
    ): Res =
        withContext(Dispatchers.IO) {
            val url = buildUrl(path)
            val requestBody = toRequestBodyOrNull(method, body)
            val request =
                Request.Builder()
                    .url(url)
                    .method(method, requestBody)
                    .addHeader("Accept", MEDIA_TYPE_JSON)
                    .apply { if (body != null) addHeader("Content-Type", MEDIA_TYPE_JSON) }
                    .build()

            client.newCall(request).execute().use { response ->
                if (response.code !in accept) {
                    throw response.asException(json)
                }
                if (Res::class == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    return@use Unit as Res
                }
                val content = response.body?.string().orEmpty()
                if (content.isEmpty()) {
                    error("Expected response body for ${Res::class.simpleName}")
                }
                return@use json.decodeFromString(content)
            }
        }

    private fun buildUrl(path: String): String {
        val normalizedBase = baseUrl.removeSuffix("/")
        val normalizedPath = if (path.startsWith('/')) path else "/${'$'}path"
        return normalizedBase + normalizedPath
    }

    private inline fun <reified Req : Any?> toRequestBodyOrNull(
        method: String,
        body: Req,
    ): RequestBody? {
        if (method == "GET" || method == "DELETE") {
            return null
        }
        if (body == null || body is Unit) {
            return EMPTY_JSON_BODY
        }
        val payload = json.encodeToString(body)
        return payload.toRequestBody(JSON_MEDIA_TYPE)
    }

    companion object {
        private const val MEDIA_TYPE_JSON = "application/json"
        private val JSON_MEDIA_TYPE = MEDIA_TYPE_JSON.toMediaType()
        private val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
    }
}

class RoomsApiException(
    val statusCode: Int,
    val errorCodeRaw: String?,
    val errorCode: NetErrorCode?,
    message: String,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

@Serializable
data class CreateRoomRequest(
    val name: String,
    val region: String? = null,
    val maxPlayers: Int? = null,
    val mode: String? = null,
)

@Serializable
data class CreateRoomResponse(
    val roomId: String,
    val seed: String,
    val region: String,
    val wsUrl: String,
    val wsToken: String,
    val role: Role,
    val state: RoomState,
    val maxPlayers: Int,
)

@Serializable
data class JoinRoomRequest(val name: String)

@Serializable
data class JoinRoomResponse(
    val roomId: String,
    val wsUrl: String,
    val wsToken: String,
    val role: Role,
    val state: RoomState,
)

@Serializable
data class ReadyRequest(val ready: Boolean)

@Serializable
data class CharacterRequest(val characterId: String)

@Serializable
data class StartRoomRequest(val countdownSec: Int? = null)

@Serializable
data class StartRoomResponse(
    val state: RoomState,
    val startAtMs: Long,
)

@Serializable
data class StatusResponse(
    val regions: List<StatusRegion>,
    val serverPv: Int,
) {
    @Serializable
    data class StatusRegion(
        val id: String,
        val pingMs: Int,
        val wsUrl: String,
    )
}

@Serializable
private data class ErrorPayload(val code: String? = null, val message: String? = null)

private fun Response.asException(json: Json): RoomsApiException {
    val bodyText = body?.string().orEmpty()
    val payload =
        runCatching {
            if (bodyText.isEmpty()) null else json.decodeFromString(ErrorPayload.serializer(), bodyText)
        }.getOrNull()
    val netCode = NetErrorCode.fromRaw(payload?.code)
    val retryAfter = headers["Retry-After"]?.toLongOrNull()
    val message = payload?.message ?: message.ifBlank { "HTTP ${'$'}code" }
    return RoomsApiException(code, payload?.code, netCode, message, retryAfter)
}
