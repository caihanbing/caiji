package app.caiji.android.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AppRelease(
    val tagName: String,
    val version: ReleaseVersion,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?
)

internal fun interface ReleaseSource {
    suspend fun latest(): AppRelease
}

internal class OrderedReleaseSource(
    private val preferred: ReleaseSource,
    private val fallback: ReleaseSource
) : ReleaseSource {
    override suspend fun latest(): AppRelease = try {
        preferred.latest()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback.latest()
    }
}

private class SharedReleaseRequest(
    private val scope: CoroutineScope,
    private val fetch: suspend () -> AppRelease
) : ReleaseSource {
    private val lock = Any()
    private var inFlight: Deferred<AppRelease>? = null

    override suspend fun latest(): AppRelease {
        val request = synchronized(lock) {
            inFlight?.takeIf { it.isActive } ?: scope.async(
                context = SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job]),
                start = kotlinx.coroutines.CoroutineStart.LAZY
            ) { fetch() }.also { created ->
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
}

internal class GitHubReleaseSource(
    scope: CoroutineScope,
    fetchOverride: (suspend () -> AppRelease)? = null
) : ReleaseSource {
    private val shared = SharedReleaseRequest(scope) { fetchOverride?.invoke() ?: fetchGitHubRelease() }
    override suspend fun latest(): AppRelease = shared.latest()
}

internal class GiteeReleaseSource(
    scope: CoroutineScope,
    fetchOverride: (suspend () -> AppRelease)? = null
) : ReleaseSource {
    private val shared = SharedReleaseRequest(scope) { fetchOverride?.invoke() ?: fetchGiteeRelease() }
    override suspend fun latest(): AppRelease = shared.latest()
}

private suspend fun fetchGitHubRelease(): AppRelease = fetchRelease(
    "https://api.github.com/repos/caihanbing/caiji/releases/latest",
    "github.com",
    mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28")
) { parseGitHubReleasePayload(it) }

private suspend fun fetchGiteeRelease(): AppRelease = fetchRelease(
    "https://gitee.com/api/v5/repos/caihanbingo/caiji/releases/latest",
    "gitee.com",
    mapOf("Accept" to "application/json")
) { parseGiteeReleasePayload(it) }

private suspend fun fetchRelease(
    endpoint: String,
    sourceHost: String,
    headers: Map<String, String>,
    parse: (String) -> AppRelease
): AppRelease = withContext(Dispatchers.IO) {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("User-Agent", "Caiji-Android")
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("Release 请求失败：HTTP $status")
        parse(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw if (error is IOException) error else IOException("$sourceHost Release 数据格式无效", error)
    } finally {
        connection.disconnect()
    }
}

internal fun parseReleasePayload(payload: String): AppRelease = parseGitHubReleasePayload(payload)

internal fun parseGitHubReleasePayload(payload: String): AppRelease = parseReleasePayload(
    payload = payload,
    sourceHost = "github.com",
    releaseUrlForTag = { tag -> "https://github.com/caihanbing/caiji/releases/tag/$tag" },
    explicitReleaseUrl = { json -> json.optString("html_url") },
    isValidUrl = ::isGitHubUrl
)

internal fun parseGiteeReleasePayload(payload: String): AppRelease = parseReleasePayload(
    payload = payload,
    sourceHost = "gitee.com",
    releaseUrlForTag = { tag -> "https://gitee.com/caihanbingo/caiji/releases/tag/$tag" },
    explicitReleaseUrl = { json -> json.optString("html_url").takeUnless { it.isBlank() || it == "null" } },
    isValidUrl = ::isGiteeUrl
)

private fun parseReleasePayload(
    payload: String,
    sourceHost: String,
    releaseUrlForTag: (String) -> String,
    explicitReleaseUrl: (JSONObject) -> String?,
    isValidUrl: (String) -> Boolean
): AppRelease {
    try {
        val json = JSONObject(payload)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            throw IOException("$sourceHost Release 不是正式版本")
        }
        val tag = json.optString("tag_name").takeIf(String::isNotBlank)
            ?: throw IOException("$sourceHost Release 缺少版本标签")
        val version = ReleaseVersion.parse(tag)
            ?: throw IOException("$sourceHost Release 版本标签格式无效")
        val releaseUrl = explicitReleaseUrl(json)?.takeIf(isValidUrl)
            ?: if (sourceHost == "gitee.com" && explicitReleaseUrl(json) == null) releaseUrlForTag(tag)
            else throw IOException("$sourceHost Release 链接无效")
        val apkName = Regex("caiji-.+-release\\.apk")
        val apkUrl = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").matches(apkName) }
                ?.optString("browser_download_url")
                ?.takeIf(isValidUrl)
        }
        return AppRelease(tag, version, json.optString("body").trim(), releaseUrl, apkUrl)
    } catch (error: IOException) {
        throw error
    } catch (error: Exception) {
        throw IOException("$sourceHost Release 数据格式无效", error)
    }
}

private fun isGitHubUrl(value: String): Boolean = isHttpsHost(value, "github.com")
private fun isGiteeUrl(value: String): Boolean = isHttpsHost(value, "gitee.com")

private fun isHttpsHost(value: String, expectedHost: String): Boolean = runCatching {
    val url = URL(value)
    url.protocol == "https" && url.host == expectedHost
}.getOrDefault(false)
