package com.yourdomain.filmviewer.stream

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DefaultResolver(context: Context) : StreamResolver {

    private val client = OkHttpClient()

    // Liberal .m3u8 finder
    private val M3U8 = Regex("""https?://[^\s'"<>()]+\.m3u8[^\s'"<>()]*""", RegexOption.IGNORE_CASE)

    override suspend fun resolve(raw: String): StreamSource = withContext(Dispatchers.IO) {
        val trimmed = raw.trim()
        // If it's already an m3u8 URL, honor directly
        if (looksLikeM3u8Url(trimmed)) return@withContext StreamSource.Hls(trimmed)

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        if (uri == null) {
            // Not a URL; let the player try it as-is (unlikely)
            return@withContext StreamSource.Hls(trimmed)
        }

        val host = uri.host.orEmpty().lowercase()

        // YouTube detection
        if (isYouTubeHost(host)) {
            val vid = extractYouTubeId(uri)
            if (!vid.isNullOrBlank()) return@withContext StreamSource.YouTube(vid)
        }

        // Hudl (vcloud or fan)
        if (host.contains("vcloud.hudl.com") || host.contains("fan.hudl.com") || host.contains("hudl.com")) {
            // Try to fetch and find an m3u8 in the HTML first
            val m3u8 = fetchFirstM3u8(trimmed)
            if (!m3u8.isNullOrBlank()) return@withContext StreamSource.Hls(m3u8)
            // fallback to embedding the page directly
            return@withContext StreamSource.WebEmbed(trimmed)
        }

        // Generic: try fetching HTML and scanning for .m3u8
        val m3u8 = fetchFirstM3u8(trimmed)
        if (!m3u8.isNullOrBlank()) return@withContext StreamSource.Hls(m3u8)

        // Else: if the URL itself is a watch page (e.g., YouTube live but no ID found),
        // fall back to embedding.
        return@withContext StreamSource.WebEmbed(trimmed)
    }

    private fun looksLikeM3u8Url(s: String): Boolean =
        s.contains(".m3u8", ignoreCase = true) && s.startsWith("http")

    private fun isYouTubeHost(host: String): Boolean =
        host.contains("youtube.com") || host.contains("youtu.be")

    private fun extractYouTubeId(uri: Uri): String? {
        val host = uri.host.orEmpty().lowercase()
        return when {
            host.contains("youtu.be") -> uri.lastPathSegment
            host.contains("youtube.com") -> {
                uri.getQueryParameter("v")
                    ?: uri.pathSegments.takeIf { it.isNotEmpty() }?.let { segs ->
                        // handle /live/<id> or /embed/<id>
                        val idx = segs.indexOfFirst { it.equals("live", true) || it.equals("embed", true) }
                        if (idx >= 0 && idx + 1 < segs.size) segs[idx + 1] else null
                    }
            }
            else -> null
        }
    }

    private fun fetchFirstM3u8(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "FilmViewer/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                M3U8.find(body)?.value
            }
        } catch (_: Throwable) {
            null
        }
    }
}
