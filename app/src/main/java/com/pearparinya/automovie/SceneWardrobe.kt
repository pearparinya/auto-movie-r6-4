package com.pearparinya.automovie

/** Wardrobe defaults are fallbacks; the scene or saved wardrobe always wins. */
internal object SceneWardrobe {
    fun defaultFor(key: String): String = when (key) {
        "gawin" -> "เสื้อโปโลเข้ารูปพอดีตัวสีเทาเข้มกับกางเกงขายาวสีดำ"
        "rinlada" -> "เสื้อแขนยาวคอโค้งสีดำทรงพอดีเอวกับกางเกงขายาวสีครีม"
        "mind" -> "เสื้อคอวีระดับปกติสีครีมทรงพอดีตัวกับกางเกงขายาวสีเบจ"
        else -> "ชุดลำลองสวยงามเหมาะกับกิจกรรม ทรงพอดีตัว"
    }

    fun forScene(key: String, explicit: String): String =
        explicit.takeIf { it.isNotBlank() } ?: defaultFor(key)
}
