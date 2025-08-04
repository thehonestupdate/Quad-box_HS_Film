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

    private var keyDownTimes = mutableMapOf<Int, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ffRunnable: Runnable
    private var ffSkipMs = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        linkEntry = findViewById(R.id.linkEntry)
        linkBox = findViewById(R.id.linkBox)
        startBtn = findViewById(R.id.startBtn)
        playerView = findViewById(R.id.player_view)
        speedIndicator = findViewById(R.id.speedIndicator)

        // Initialize ExoPlayer with default buffering
        player = ExoPlayer.Builder(this).build().also { playerView.player = it }

        // Handle shareable ?q= links
        intent.data?.getQueryParameter("q")?.let { code ->
            val decoded = String(android.util.Base64.decode(code, android.util.Base64.DEFAULT))
            val links = decoded.lines().filter { it.isNotBlank() }
            if (links.isNotEmpty()) {
                videoQueue = links.toMutableList()
                startPlayback()
            }
        }

        startBtn.setOnClickListener {
            val q = linkBox.text.toString().trim().substringAfter("?q=", "")
            try {
                val decoded = String(android.util.Base64.decode(q, android.util.Base64.DEFAULT))
                val links = decoded.lines().filter { it.isNotBlank() }
                if (links.isNotEmpty()) {
                    videoQueue = links.toMutableList()
                    startPlayback()
                } else toast("No links in queue")
            } catch (e: Exception) {
                toast("Invalid link format")
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && currentIndex < videoQueue.size - 1) {
                    playVideo(currentIndex + 1)
                }
            }
        })
    }

    private fun startPlayback() {
        linkEntry.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        playbackSpeed = 1.0f
        speedIndicator.visibility = View.GONE
        playVideo(0)
    }

    private fun playVideo(index: Int) {
        currentIndex = index
        val uri = Uri.parse(videoQueue[index])
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        player.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    private fun updateSpeedIndicator() {
        if (playbackSpeed != 1.0f) {
            speedIndicator.text = String.format("%.2fx", playbackSpeed)
            speedIndicator.visibility = View.VISIBLE
        } else speedIndicator.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!keyDownTimes.containsKey(keyCode)) keyDownTimes[keyCode] = SystemClock.elapsedRealtime()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.repeatCount == 0) {
                    player.seekTo(player.currentPosition + 5000L)
                    ffSkipMs = 5000L
                    ffRunnable = Runnable {
                        player.seekTo(player.currentPosition + ffSkipMs)
                        ffSkipMs += 5000L
                        handler.postDelayed(ffRunnable, 300)
                    }
                    handler.postDelayed(ffRunnable, 700)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.repeatCount == 0) player.seekTo(player.currentPosition - 5000L)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                playbackSpeed = (playbackSpeed + 0.25f).coerceAtMost(10.0f)
                player.playbackParameters = PlaybackParameters(playbackSpeed)
                updateSpeedIndicator()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playbackSpeed = (playbackSpeed - 0.25f).coerceAtLeast(0.25f)
                player.playbackParameters = PlaybackParameters(playbackSpeed)
                updateSpeedIndicator()
                return true
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        handler.removeCallbacks(ffRunnable)
        val down = keyDownTimes.remove(keyCode)
        val held = down?.let { SystemClock.elapsedRealtime() - it } ?: 0L
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && held >= 700) {
            if (currentIndex < videoQueue.size - 1) playVideo(currentIndex + 1)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }
}
