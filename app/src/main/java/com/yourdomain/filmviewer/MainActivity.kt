package com.yourdomain.filmviewer

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
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
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

        // Builder with default media source factory (auto‑detects MP4 or HLS)
        player = ExoPlayer.Builder(this)
            .build()
            .also { playerView.player = it }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && currentIndex < videoQueue.size - 1) {
                    playVideo(currentIndex + 1)
                }
            }
        })

        // Deep‑link launch
        intent.data?.getQueryParameter("q")?.let { parseAndStartQueue(it) }

        // Manual launch
        startBtn.setOnClickListener {
            val base64 = linkBox.text.toString().substringAfter("?q=", "")
            parseAndStartQueue(base64)
        }
    }

    /** Decode base64 list → populate queue → start */
    private fun parseAndStartQueue(base64: String) {
        try {
            val decoded = String(android.util.Base64.decode(base64, android.util.Base64.DEFAULT))
            val links = decoded.lines().filter { it.isNotBlank() }
            if (links.isEmpty()) { toast("Queue empty"); return }
            videoQueue = links.toMutableList()
            linkEntry.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            playVideo(0)
        } catch (e: Exception) {
            toast("Bad share link")
        }
    }

    /** Play by index – works for either MP4 or .m3u8 */
    private fun playVideo(index: Int) {
        currentIndex = index
        val uri = Uri.parse(videoQueue[index])
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        player.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    /* =====================   UI helpers   ===================== */
    private fun updateSpeedIndicator() {
        speedIndicator.apply {
            text = String.format("%.2fx", playbackSpeed)
            visibility = if (playbackSpeed == 1.0f) View.GONE else View.VISIBLE
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /* =====================   Key handling   ===================== */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!keyDownTimes.containsKey(keyCode)) keyDownTimes[keyCode] = SystemClock.elapsedRealtime()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.repeatCount == 0) {
                    player.seekTo(player.currentPosition + 5_000)
                    startFastForwardRamp()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.repeatCount == 0) player.seekTo(player.currentPosition - 5_000)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                playbackSpeed = (playbackSpeed + 0.25f).coerceAtMost(10.0f)
                player.playbackParameters = PlaybackParameters(playbackSpeed)
                updateSpeedIndicator(); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playbackSpeed = (playbackSpeed - 0.25f).coerceAtLeast(0.25f)
                player.playbackParameters = PlaybackParameters(playbackSpeed)
                updateSpeedIndicator(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startFastForwardRamp() {
        ffSkipMs = 5_000L
        ffRunnable = Runnable {
            player.seekTo(player.currentPosition + ffSkipMs)
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
        player.release()
        super.onDestroy()
    }
}
