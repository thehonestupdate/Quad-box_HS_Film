package com.yourdomain.filmviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yourdomain.filmviewer.ui.QuadPlayerActivity

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var linkEntry: LinearLayout
    private lateinit var linkBox: EditText
    private lateinit var startBtn: TextView

    private var videoQueue = mutableListOf<String>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        linkEntry = findViewById(R.id.linkEntry)
        linkBox = findViewById(R.id.linkBox)
        startBtn = findViewById(R.id.startBtn)
        playerView = findViewById(R.id.player_view)

        // IMPORTANT: no controller UI, no DPAD handling
        playerView.useController = false

        // If launched via quad deep link, route immediately
        intent.data?.let { data ->
            if (isQuadUri(data)) {
                startActivity(Intent(this, QuadPlayerActivity::class.java).setData(data))
                finish()
                return
            }
        }

        // Build single-player (no controls, only for legacy single URL or base64 list)
        val exo = ExoPlayer.Builder(this).build()
        playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && currentIndex < videoQueue.size - 1) {
                    playVideo(currentIndex + 1)
                }
            }
        })
        player = exo

        // Deep-link for single: ?q=BASE64 or direct URL
        intent.data?.let { data ->
            val q = data.getQueryParameter("q")
            when {
                q != null -> parseAndStartQueue(q)
                data.scheme?.startsWith("http") == true -> startSingleFromUrl(data.toString())
            }
        }

        // Manual start from entry box (paste your builder link here)
        startBtn.setOnClickListener {
            val raw = linkBox.text.toString().trim()
            if (raw.isEmpty()) {
                toast("Paste a link")
                return@setOnClickListener
            }

            val maybe = runCatching { Uri.parse(raw) }.getOrNull()
            if (maybe != null && isQuadUri(maybe)) {
                startActivity(Intent(this, QuadPlayerActivity::class.java).setData(maybe))
                return@setOnClickListener
            }

            val base64FromUrl = maybe?.getQueryParameter("q")
            if (!base64FromUrl.isNullOrBlank()) {
                parseAndStartQueue(base64FromUrl)
            } else {
                parseAndStartQueue(raw)
            }
        }

        // Make sure the text box is focusable right away for remote input
        linkEntry.visibility = View.VISIBLE
        linkBox.requestFocus()
    }

    // quad URLs the router understands:
    // - quadboxhsfilm://quad?u1=...
    // - https://<your-builder>/quad?u1=...
    private fun isQuadUri(uri: Uri): Boolean {
        val pathLooksQuad = uri.path?.startsWith("/quad") == true
        val schemeHostQuad = (uri.host == "quad" && (uri.scheme == "filmviewer" || uri.scheme == "quadboxhsfilm"))
        val hasAnyU = uri.getQueryParameter("u1") != null ||
                uri.getQueryParameter("u2") != null ||
                uri.getQueryParameter("u3") != null ||
                uri.getQueryParameter("u4") != null
        return (schemeHostQuad || pathLooksQuad) && hasAnyU
    }

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

    private fun playVideo(index: Int) {
        currentIndex = index
        val uri = Uri.parse(videoQueue[index])
        player?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
