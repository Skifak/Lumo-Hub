package com.lumo.hub.network

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class AppRelease(val version: String, val downloadUrl: String)
sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Downloading(val progress: Int) : AppUpdateState
    data class Current(val version: String) : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

class AppUpdateRepository(private val context: Context) {
    private val client = OkHttpClient()
    private val latestUrl = "https://api.github.com/repos/Skifak/Lumo-Hub/releases/latest"
    fun currentVersion() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

    suspend fun check(): AppUpdateState = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(latestUrl).header("Accept", "application/vnd.github+json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub вернул код ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                val version = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(json.optString("tag_name"))?.groupValues?.drop(1)?.joinToString(".")
                    ?: error("Некорректный тег релиза")
                val assets = json.optJSONArray("assets") ?: error("В релизе нет release.apk")
                val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == "release.apk" }
                    ?: error("В релизе нет release.apk")
                val release = AppRelease(version, asset.getString("browser_download_url"))
                if (compare(version, currentVersion()) > 0) AppUpdateState.Available(release) else AppUpdateState.Current(currentVersion())
            }
        } catch (t: Throwable) { AppUpdateState.Error(t.message ?: "Не удалось проверить обновления") }
    }

    suspend fun downloadAndInstall(release: AppRelease, onProgress: (Int) -> Unit = {}): AppUpdateState = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val temporaryApk = File(updatesDir, ".release.apk.download")
        val apk = File(updatesDir, "release.apk")
        var call: okhttp3.Call? = null
        try {
            temporaryApk.delete()
            call = client.newCall(Request.Builder().url(release.downloadUrl).build())
            val activeCall = call!!
            currentCoroutineContext().job.invokeOnCompletion { activeCall.cancel() }
            activeCall.execute().use { response ->
                if (!response.isSuccessful) error("Не удалось скачать APK: ${response.code}")
                val body = response.body ?: error("Пустой APK")
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    temporaryApk.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            // A server may omit Content-Length; 0 means indeterminate in the UI.
                            onProgress(if (total > 0) ((downloaded * 100 / total).toInt().coerceIn(0, 100)) else 0)
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (!temporaryApk.isFile || temporaryApk.length() == 0L) error("Пустой APK")
            if (apk.exists() && !apk.delete()) error("Не удалось заменить старый APK")
            if (!temporaryApk.renameTo(apk)) error("Не удалось сохранить APK")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) })
            AppUpdateState.Available(release)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) { AppUpdateState.Error(t.message ?: "Не удалось скачать обновление")
        } finally {
            if (temporaryApk.exists()) temporaryApk.delete()
        }
    }

    private fun compare(a: String, b: String): Int = (0..2).firstOrNull { a.split('.')[it].toInt() != (b.split('.').getOrNull(it)?.toIntOrNull() ?: 0) }?.let { a.split('.')[it].toInt().compareTo(b.split('.').getOrNull(it)?.toIntOrNull() ?: 0) } ?: 0
}
