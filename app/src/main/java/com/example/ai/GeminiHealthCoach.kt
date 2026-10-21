package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiHealthCoach {

    private const val MODEL_NAME = "gemini-3.1-pro-preview"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeHealthData(
        prompt: String,
        steps: Int,
        heartRate: Int,
        sleepMinutes: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                Exception("Gemini API key is not configured. Please add GEMINI_API_KEY to secrets or .env file.")
            )
        }

        val url = "$BASE_URL$MODEL_NAME:generateContent?key=$apiKey"

        val contextPrompt = """
            You are a privacy-first local health & fitness AI assistant.
            The user's current health metrics (stored 100% locally on device) are:
            - Daily Steps: $steps
            - Average Heart Rate: $heartRate bpm
            - Sleep Duration: ${sleepMinutes / 60}h ${sleepMinutes % 60}m
            
            User Query: $prompt
            
            Provide a clear, concise, encouraging, and clinically informed response.
            Emphasize that all data analysis is performed with zero cloud identity tracking.
        """.trimIndent()

        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", contextPrompt))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", "high")
                    })
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (!response.isSuccessful || responseBodyString == null) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: ${response.message}")
                )
            }

            val jsonResponse = JSONObject(responseBodyString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("text")) {
                            textBuilder.append(part.getString("text"))
                        }
                    }
                    return@withContext Result.success(textBuilder.toString())
                }
            }

            Result.failure(Exception("No content parts received from AI response."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
