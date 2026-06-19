package com.example.currencyconverter

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import java.io.File

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GithubAsset>
)

data class GithubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/atlanteg/axe-currency/releases/latest"
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun getLatestRelease(): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val body = client.newCall(req).execute().body?.string() ?: return@withContext null
            gson.fromJson(body, GithubRelease::class.java)
        } catch (_: Exception) { null }
    }

    fun versionCodeFromTag(tag: String): Int =
        tag.trimStart('v').toIntOrNull() ?: 0

    fun displayVersion(release: GithubRelease): String {
        release.body?.lines()?.forEach { line ->
            if (line.startsWith("versionName="))
                return line.removePrefix("versionName=").trim()
        }
        return release.tagName.trimStart('v')
    }

    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            val response = client.newCall(req).execute()
            val body = response.body ?: return@withContext
            val total = body.contentLength()

            val apkDir = File(context.cacheDir, "apk").apply { mkdirs() }
            val apkFile = File(apkDir, "axe-update.apk")

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }

            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
