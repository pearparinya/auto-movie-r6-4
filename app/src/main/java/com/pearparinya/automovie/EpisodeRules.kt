package com.pearparinya.automovie

internal object EpisodeRules {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val INTRO_MS = 3000L
    fun filename(title: String, ep: Int, episodeTitle: String): String {
        require(ep > 0)
        val raw = "${title}_EP%02d_${episodeTitle}".format(ep)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        var safe = raw.ifBlank { "EP%02d".format(ep) }
        while(safe.toByteArray(Charsets.UTF_8).size > 200) safe = safe.dropLast(1)
        return "$safe.mp4"
    }
    fun estimateBytes(durationMs: Long, bitrate: Int, sourceBytes: Long): Long =
        sourceBytes + durationMs * bitrate / 8000 * 2 + 100L * 1024 * 1024
}
