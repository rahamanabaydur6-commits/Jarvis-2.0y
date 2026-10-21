package com.jarvis.assistant.ai

import com.jarvis.assistant.data.Net
import com.jarvis.assistant.data.SecureStore
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

@Serializable data class InlineData(val mimeType: String, val data: String)
@Serializable data class Part(val text: String? = null, val inlineData: InlineData? = null)
@Serializable data class Content(val role: String? = null, val parts: List<Part>)
@Serializable data class GenConfig(val temperature: Float? = null, val maxOutputTokens: Int? = null, val responseMimeType: String? = null)
@Serializable data class GenRequest(val contents: List<Content>, val systemInstruction: Content? = null, val generationConfig: GenConfig? = null)
@Serializable data class Usage(val totalTokenCount: Int = 0)
@Serializable data class Candidate(val content: Content? = null, val finishReason: String? = null)
@Serializable data class GenResponse(val candidates: List<Candidate> = emptyList(), val usageMetadata: Usage? = null)
@Serializable data class ModelInfo(val name: String, val supportedGenerationMethods: List<String> = emptyList())
@Serializable data class ModelList(val models: List<ModelInfo> = emptyList())

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @Header("x-goog-api-key") key: String,
        @Path("model") model: String,
        @Body body: GenRequest
    ): GenResponse

    @GET("v1beta/models")
    suspend fun models(@Header("x-goog-api-key") key: String, @Query("pageSize") pageSize: Int = 200): ModelList
}

class GeminiError(msg: String) : Exception(msg)

class GeminiRepository(private val secure: SecureStore) {
    private val api: GeminiApi = Net.retrofit.create(GeminiApi::class.java)

    private fun key() = secure.get(SecureStore.GEMINI)
        ?: throw GeminiError("Gemini API key set nahi hai. Settings tab mein key daalo (aistudio.google.com/apikey).")

    /** Returns (text, totalTokens). */
    suspend fun generate(
        model: String, system: String, turns: List<Content>,
        temperature: Float, maxTokens: Int, jsonMode: Boolean
    ): Pair<String, Int> {
        val k = key()
        try {
            val r = api.generate(
                k, model,
                GenRequest(
                    contents = turns,
                    systemInstruction = Content(parts = listOf(Part(text = system))),
                    generationConfig = GenConfig(temperature, maxTokens, if (jsonMode) "application/json" else null)
                )
            )
            val cand = r.candidates.firstOrNull()
            val text = cand?.content?.parts?.mapNotNull { it.text }?.joinToString("")
            if (text.isNullOrBlank()) throw GeminiError("Empty response (finish reason: ${cand?.finishReason ?: "blocked/none"}). Max tokens badhao ya prompt badlo.")
            return text to (r.usageMetadata?.totalTokenCount ?: 0)
        } catch (e: HttpException) {
            throw GeminiError(describe(e))
        } catch (e: IOException) {
            throw GeminiError("Network problem: ${e.message}. Internet check karo.")
        }
    }

    /** Models that support generateContent for the configured key. Also used as "Test connection". */
    suspend fun listModels(): List<String> {
        val k = key()
        try {
            return api.models(k).models
                .filter { "generateContent" in it.supportedGenerationMethods && it.name.startsWith("models/gemini") }
                .map { it.name.removePrefix("models/") }
                .sorted()
        } catch (e: HttpException) {
            throw GeminiError(describe(e))
        } catch (e: IOException) {
            throw GeminiError("Network problem: ${e.message}")
        }
    }

    private fun describe(e: HttpException): String {
        val body = try { e.response()?.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
        val base = when (e.code()) {
            429 -> "Rate limit / quota hit (429). Thodi der baad Retry karo."
            400 -> "Bad request (400). API key ya model name galat ho sakta hai."
            401, 403 -> "API key reject hui (${e.code()}). Key check karo."
            404 -> "Model nahi mila (404). Settings mein dusra model chuno."
            500, 503 -> "Gemini server busy (${e.code()}). Retry karo."
            else -> "HTTP ${e.code()}"
        }
        return if (body.isNullOrBlank()) base else "$base\n$body"
    }
}
