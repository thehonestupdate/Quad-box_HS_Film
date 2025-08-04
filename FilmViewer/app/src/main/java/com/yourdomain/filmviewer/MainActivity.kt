package com.yourdomain.filmviewer

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var linkEntry: LinearLayout
    private lateinit var linkBox: EditText
    private lateinit var startBtn: Button
    private lateinit var speedIndicator: TextView

    private var playbackSpeed = 1.0f
    private var videoQueue = mutableListOf<String>()
    private var currentIndex = 0

    // Long press handling
    private var keyDownTimes = mutableMapOf<Int, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        linkEntry = findViewById(R.id.linkEntry)
        linkBox = findViewById(R.id.linkBox)
        startBtn = findViewById(R.id.startBtn)
        playerView = findViewById(R.id.player_view)
        speedIndicator = findViewById(R.id.speedIndicator)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // If app was launched with a URL that contains ?q=..., parse it
        intent?.data?.let { uri ->
            uri.getQueryParameter("q")?.let { code ->
                val decoded = String(android.util.Base64.decode(code, android.util.Base64.DEFAULT))
                val links = decoded.split("\n".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
                if (links.isNotEmpty()) {
                    videoQueue.clear()
                    videoQueue.addAll(links)
                    startPlayback()
                }
            }
        }

        startBtn.setOnClickListener {
            val url = linkBox.text.toString().trim()
            val qIndex = url.indexOf("?q=")
            if (qIndex != -1) {
                val base64 = url.substring(qIndex + 3)
                try {
                    val decoded = String(android.util.Base64.decode(base64, android.util.Base64.DEFAULT))
                    val links = decoded.split("\n".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
                    if (links.isNotEmpty()) {
                        videoQueue.clear()
                        videoQueue.addAll(links)
                        startPlayback()
                    } else {
                        toast("No links found in queue")
                    }
                } catch (e: Exception) {
                    toast("Invalid shareable link")
                }
            } else {
                toast("Paste a full URL that contains ?q=...")
            }
        }

        player.addListener(object : com.google.android.exoplayer2.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == com.google.android.exoplayer2.Player.STATE_ENDED) {
                    // Auto-advance
                    if (currentIndex < videoQueue.size - 1) {
                        playVideo(currentIndex + 1)
                    }
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
        val item = MediaItem.fromUri(uri)
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        player.playbackParameters = player.playbackParameters.withSpeed(playbackSpeed)
    }

    private fun updateSpeedIndicator() {
        if (playbackSpeed != 1.0f) {
            speedIndicator.visibility = View.VISIBLE
            speedIndicator.text = String.format("%.2fx", playbackSpeed)
        } else {
            speedIndicator.visibility = View.GONE
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Record down time for long-press
        if (!keyDownTimes.containsKey(keyCode)) {
            keyDownTimes[keyCode] = SystemClock.elapsedRealtime()
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.repeatCount == 0) {
                    // single press = +5s
                    player.seekTo((player.currentPosition + 5000L).coerceAtLeast(0L))
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.repeatCount == 0) {
                    // single press = -5s
                    player.seekTo((player.currentPosition - 5000L).coerceAtLeast(0L))
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                playbackSpeed = (playbackSpeed + 0.25f).coerceAtMost(4.0f)
                player.playbackParameters = player.playbackParameters.withSpeed(playbackSpeed)
                updateSpeedIndicator()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playbackSpeed = (playbackSpeed - 0.25f).coerceAtLeast(0.25f)
                player.playbackParameters = player.playbackParameters.withSpeed(playbackSpeed)
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
        // detect long press duration
        val down = keyDownTimes.remove(keyCode)
        val heldMs = if (down != null) SystemClock.elapsedRealtime() - down else 0L

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (heldMs >= 700) {
                    // long press = +20s
                    player.seekTo((player.currentPosition + 20000L).coerceAtLeast(0L))
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (heldMs >= 700) {
                    // long press = -20s
                    player.seekTo((player.currentPosition - 20000L).coerceAtLeast(0L))
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (heldMs >= 700) {
                    // long press = next video
                    if (currentIndex < videoQueue.size - 1) {
                        playVideo(currentIndex + 1)
                    }
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
