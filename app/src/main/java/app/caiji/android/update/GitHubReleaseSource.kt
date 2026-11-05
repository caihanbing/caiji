package app.caiji.android.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class GitHubRelease(
    val tagName: String,
    val version: ReleaseVersion,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?
)

internal class GitHubReleaseSource(
    private val scope: CoroutineScope,
    private val fetchOverride: (suspend () -> GitHubRelease)? = null
) {
    private val lock = Any()
    private var inFlight: Deferred<GitHubRelease>? = null

    suspend fun latest(): GitHubRelease {
        val request = synchronized(lock) {
            inFlight?.takeIf { it.isActive } ?: scope.async(
                context = SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job]),
                start = kotlinx.coroutines.CoroutineStart.LAZY
            ) {
                fetchOverride?.invoke() ?: fetchLatestRelease()
            }.also { created ->
                inFlight = created
                created.invokeOnCompletion {
                    synchronized(lock) {
                        if (inFlight === created) inFlight = null
                    }
                }
                created.start()
            }
        }
        return request.await()
    }

    private suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASES_LATEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Caiji-Android")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("GitHub Release 请求失败：HTTP $status")
            parseReleasePayload(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val RELEASES_LATEST_URL = "https://api.github.com/repos/caihanbing/caiji/releases/latest"
    }
}

internal fun parseReleasePayload(payload: String): GitHubRelease {
    try {
        val json = JSONObject(payload)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            throw IOException("GitHub 返回的 Release 不是正式版本")
        }
        val tag = json.optString("tag_name").takeIf(String::isNotBlank)
            ?: throw IOException("GitHub Release 缺少版本标签")
        val version = ReleaseVersion.parse(tag)
            ?: throw IOException("GitHub Release 版本标签格式无效")
        val releaseUrl = json.optString("html_url").takeIf(::isGitHubUrl)
            ?: throw IOException("GitHub Release 链接无效")
        val apkUrl = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").matches(Regex("caiji-.+-release\\.apk")) }
                ?.optString("browser_download_url")
                ?.takeIf(::isGitHubUrl)
        }
        return GitHubRelease(tag, version, json.optString("body").trim(), releaseUrl, apkUrl)
    } catch (error: IOException) {
        throw error
    } catch (error: Exception) {
        throw IOException("GitHub Release 数据格式无效", error)
    }
}

private fun isGitHubUrl(value: String): Boolean = runCatching {
    val url = URL(value)
    url.protocol == "https" && url.host == "github.com"
}.getOrDefault(false)

