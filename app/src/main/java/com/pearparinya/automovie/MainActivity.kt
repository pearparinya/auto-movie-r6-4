package com.pearparinya.automovie

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var currentScene = 1
    private var repairCount = 0
    private var sceneLocked = false
    private var selectedCategory: String? = null

    private lateinit var sceneLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var nextButton: Button
    private lateinit var input: EditText
    private lateinit var titlePanel: LinearLayout

    private val navy = Color.rgb(12, 15, 38)
    private val panel = Color.rgb(37, 32, 78)
    private val gold = Color.rgb(255, 190, 74)
    private val ice = Color.rgb(248, 247, 255)
    private val muted = Color.rgb(196, 193, 222)
    private val success = Color.rgb(83, 224, 210)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(navy)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(52), dp(18), dp(8))
            setBackgroundColor(navy)
        }

        // ============================================================
        // HEADER
        // ============================================================

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, 0, 0, dp(3))
        }

        headerRow.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            contentDescription = "AUTO-MOVIE ENGINE 2.0"
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(94), dp(94)).apply {
            marginEnd = dp(12)
        })

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(4), 0, 0, 0)
        }

        headerText.addView(TextView(this).apply {
            text = "AUTO-MOVIE 2.0"
            textSize = 21f
            letterSpacing = 0.02f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(15, 21, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, full())

        headerText.addView(TextView(this).apply {
            text = "ระบบสร้างหนังอัตโนมัติ • R${appVersion()}"
            textSize = 16f
            setTextColor(muted)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(
                12, 16, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
            setPadding(0, 0, 0, 0)
        }, full())

        headerText.addView(TextView(this).apply {
            text = "สร้างเรื่อง → สร้างฉาก → ตรวจภาพ → ยืนยัน"
            textSize = 10f
            setTextColor(ice)
            gravity = Gravity.START
            maxLines = 1
        }, full())

        headerRow.addView(headerText, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))

        root.addView(headerRow, full())

        // ============================================================
        // SCENE STATUS
        // ============================================================

        sceneLabel = TextView(this).apply {
            text = "EP 01   •   SCENE 01 / 20   •   8 SEC"
            textSize = 17f
            setTextColor(ice)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(5)
            )
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

        root.addView(
            progress,
            full(dp(10))
        )

        // ============================================================
        // DIRECTOR COMMAND
        // ============================================================

        input = EditText(this).apply {
            hint = "ชื่อเรื่อง / คำสั่งผู้กำกับ…"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            minLines = 2
            gravity = Gravity.TOP

            setPadding(
                dp(14),
                dp(9),
                dp(14),
                dp(9)
            )

            setBackgroundColor(panel)
        }

        val inputParams = full()

        inputParams.setMargins(
            0,
            dp(5),
            0,
            dp(5)
        )

        root.addView(
            input,
            inputParams
        )

        root.addView(
            action(
                "❶ ✨ สร้างชื่อเรื่อง 5 ชื่อ • TITLES",
                "สร้างชื่อเรื่อง 5 ชื่อ"
            ) {
                generateTitles()
            },
            full()
        )

        titlePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(panel)
        }
        root.addView(titlePanel, full())

        root.addView(
            section("ขั้นตอนการสร้าง • PRODUCTION FLOW").apply {
                gravity = Gravity.CENTER
            }
        )

        // ============================================================
        // CREATE EP
        // ============================================================

        root.addView(
            action(
                "❷ 🎬 สร้างเรื่อง 20 ฉาก • CREATE EP",
                "เริ่มสร้างเรื่อง"
            ) {
                createEp()
            },
            full()
        )

        // ============================================================
        // GENERATE SCENE
        // ============================================================

        root.addView(
            action(
                "❸ 🖼️ สร้างภาพฉากปัจจุบัน • SCENE",
                "สร้างฉากปัจจุบัน"
            ) {
                share(
                    buildSceneCommand()
                )
            },
            full()
        )

        // ============================================================
        // QC CHECK
        // ============================================================

        root.addView(
            action(
                "❹ 🔍 ตรวจภาพฉาก • QC",
                "QC ฉากปัจจุบัน"
            ) {
                share(
                    buildQcCommand()
                )
            },
            full()
        )

        // ============================================================
        // REPAIR
        // ============================================================

        root.addView(
            action(
                "❺ 🛠️ แก้ไขภาพไม่ผ่าน • REPAIR",
                "เฉพาะจุด • สูงสุด 3 ครั้ง"
            ) {
                repair()
            },
            full()
        )

        // ============================================================
        // PASS & LOCK
        // ============================================================

        root.addView(
            action(
                "❻ 🔒 ยืนยันและล็อกฉาก • LOCK",
                "ยืนยันฉากนี้"
            ) {
                lockScene()
            },
            full()
        )

        // ============================================================
        // NEXT SCENE
        // ============================================================

        nextButton = action(
            "➡️ ฉากถัดไป • NEXT SCENE",
            "ไปยังฉากต่อไป"
        ) {
            nextScene()
        }.apply {

            isEnabled = false
            alpha = 0.45f
        }

        root.addView(
            nextButton,
            full()
        )

        // ============================================================
        // STATUS
        // ============================================================

        status = TextView(this).apply {

            text =
                "● ระบบพร้อมใช้งาน  •  เชื่อมโยงฉากอัตโนมัติ"

            textSize = 11f

            setTextColor(success)

            gravity = Gravity.CENTER

            setPadding(
                0,
                dp(6),
                0,
                dp(3)
            )
        }

        root.addView(status)

        // ============================================================
        // HELP + UPDATE — ONE ROW / 50:50
        // ============================================================

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        utilityRow.addView(
            Button(this).apply {
                text = "❓ วิธีใช้งาน R${appVersion()}"
                textSize = 14f
                maxLines = 1
                isSingleLine = true
                setAutoSizeTextTypeUniformWithConfiguration(
                    10, 14, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP
                )
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.rgb(255, 190, 74)
                )
                setOnClickListener {
                    showHelp()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            ).apply {
                marginEnd = dp(3)
            }
        )

        utilityRow.addView(
            Button(this).apply {
                text = "🔄 อัปเดตแอป"
                textSize = 14f
                maxLines = 1
                isSingleLine = true
                setAutoSizeTextTypeUniformWithConfiguration(
                    10, 14, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP
                )
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.rgb(35, 105, 210)
                )
                setOnClickListener {
                    checkForAppUpdate()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            ).apply {
                marginStart = dp(3)
            }
        )

        root.addView(utilityRow, full())

        // ============================================================
        // FOOTER
        // ============================================================

        root.addView(
            TextView(this).apply {

                text = "AUTO-CONTEXT • 8s • 9:16 • Flow/Veo 3.1 • พัฒนาโดย ปริญญา"

                textSize = 10f
                maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(
                    8, 10, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP
                )

                setTextColor(Color.GRAY)

                gravity = Gravity.CENTER

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }
        )

        scroll.addView(root)

        setContentView(scroll)
    }

    // ============================================================
    // AI TITLE GENERATOR
    // ============================================================

    private var categoryIndex = 0

    private val storyCategories = listOf(
        "บันทึกรอยร้าวชีวิตคู่",
        "เกมรักซ่อนลวง",
        "ความลับใต้ชายคา",
        "ซ่อมรักหลังบ้าน",
        "หักมุมระทึกขวัญจิตวิทยา"
    )

    private val titleBanks = listOf(
        listOf(
            "วันที่เราเริ่มไม่เหมือนเดิม",
            "บ้านหลังเดิมที่ไม่มีคำว่าเรา",
            "คนข้างกายที่ไกลกว่าเดิม",
            "รอยร้าวใต้คำว่าครอบครัว",
            "เมื่อความเงียบเข้ามาแทนที่รัก",
            "คำว่ารักที่หายไปจากบ้าน",
            "ระยะห่างบนเตียงเดียวกัน",
            "วันที่หัวใจไม่กลับบ้าน",
            "ครอบครัวที่เหลือเพียงชื่อ",
            "ก่อนเราจะกลายเป็นคนแปลกหน้า"
        ),
        listOf(
            "รักนี้มีคนซ่อนอยู่",
            "เกมหัวใจที่ไม่มีคนชนะ",
            "คำโกหกในคืนที่ไว้ใจ",
            "คนรักหรือคนลวง",
            "เงาของใครในหัวใจเธอ",
            "เมื่อรักกลายเป็นเกม",
            "ข้อความลับหลังคำว่ารัก",
            "คนที่สามในความเงียบ",
            "รักซ้อนในบ้านเดียวกัน",
            "คืนที่ความจริงเปิดเผย"
        ),
        listOf(
            "ความลับหลังประตูบ้าน",
            "สิ่งที่ซ่อนอยู่ใต้ชายคา",
            "บ้านนี้มีเรื่องที่ไม่เคยพูด",
            "เสียงกระซิบในครอบครัว",
            "ความจริงในห้องที่ปิดตาย",
            "เมื่อบ้านเก็บความลับไม่ไหว",
            "เรื่องที่ไม่มีใครกล้าถาม",
            "ความลับบนโต๊ะอาหาร",
            "คนในบ้านที่ฉันไม่เคยรู้จัก",
            "ใต้หลังคาเดียวกันคนละความจริง"
        ),
        listOf(
            "กลับมารักกันอีกครั้ง",
            "บ้านที่เรายังซ่อมได้",
            "ก่อนรักจะสายเกินไป",
            "ขอโอกาสให้คำว่าเรา",
            "วันที่เราเลือกเริ่มใหม่",
            "ซ่อมหัวใจใต้หลังคาเดิม",
            "มือที่ยังไม่ยอมปล่อย",
            "รักที่ยังเหลือทางกลับ",
            "เมื่อเราหันหน้ามาคุยกัน",
            "บ้านเดิมกับหัวใจดวงใหม่"
        ),
        listOf(
            "คนที่ยืนอยู่หลังฉัน",
            "คืนที่บ้านไม่เหมือนเดิม",
            "เสียงเรียกจากห้องว่าง",
            "ความจริงที่จำไม่ได้",
            "คนแปลกหน้าในกระจก",
            "ก่อนประตูบานนั้นจะเปิด",
            "ใครบางคนรู้ทุกอย่าง",
            "ความเงียบที่กำลังโกหก",
            "เงื่อนงำในคืนฝนตก",
            "เมื่อคนใกล้ตัวไม่ใช่คนเดิม"
        )
    )

    private fun generateTitles() {
        val category = storyCategories[categoryIndex]
        selectedCategory = category
        val bank = titleBanks[categoryIndex]
        categoryIndex = (categoryIndex + 1) % storyCategories.size

        val titles = bank.shuffled().take(5)
        titlePanel.removeAllViews()
        titlePanel.visibility = android.view.View.VISIBLE

        titlePanel.addView(TextView(this).apply {
            text = "CATEGORY • $category\nแตะชื่อเรื่องที่ต้องการ"
            textSize = 13f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(10))
        })

        titles.forEachIndexed { index, title ->
            titlePanel.addView(Button(this).apply {
                text = "${index + 1}.  $title"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textSize = 14f
                setTextColor(ice)
                backgroundTintList = android.content.res.ColorStateList.valueOf(navy)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener {
                    input.setText(title)
                    input.setSelection(input.text.length)
                    status.text = "✓ เลือกชื่อเรื่องแล้ว • $title • $category"
                }
            }, full())
        }

        status.text = "✦ สร้าง 5 ชื่อแล้ว • $category"
    }

    private fun appVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "6.8"
        } catch (_: Exception) {
            "6.8"
        }
    }

    // ============================================================
    // CREATE EP
    // ============================================================

    private fun createEp() {

        val story =
            input.text.toString().trim()

        if (story.isEmpty()) {

            status.text =
                "⚠ กรุณาใส่ชื่อเรื่องหรือคำสั่ง"

            return
        }

        currentScene = 1
        repairCount = 0
        sceneLocked = false

        updateUi()

        status.text =
            "🎬 กำลังเริ่ม EP • AUTO-CONTEXT"

        share(
            buildMasterPrompt(story, selectedCategory ?: "กำหนดโดยเนื้อเรื่อง")
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
            "🛠 REPAIR $repairCount / 3 • SCENE ${fmt(currentScene)}"

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
            if (currentScene < 20) {
                1f
            } else {
                0.45f
            }

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

        status.text =
            "▶ AUTO-CONTEXT — SCENE ${fmt(currentScene)}"

        share(
            buildNextSceneCommand()
        )
    }

    // ============================================================
    // UPDATE UI
    // ============================================================

    private fun updateUi() {

        sceneLabel.text =
            "EP 01   •   SCENE ${fmt(currentScene)} / 20   •   8 SEC"

        progress.progress =
            currentScene

        nextButton.isEnabled =
            false

        nextButton.alpha =
            0.45f

        status.text =
            "● SYSTEM READY  •  SCENE ${fmt(currentScene)}  •  AUTO-CONTEXT ON"
    }

    // ============================================================
    // SHARE TO CHATGPT
    // ============================================================

    private fun share(
        text: String
    ) {

        hideKeyboard()

        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {

                type = "text/plain"

                putExtra(
                    Intent.EXTRA_TEXT,
                    text
                )
            }

        // เปิด ChatGPT โดยตรงก่อน เพื่อตัด Android Share Sheet ออก
        // ไม่ใช้ resolveActivity() เพราะ Android 11+ จำกัด package visibility
        val chatGptPackages = listOf(
            "com.openai.chatgpt",
            "com.openai.chatgpt.beta"
        )

        for (packageName in chatGptPackages) {
            try {
                val directIntent = Intent(sendIntent).apply {
                    setPackage(packageName)
                }
                startActivity(directIntent)
                status.text = "↗ เปิด ChatGPT พร้อมคำสั่งแล้ว"
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // ลอง package ถัดไป
            } catch (_: SecurityException) {
                // ลอง package ถัดไป
            }
        }

        // ถ้าเครื่องไม่พบ ChatGPT ให้กลับไปใช้ Share Sheet ตามเดิม
        try {
            startActivity(
                Intent.createChooser(
                    sendIntent,
                    "ส่งคำสั่งไปยัง ChatGPT"
                )
            )
            status.text = "⚠ ไม่พบแอป ChatGPT • เลือกแอปจากเมนูแชร์"
        } catch (_: Exception) {
            status.text = "⛔ ไม่สามารถเปิด ChatGPT หรือเมนูแชร์ได้"
        }
    }

    // ============================================================
    // MASTER PRODUCTION RULES
    // ============================================================

    private fun rules(): String {

        return """
AUTO-MOVIE ENGINE 2.0
R${appVersion()} ANDROID PROFESSIONAL AUTO-CONTEXT + WARDROBE FIREWALL

CHANNEL:
สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย

SYSTEM MODE:
AUTO-CONTEXT = ON

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

WARDROBE FIREWALL:

ORIGINAL IDENTITY MASTER ใช้ได้เฉพาะ:
- ใบหน้า
- สีผิว
- รูปร่างธรรมชาติ
- ทรงผม
- อายุโดยประมาณ
- อัตลักษณ์

STRICTLY FORBIDDEN FROM IDENTITY MASTER:
- เสื้อ / กางเกง / กระโปรง / เดรส / สูท
- รองเท้า
- เครื่องประดับที่เป็นส่วนของชุด
- สีเสื้อผ้า / รูปแบบเสื้อผ้า
- ความโป๊/ความปิดของชุด

ห้ามคัดลอก เลียนแบบ หรืออนุมาน WARDROBE
จาก ORIGINAL IDENTITY MASTER

WARDROBE SOURCE PRIORITY:
1. CURRENT SCENE MASTER
2. SAME-DAY CONTINUITY จาก EP MASTER
3. STORY CONTEXT
4. DIRECTOR COMMAND

ถ้า CURRENT SCENE MASTER ระบุชุดไว้ ต้องใช้ชุดนั้นเป็นอันดับแรก
ถ้าไม่ได้ระบุชุดใหม่ ให้ใช้ SAME-DAY CONTINUITY จากบทที่ล็อกไว้ ไม่ใช่จากภาพ Output

NEVER:
IDENTITY MASTER -> WARDROBE
PREVIOUS GENERATED IMAGE -> IDENTITY MASTER
PREVIOUS GENERATED IMAGE -> WARDROBE SOURCE

WARDROBE LEAK:
ถ้า Output ใช้เสื้อผ้า สีชุด หรือรูปแบบชุด
เหมือน/ใกล้เคียง ORIGINAL IDENTITY MASTER
โดยไม่มีคำสั่งรองรับจาก CURRENT SCENE MASTER, CONTINUITY หรือ DIRECTOR:
QC = FAIL
FAIL CODE = WLF-01 — IDENTITY MASTER WARDROBE LEAK

ห้าม:

- ลดรูปร่าง
- ทำให้ผอม
- เปลี่ยนสัดส่วน
- Reshape Body

เพื่อให้เข้ากับเสื้อผ้า

VOICE RULES:

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

REPAIR RULE:

DELTA-ONLY

แก้เฉพาะจุดที่ไม่ผ่าน

รักษาทุกส่วนที่ผ่านแล้ว

REPAIR สูงสุด 3 ครั้งต่อ Scene

AUTO-CONTEXT RULE:

เมื่อ CREATE EP
ได้สร้างบท 20 Scene
ไว้ก่อนหน้านี้แล้ว

คำสั่ง GENERATE SCENE
และ NEXT SCENE

ต้องค้นหาและใช้
EP ล่าสุดที่สร้างไว้
ในบทสนทนาเดียวกัน

ต้องดึงบท
CURRENT SCENE
จาก EP เดิมโดยอัตโนมัติ

ห้ามถาม Director
ให้ส่งบท Scene ซ้ำ
ถ้าบทนั้นมีอยู่แล้ว
ในบทสนทนา

ห้ามสร้าง Scene ใหม่
แทน Scene ที่มีอยู่แล้ว

ห้ามเปลี่ยนเหตุการณ์เดิม
โดยไม่มีคำสั่ง Director

ถ้าข้อมูล Identity Master
ไม่ครบ

ให้ถามเฉพาะ
Identity Master
ของตัวละครที่ขาด

ห้ามถามหาบท Scene ซ้ำ

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
        story: String,
        category: String
    ): String {

        return rules() + """

EP MASTER METADATA:

CATEGORY: $category

DIRECTOR COMMAND:

$story

ดำเนินการสร้าง EP ทันที

สร้างให้ครบ 20 Scene

ทุก Scene ต้องมีข้อมูลครบ:

1. SCENE NUMBER
2. DURATION = 8 SECONDS
3. LOCATION
4. CHARACTERS
5. VISUAL / ACTION
6. EMOTION
7. DIALOGUE หรือ NARRATION
8. CAMERA
9. FLOW / VEO 3.1 PROMPT

AUTO-CONTEXT MEMORY:

CATEGORY ของ EP นี้คือ:
$category

ต้องแสดง CATEGORY ไว้ในส่วนหัวของ EP MASTER
และคง CATEGORY เดิมตลอด GENERATE SCENE / QC / REPAIR / PASS & LOCK / NEXT SCENE
ห้ามเปลี่ยนหมวดหมู่ระหว่าง EP

บททั้ง 20 Scene
ที่สร้างจากคำสั่งนี้
คือ EP MASTER
สำหรับ Workflow นี้

เมื่อได้รับคำสั่ง
GENERATE SCENE
QC
REPAIR
PASS & LOCK
หรือ NEXT SCENE

ให้ใช้ EP MASTER
จากบทสนทนานี้
เป็น Story Context

ห้ามขอให้ Director
คัดลอกบท Scene
กลับมาให้อีกครั้ง

ใช้ภาษาไทยธรรมชาติ
อ่านง่าย

เนื้อเรื่องต้องต่อเนื่อง
ตลอดทั้ง EP

เมื่อสร้างบทครบ 20 Scene:

หยุด

รอ Director
สั่ง GENERATE SCENE

ห้ามสร้างภาพเอง
จนกว่าจะได้รับ
GENERATE SCENE
        """.trimIndent()
    }

    // ============================================================
    // GENERATE SCENE — AUTO CONTEXT
    // ============================================================

    private fun buildSceneCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
GENERATE CURRENT SCENE

CURRENT SCENE:
${fmt(currentScene)}

ขั้นตอนบังคับ:

1. ค้นหา EP MASTER ล่าสุด
   ที่ CREATE EP สร้างไว้ก่อนหน้านี้
   ในบทสนทนาเดียวกันนี้

2. ค้นหาและดึงบท:

   SCENE ${fmt(currentScene)}

   จาก EP MASTER
   โดยอัตโนมัติ

3. ห้ามถาม Director
   ให้ส่งบท
   SCENE ${fmt(currentScene)}
   ซ้ำ

4. ห้ามแต่ง Scene ใหม่
   แทน Scene เดิม

5. ต้องยึดข้อมูลจาก Scene เดิม:

   - LOCATION
   - CHARACTERS
   - VISUAL / ACTION
   - EMOTION
   - DIALOGUE / NARRATION
   - CAMERA
   - FLOW / VEO 3.1 PROMPT
   - STORY CONTINUITY

6. ตรวจว่า Scene นี้
   ต้องใช้ตัวละครใครบ้าง

7. ตรวจ ORIGINAL IDENTITY MASTER
   ของตัวละครเหล่านั้น

8. ถ้า ORIGINAL IDENTITY MASTER
   ครบทั้งหมด:

   สร้างภาพ
   SCENE ${fmt(currentScene)}
   ทันที

   ห้ามถามคำถามเพิ่มเติม

9. ถ้า ORIGINAL IDENTITY MASTER
   ไม่ครบ:

   แจ้งเฉพาะชื่อ
   ตัวละครที่ยังขาด
   ORIGINAL IDENTITY MASTER

   ห้ามถามหาบท Scene

10. Identity Master
    ใช้สำหรับ:

    - Face
    - Skin
    - Natural Body
    - Hair
    - Approximate Age
    - Identity

    เท่านั้น

11. WARDROBE FIREWALL

    ก่อนสร้างภาพ ต้องแยก IDENTITY ออกจาก WARDROBE อย่างเด็ดขาด
    ORIGINAL IDENTITY MASTER ห้ามเป็นแหล่งข้อมูลเสื้อผ้า สีชุด รูปแบบชุด รองเท้า หรือความโป๊/ความปิดของชุด

    WARDROBE SOURCE PRIORITY:
    1. CURRENT SCENE MASTER
    2. SAME-DAY CONTINUITY จาก EP MASTER
    3. STORY CONTEXT
    4. DIRECTOR COMMAND

    ถ้าชุดในภาพที่จะสร้างเหมือนชุดจาก ORIGINAL IDENTITY MASTER
    โดยไม่มีแหล่งข้อมูลข้างต้นรองรับ ต้องเปลี่ยนเป็นชุดที่ถูกต้องก่อนสร้าง Output

12. ห้ามใช้ Output
    จาก Scene ก่อนหน้า
    เป็น Identity Master

13. One Scene / One Image

14. หลังสร้างภาพเสร็จ:

    STOP

    รอ QC CHECK

ห้ามดำเนินการ
SCENE ${fmt(currentScene + 1)}
อัตโนมัติ
        """.trimIndent()
    }

    // ============================================================
    // QC CHECK
    // ============================================================

    private fun buildQcCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
QC CURRENT SCENE

CURRENT SCENE:
${fmt(currentScene)}

ค้นหา EP MASTER
และบท SCENE ${fmt(currentScene)}
จากบทสนทนาเดิม
โดยอัตโนมัติ

ห้ามถาม Director
ให้ส่งบท Scene ซ้ำ

ตรวจภาพ Output
ที่แนบจริง

ใช้ FAIL-CLOSED

ตรวจ:

- Identity
- Face
- Natural Body
- Skin
- Hair
- Approximate Age
- Character Consistency
- Wardrobe Source Priority
- Wardrobe Continuity
- Wardrobe Leakage เทียบกับ ORIGINAL IDENTITY MASTER
- WLF-01 Detection
- Location Continuity
- Story Continuity
- Action
- Emotion
- Composition
- Mouth
- Lip Sync
- Speaker Assignment
- Narration Rule
- Camera Compatibility
- Locked-off Compatibility
- 9:16 Compatibility
- One Scene / One Image

ผลการตรวจต้องเป็น:

PASS

หรือ

FAIL

ถ้า PASS:

ระบุว่า

SCENE ${fmt(currentScene)}
QC = PASS

พร้อมสำหรับ
PASS & LOCK

ถ้า FAIL:

ระบุเฉพาะ
จุดที่ไม่ผ่าน

ถ้าพบว่าเสื้อผ้า Output มาจาก/เลียนแบบ ORIGINAL IDENTITY MASTER
โดยไม่มี Scene/Continuity/Director รองรับ:
FAIL CODE:
WLF-01 — IDENTITY MASTER WARDROBE LEAK

สร้าง
DELTA REPAIR LIST

ห้ามแก้ส่วนที่ผ่านแล้ว

ห้ามสร้าง Scene ถัดไป
        """.trimIndent()
    }

    // ============================================================
    // REPAIR COMMAND
    // ============================================================

    private fun buildRepairCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
REPAIR CURRENT SCENE

CURRENT SCENE:
${fmt(currentScene)}

REPAIR ATTEMPT:
$repairCount / 3

ค้นหาโดยอัตโนมัติ:

1. EP MASTER
2. บท SCENE ${fmt(currentScene)}
3. ภาพ Output ล่าสุด
4. QC RESULT ล่าสุด
5. DELTA REPAIR LIST ล่าสุด

จากบทสนทนาเดียวกัน

ห้ามถาม Director
ให้ส่งข้อมูลเดิมซ้ำ
ถ้าข้อมูลมีอยู่แล้ว

REPAIR MODE:

DELTA-ONLY

แก้เฉพาะ
จุดที่ QC ไม่ผ่าน

รักษาทุกส่วน
ที่ผ่านแล้ว

ห้ามเปลี่ยนโดยไม่จำเป็น:

- Identity
- Face
- Natural Body
- Skin
- Hair
- Wardrobe Continuity
- Location
- Composition
- Action
- Story Continuity

ใช้ ORIGINAL IDENTITY MASTER
เท่านั้น

ห้ามใช้ Output
จาก Scene ก่อนหน้า
เป็น Identity Master

ถ้า QC RESULT ล่าสุดมี:
WLF-01 — IDENTITY MASTER WARDROBE LEAK

ให้ REPAIR เฉพาะ WARDROBE:
- เปลี่ยนชุดตาม CURRENT SCENE MASTER
- ถ้า Scene ไม่ระบุชุด ให้ใช้ SAME-DAY CONTINUITY จาก EP MASTER
- ห้ามคัดลอกชุดจาก ORIGINAL IDENTITY MASTER
- คง Face / Identity / Natural Body / Hair / Skin
- คง Location / Camera / Composition / Action / Emotion เท่าที่ไม่ขัดกับการแก้ Wardrobe

POST-REPAIR VALIDATION:
หลังสร้างภาพ Repair ใหม่ ต้องตรวจ defect เดิมซ้ำทันที

ถ้า defect เดิมยังอยู่:
REPAIR VALIDATION = FAIL
ห้ามถือว่า Repair สำเร็จ
ห้าม PASS & LOCK
ให้รอ REPAIR ครั้งถัดไป โดยนับตาม REPAIR ATTEMPT ปัจจุบัน สูงสุด 3 ครั้งต่อ Scene

ถ้า defect เดิมหาย:
REPAIR VALIDATION = CLEARED
จากนั้น STOP
รอ QC CHECK ใหม่

ห้าม PASS อัตโนมัติ
ห้ามไป Scene ถัดไป
        """.trimIndent()
    }

    // ============================================================
    // PASS & LOCK
    // ============================================================

    private fun buildLockCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
