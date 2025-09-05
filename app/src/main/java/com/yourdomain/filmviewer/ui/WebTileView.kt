package com.yourdomain.filmviewer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class WebTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var webView: WebView? = null

    init {
        setBackgroundColor(Color.BLACK)
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        createWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            isFocusable = true
            isFocusableInTouchMode = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }
        addView(webView, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    fun loadUrl(url: String, startMuted: Boolean) {
        // Many providers will respect autoplay when muted; not guaranteed universally
        webView?.loadUrl(url)
    }

    fun loadYouTube(videoId: String, startMuted: Boolean) {
        val html = buildYouTubeHtml(videoId, startMuted)
        webView?.loadDataWithBaseURL(
            "https://www.youtube.com",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    fun destroySafely() {
        try {
            webView?.loadUrl("about:blank")
            webView?.stopLoading()
            removeAllViews()
            webView?.destroy()
        } catch (_: Throwable) {}
        webView = null
    }

    private fun buildYouTubeHtml(videoId: String, startMuted: Boolean): String = """
        <html>
        <body style="margin:0;background:black;overflow:hidden;">
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                videoId: '$videoId',
                playerVars: {
                  'autoplay': 1,
                  'controls': 1,
                  'playsinline': 1
                },
                events: { 'onReady': onPlayerReady }
              });
            }
            function onPlayerReady(e) {
              ${if (startMuted) "e.target.mute();" else "e.target.unMute();"}
              e.target.playVideo();
            }
            function mute(){ if(player) player.mute(); }
            function unmute(){ if(player) player.unMute(); }
          </script>
        </body>
        </html>
    """.trimIndent()
}
