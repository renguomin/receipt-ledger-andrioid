package com.receiptledger.app.gemini

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.receiptledger.app.ui.AiReadLogic
import com.receiptledger.engine.gemini.GeminiReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.math.max
import kotlin.math.roundToInt

class GeminiClient(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class ExtractResult(
        val receipt: GeminiReceipt,
        val summary: String,
    )

    fun warmup(apiKey: String?) {
        runCatching {
            http.newCall(
                Request.Builder()
                    .url("https://generativelanguage.googleapis.com/")
                    .head()
                    .build(),
            ).execute().close()
        }
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) return
        runCatching {
            val ping = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "ping")),
                        ),
                    ),
                )
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("maxOutputTokens", 1)
                        .put(
                            "thinkingConfig",
                            JSONObject().put("thinkingLevel", "minimal"),
                        ),
                )
            http.newCall(
                Request.Builder()
                    .url(
                        "https://generativelanguage.googleapis.com/v1beta/models/" +
                            "$DEFAULT_MODEL:generateContent?key=$key",
                    )
                    .post(ping.toString().toRequestBody(JSON_MEDIA))
                    .build(),
            ).execute().close()
        }
    }

    suspend fun extract(
        apiKey: String,
        imageFile: File,
        ocrText: String,
        categories: List<Pair<String, String>>,
        model: String = DEFAULT_MODEL,
    ): ExtractResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val jpeg = jpegForGemini(imageFile)
        val imageB64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        Log.d(TAG, "Sending ${jpeg.size} byte JPEG (${imageFile.length()} byte original)")
        val prompt = buildString {
            appendLine("Extract receipt fields. Return JSON only. No explanations.")
            appendLine(GeminiReceipt.JSON_SCHEMA_HINT)
            appendLine(GeminiReceipt.categorySection(categories))
            appendLine("OCR text from the device (may be messy):")
            appendLine(ocrText.take(4000))
        }
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(
                                JSONObject()
                                    .put(
                                        "inlineData",
                                        JSONObject()
                                            .put("mimeType", "image/jpeg")
                                            .put("data", imageB64),
                                    )
                                    .put(
                                        "mediaResolution",
                                        JSONObject().put("level", "MEDIA_RESOLUTION_MEDIUM"),
                                    ),
                            ),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("maxOutputTokens", 4096)
                    .put(
                        "thinkingConfig",
                        JSONObject()
                            .put("thinkingLevel", "minimal")
                            .put("includeThoughts", false),
                    ),
            )
        val modelId = model.trim().ifBlank { DEFAULT_MODEL }
        val timer = GeminiCallTimer()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val timedHttp = http.newBuilder()
            .eventListener(timer)
            .retryOnConnectionFailure(false)
            .build()
        try {
            timedHttp.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error(friendlyHttpError(response.code, text))
                }
                val payload = json.parseToJsonElement(text).jsonObject
                val thoughts = payload["usageMetadata"]?.jsonObject?.get("thoughtsTokenCount")
                    ?.jsonPrimitive?.intOrNull
                payload["usageMetadata"]?.jsonObject?.let { usage ->
                    Log.d(
                        TAG,
                        "tokens prompt=${usage["promptTokenCount"]?.jsonPrimitive?.intOrNull} " +
                            "out=${usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull} " +
                            "thoughts=$thoughts " +
                            "total=${usage["totalTokenCount"]?.jsonPrimitive?.intOrNull} " +
                            "reused=${timer.reusedConnection}",
                    )
                }
                val modelText = payload["candidates"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonObject
                    ?.get("parts")
                    ?.jsonArray
                    ?.firstOrNull { part ->
                        part.jsonObject["text"] != null
                    }
                    ?.jsonObject
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.content
                    ?: error("Gemini returned no text")
                val receipt = json.decodeFromString<GeminiReceipt>(stripFence(modelText))
                val totalMs = (System.nanoTime() - started) / 1_000_000
                val summary = timer.summary(totalMs, thoughts)
                Log.d(TAG, summary)
                ExtractResult(receipt, summary)
            }
        } catch (error: InterruptedIOException) {
            throw IllegalStateException(TIMEOUT_MESSAGE, error)
        } catch (error: SocketTimeoutException) {
            throw IllegalStateException(TIMEOUT_MESSAGE, error)
        }
    }

    private fun friendlyHttpError(code: Int, body: String): String {
        val snippet = body.take(400)
        val busy = snippet.contains("high demand", ignoreCase = true) ||
            snippet.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            code == 429 ||
            code == 503
        if (busy) {
            return "Gemini is busy. ${AiReadLogic.FALLBACK_HINT}"
        }
        return "Gemini request failed ($code): $snippet"
    }

    private fun jpegForGemini(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return file.readBytes()
        val longest = max(srcW, srcH)
        val sample = if (longest > MAX_EDGE * 2) {
            Integer.highestOneBit(longest / MAX_EDGE)
        } else {
            1
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) },
        ) ?: return file.readBytes()
        val scaled = if (max(bitmap.width, bitmap.height) > MAX_EDGE) {
            val factor = MAX_EDGE.toFloat() / max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * factor).roundToInt().coerceAtLeast(1),
                (bitmap.height * factor).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return bytes
    }

    private fun stripFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val KEY_FILE_NAME = "gemini-api-key.txt"
        private const val TAG = "GeminiClient"
        private const val TIMEOUT_MESSAGE =
            "Gemini took longer than ${GeminiHttp.CALL_TIMEOUT_SECONDS} seconds. ${AiReadLogic.FALLBACK_HINT}"
        private const val MAX_EDGE = 1600
        private const val JPEG_QUALITY = 80
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
