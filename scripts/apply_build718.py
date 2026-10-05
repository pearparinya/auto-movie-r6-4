from pathlib import Path

path = Path("app/src/main/java/com/pearparinya/automovie/MainActivity.kt")
s = path.read_text(encoding="utf-8")

old_ui = '''        root.addView(
            stepAction(3, "❸ ➜ สร้างฉากถัดไป • NEXT SCENE", "สร้างฉากต่อไปทันที ไม่ต้อง CONFIRM/QC/LOCK") { generateNextQuickScene() },
            full()
        )
'''
new_ui = old_ui + '''        root.addView(
            Button(this).apply {
                text = "↻ สร้างฉากเดิมอีกครั้ง • RETRY CURRENT SCENE"
                textSize = 12f
                maxLines = 1
                isSingleLine = true
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
                setOnClickListener { retryCurrentQuickScene() }
            },
            full(dp(38))
        )
'''
if old_ui not in s:
    raise SystemExit("BUILD 718 UI anchor not found")
s = s.replace(old_ui, new_ui, 1)

anchor = '''    // BUILD 694: normalize generated instructions before sharing to ChatGPT.
'''
retry_fn = '''    // BUILD 718: retry the exact current scene without advancing EP/SCENE state.
    private fun retryCurrentQuickScene() {
        if (!requireStep(3)) return
        if (!characterMastersReady()) { showCharacterMasterSetup(); return }
        saveWorkState()
        share(
            safeNormalizePrompt(buildQuickSceneCommand()),
            characterKeys = activeSceneCastKeys(currentScene)
        )
        status.text = "↻ RETRY • EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} • เลขฉากเดิม"
        nextHint.text = "กำลังสร้าง SCENE ${fmt(currentScene)} ซ้ำ • Story State ไม่เดินหน้า"
    }

'''
if anchor not in s:
    raise SystemExit("BUILD 718 retry anchor not found")
s = s.replace(anchor, retry_fn + anchor, 1)

camera_anchor = '''    private fun buildQuickSceneCommand(): String {
'''
camera_fn = '''    // BUILD 718: choose a cinematic framing from story purpose while keeping the camera static.
    private fun cinematicShotText(scene: Int): String {
        return when ((scene - 1).coerceAtLeast(0) % 10) {
            0 -> "Medium group shot เห็นความสัมพันธ์ของตัวละครและพื้นที่สำคัญในฉาก"
            1 -> "Medium close two/three-shot เน้นการเปิดเผยข้อมูลและปฏิกิริยาพร้อมกัน"
            2 -> "Two-shot ระดับกลาง เน้นการถามตอบและสิ่งของสำคัญระหว่างตัวละคร"
            3 -> "Over-the-shoulder style composition แบบกล้องนิ่ง ให้หลักฐานเป็นจุดสนใจ"
            4 -> "Insert-oriented medium shot ให้เอกสาร โทรศัพท์ หรือหลักฐานเด่นพร้อมเห็นปฏิกิริยาตัวละคร"
            5 -> "Medium two-shot เน้นสีหน้าและภาษากายของคู่สนทนา"
            6 -> "Over-the-shoulder style composition แบบกล้องนิ่งสำหรับการเปรียบเทียบข้อมูล"
            7 -> "Wide-medium group shot เห็นทั้งสามคนและจังหวะเผชิญหน้าชัดเจน"
            8 -> "Reaction-focused medium close group shot เน้นผลกระทบจากข้อมูลใหม่"
            else -> "Balanced medium group shot ให้หลักฐานและปฏิกิริยาของตัวละครอยู่ในเฟรมเดียวกัน"
        }
    }

'''
if camera_anchor not in s:
    raise SystemExit("BUILD 718 camera anchor not found")
s = s.replace(camera_anchor, camera_fn + camera_anchor, 1)

old_vars = '''        val action = storyActionProgressionText()
        val memory = storyMemoryText()
'''
new_vars = '''        val action = storyActionProgressionText()
        val memory = storyMemoryText()
        val shot = cinematicShotText(currentScene)
'''
if old_vars not in s:
    raise SystemExit("BUILD 718 scene vars anchor not found")
s = s.replace(old_vars, new_vars, 1)

old_scene = '''ฉาก: $action
ภาพแนวตั้ง 9:16 ละครไทยสมจริง
'''
new_scene = '''ฉาก: $action
มุมภาพ: $shot กล้องนิ่ง ไม่มีการเคลื่อนกล้อง
ภาพแนวตั้ง 9:16 ละครไทยสมจริง
'''
if old_scene not in s:
    raise SystemExit("BUILD 718 prompt anchor not found")
s = s.replace(old_scene, new_scene, 1)

s = s.replace(
    'nextHint.text = "งานเดิมยังอยู่ • ตรวจภาพใน ChatGPT แล้วกด NEXT SCENE เพื่อทำต่อ"',
    'nextHint.text = "ตรวจภาพแล้ว: สำเร็จให้กด NEXT SCENE • ค้าง/ผิดให้กด RETRY CURRENT SCENE"',
    1
)

s = s.replace(
    '// BUILD 717: BODY IDENTITY CONTINUITY + CHARACTER IDENTITY ANCHOR',
    '// BUILD 718: CINEMATIC SHOT DIRECTOR + RETRY CURRENT SCENE',
    1
)

path.write_text(s, encoding="utf-8")
print("BUILD 718 runtime transform applied")
