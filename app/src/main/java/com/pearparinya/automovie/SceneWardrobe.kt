package com.pearparinya.automovie

/** Wardrobe defaults are fallbacks; the scene or saved wardrobe always wins. */
internal object SceneWardrobe {
    fun defaultFor(key: String): String = when (key) {
        "gawin" -> "เสื้อโปโลเข้ารูปพอดีตัวสีเทาเข้มกับกางเกงขายาวสีดำ"
        "rinlada" -> "เสื้อผ้ายืดคอกว้างลึกเข้ารูปสีดำสนิทคู่กับกระโปรงหรือกางเกงเข้ารูปสีครีม"
        "mind" -> "เสื้อผ้ายืดคอวีลึกเข้ารูปสีครีมคู่กับกางเกงขายาวสีเบจ"
        else -> "ชุดลำลองคอกว้างเข้ารูปพอดีตัวตามสัดส่วนเดิม"
    }

    fun forScene(key: String, explicit: String): String =
        explicit.takeIf { it.isNotBlank() } ?: defaultFor(key)
}
