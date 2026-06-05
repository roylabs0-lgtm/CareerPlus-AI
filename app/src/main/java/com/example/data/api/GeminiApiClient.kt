package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    /**
     * Sends prompt directly to Gemini AI and returns the result string.
     * Incorporates automatic retry loop (up to 3 times) and friendly general fallbacks on failures.
     */
    suspend fun getGeminiResponse(prompt: String, systemPrompt: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Please configure your Gemini API Key in AI Studio Secrets tab to enable active AI Coach consultation."
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) }
        )

        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val response = service.generateContent(apiKey, request)
                val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!textResult.isNullOrBlank()) {
                    return@withContext textResult
                }
            } catch (e: Exception) {
                lastException = e
                // Wait briefly before retrying
                kotlinx.coroutines.delay(800L * attempt)
            }
        }

        // Instead of returning HTTP 503 or raw stack traces, return the user-friendly privacy-safe warning
        "AI Coach is temporarily unavailable. Please try again shortly."
    }
}
