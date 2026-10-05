from pathlib import Path

path = Path("app/src/main/java/com/pearparinya/automovie/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# BUILD 718 features are still source-transformed at build time. Apply them first,
# then strengthen only the generated prompt so the stable Activity remains untouched.
exec(Path("scripts/apply_build718.py").read_text(encoding="utf-8"), {"__name__": "__build718__"})
s = path.read_text(encoding="utf-8")

camera_anchor = '''    private fun buildQuickSceneCommand(): String {
'''
helpers = '''    // BUILD 719: identity is resolved per character before camera/composition.
    private fun perCharacterIdentityLockText(castKeys: List<String>): String {
        val lines = castKeys.mapNotNull { key ->
            when (key) {
                "MASTER 01" -> "กวิน: ใช้ ORIGINAL MASTER ของกวินโดยตรง คงใบหน้า ผมสั้นสีดำ รูปร่าง และภาพลักษณ์เดิม"
                "MASTER 02" -> "รินลดา: ใช้ ORIGINAL MASTER ของรินลดาโดยตรง คงใบหน้า ผมยาวสีดำ รูปร่าง และความเซ็กซี่เดิม"
                "MASTER 03" -> "มายด์: ใช้ ORIGINAL MASTER ของมายด์โดยตรง คงใบหน้า ผมบ๊อบสั้นสีน้ำตาล รูปร่าง และความเซ็กซี่เดิม"
                else -> null
            }
        }
        return lines.joinToString("\\n")
    }

    // BUILD 719: keep recurring evidence recognizable while action controls its orientation.
    private fun propContinuityText(action: String): String {
        val props = mutableListOf<String>()
        if (action.contains("โทรศัพท์")) {
            props += "โทรศัพท์เป็นเครื่องเดิมของเจ้าของเดิม คงสีและลักษณะโดยรวมเดิม การหันหน้าจอหรือด้านหลังเปลี่ยนตามการใช้งานในฉากอย่างสมเหตุผล"
        }
        if (action.contains("กล่อง") || action.contains("สิ่งของ")) {
            props += "กล่องหรือสิ่งของหลักฐานเป็นชิ้นเดิมจากฉากก่อน คงลักษณะโดยรวมและผู้ถือปัจจุบันตามเหตุการณ์"
        }
        if (action.contains("เอกสาร") || action.contains("ข้อมูล")) {
            props += "เอกสารและข้อมูลหลักฐานเป็นชุดเดิมที่เรื่องกำลังตรวจสอบ รักษาความต่อเนื่องของตำแหน่งและผู้ใช้งาน"
        }
        return props.joinToString("\\n")
    }

'''
if camera_anchor not in s:
    raise SystemExit("BUILD 719 prompt anchor not found")
s = s.replace(camera_anchor, helpers + camera_anchor, 1)

old_vars = '''        val action = storyActionProgressionText()
        val memory = storyMemoryText()
        val shot = cinematicShotText(currentScene)
'''
new_vars = '''        val action = storyActionProgressionText()
        val memory = storyMemoryText()
        val shot = cinematicShotText(currentScene)
        val castKeys = activeSceneCastKeys(currentScene)
        val identityLock = perCharacterIdentityLockText(castKeys)
        val propContinuity = propContinuityText(action)
'''
if old_vars not in s:
    raise SystemExit("BUILD 719 vars anchor not found")
s = s.replace(old_vars, new_vars, 1)

old_identity = '''ใช้รูปที่แนบเป็นต้นฉบับของแต่ละตัวละคร คงใบหน้า ทรงผม สีผม ความยาวผม รูปร่าง และความเซ็กซี่ตามรูปต้นฉบับ โดยจับคู่ตัวละครกับ ORIGINAL MASTER ของตนเอง
'''
new_identity = '''ใช้รูปที่แนบเป็นต้นฉบับของแต่ละตัวละคร โดยยึด ORIGINAL MASTER รายคนเป็นลำดับแรกก่อนมุมกล้องและองค์ประกอบภาพ
$identityLock
'''
if old_identity not in s:
    raise SystemExit("BUILD 719 identity anchor not found")
s = s.replace(old_identity, new_identity, 1)

old_scene = '''ฉาก: $action
มุมภาพ: $shot กล้องนิ่ง ไม่มีการเคลื่อนกล้อง
ภาพแนวตั้ง 9:16 ละครไทยสมจริง
'''
new_scene = '''ฉาก: $action
${if (propContinuity.isNotBlank()) "ความต่อเนื่องของสิ่งของ:\\n$propContinuity\\n" else ""}มุมภาพ: $shot กล้องนิ่ง ไม่มีการเคลื่อนกล้อง โดยรักษา Character Identity เป็นลำดับแรก
ภาพแนวตั้ง 9:16 ละครไทยสมจริง
'''
if old_scene not in s:
    raise SystemExit("BUILD 719 scene anchor not found")
s = s.replace(old_scene, new_scene, 1)

s = s.replace(
    '// BUILD 718: CINEMATIC SHOT DIRECTOR + RETRY CURRENT SCENE',
    '// BUILD 719: PER-CHARACTER IDENTITY LOCK + PROP CONTINUITY + BUILD 718 FEATURES',
    1
)

path.write_text(s, encoding="utf-8")
print("BUILD 719 runtime transform applied")
