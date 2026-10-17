package com.litemusic.app.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Dynamic Multi-Endpoint Health, Latency Pinger & Circuit Breaker.
 * Continuously measures network latency and dynamically ranks Invidious, Piped,
 * and InnerTube mirror endpoints, ensuring requests always hit the fastest available instance.
 */
class EndpointHealthManager private constructor() {

    private val tag = "EndpointHealthMgr"

    data class EndpointNode(
        val url: String,
        @Volatile var latencyMs: Long = 5000L,
        @Volatile var isHealthy: Boolean = true,
        @Volatile var consecutiveFailures: Int = 0,
        @Volatile var lastCheckedMs: Long = 0L,
        @Volatile var disabledUntilMs: Long = 0L
    )

    private val pingClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .connectionPool(ConnectionPool(12, 3, TimeUnit.MINUTES))
        .build()

    // Verified public Invidious instances
    private val invidiousPool = CopyOnWriteArrayList(
        listOf(
            "https://invidious.projectsegfau.lt",
            "https://inv.nadeko.net",
            "https://yewtu.be",
            "https://invidious.nerdvpn.de",
            "https://inv.tux.pizza",
            "https://invidious.private.coffee"
        ).map { EndpointNode(it) }
    )

    // Verified public Piped instances
    private val pipedPool = CopyOnWriteArrayList(
        listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://piped-api.lunar.icu"
        ).map { EndpointNode(it) }
    )

    // InnerTube primary and fallback endpoints
    private val innerTubePool = CopyOnWriteArrayList(
        listOf(
            "https://youtubei.googleapis.com/youtubei/v1",
            "https://www.youtube.com/youtubei/v1",
            "https://music.youtube.com/youtubei/v1"
        ).map { EndpointNode(it) }
    )

    private var pingerJob: Job? = null

    companion object {
        @Volatile
        private var instance: EndpointHealthManager? = null

        fun getInstance(): EndpointHealthManager {
            return instance ?: synchronized(this) {
                instance ?: EndpointHealthManager().also { instance = it }
            }
        }
    }

    /**
     * Start background latency pinger running every 5 minutes without blocking UI.
     */
    fun startBackgroundPinger(scope: CoroutineScope) {
        if (pingerJob?.isActive == true) return
        pingerJob = scope.launch(Dispatchers.IO) {
            // Initial warm-up ping immediately
            pingAllEndpoints()
            while (isActive) {
                delay(5 * 60 * 1000L) // 5 minutes
                pingAllEndpoints()
            }
        }
    }

    suspend fun pingAllEndpoints() {
        val allNodes = invidiousPool + pipedPool + innerTubePool
        val deferreds = allNodes.map { node ->
            CoroutineScope(Dispatchers.IO).async {
                pingNode(node)
            }
        }
        deferreds.awaitAll()
        sortPools()
    }

    private fun pingNode(node: EndpointNode) {
        val now = System.currentTimeMillis()
        if (node.disabledUntilMs > now) {
            return // Skip tripped circuit breaker until cooldown expires
        }

        val testUrl = when {
            node.url.contains("piped") -> "${node.url}/streams/dQw4w9WgXcQ"
            node.url.contains("invidious") || node.url.contains("inv.") || node.url.contains("yewtu.be") -> "${node.url}/api/v1/stats"
            else -> "${node.url}/player?prettyPrint=false"
        }

        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(testUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            pingClient.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                // Accept 200, 400 (POST expected for player), or 404 (stream found or API alive)
                val isAlive = resp.isSuccessful || resp.code == 400 || resp.code == 404
                if (isAlive) {
                    node.latencyMs = elapsed
                    node.isHealthy = true
                    node.consecutiveFailures = 0
                    node.lastCheckedMs = now
                } else {
                    handleFailure(node, now)
                }
            }
        } catch (_: Exception) {
            handleFailure(node, now)
        }
    }

    private fun handleFailure(node: EndpointNode, now: Long) {
        node.consecutiveFailures++
        node.lastCheckedMs = now
        if (node.consecutiveFailures >= 2) {
            node.isHealthy = false
            node.disabledUntilMs = now + 60_000L // 60s cooldown
            node.latencyMs = Long.MAX_VALUE
        }
    }

    private fun sortPools() {
        invidiousPool.sortBy { if (it.isHealthy) it.latencyMs else Long.MAX_VALUE }
        pipedPool.sortBy { if (it.isHealthy) it.latencyMs else Long.MAX_VALUE }
        innerTubePool.sortBy { if (it.isHealthy) it.latencyMs else Long.MAX_VALUE }
        Log.d(tag, "Ranked fastest Invidious: ${getRankedInvidiousInstances().firstOrNull()} (${invidiousPool.firstOrNull()?.latencyMs}ms)")
        Log.d(tag, "Ranked fastest Piped: ${getRankedPipedInstances().firstOrNull()} (${pipedPool.firstOrNull()?.latencyMs}ms)")
    }

    fun recordSuccess(url: String, elapsedMs: Long) {
        val node = (invidiousPool + pipedPool + innerTubePool).find { url.startsWith(it.url) }
        if (node != null) {
            node.latencyMs = (node.latencyMs + elapsedMs) / 2
            node.isHealthy = true
            node.consecutiveFailures = 0
            node.disabledUntilMs = 0L
        }
    }

    fun recordFailure(url: String) {
        val node = (invidiousPool + pipedPool + innerTubePool).find { url.startsWith(it.url) }
        if (node != null) {
            handleFailure(node, System.currentTimeMillis())
            sortPools()
        }
    }

    fun getRankedInvidiousInstances(): List<String> {
        val now = System.currentTimeMillis()
        val healthy = invidiousPool.filter { it.isHealthy && it.disabledUntilMs <= now }
        return if (healthy.isNotEmpty()) healthy.map { it.url } else invidiousPool.map { it.url }
    }

    fun getRankedPipedInstances(): List<String> {
        val now = System.currentTimeMillis()
        val healthy = pipedPool.filter { it.isHealthy && it.disabledUntilMs <= now }
        return if (healthy.isNotEmpty()) healthy.map { it.url } else pipedPool.map { it.url }
    }

    fun getRankedInnerTubeEndpoints(): List<String> {
        val now = System.currentTimeMillis()
        val healthy = innerTubePool.filter { it.isHealthy && it.disabledUntilMs <= now }
        return if (healthy.isNotEmpty()) healthy.map { it.url } else innerTubePool.map { it.url }
    }
}