PASS & LOCK

CURRENT SCENE:
${fmt(currentScene)}

ค้นหา:

- EP MASTER
- SCENE ${fmt(currentScene)}
- ภาพ Output ล่าสุด
- QC RESULT ล่าสุด

จากบทสนทนาเดียวกัน

ห้ามถาม Director
ให้ส่งข้อมูลเดิมซ้ำ

ก่อนล็อก ต้องยืนยันว่า QC RESULT ล่าสุดของ SCENE ${fmt(currentScene)} ระบุ PASS อย่างชัดเจน

ถ้า QC ล่าสุดเป็น FAIL, มี WLF-01, หรือ POST-REPAIR VALIDATION ยังไม่ CLEARED:
ห้ามล็อก และให้แจ้งว่าต้อง QC/REPAIR ก่อน

เมื่อตรวจพบ QC = PASS เท่านั้น:
ล็อกภาพ Output ล่าสุด
ของ SCENE ${fmt(currentScene)}
เป็นภาพที่ผ่าน QC

สถานะ:

SCENE ${fmt(currentScene)}
=
PASS & LOCK

ห้ามแก้ไข Scene นี้
โดยไม่มีคำสั่ง Director

${
            if (currentScene == 20) {

                """
SCENE 20 COMPLETE

SAVE_EP
HANDOFF
STOP

ห้ามเริ่ม EP ถัดไป
                """.trimIndent()

            } else {

                """
STOP

รอคำสั่ง
NEXT SCENE

ห้ามเริ่ม Scene ถัดไป
อัตโนมัติ
                """.trimIndent()
            }
        }
        """.trimIndent()
    }

    // ============================================================
    // NEXT SCENE — AUTO CONTEXT
    // ============================================================

    private fun buildNextSceneCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
NEXT SCENE

PREVIOUS SCENE:

SCENE ${fmt(currentScene - 1)}
=
PASS & LOCK

CURRENT SCENE:

SCENE ${fmt(currentScene)}

ขั้นตอนบังคับ:

1. ค้นหา EP MASTER ล่าสุด
   ในบทสนทนาเดียวกัน

2. ดึงบท
   SCENE ${fmt(currentScene)}
   จาก EP MASTER
   โดยอัตโนมัติ

3. ห้ามถาม Director
   ให้ส่งบท Scene ซ้ำ

4. ห้ามสร้างเนื้อเรื่องใหม่
   แทนบทเดิม

5. รักษา:

   - Story Continuity
   - Character Continuity
   - Wardrobe Continuity
   - Location Continuity
   - Emotional Continuity
   - Timeline Continuity

6. ตรวจ ORIGINAL IDENTITY MASTER
   ของตัวละครที่ต้องปรากฏ
   ใน SCENE ${fmt(currentScene)}

7. ถ้า Identity Master ครบ:

   เตรียมดำเนินการ
   SCENE ${fmt(currentScene)}
   ตามบทเดิมทันที

8. ถ้า Identity Master ไม่ครบ:

   แจ้งเฉพาะ
   ชื่อตัวละครที่ขาด

   ห้ามถามหาบท Scene

9. ห้ามใช้ภาพ Output
   จาก SCENE ${fmt(currentScene - 1)}
   เป็น Identity Master

10. ห้ามเปลี่ยน
    Natural Body

11. One Scene / One Image

12. ดำเนินการเฉพาะ:

    SCENE ${fmt(currentScene)}

13. หลังดำเนินการ:

    STOP

    รอ QC CHECK

ห้ามข้ามไป
SCENE ${fmt(currentScene + 1)}
อัตโนมัติ
        """.trimIndent()
    }

    // ============================================================
    // APP UPDATE
    // ============================================================

    private fun checkForAppUpdate() {
        status.text = "🔄 กำลังตรวจสอบเวอร์ชันล่าสุด…"

        Thread {
            try {
                val connection = (URL(
                    "https://api.github.com/repos/pearparinya/auto-movie-r6-4/releases/latest"
                ).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AUTO-MOVIE-Android")
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("GitHub HTTP $responseCode")
                }

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val release = JSONObject(json)
                val tag = release.optString("tag_name").removePrefix("R").removePrefix("v")
                val assets = release.optJSONArray("assets")
                    ?: throw IllegalStateException("ไม่พบไฟล์ APK")

                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    throw IllegalStateException("ไม่พบไฟล์ APK ในรุ่นล่าสุด")
                }

                runOnUiThread {
                    if (!isNewerVersion(tag, appVersion())) {
                        status.text = "✓ AUTO-MOVIE R${appVersion()} เป็นเวอร์ชันล่าสุดแล้ว"
                    } else {
                        showUpdateDialog(tag, apkUrl)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "⚠ ตรวจสอบอัปเดตไม่สำเร็จ"
                    AlertDialog.Builder(this)
                        .setTitle("ตรวจสอบอัปเดตไม่สำเร็จ")
                        .setMessage("กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง\n\n${e.message ?: ""}")
                        .setPositiveButton("ตกลง", null)
                        .show()
                }
            }
        }.start()
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(r.size, l.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun showUpdateDialog(version: String, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("พบ AUTO-MOVIE R$version")
            .setMessage("มีเวอร์ชันใหม่พร้อมใช้งาน\n\nกด “ดาวน์โหลดและติดตั้ง” เพื่อเริ่มอัปเดต")
            .setNegativeButton("ไว้ภายหลัง", null)
            .setPositiveButton("ดาวน์โหลดและติดตั้ง") { _, _ ->
                downloadAndInstallUpdate(version, apkUrl)
            }
            .show()
    }

    private fun downloadAndInstallUpdate(version: String, apkUrl: String) {
        try {
            val fileName = "AUTO-MOVIE-R$version-release.apk"
            val targetFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (targetFile.exists()) targetFile.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("AUTO-MOVIE R$version")
                .setDescription("กำลังดาวน์โหลดอัปเดต…")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(targetFile))

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            status.text = "⬇ กำลังดาวน์โหลด AUTO-MOVIE R$version…"

            Thread {
                var finished = false
                while (!finished) {
                    val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                    cursor.use {
                        if (it != null && it.moveToFirst()) {
                            val state = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            when (state) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    finished = true
                                    runOnUiThread {
                                        status.text = "✓ ดาวน์โหลดเสร็จแล้ว • เปิดหน้าติดตั้ง"
                                        installDownloadedApk(targetFile)
                                    }
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    finished = true
                                    runOnUiThread {
                                        status.text = "⛔ ดาวน์โหลดอัปเดตไม่สำเร็จ"
                                    }
                                }
                            }
                        }
                    }
                    if (!finished) Thread.sleep(750)
                }
            }.start()
        } catch (e: Exception) {
            status.text = "⛔ เริ่มดาวน์โหลดอัปเดตไม่สำเร็จ"
        }
    }

    private fun installDownloadedApk(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            status.text = "⚠ เปิดตัวติดตั้งไม่สำเร็จ"
            AlertDialog.Builder(this)
                .setTitle("เปิดตัวติดตั้งไม่สำเร็จ")
                .setMessage("Android อาจต้องอนุญาตให้ AUTO-MOVIE ติดตั้งแอปจากแหล่งนี้ก่อน")
                .setPositiveButton("ตกลง", null)
                .show()
        }
    }

    // ============================================================
    // HELP
    // ============================================================

    private fun showHelp() {

        AlertDialog.Builder(this)

            .setTitle(
                "วิธีใช้งาน AUTO-MOVIE R${appVersion()}"
            )

            .setMessage(
                """
AUTO-CONTEXT + WARDROBE FIREWALL WORKFLOW

1. ใส่ชื่อเรื่อง
   หรือ Director Command

2. กด CREATE EP

ระบบจะสร้าง
EP MASTER จำนวน 20 Scene

3. แนบ ORIGINAL
   IDENTITY MASTER
   ของตัวละคร

4. กลับมาที่แอป
   กด GENERATE SCENE

AUTO-CONTEXT
จะดึงบท Scene
จาก EP MASTER เดิม
โดยอัตโนมัติ

ไม่ต้องคัดลอกบท
Scene มาวางใหม่

5. เมื่อได้ภาพ
   กด QC CHECK

QC จะตรวจ WARDROBE FIREWALL
ถ้าชุดรั่วจาก Identity Master: WLF-01 = FAIL

6. ถ้า FAIL
   กด REPAIR

REPAIR จะทำ Delta-Only และตรวจ defect เดิมซ้ำด้วย POST-REPAIR VALIDATION

สูงสุด:
3 ครั้ง / Scene

7. เมื่อ QC PASS
   กด PASS & LOCK

8. NEXT SCENE
   จะเปิดให้กด

9. กด NEXT SCENE

ระบบจะดึง
Scene ถัดไป
จาก EP MASTER เดิม
โดยอัตโนมัติ

10. ทำซ้ำจนถึง
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

            text = if (title.firstOrNull() in listOf('❶','❷','❸','❹','❺','❻')) {
                android.text.SpannableString(title).apply {
                    setSpan(
                        android.text.style.AbsoluteSizeSpan(22, true),
                        0, 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                title
            }
            contentDescription = "$title — $subtitle"

            textSize = 14f

            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(
                11, 14, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )

            isAllCaps = false

            gravity =
                Gravity.CENTER

            setPadding(
                dp(12),
                dp(5),
                dp(12),
                dp(5)
            )

            minHeight = dp(40)

            setTextColor(ice)
            setTypeface(typeface, Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(panel)
            elevation = dp(2).toFloat()

            setOnClickListener {
                function()
            }
        }
    }

    private fun section(
        textValue: String
    ): TextView {

        return TextView(this).apply {

            text =
                textValue

            textSize =
                13f

            setTextColor(gold)

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                dp(4),
                0,
                dp(4)
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
                dp(1),
                0,
                dp(1)
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
