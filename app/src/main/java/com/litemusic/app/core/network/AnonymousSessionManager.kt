package com.litemusic.app.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Autonomous YouTube Anonymous Session & Credentials Manager.
 * Automatically generates, maintains, and injects anonymous cookies, visitorData,
 * PO token placeholders, and InnerTube credentials without requiring user login.
 */
class AnonymousSessionManager private constructor() {

    private val tag = "AnonSessionMgr"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Official InnerTube Web/Music client API key used by YouTube Web & Umihi-Music
    val apiKey: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedVisitorData: String? = null
    @Volatile
    private var lastVisitorFetchMs: Long = 0L

    companion object {
        @Volatile
        private var instance: AnonymousSessionManager? = null

        fun getInstance(): AnonymousSessionManager {
            return instance ?: synchronized(this) {
                instance ?: AnonymousSessionManager().also { instance = it }
            }
        }

        const val UA_VISION_OS = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
        const val UA_DESKTOP_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val UA_ANDROID_MOBILE = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    fun ensureVisitorDataAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            getOrFetchVisitorData(force = false)
        }
    }

    /**
     * Retrieves valid visitorData with automatic TTL refresh (6 hours).
     */
    suspend fun getOrFetchVisitorData(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && !cachedVisitorData.isNullOrEmpty() && (now - lastVisitorFetchMs) < 6 * 3600 * 1000L) {
            return@withContext cachedVisitorData
        }

        try {
            val payload = JSONObject().apply {
                put("context", getWebContext())
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .header("User-Agent", UA_DESKTOP_CHROME)
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Format-Version", "2")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val vis = json.optJSONObject("responseContext")?.optString("visitorData")
                    if (!vis.isNullOrEmpty()) {
                        cachedVisitorData = vis
                        lastVisitorFetchMs = now
                        Log.d(tag, "Successfully fetched fresh visitorData: ${vis.take(20)}...")
                        return@withContext vis
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to refresh visitorData: ${e.message}")
        }
        cachedVisitorData
    }

    /**
     * Returns standard anonymous consent and visitor cookies.
     */
    fun getAnonymousCookieHeader(visitorData: String? = cachedVisitorData): String {
        val base = "SOCS=CAESEwgDEgk2NDYxNTM2NzQaAnZpIAEaBgiA_L20Bg; CONSENT=PENDING+999"
        return if (!visitorData.isNullOrEmpty()) {
            "$base; VISITOR_INFO1_LIVE=$visitorData"
        } else {
            base
        }
    }

    fun getVisionOsContext(visitorData: String? = cachedVisitorData): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "101")
                put("clientVersion", "1.02")
                put("osName", "visionOS")
                put("osVersion", "26.5.23O471")
                put("userAgent", UA_VISION_OS)
                put("hl", "vi")
                put("gl", "VN")
                put("timeZone", "Asia/Ho_Chi_Minh")
                put("utcOffsetMinutes", 420)
                if (!visitorData.isNullOrEmpty()) {
                    put("visitorData", visitorData)
                }
            })
        }
    }

    fun getWebRemixContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20241001.01.00")
                put("hl", "vi")
                put("gl", "VN")
                put("utcOffsetMinutes", 420)
            })
        }
    }

    fun getWebContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20241001.01.00")
                put("hl", "vi")
                put("gl", "VN")
                put("utcOffsetMinutes", 420)
            })
        }
    }
}
