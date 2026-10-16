package com.pearparinya.automovie

/** A shared brief for image requests. References identify people; scene facts define clothes and action. */
internal object ImagePromptPolicy {
    fun identity(source: String) =
        "ใช้ $source อ้างอิงตัวละครให้ตรง MASTER รายคน คงเอกลักษณ์ใบหน้า ทรงผม สีผิว และสัดส่วนรูปร่างตามภาพอย่างเป็นธรรมชาติ เครื่องแต่งกายตามบทของฉากปัจจุบันแยกจากข้อมูลชุดในภาพอ้างอิง"

    fun style(aspect: String, framing: String = "", lighting: String = "") =
        "ภาพถ่ายสมจริง $aspect • ${framing.ifBlank { "จัดระยะให้เห็นใบหน้าและการกระทำครบตามฉาก" }} • ${lighting.ifBlank { "แสงเหมาะกับเวลาและสถานที่ตามบท" }}"

    fun scene(cast: String, location: String, action: String, aspect: String = "9:16",
              source: String = "ภาพ MASTER ที่แนบ", framing: String = "", lighting: String = "",
              continuity: String = "", filename: String = ""): String = buildString {
        append("สร้างภาพจริงหนึ่งภาพด้วยเครื่องมือสร้างภาพและแสดงในแชต\n${identity(source)}\nตัวละครและชุด:\n$cast\nสถานที่: $location\nการกระทำ: $action\n${style(aspect,framing,lighting)}\n")
        if (continuity.isNotBlank()) append("ความต่อเนื่อง: $continuity\n")
        append("ไม่มีข้อความหรือลายน้ำ")
        if (filename.isNotBlank()) append("\nชื่อไฟล์: $filename")
        append("\nหากสร้างไม่ได้ให้แจ้งตามจริง")
    }

    fun edit(source: String, change: String, aspect: String = "9:16", cast: String = "", filename: String = ""): String = buildString {
        append("แก้ไข $source ที่แนบและแสดงภาพจริงหนึ่งภาพ\nเปลี่ยนเฉพาะ: $change\n")
        if (cast.isNotBlank()) append("ตัวละครอ้างอิง:\n$cast\n")
        append("คงบุคคล ใบหน้า ทรงผม สีผิว รูปร่าง ท่าทาง มือ ระยะกล้อง แสง และองค์ประกอบที่ไม่ได้ขอเปลี่ยนตามภาพเดิม\nภาพถ่ายสมจริง $aspect ไม่มีข้อความหรือลายน้ำ")
        if (filename.isNotBlank()) append("\nชื่อไฟล์: $filename")
        append("\nหากแก้ไขไม่ได้ให้แจ้งตามจริง")
    }

    fun newCharacter(reference: String, location: String) =
        "สร้างภาพจริงหนึ่งภาพของตัวละครสมมุติผู้ใหญ่คนใหม่ ตั้งชื่อไทย ออกแบบใบหน้าและทรงผมใหม่\nอ้างอิง $reference เฉพาะสไตล์ภาพและความสมจริง\nชุด: ชุดลำลองเหมาะกับกิจกรรม\nสถานที่: $location\nการกระทำ: เลือกหนึ่งกิจกรรมในชีวิตประจำวัน\n${style("9:16")} ไม่มีข้อความหรือลายน้ำ\nแสดงภาพจริงแล้วแจ้งชื่อและชุดสั้น ๆ หากสร้างไม่ได้ให้แจ้งตามจริง"

    fun coverReference(source: String) =
        "ใช้ $source อ้างอิงตัวละคร ใบหน้า ทรงผม รูปร่าง ชุดและสถานที่ตามตอน จัดภาพถ่ายสมจริงให้สื่อเหตุการณ์เด่น และเว้นพื้นที่ข้อความตามรูปแบบปก"
}
