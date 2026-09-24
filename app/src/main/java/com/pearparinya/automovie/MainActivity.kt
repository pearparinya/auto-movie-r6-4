package com.pearparinya.automovie

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(42, 70, 42, 42)
            setBackgroundColor(Color.rgb(248, 249, 252))
        }

        val title = TextView(this).apply {
            text = "AUTO-MOVIE ENGINE 2.0"
            textSize = 26f
            setTextColor(Color.rgb(25, 35, 55))
            gravity = Gravity.CENTER
        }

        val version = TextView(this).apply {
            text = "R6.4 ANDROID PORTABLE"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 36)
        }

        val input = EditText(this).apply {
            hint = "พิมพ์ชื่อเรื่องหรือคำสั่งสร้าง EP"
            minLines = 4
            gravity = Gravity.TOP
        }

        val button = Button(this).apply {
            text = "เริ่มสร้าง EP"
        }

        val status = TextView(this).apply {
            text = "พร้อมทำงาน"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val note = TextView(this).apply {
            text =
                "20 Scenes • Scene 8s • 9:16 • Locked-off • Flow/Veo 3.1"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        button.setOnClickListener {

            val story = input.text.toString().trim()

            if (story.isEmpty()) {
                status.text = "กรุณาใส่ชื่อเรื่องหรือคำสั่ง"
                return@setOnClickListener
            }

            hideKeyboard()

            status.text = "กำลังส่งคำสั่ง AUTO-MOVIE..."

            val masterPrompt = buildMasterPrompt(story)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, masterPrompt)
            }

            try {
                startActivity(
                    Intent.createChooser(
                        sendIntent,
                        "ส่งไปยัง ChatGPT"
                    )
                )

                status.text = "เลือก ChatGPT เพื่อเริ่มสร้าง EP"

            } catch (e: Exception) {
                status.text = "ไม่สามารถเปิดเมนูแชร์ได้"
            }
        }

        root.addView(title)
        root.addView(version)

        root.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(status)
        root.addView(note)

        scrollView.addView(root)

        setContentView(scrollView)
    }

    private fun buildMasterPrompt(story: String): String {

        return """
AUTO-MOVIE ENGINE 2.0
R6.4 ANDROID PORTABLE

DIRECTOR COMMAND:
$story

ดำเนินการสร้าง EP ทันที

ชื่อช่อง:
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

CHARACTER CONSISTENCY:

รักษา:
- ใบหน้า
- รูปร่าง
- สีผิว
- ทรงผม
- อายุโดยประมาณ
- อัตลักษณ์ตัวละคร

รูปอ้างอิงใช้สำหรับ:
IDENTITY + NATURAL BODY เท่านั้น

ห้ามนำเสื้อผ้าจากรูปอ้างอิงมาเป็นข้อบังคับ

เสื้อผ้าต้องกำหนดจาก:
STORY CONTEXT + CONTINUITY + DIRECTOR

ห้าม:
- ลดรูปร่าง
- ทำให้ผอม
- เปลี่ยนสัดส่วน
เพื่อให้เข้ากับเสื้อผ้า

VOICE RULES:

ถ้าเป็นบทพูด:
ขยับปากเฉพาะตัวละครที่กำลังพูด

ถ้าเป็นผู้บรรยาย:
ตัวละครทุกคนในภาพต้องไม่พูด
ปากปิดอย่างเป็นธรรมชาติ
ห้าม Lip Sync

WRITING:

ใช้ภาษาไทยธรรมชาติ
ประโยคเข้าใจง่าย
เนื้อเรื่องต้องต่อเนื่องกันทั้ง 20 ฉาก

แต่ละฉากต้องระบุ:

1. SCENE NUMBER
2. DURATION = 8 SECONDS
3. LOCATION
4. CHARACTERS
5. VISUAL / ACTION
6. EMOTION
7. DIALOGUE หรือ NARRATION
8. CAMERA
9. FLOW / VEO 3.1 PROMPT

ห้ามลดจำนวนฉาก

เมื่อถึง SCENE 20:
SAVE_EP
HANDOFF
STOP

ห้ามเริ่ม EP ถัดไปอัตโนมัติ

เริ่มสร้าง EP ตอนนี้
        """.trimIndent()
    }

    private fun hideKeyboard() {

        val view = currentFocus ?: return

        val imm =
            getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager

        imm.hideSoftInputFromWindow(
            view.windowToken,
            0
        )
    }
}
