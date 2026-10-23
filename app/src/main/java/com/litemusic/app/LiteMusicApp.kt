package com.litemusic.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LiteMusicApp : Application(), ImageLoaderFactory {

    companion object {
        const val CHANNEL_ID = "litemusic_playback_channel"
        private const val TAG = "LiteMusicApp"
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .header("Referer", "https://music.youtube.com/")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        createNotificationChannel()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val appVersion = try {
                    val pInfo = packageManager.getPackageInfo(packageName, 0)
                    val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pInfo.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        pInfo.versionCode.toString()
                    }
                    "${pInfo.versionName} ($vCode)"
                } catch (_: Throwable) {
                    "Unknown"
                }

                val sb = StringBuilder()
                sb.append("====================================================\n")
                sb.append("           LITEMUSIC FATAL CRASH REPORT            \n")
                sb.append("====================================================\n")
                sb.append("Timestamp:    ").append(timestamp).append("\n")
                sb.append("Thread:       ").append(thread.name).append(" (id=").append(thread.id).append(")\n")
                sb.append("App Version:  ").append(appVersion).append("\n")
                sb.append("Android SDK:  ").append(Build.VERSION.SDK_INT).append(" (Android ").append(Build.VERSION.RELEASE).append(")\n")
                sb.append("Device:       ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n")
                sb.append("Brand/Board:  ").append(Build.BRAND).append(" / ").append(Build.BOARD).append("\n")
                sb.append("Fingerprint:  ").append(Build.FINGERPRINT).append("\n")
                sb.append("Exception:    ").append(throwable.javaClass.name).append("\n")
                sb.append("Message:      ").append(throwable.message).append("\n\n")
                sb.append("Stack Trace:\n")
                sb.append(stackTrace)
                sb.append("\n====================================================\n")

                val report = sb.toString()
                Log.e(TAG, "FATAL UNCAUGHT CRASH:\n$report")

                // 1. SharedPreferences backup
                try {
                    getSharedPreferences("litemusic_crash_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_crash_log", report)
                        .putLong("last_crash_time", System.currentTimeMillis())
                        .commit()
                } catch (_: Throwable) {}

                // 2. Download folder: /storage/emulated/0/Download/LiteMusic_crash.txt
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadDir.exists()) downloadDir.mkdirs()
                    val file = File(downloadDir, "LiteMusic_crash.txt")
                    file.writeText(report)
                } catch (_: Throwable) {
                    try {
                        val directFile = File("/storage/emulated/0/Download/LiteMusic_crash.txt")
                        directFile.writeText(report)
                    } catch (_: Throwable) {}
                }

                // 3. App-specific external files (accessible without permissions across Android 7-15)
                try {
                    val extDir = getExternalFilesDir(null)
                    if (extDir != null) {
                        File(extDir, "LiteMusic_crash.txt").writeText(report)
                    }
                } catch (_: Throwable) {}

                // 4. App-specific internal files
                try {
                    File(filesDir, "LiteMusic_crash.txt").writeText(report)
                } catch (_: Throwable) {}

            } catch (err: Throwable) {
                Log.e(TAG, "Failed in crash handler", err)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
