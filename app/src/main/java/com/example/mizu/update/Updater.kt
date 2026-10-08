package com.example.mizu.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.mizu.BuildConfig
import com.example.mizu.core.VersionComparator
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * In-app updater. The only network code in the app, and it runs only when the user asks:
 * reads the latest GitHub Release of [BuildConfig.UPDATE_REPO], downloads its .apk asset,
 * then hands it to the system installer (Android only accepts it if it is signed with the same key).
 */
class Updater(private val context: Context) {
    private val downloadPrefix = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/"

    /** @return the newer release, or null when this build is already the latest. */
    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            if (!VersionComparator.isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null

            val assets = json.optJSONArray("assets") ?: throw IOException("no assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val url = asset.getString("browser_download_url")
                if (asset.getString("name").endsWith(".apk", ignoreCase = true) &&
                    url.startsWith(downloadPrefix, ignoreCase = true)
                ) {
                    return@withContext UpdateInfo(
                        versionName = tag.removePrefix("v"),
                        notes = json.optString("body", "").trim(),
                        apkUrl = url,
                        sizeBytes = asset.optLong("size", 0),
                        sha256 = asset.optString("digest", "").removePrefix("sha256:").ifBlank { null },
                    )
                }
            }
            throw IOException("no apk in release $tag")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(info.apkUrl.startsWith(downloadPrefix, ignoreCase = true)) { "unexpected download url" }
        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val out = File(dir, "mizu-${info.versionName}.apk")
        val conn = open(info.apkUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && !actual.equals(info.sha256, ignoreCase = true)) {
                out.delete()
                throw IOException("checksum mismatch")
            }
            val pkg = context.packageManager.getPackageArchiveInfo(out.path, 0)
            if (pkg == null || pkg.packageName != context.packageName) {
                out.delete()
                throw IOException("not a Mizu package")
            }
            out
        } finally {
            conn.disconnect()
        }
    }

    /** Opens the system installer, or the "install unknown apps" screen first if Mizu is not yet allowed. */
    fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mizu/${BuildConfig.VERSION_NAME}")
        }
}
