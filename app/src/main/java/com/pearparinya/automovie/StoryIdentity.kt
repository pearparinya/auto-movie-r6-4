package com.pearparinya.automovie

internal object StoryIdentity {
    private val cast = listOf(Triple("กวิน", "gawin", "GAWIN_MASTER.png"), Triple("รินลดา", "rinlada", "RINLADA_MASTER.png"), Triple("มายด์", "mind", "MIND_MASTER.png"))
    fun keyFor(name: String): String? = cast.firstOrNull { it.first == name }?.second
    fun masterFor(name: String): String? = cast.firstOrNull { it.first == name }?.third
    fun keysFromPrompt(prompt: String): List<String> = cast.filter { prompt.contains(it.first) || prompt.contains(it.third, ignoreCase = true) }.map { it.second }
}
