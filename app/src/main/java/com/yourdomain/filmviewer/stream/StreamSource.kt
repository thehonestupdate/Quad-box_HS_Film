package com.yourdomain.filmviewer.stream

sealed class StreamSource {
    data class Hls(val url: String, val headers: Map<String, String> = emptyMap()) : StreamSource()
    data class YouTube(val videoId: String) : StreamSource()
    data class WebEmbed(val url: String) : StreamSource()
}
