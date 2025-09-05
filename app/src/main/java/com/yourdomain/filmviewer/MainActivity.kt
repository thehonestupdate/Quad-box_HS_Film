package com.yourdomain.Quad-box_HS_Film

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.StyledPlayerView

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var linkEntry: LinearLayout
    private lateinit var linkBox: EditText
    private lateinit var startBtn: TextView
    private lateinit var speedIndicator: TextView

    private var playbackSpeed = 1.0f
    private var videoQueue = mutableListOf<String>()
    private var currentIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private var ffRunnable: Runnable? = null
    private var ffSkipMs = 5_000L
    private val keyDownTimes = mutableMapOf<Int, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI refs
        linkEntry = findViewById(R.id.linkEntry)
        linkBox = findViewById(R.id.linkBox)
        startBtn = findViewById(R.id.startBtn)
        playerView = findViewById(R.id.player_view)
        speedIndicator = findViewById(R.id.speedIndicator)

        // If launched via deep link, route to Quad when appropriate BEFORE building single-player
        intent.data?.let { data ->
            if (isQuadUri(data)) {
                launchQuad(data)
                finish()
                return
            }
        }

        // Build single-player ExoPlayer (Media3)
        player = ExoPlayer.Builder(this).build().also { p ->
            playerView.player = p
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && currentIndex < videoQueue.size - 1) {
                        playVideo(currentIndex + 1)
                    }
                }
            })
        }

        // Deep-link (single queue) support: ?q=BASE64 or direct URL
        intent.data?.let { data ->
            val q = data.getQueryParameter("q")
            when {
                q != null -> parseAndStartQueue(q)
                data.scheme?.startsWith("http") == true -> {
                    // Treat as a direct single URL
                    startSingleFromUrl(data.toString())
                }
            }
        }

        // Manual launch from entry box
        startBtn.setOnClickListener {
            val raw = linkBox.text.toString().trim()
            if (raw.isEmpty()) {
                toast("Paste a link")
                return@setOnClickListener
            }

            val maybe = runCatching { Uri.parse(raw) }.getOrNull()
            if (maybe != null && isQuadUri(maybe)) {
                launchQuad(maybe)
                return@setOnClickListener
            }

            // If it's a builder link with ?q=..., use that. Otherwise treat as single/queue.
            val base64FromUrl = maybe?.getQueryParameter("q")
            if (!base64FromUrl.isNullOrBlank()) {
                parseAndStartQueue(base64FromUrl)
            } else {
                // raw could be a base64 blob (just the q value) or a direct media URL
                parseAndStartQueue(raw)
            }
        }
    }

    /** Detect quad URLs:
     *  - filmviewer://quad?u1=...
     *  - https://any.host/quad?u1=...
     */
    private fun isQuadUri(uri: Uri): Boolean {
        val pathLooksQuad = uri.path?.startsWith("/quad") == true
        val schemeHostQuad = (uri.scheme == "filmviewer" && uri.host == "quad")
        val hasAnyU = uri.getQueryParameter("u1") != null ||
                uri.getQueryParameter("u2") != null ||
                uri.getQueryParameter("u3") != null ||
                uri.getQueryParameter("u4") != null
        return (schemeHostQuad || pathLooksQuad) && hasAnyU
    }

    private fun launchQuad(uri: Uri) {
        startActivity(Intent(this, ui.QuadPlayerActivity::class.java).setData(uri))
    }

    /** Decode base64 queue if possible; otherwise treat input as a single URL */
    private fun parseAndStartQueue(base64OrUrl: String) {
        val decoded = runCatching {
            val bytes = android.util.Base64.decode(base64OrUrl, android.util.Base64.DEFAULT)
            String(bytes)
        }.getOrNull().orEmpty()

        val links: List<String> = when {
            decoded.isNotBlank() && (decoded.contains("http://") || decoded.contains("https://")) ->
                decoded.lines().map { it.trim() }.filter { it.isNotBlank() }
            base64OrUrl.startsWith("http://") || base64OrUrl.startsWith("https://") ->
                listOf(base64OrUrl)
            else -> emptyList()
        }

        if (links.isEmpty()) {
            toast("Bad or empty link")
            return
        }

        videoQueue = links.toMutableList()
        linkEntry.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        playVideo(0)
    }

    private fun startSingleFromUrl(url: String) {
        videoQueue = mutableListOf(url)
        linkEntry.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        playVideo(0)
    }

    /** Play by index – handles MP4 or HLS (.m3u8) */
    private fun playVideo(index: Int) {
        currentIndex = index
        val uri = Uri.parse(videoQueue[index])
        player?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            playbackParameters = PlaybackParameters(playbackSpeed)
        }
    }

    /* =====================   UI helpers   ===================== */
    private fun updateSpeedIndicator() {
        speedIndicator.apply {
            text = String.format("%.2fx", playbackSpeed)
            visibility = if (playbackSpeed == 1.0f) View.GONE else View.VISIBLE
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /* =====================   Key handling   ===================== */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!keyDownTimes.containsKey(keyCode)) keyDownTimes[keyCode] = SystemClock.elapsedRealtime()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.repeatCount == 0) {
                    player?.seekTo((player?.currentPosition ?: 0L) + 5_000)
                    startFastForwardRamp()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.repeatCount == 0) {
                    player?.seekTo((player?.currentPosition ?: 0L) - 5_000)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                playbackSpeed = (playbackSpeed + 0.25f).coerceAtMost(10.0f)
                player?.playbackParameters = PlaybackParameters(playbackSpeed)
                updateSpeedIndicator(); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playbackSpeed = (playbackSpeed - 0.25f).coerceAtLeast(0.25f)
                player?.playbackParameters = PlaybackParameters(playbackSpeed)
                updateSpeedIndicator(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) {
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startFastForwardRamp() {
        ffSkipMs = 5_000L
        ffRunnable = Runnable {
            player?.seekTo((player?.currentPosition ?: 0L) + ffSkipMs)
            ffSkipMs += 5_000L
            handler.postDelayed(ffRunnable!!, 300)
        }
        handler.postDelayed(ffRunnable!!, 700)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        ffRunnable?.let { handler.removeCallbacks(it) }
        val down = keyDownTimes.remove(keyCode) ?: 0L
        val held = SystemClock.elapsedRealtime() - down
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && held >= 700 && currentIndex < videoQueue.size - 1) {
            playVideo(currentIndex + 1)
        }
        return super.onKeyUp(keyCode, event)
    }

    /* =====================   Lifecycle   ===================== */
    override fun onDestroy() {
        ffRunnable?.let { handler.removeCallbacks(it) }
        player?.release()
        player = null
        super.onDestroy()
    }
}
