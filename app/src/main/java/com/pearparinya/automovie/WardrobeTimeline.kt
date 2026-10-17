package com.pearparinya.automovie

internal data class WardrobeBeat(val character: String, val day: String, val event: String,
    val activity: String, val group: String, val outfit: String, val change: Boolean = false)
internal data class WardrobeEntry(val beat: WardrobeBeat, val resolved: String)

/** Explicit scene clothes win; only inherit from the most recent appearance in a compatible event. */
internal object WardrobeTimeline {
    fun activity(text: String): String = when {
        text in listOf("sleep","นอน","ชุดนอน") || listOf("เข้านอน","นอนหลับ","กำลังนอน").any { text.contains(it) } -> "sleep"
        text in listOf("meeting","ประชุม") || text.contains("ประชุม") -> "meeting"
        text in listOf("work","ทำงาน","ทำงานออฟฟิศ") -> "work"
        text in listOf("exercise","ออกกำลังกาย") -> "exercise"
        text in listOf("formal","พิธีการ") -> "formal"
        text in listOf("home","อยู่บ้าน","พักผ่อน") -> "home"
        else -> "casual"
    }
    fun resolve(now: WardrobeBeat, history: List<WardrobeEntry>): String {
        if (now.outfit.isNotBlank()) return now.outfit
        val previous=history.lastOrNull { it.beat.character == now.character }
        if (!now.change && previous != null) {
            val before=previous.beat
            val compatibleDay=(now.day.isBlank() && before.day.isBlank()) || (now.day.isNotBlank() && now.day == before.day)
            val sameEvent=if (now.event.isNotBlank() || before.event.isNotBlank()) now.event.isNotBlank() && now.event == before.event
                else now.group.isNotBlank() && now.group == before.group
            if (compatibleDay && sameEvent && now.activity == before.activity) return previous.resolved
        }
        val color=listOf("กรมท่า","เทาอ่อน","น้ำตาลอ่อน","ฟ้าอ่อน")[Math.floorMod((now.day+now.event+now.group+now.character).hashCode(),4)]
        return when(now.activity) {
            "sleep" -> "ชุดนอนผ้าทึบสี$color ทรงสบายพอดีตัว เหมาะกับการนอน"
            "meeting", "work" -> "เสื้อเชิ้ตสี$color กับกางเกงขายาวสีเข้ม ชุดทำงานเหมาะกับการประชุม"
            "exercise" -> "เสื้อกีฬาสี$color กับกางเกงกีฬา เหมาะกับการออกกำลังกาย"
            "formal" -> "ชุดทางการสี$color เหมาะกับงานพิธีตามบท"
            "home" -> "เสื้อลำลองสี$color กับกางเกงผ้าสบายสำหรับอยู่บ้าน"
            else -> "เสื้อลำลองสี$color กับกางเกงขายาว ทรงพอดีตัว"
        }
    }
}
