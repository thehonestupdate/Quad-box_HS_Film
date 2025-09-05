package com.yourdomain.filmviewer.util

import android.content.Intent
import android.net.Uri

object AppLinkParser {

    /** Returns up to four inputs, ordered u1..u4 (nulls if missing). */
    fun getQuadInputs(intent: Intent): List<String?> {
        val data: Uri? = intent.data
        val u1 = intent.getStringExtra("u1") ?: data?.getQueryParameter("u1")
        val u2 = intent.getStringExtra("u2") ?: data?.getQueryParameter("u2")
        val u3 = intent.getStringExtra("u3") ?: data?.getQueryParameter("u3")
        val u4 = intent.getStringExtra("u4") ?: data?.getQueryParameter("u4")
        return listOf(u1, u2, u3, u4)
    }
}
