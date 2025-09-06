package com.yourdomain.filmviewer.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.StyledPlayerView
import com.yourdomain.filmviewer.R
import com.yourdomain.filmviewer.stream.DefaultResolver
import com.yourdomain.filmviewer.stream.StreamSource
import com.yourdomain.filmviewer.util.AppLinkParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QuadPlayerActivity : AppCompatActivity() {

    private val tileIds = intArrayOf(
        R.id.tile0, // top-left
        R.id.tile1, // top-right (AUDIO)
        R.id.tile2, // bottom-left
        R.id.tile3  // bottom-right
    )

    private val players = arrayOfNulls<ExoPlayer>(4)
    private val webTiles = arrayOfNulls<WebTileView>(4)

    private lateinit var resolver: DefaultResolver

    private lateinit var bgTrackSelector: DefaultTrackSelector
    private lateinit var focusTrackSelector: DefaultTrackSelector

    private var loadJobs = mutableListOf<Job>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quad_player)

        bgTrackSelector = DefaultTrackSelector(this)
        focusTrackSelector = DefaultTrackSelector(this)

        resolver = DefaultResolver(this)

        val inputs = AppLinkParser.getQuadInputs(intent)
        for (i in 0 until 4) {
            val raw = inputs.getOrNull(i)
            if (raw.isNullOrBlank()) continue
            loadJobs += lifecycleScope.launch {
                when (val src = resolver.resolve(raw)) {
                    is StreamSource.Hls -> attachExoToTile(i, src)
                    is StreamSource.YouTube -> attachYouTubeToTile(i, src.videoId, startMuted = i != 1)
                    is StreamSource.WebEmbed -> attachWebEmbedToTile(i, src.url, startMuted = i != 1)
                }
            }
        }
    }

    private fun containerOf(index: Int): ViewGroup = findViewById(tileIds[index])

    @SuppressLint("InflateParams")
    private fun attachExoToTile(index: Int, src: StreamSource.Hls) {
        destroyTile(index)

        val container = containerOf(index)
        container.removeAllViews()
        val pv = layoutInflater.inflate(R.layout.partial_player_view, container, false) as StyledPlayerView
        container.addView(
            pv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val trackSelector = if (index == 1) focusTrackSelector else bgTrackSelector
        val player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()

        if (index != 1) {
            player.trackSelectionParameters = TrackSelectionParameters.Builder(this)
                .setMaxVideoSize(960, 540)
                .setMaxVideoBitrate(2_000_000)
                .build()
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("QuadBoxHSFilm/1.0")
            .apply {
                if (src.headers.isNotEmpty()) {
                    setDefaultRequestProperties(src.headers)
                }
            }

        val mediaSource = HlsMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(src.url))

        pv.player = player
        players[index] = player

        player.volume = if (index == 1) 1f else 0f
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    private fun attachYouTubeToTile(index: Int, videoId: String, startMuted: Boolean) {
        destroyTile(index)
        val container = containerOf(index)
        val web = WebTileView(this)
        webTiles[index] = web
        container.removeAllViews()
        container.addView(
            web,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        web.loadYouTube(videoId, startMuted)
    }

    private fun attachWebEmbedToTile(index: Int, url: String, startMuted: Boolean) {
        destroyTile(index)
        val container = containerOf(index)
        val web = WebTileView(this)
        webTiles[index] = web
        container.removeAllViews()
        container.addView(
            web,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        web.loadUrl(url, startMuted)
    }

    private fun destroyTile(index: Int) {
        players[index]?.release()
        players[index] = null
        webTiles[index]?.destroySafely()
        webTiles[index] = null
        containerOf(index).removeAllViews()
    }

    override fun onStop() {
        super.onStop()
        for (i in 0 until 4) {
            players[i]?.playWhenReady = false
        }
    }

    override fun onDestroy() {
        loadJobs.forEach { it.cancel() }
        for (i in 0 until 4) destroyTile(i)
        super.onDestroy()
    }
}
