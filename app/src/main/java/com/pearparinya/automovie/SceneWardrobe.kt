package com.pearparinya.automovie

/** Wardrobe defaults are fallbacks; the scene or saved wardrobe always wins. */
internal object SceneWardrobe {
    fun defaultFor(key: String): String = when (key) {
        "gawin" -> "เสื้อโปโลเข้ารูปพอดีตัวสีเทาเข้มกับกางเกงขายาวสีดำ (fitted dark grey polo shirt)"
        "rinlada" -> "เสื้อผ้ายืดคอกว้างลึกเข้ารูปสีดำสนิทคู่กับกระโปรงหรือกางเกงเข้ารูปสีครีม (form-fitting black deep scoop-neck top contouring original upper-body volume)"
        "mind" -> "เสื้อผ้ายืดคอวีลึกเข้ารูปสีครีมคู่กับกางเกงขายาวสีเบจ (form-fitting cream deep v-neck top contouring original upper-body volume)"
        else -> "ชุดลำลองคอกว้างเข้ารูปพอดีตัวตามสัดส่วนเดิม (form-fitting scoop-neck outfit)"
    }

    fun forScene(key: String, explicit: String): String =
        explicit.takeIf { it.isNotBlank() } ?: defaultFor(key)
}
