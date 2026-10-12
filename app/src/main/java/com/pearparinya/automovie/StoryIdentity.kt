package com.pearparinya.automovie

internal object StoryIdentity {
    private val cast = listOf(Triple("กวิน", "gawin", "GAWIN_MASTER.png"), Triple("รินรดา", "rinlada", "RINLADA_MASTER.png"), Triple("เมย์", "mind", "MIND_MASTER.png"))
    private fun canonical(name: String): String = when (name) { "รินลดา" -> "รินรดา"; "มายด์" -> "เมย์"; else -> name }
    fun keyFor(name: String): String? = cast.firstOrNull { it.first == canonical(name) }?.second
    fun masterFor(name: String): String? = cast.firstOrNull { it.first == canonical(name) }?.third
    fun matchesMaster(name: String, file: String): Boolean {
        val expected = masterFor(name) ?: return false
        return file.substringBeforeLast('.').equals(expected.substringBeforeLast('.'), ignoreCase = true) &&
            file.substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp")
    }
    fun keysFromPrompt(prompt: String): List<String> = cast.filter { prompt.contains(it.first) || (it.second == "rinlada" && prompt.contains("รินลดา")) || (it.second == "mind" && prompt.contains("มายด์")) || prompt.contains(it.third, ignoreCase = true) }.map { it.second }
}
