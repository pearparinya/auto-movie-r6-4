package com.pearparinya.automovie

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var currentScene = 1
    private var repairCount = 0
    private var sceneLocked = false

    private lateinit var sceneLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var nextButton: Button
    private lateinit var input: EditText

    private val navy = Color.rgb(8, 17, 35)
    private val panel = Color.rgb(17, 31, 55)
    private val gold = Color.rgb(218, 177, 83)
    private val ice = Color.rgb(235, 242, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(navy)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(navy)
        }

        // =========================
        // HEADER
        // =========================

        root.addView(TextView(this).apply {
            text = "🎬  AUTO-MOVIE ENGINE 2.0"
            textSize = 25f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "R6.5 • PROFESSIONAL PRODUCTION CONSOLE"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(20))
        })

        // =========================
        // SCENE STATUS
        // =========================

        sceneLabel = TextView(this).apply {
            text = "EP 01  •  SCENE 01 / 20  •  8s"
            textSize = 18f
            setTextColor(ice)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(8))
            setBackgroundColor(panel)
        }

        root.addView(sceneLabel, full())

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 20
            progress = 1
        }

        root.addView(progress, full(dp(10)))

        // =========================
        // DIRECTOR COMMAND
        // =========================

        input = EditText(this).apply {
            hint = "ชื่อเรื่อง / Director Command"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            minLines = 3
            gravity = Gravity.TOP
            setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
            )
            setBackgroundColor(panel)
        }

        val inputParams = full()

        inputParams.setMargins(
            0,
            dp(18),
            0,
            dp(16)
        )

        root.addView(input, inputParams)

        root.addView(section("PRODUCTION WORKFLOW"))

        // =========================
        // CREATE EP
        // =========================

        root.addView(
            action(
                "🎬  CREATE EP",
                "เริ่มสร้าง EP"
            ) {
                createEp()
            },
            full()
        )

        // =========================
        // GENERATE
        // =========================

        root.addView(
            action(
                "🖼️  GENERATE SCENE",
                "สร้างภาพ Scene ปัจจุบัน"
            ) {
                share(buildSceneCommand())
            },
            full()
        )

        // =========================
        // QC
        // =========================

        root.addView(
            action(
                "🔍  QC CHECK",
                "วิเคราะห์ภาพแบบ Fail-Closed"
            ) {
                share(buildQcCommand())
            },
            full()
        )

        // =========================
        // REPAIR
        // =========================

        root.addView(
            action(
                "🛠️  REPAIR",
                "แก้เฉพาะจุดที่ไม่ผ่าน • สูงสุด 3 ครั้ง"
            ) {
                repair()
            },
            full()
        )

        // =========================
        // PASS & LOCK
        // =========================

        root.addView(
            action(
                "🔒  PASS & LOCK",
                "ยืนยันและล็อก Scene"
            ) {
                lockScene()
            },
            full()
        )

        // =========================
        // NEXT SCENE
        // =========================

        nextButton = action(
            "▶  NEXT SCENE",
            "ไปฉากถัดไปหลัง PASS & LOCK"
        ) {
            nextScene()
        }.apply {
            isEnabled = false
            alpha = 0.45f
        }

        root.addView(nextButton, full())

        // =========================
        // STATUS
        // =========================

        status = TextView(this).apply {
            text = "● READY — พร้อมทำงาน"
            textSize = 15f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(
                0,
                dp(22),
                0,
                dp(10)
            )
        }

        root.addView(status)

        // =========================
        // HELP
        // =========================

        root.addView(
            Button(this).apply {
                text = "❔ วิธีใช้งาน"

                setOnClickListener {
                    showHelp()
                }
            },
            full()
        )

        // =========================
        // FOOTER
        // =========================

        root.addView(TextView(this).apply {

            text =
                "8s • 9:16 • Locked-off • One Scene / One Image • Flow/Veo 3.1\n" +
                "พัฒนาโดย ปริญญา"

            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER

            setPadding(
                0,
                dp(20),
                0,
                0
            )
        })

        scroll.addView(root)

        setContentView(scroll)
    }

    // ============================================================
    // CREATE EP
    // ============================================================

    private fun createEp() {

        val story = input.text.toString().trim()

        if (story.isEmpty()) {

            status.text =
                "⚠ กรุณาใส่ชื่อเรื่องหรือคำสั่ง"

            return
        }

        currentScene = 1
        repairCount = 0
        sceneLocked = false

        updateUi()

        share(
            buildMasterPrompt(story)
        )
    }

    // ============================================================
    // REPAIR
    // ============================================================

    private fun repair() {

        if (sceneLocked) {

            status.text =
                "🔒 Scene นี้ถูกล็อกแล้ว"

            return
        }

        if (repairCount >= 3) {

            status.text =
                "⛔ REPAIR ครบ 3 ครั้ง — ต้อง QC/ตัดสินใจใหม่"

            return
        }

        repairCount++

        status.text =
            "🛠 REPAIR $repairCount / 3"

        share(
            buildRepairCommand()
        )
    }

    // ============================================================
    // PASS & LOCK
    // ============================================================

    private fun lockScene() {

        sceneLocked = true

        nextButton.isEnabled =
            currentScene < 20

        nextButton.alpha =
            if (currentScene < 20) 1f
            else 0.45f

        status.text =
            if (currentScene == 20) {

                "✅ SCENE 20 LOCKED • SAVE_EP • HANDOFF • STOP"

            } else {

                "✅ PASS & LOCK — SCENE ${fmt(currentScene)}"
            }

        share(
            buildLockCommand()
        )
    }

    // ============================================================
    // NEXT SCENE
    // ============================================================

    private fun nextScene() {

        if (!sceneLocked) {

            status.text =
                "⛔ ต้อง PASS & LOCK ก่อน"

            return
        }

        if (currentScene >= 20) {

            status.text =
                "🏁 EP COMPLETE — STOP"

            return
        }

        currentScene++

        repairCount = 0
        sceneLocked = false

        updateUi()

        share(
            buildNextSceneCommand()
        )
    }

    // ============================================================
    // UPDATE UI
    // ============================================================

    private fun updateUi() {

        sceneLabel.text =
            "EP 01  •  SCENE ${fmt(currentScene)} / 20  •  8s"

        progress.progress =
            currentScene

        nextButton.isEnabled =
            false

        nextButton.alpha =
            0.45f

        status.text =
            "● READY — SCENE ${fmt(currentScene)}"
    }

    // ============================================================
    // SHARE TO CHATGPT
    // ============================================================

    private fun share(text: String) {

        hideKeyboard()

        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {

                type = "text/plain"

                putExtra(
                    Intent.EXTRA_TEXT,
                    text
                )
            }

        try {

            startActivity(
                Intent.createChooser(
                    sendIntent,
                    "ส่งคำสั่งไปยัง ChatGPT"
                )
            )

        } catch (e: Exception) {

            status.text =
                "⛔ ไม่สามารถเปิดเมนูแชร์ได้"
        }
    }

    // ============================================================
    // MASTER PRODUCTION RULES
    // ============================================================

    private fun rules(): String {

        return """
AUTO-MOVIE ENGINE 2.0
R6.5 ANDROID PROFESSIONAL

CHANNEL:
สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย

PRODUCTION RULES:

- สร้างจำนวน 1 EP
- EP ต้องมี 20 ฉาก
- ทุกฉากยาว 8 วินาทีเท่ากัน
- วิดีโอแนวตั้ง 9:16
- สำหรับ Flow / Veo 3.1
- One Scene / One Image

CAMERA:

Locked-off static camera.

ห้าม:
- Zoom
- Pan
- Tilt
- Roll
- Dolly
- Tracking
- Camera movement
- Cut
- Reframe

IDENTITY MASTER:

รูปต้นฉบับของตัวละคร
คือ ORIGINAL IDENTITY MASTER

ต้องใช้ ORIGINAL IDENTITY MASTER
ในทุก Scene ที่ตัวละครนั้นปรากฏ

ห้ามใช้ภาพ Output
จาก Scene ก่อนหน้า
เป็น Identity Master

Reference Image ใช้สำหรับ:

- ใบหน้า
- สีผิว
- รูปร่างธรรมชาติ
- ทรงผม
- อายุโดยประมาณ
- อัตลักษณ์

เท่านั้น

WARDROBE:

เสื้อผ้ากำหนดจาก:

STORY CONTEXT
+
CONTINUITY
+
DIRECTOR

ห้ามนำเสื้อผ้า
จาก Identity Reference
มาเป็นข้อบังคับ

ห้าม:

- ลดรูปร่าง
- ทำให้ผอม
- เปลี่ยนสัดส่วน
- Reshape Body

เพื่อให้เข้ากับเสื้อผ้า

VOICE:

ถ้าเป็นบทพูด:

ขยับปากเฉพาะ
ตัวละครที่กำลังพูด

ถ้าเป็นผู้บรรยาย:

ตัวละครทุกคนในภาพ
ต้องไม่พูด

ปากปิดอย่างเป็นธรรมชาติ

NO LIP SYNC

QC RULE:

ตรวจจากภาพ Output จริงเท่านั้น

ห้ามตัดสินจาก Prompt Intent

FAIL-CLOSED

ถ้าไม่แน่ใจ = FAIL

REPAIR:

DELTA-ONLY

แก้เฉพาะจุดที่ไม่ผ่าน

รักษาส่วนที่ผ่านแล้ว

REPAIR สูงสุด 3 ครั้งต่อ Scene

SCENE 20:

SAVE_EP
HANDOFF
STOP

ห้ามเริ่ม EP ถัดไปอัตโนมัติ
        """.trimIndent()
    }

    // ============================================================
    // CREATE MASTER PROMPT
    // ============================================================

    private fun buildMasterPrompt(
        story: String
    ): String {

        return rules() + """

DIRECTOR COMMAND:

$story

ดำเนินการสร้าง EP ทันที

สร้างให้ครบ 20 Scene

แต่ละ Scene ต้องระบุ:

1. SCENE NUMBER
2. DURATION = 8 SECONDS
3. LOCATION
4. CHARACTERS
5. VISUAL / ACTION
6. EMOTION
7. DIALOGUE หรือ NARRATION
8. CAMERA
9. FLOW / VEO 3.1 PROMPT

ใช้ภาษาไทยธรรมชาติ
อ่านง่าย
ดำเนินเรื่องต่อเนื่อง

เริ่มสร้าง EP ตอนนี้
        """.trimIndent()
    }

    // ============================================================
    // GENERATE SCENE
    // ============================================================

    private fun buildSceneCommand(): String {

        return rules() + """

CURRENT SCENE:
${fmt(currentScene)}

สร้าง / เตรียมภาพ
สำหรับ SCENE ${fmt(currentScene)}
เท่านั้น

ยึดบท Scene ปัจจุบัน

ใช้ ORIGINAL IDENTITY MASTER
ของตัวละครทุกคนที่ปรากฏ

ห้ามข้าม Scene
        """.trimIndent()
    }

    // ============================================================
    // QC
    // ============================================================

    private fun buildQcCommand(): String {

        return rules() + """

QC CURRENT SCENE:
${fmt(currentScene)}

ตรวจภาพที่แนบจริงแบบ FAIL-CLOSED

ตรวจ:

- Identity
- Face
- Natural Body
- Skin
- Hair
- Wardrobe Continuity
- Action
- Emotion
- Composition
- Mouth / Lip Sync
- Camera Compatibility
- Story Continuity

สรุปผล:

PASS

หรือ

FAIL

ถ้า FAIL
ระบุเฉพาะจุดที่ต้องแก้
        """.trimIndent()
    }

    // ============================================================
    // REPAIR COMMAND
    // ============================================================

    private fun buildRepairCommand(): String {

        return rules() + """

REPAIR CURRENT SCENE:
${fmt(currentScene)}

REPAIR ATTEMPT:
$repairCount / 3

แก้เฉพาะจุดที่ QC ไม่ผ่าน

DELTA-ONLY

รักษาทุกส่วนที่ผ่านแล้ว

ห้ามเปลี่ยนโดยไม่จำเป็น:

- Identity
- Natural Body
- Face
- Hair
- Wardrobe Continuity
- Composition
        """.trimIndent()
    }

    // ============================================================
    // LOCK
    // ============================================================

    private fun buildLockCommand(): String {

        return rules() + """

PASS & LOCK

SCENE:
${fmt(currentScene)}

ล็อก Scene นี้
เป็น Scene ที่ผ่าน QC แล้ว

${
            if (currentScene == 20)
                "SAVE_EP\nHANDOFF\nSTOP"
            else
                "ห้ามเปลี่ยน Scene ที่ล็อกแล้ว โดยไม่มีคำสั่ง Director"
        }
        """.trimIndent()
    }

    // ============================================================
    // NEXT
    // ============================================================

    private fun buildNextSceneCommand(): String {

        return rules() + """

SCENE ${fmt(currentScene - 1)}
=
PASS & LOCK

ดำเนินการ:

SCENE ${fmt(currentScene)}

เท่านั้น

ใช้ ORIGINAL IDENTITY MASTER
ของตัวละครทุกคนที่ปรากฏ

รักษา Story Continuity
และ Wardrobe Continuity
        """.trimIndent()
    }

    // ============================================================
    // HELP
    // ============================================================

    private fun showHelp() {

        AlertDialog.Builder(this)

            .setTitle(
                "วิธีใช้งาน AUTO-MOVIE R6.5"
            )

            .setMessage(
                """
1. ใส่ชื่อเรื่อง / Director Command

2. กด CREATE EP

3. กด GENERATE SCENE

4. กด QC CHECK

5. ถ้า FAIL
   กด REPAIR
   สูงสุด 3 ครั้ง

6. เมื่อผ่าน
   กด PASS & LOCK

7. NEXT SCENE
   จะเปิดให้กด

8. ทำซ้ำจนถึง
   SCENE 20

SCENE 20:
SAVE_EP
HANDOFF
STOP
                """.trimIndent()
            )

            .setPositiveButton(
                "เข้าใจแล้ว",
                null
            )

            .show()
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private fun action(
        title: String,
        subtitle: String,
        function: () -> Unit
    ): Button {

        return Button(this).apply {

            text =
                "$title\n$subtitle"

            textSize = 15f

            isAllCaps = false

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(18),
                dp(10),
                dp(18),
                dp(10)
            )

            setOnClickListener {
                function()
            }
        }
    }

    private fun section(
        textValue: String
    ): TextView {

        return TextView(this).apply {

            text = textValue

            textSize = 12f

            setTextColor(gold)

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                dp(8),
                0,
                dp(8)
            )
        }
    }

    private fun full(
        height: Int =
            LinearLayout.LayoutParams.WRAP_CONTENT
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        ).apply {

            setMargins(
                0,
                dp(5),
                0,
                dp(5)
            )
        }
    }

    private fun fmt(
        number: Int
    ): String {

        return number
            .toString()
            .padStart(
                2,
                '0'
            )
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
            resources.displayMetrics.density
        ).toInt()
    }

    private fun hideKeyboard() {

        val view =
            currentFocus ?: return

        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            view.windowToken,
            0
        )
    }
}
