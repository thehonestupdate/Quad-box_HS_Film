package com.yourdomain.filmviewer.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters
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
import androidx.media3.datasource.DefaultHttpDataSource

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

        // Track selectors: cap background tiles, leave focus tile flexible
        bgTrackSelector = DefaultTrackSelector(this).apply {
            parameters = DefaultTrackSelector.Parameters.Builder(this@QuadPlayerActivity).build()
            // For Media3, cap quality via player.trackSelectionParameters (below) after build
        }
        focusTrackSelector = DefaultTrackSelector(this)

        resolver = DefaultResolver(this)

        val inputs = AppLinkParser.getQuadInputs(intent) // list of up to 4 strings
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

    private fun containerOf(index: Int): ViewGroup {
        return findViewById(tileIds[index])
    }

    @SuppressLint("InflateParams")
    private fun attachExoToTile(index: Int, src: StreamSource.Hls) {
        // Clean existing
        destroyTile(index)

        // Inflate a StyledPlayerView
        val container = containerOf(index)
        container.removeAllViews()
        val pv = layoutInflater.inflate(R.layout.partial_player_view, container, false) as StyledPlayerView
        container.addView(pv, ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        ))

        // Build player
        val trackSelector = if (index == 1) focusTrackSelector else bgTrackSelector
        val player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()

        // Cap background tiles: ~540p and ~2Mbps (Media3 way via TrackSelectionParameters)
        if (index != 1) {
            player.trackSelectionParameters = TrackSelectionParameters.Builder(this)
                .setMaxVideoSize(960, 540)
                .setMaxVideoBitrate(2_000_000)
                .build()
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("FilmViewer/1.0")
            .apply {
                if (src.headers.isNotEmpty()) {
                    setDefaultRequestProperties(src.headers)
                }
            }

        val mediaSource = HlsMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(src.url))

        pv.player = player
        players[index] = player

        // Audio policy: only top-right (index 1) has sound
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
        container.addView(web, ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        ))
        web.loadYouTube(videoId, startMuted)
    }

    private fun attachWebEmbedToTile(index: Int, url: String, startMuted: Boolean) {
        destroyTile(index)
        val container = containerOf(index)
        val web = WebTileView(this)
        webTiles[index] = web
        container.removeAllViews()
        container.addView(web, ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        ))
        // For generic embeds, just load the URL. (Some providers handle autoplay only when muted)
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
        // Release to be safe for TV memory constraints
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
