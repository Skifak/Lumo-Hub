package com.lumo.hub.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiClient(
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun listModels(provider: OpenAiProvider): List<ModelInfo> = execute(provider, "/models") { body ->
        json.decodeFromString<ModelsResponse>(body).data.map { ModelInfo(it.id, it.owned_by) }
    }

    suspend fun complete(provider: OpenAiProvider, request: ChatCompletionRequest): ChatCompletion =
        execute(provider, "/chat/completions", "POST", json.encodeToString(request)) { body ->
            val result = json.decodeFromString<CompletionJson>(body)
            ChatCompletion(result.id, result.model, result.choices.firstOrNull()?.message?.content.orEmpty())
        }

    /** Cold stream: cancelling the collector cancels the underlying HTTP call. */
    fun stream(provider: OpenAiProvider, request: ChatCompletionRequest): Flow<ChatStreamEvent> = callbackFlow {
        val call = http.newCall(request(provider, "/chat/completions", json.encodeToString(request.copy(stream = true))))
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (!call.isCanceled()) trySend(ChatStreamEvent.Completed(null)).also { close(OpenAiException.Network(e)) }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) { close(OpenAiException.Http(response.code, response.body?.string().orEmpty())); return }
                try {
                    response.body?.use { body ->
                        var event = StringBuilder()
                        body.source().use { source ->
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line().orEmpty()
                                if (line.isEmpty()) { emitEvent(event.toString()); event = StringBuilder() }
                                else if (line.startsWith("data:")) event.append(line.removePrefix("data:").trim()).append('\n')
                            }
                        }
                        if (event.isNotEmpty()) emitEvent(event.toString())
                    } ?: close(OpenAiException.InvalidResponse(IllegalStateException("empty body")))
                    close()
                } catch (t: Throwable) { close(if (t is OpenAiException) t else OpenAiException.InvalidResponse(t)) }
            }
            private fun emitEvent(data: String) {
                val payload = data.trim()
                if (payload.isEmpty() || payload == "[DONE]") { if (payload == "[DONE]") trySend(ChatStreamEvent.Completed(null)); return }
                val parsed = json.decodeFromString<CompletionJson>(payload)
                val choice = parsed.choices.firstOrNull()
                choice?.delta?.content?.takeIf { it.isNotEmpty() }?.let { trySend(ChatStreamEvent.Delta(it)) }
                choice?.finish_reason?.let { trySend(ChatStreamEvent.Completed(it)) }
            }
        })
        awaitClose { call.cancel() }
    }

    private suspend fun <T> execute(provider: OpenAiProvider, path: String, method: String = "GET", body: String? = null, parse: (String) -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                http.newCall(request(provider, path, body, method)).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw OpenAiException.Http(response.code, text)
                    runCatching { parse(text) }.getOrElse { throw OpenAiException.InvalidResponse(it) }
                }
            } catch (e: OpenAiException) { throw e } catch (e: IOException) { throw OpenAiException.Network(e) }
        }

    private fun request(provider: OpenAiProvider, path: String, body: String? = null, method: String = "POST"): Request {
        val base = provider.baseUrl.trimEnd('/')
        return Request.Builder().url("$base$path").header("Authorization", "Bearer ${provider.apiKey}")
            .header("Accept", "text/event-stream").method(method, body?.toRequestBody("application/json".toMediaType())).build()
    }
}
