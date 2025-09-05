package com.yourdomain.filmviewer.stream

interface StreamResolver {
    /** Resolve a raw user-provided string into a playable StreamSource.
     *  May perform network I/O (suspend).
     */
    suspend fun resolve(raw: String): StreamSource
}
