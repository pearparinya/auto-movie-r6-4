package com.pearparinya.automovie

/** Describe visible proportions without inferring measurements or reusing another person's build. */
internal object BodyReferencePolicy {
    val fields = listOf("body" to "รูปร่างโดยรวม", "shoulders_torso" to "ไหล่และความยาวลำตัว",
        "chest_waist" to "ความสัมพันธ์ทรวงอกและเอว", "hips_limbs" to "สะโพก แขนและขาที่เห็น", "reference_limits" to "ส่วนที่มองไม่เห็น / ข้อจำกัดมุมภาพ")
    val guide = "ยึดโครงร่างจาก MASTER ตรงรายคน: ความสัมพันธ์ของไหล่ ทรวงอก เอว สะโพก ความยาวลำตัวและแขนขาที่มองเห็น ให้เสื้อผ้ารับกับรูปร่างเดิมตามธรรมชาติ ไม่ปรับรูปร่างให้เข้ากับชุด ไม่ทำให้ผอมหรือเพิ่มสัดส่วนเอง ไม่ใช้รูปร่างคนหนึ่งแทนอีกคน คำนึงถึงมุมกล้อง ท่าทางและเนื้อผ้า ไม่เดาขนาดจริงหรือรายละเอียดส่วนที่ภาพไม่แสดง ภาพฉากก่อนหน้าใช้ดูสถานที่และอุปกรณ์ ไม่ใช้แทน MASTER ด้านรูปร่าง"
    fun singleCheck(name: String, reference: String, profile: String, outfit: String, aspect: String): String = """
สร้างภาพทดสอบหนึ่งภาพของตัวละครผู้ใหญ่ $name เพียงคนเดียว ใช้ $reference ที่แนบเป็นต้นฉบับ
ข้อมูลรายคน: $profile
$guide
ชุดของฉากที่จะใช้จริง: $outfit
ยืนผ่อนคลาย หันหน้าหากล้อง แขนไม่บังลำตัว ระยะศีรษะถึงเข่า มุมกล้องระดับสายตา พื้นหลังเรียบ แสงนุ่มสม่ำเสมอ ภาพถ่ายสมจริง $aspect ไม่มีข้อความหรือลายน้ำ
ภาพนี้ใช้ตรวจเทียบใบหน้าและโครงร่างกับ MASTER ก่อนสร้างฉากหลายคน ไม่แทนที่ MASTER ต้นฉบับโดยอัตโนมัติ หากสร้างไม่ได้ให้แจ้งตามจริงและหยุด
    """.trimIndent()
}

internal data class BodyReviewStamp(val sceneHash: String, val masterHash: String, val outfit: String) {
    fun matches(scene: String, master: String, clothes: String) =
        sceneHash.isNotBlank() && masterHash.isNotBlank() && sceneHash == scene && masterHash == master && outfit == clothes
}
