package com.pearparinya.automovie

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var currentEp = 1
    private var currentScene = 1
    private var repairCount = 0
    private var sceneLocked = false
    private var selectedCategory: String? = null
    private var activeStep = 1
    private var storyStartTimeMs = 0L
    private var storyEndTimeMs = 0L
    private var soundEnabled = true
    private var lastSoundStep = 0
    private var shareInProgress = false
    private var lastShareAtMs = 0L
    private lateinit var timeLabel: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val stepButtons = mutableMapOf<Int, Button>()

    private lateinit var sceneLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var nextButton: Button
    private lateinit var input: EditText
    private lateinit var titlePanel: LinearLayout
    private lateinit var nextHint: TextView
    private lateinit var confirmOutputButton: Button
    private lateinit var characterMasterButton: Button
    private var pendingCharacterKey: String? = null

    private val characterNames = linkedMapOf(
        "gawin" to "กวิน",
        "rinlada" to "รินลดา",
        "mind" to "มายด์"
    )

    private val characterPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val key = pendingCharacterKey
        pendingCharacterKey = null
        if (uri != null && key != null) {
            saveCharacterMaster(key, uri)
        }
    }

    private val navy = Color.rgb(12, 15, 38)
    private val panel = Color.rgb(37, 32, 78)
    private val gold = Color.rgb(255, 190, 74)
    private val ice = Color.rgb(248, 247, 255)
    private val muted = Color.rgb(196, 193, 222)
    private val success = Color.rgb(83, 224, 210)
    private val activeGreen = Color.rgb(46, 204, 113)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(navy)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(42), dp(14), dp(4))
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
        }, LinearLayout.LayoutParams(dp(78), dp(78)).apply {
            marginEnd = dp(8)
        })

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(4), 0, 0, 0)
        }

        headerText.addView(TextView(this).apply {
            text = "AUTO-MOVIE 2.0"
            textSize = 19f
            letterSpacing = 0.02f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(14, 19, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, full())

        headerText.addView(TextView(this).apply {
            text = "ระบบสร้างหนังอัตโนมัติ"
            textSize = 17f
            setTextColor(muted)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(
                12, 18, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
            setPadding(0, 0, 0, 0)
        }, full())

        headerText.addView(TextView(this).apply {
            val buildNumber = packageManager.getPackageInfo(packageName, 0).versionCode
            text = "R${appVersion()} • BUILD เวอร์ชั่น $buildNumber"
            textSize = 11f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
        }, full())

        headerText.addView(TextView(this).apply {
            text = "พัฒนาโดย : นายปริญญา จันทร์จักษุ"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(
                8, 10, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
        }, full())

        headerText.addView(TextView(this).apply {
            text = "MASTER FIDELITY • WARDROBE DECOUPLED • MODERN CINEMA"
            textSize = 10f
            setTextColor(ice)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
        }, full())

        headerRow.addView(headerText, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))

        root.addView(headerRow, full())

        timeLabel = TextView(this).apply {
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            maxLines = 1
            isSingleLine = true
            setPadding(dp(8), dp(2), dp(8), dp(4))
        }
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timeRow.addView(timeLabel, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))
        val soundButton = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setTextColor(gold)
            contentDescription = "เปิดหรือปิดเสียงแจ้งเตือน"
            setOnClickListener {
                soundEnabled = !soundEnabled
                text = if (soundEnabled) "🔊" else "🔇"
                statePrefs.edit().putBoolean("soundEnabled", soundEnabled).apply()
                if (soundEnabled) playTone(ToneGenerator.TONE_PROP_BEEP, 70)
            }
        }
        soundButton.text = if (statePrefs.getBoolean("soundEnabled", true)) "🔊" else "🔇"
        timeRow.addView(soundButton, LinearLayout.LayoutParams(dp(42), dp(32)))
        root.addView(timeRow, full())
        startClock()

        // ============================================================
        // SCENE STATUS
        // ============================================================

        sceneLabel = TextView(this).apply {
            text = "EP 01   •   SCENE 01 / 20   •   8 SEC"
            textSize = 16f
            setTextColor(ice)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setPadding(
                dp(10),
                dp(4),
                dp(10),
                dp(4)
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
            full(dp(7))
        )

        // ============================================================
        // DIRECTOR COMMAND
        // ============================================================

        input = EditText(this).apply {
            hint = "ชื่อเรื่อง / คำสั่งผู้กำกับ…"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            minLines = 1
            maxLines = 2
            gravity = Gravity.TOP

            setPadding(
                dp(14),
                dp(6),
                dp(14),
                dp(6)
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
            stepAction(1, "❶ ✨ สร้างเรื่องใหม่ • NEW STORY", "เลือกชื่อเรื่องและหมวดเรื่อง") { generateTitles() },
            full()
        )

        titlePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(panel)
        }
        root.addView(titlePanel, full())

        characterMasterButton = Button(this).apply {
            textSize = 12f
            maxLines = 1
            isSingleLine = true
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnClickListener { showCharacterMasterSetup() }
        }
        root.addView(characterMasterButton, full(dp(38)))
        refreshCharacterMasterButton()

        root.addView(section("⚡ FAST FLOW • ONE-TAP SCENE").apply { gravity = Gravity.CENTER })
        nextHint = TextView(this).apply {
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            maxLines = 1
            isSingleLine = true
            setPadding(0, 0, 0, dp(3))
        }
        root.addView(nextHint, full())

        root.addView(
            stepAction(2, "❷ 🎬 สร้างโครงเรื่อง + ฉาก 01 • START", "ส่งครั้งเดียว: วางโครง EP และสร้างภาพฉากแรกทันที") { startQuickStory() },
            full()
        )
        root.addView(
            stepAction(3, "❸ ➜ สร้างฉากถัดไป • NEXT SCENE", "สร้างฉากต่อไปทันที ไม่ต้อง CONFIRM/QC/LOCK") { generateNextQuickScene() },
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
        // HELP + NEW STORY + UPDATE — ONE ROW / 3 MENUS
        // ============================================================

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        utilityRow.addView(
            Button(this).apply {
                text = "❓ วิธีใช้งาน"
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
                text = "เริ่มเรื่องใหม่"
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
                    Color.rgb(230, 65, 65)
                )
                setOnClickListener {
                    confirmNewStory()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            ).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        )

        utilityRow.addView(
            Button(this).apply {
                text = "↪ Update"
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
                    checkForAppUpdate(silent = false)
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

        restoreWorkState()
        migrateWorkflowStateIfNeeded()
        refreshStepIndicator()
        setContentView(scroll)

        // ตรวจอัปเดตอัตโนมัติ 1 ครั้งเมื่อเปิดแอป
        // ถ้าไม่มีรุ่นใหม่จะไม่รบกวนผู้ใช้
        checkForAppUpdate(silent = true)
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

    private val statePrefs by lazy {
        getSharedPreferences("auto_movie_state", Context.MODE_PRIVATE)
    }

    private fun characterMasterFile(key: String): File {
        val dir = File(filesDir, "character_masters")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.master")
    }

    private fun characterMastersReady(): Boolean =
        characterNames.keys.all { characterMasterFile(it).exists() }

    // Identity-safe selective master engine.
    // The app owns the cast schedule, so it can attach only the original
    // masters that are actually allowed to appear in the current scene.
    private fun sceneCastKeys(scene: Int): List<String> {
        // BUILD 695: Dynamic Active Cast.
        // START establishes all three masters. NEXT SCENE rotates the minimum
        // useful cast so scenes do not default to the same 3-person composition.
        // The story engine may keep continuity, but only these masters are sent.
        return when (scene.coerceIn(1, 20)) {
            1 -> listOf("gawin", "rinlada", "mind")
            2 -> listOf("rinlada")
            3 -> listOf("mind")
            4 -> listOf("gawin")
            5, 9, 13, 17 -> listOf("gawin", "rinlada")
            6, 10, 14, 18 -> listOf("rinlada", "mind")
            7, 11, 15, 19 -> listOf("gawin", "mind")
            else -> listOf("gawin", "rinlada", "mind")
        }
    }

    private fun sceneCastNames(scene: Int): String =
        sceneCastKeys(scene).mapNotNull { characterNames[it] }.joinToString(" + ")

    private fun sceneCastPlan(): String =
        (1..20).joinToString("\n") { scene ->
            "SCENE ${fmt(scene)} = ${sceneCastNames(scene)}"
        }

    private fun shareableCharacterMasterFile(key: String): File? {
        val source = characterMasterFile(key)
        if (!source.exists()) return null

        // ChatGPT/Android receivers may reject the private ".master" extension
        // even when the bytes are a valid image. Export an attachment copy with
        // a real image extension while keeping the protected original unchanged.
        val exportDir = File(cacheDir, "character_master_exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val displayName = when (key) {
            "gawin" -> "GAWIN_MASTER.png"
            "rinlada" -> "RINLADA_MASTER.png"
            "mind" -> "MIND_MASTER.png"
            else -> "${key.uppercase()}_MASTER.png"
        }
        val exported = File(exportDir, displayName)
        source.inputStream().use { input ->
            exported.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return exported
    }

    private fun characterMasterUris(keys: List<String>): ArrayList<Uri> {
        val uris = arrayListOf<Uri>()
        keys.forEach { key ->
            shareableCharacterMasterFile(key)?.let { file ->
                uris.add(
                    FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )
                )
            }
        }
        return uris
    }

    private fun characterMasterUris(): ArrayList<Uri> =
        characterMasterUris(characterNames.keys.toList())

    private fun refreshCharacterMasterButton() {
        if (!::characterMasterButton.isInitialized) return
        val count = characterNames.keys.count { characterMasterFile(it).exists() }
        characterMasterButton.text =
            if (count == 3) "👥 ตัวละครหลักพร้อม 3/3 • CHARACTER MASTER"
            else "👥 ตั้งค่าตัวละครหลัก $count/3 • CHARACTER MASTER"
        characterMasterButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (count == 3) activeGreen else gold
            )
    }

    private fun showCharacterMasterSetup() {
        val labels = characterNames.map { (key, name) ->
            val ready = characterMasterFile(key).exists()
            "${if (ready) "✓" else "○"} $name"
        }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("ตัวละครหลัก • CHARACTER MASTER")
            .setSingleChoiceItems(labels, -1, null)
            .setNegativeButton("ปิด", null)
            .setPositiveButton("เลือกรูป", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val which = dialog.listView.checkedItemPosition
                if (which == android.widget.AdapterView.INVALID_POSITION) {
                    Toast.makeText(this, "แตะเลือก กวิน / รินลดา / มายด์ ก่อน", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pendingCharacterKey = characterNames.keys.elementAt(which)
                dialog.dismiss()
                characterPicker.launch(arrayOf("image/*"))
            }
        }
        dialog.show()
    }

    private fun saveCharacterMaster(key: String, uri: Uri) {
        try {
            val target = characterMasterFile(key)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                target.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("เปิดรูปไม่ได้")
            refreshCharacterMasterButton()
            val name = characterNames[key] ?: key
            val count = characterNames.keys.count { characterMasterFile(it).exists() }
            status.text = "✓ บันทึก $name แล้ว • CHARACTER MASTER $count/3"
            playTone(ToneGenerator.TONE_PROP_ACK, 90)
        } catch (_: Exception) {
            status.text = "⛔ บันทึกรูป CHARACTER MASTER ไม่สำเร็จ"
        }
    }

    private fun confirmNewStory() {
        val currentTitle = if (::input.isInitialized) input.text.toString().trim() else ""
        val label = if (currentTitle.isNotBlank()) "“$currentTitle”" else "งานปัจจุบัน"

        android.app.AlertDialog.Builder(this)
            .setTitle("เริ่มเรื่องใหม่?")
            .setMessage("$label จะถูกยกเลิก และ AUTO-MOVIE จะกลับไปเริ่มขั้นตอน ①\n\nแชทใน ChatGPT จะไม่ถูกลบ")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("เริ่มเรื่องใหม่") { _, _ ->
                startNewStory()
            }
            .show()
    }

    private fun startNewStory() {
        // ยกเลิกเฉพาะสถานะงานใน AUTO-MOVIE ไม่แตะต้องแชท ChatGPT
        input.setText("")
        titlePanel.removeAllViews()
        titlePanel.visibility = android.view.View.GONE
        selectedCategory = null
        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        activeStep = 1
        storyStartTimeMs = 0L
        storyEndTimeMs = 0L

        statePrefs.edit()
            .remove("story")
            .remove("category")
            .putInt("ep", 1)
            .putInt("scene", 1)
            .putInt("repair", 0)
            .putBoolean("locked", false)
            .putInt("activeStep", 1)
            .putLong("storyStartTimeMs", 0L)
            .putLong("storyEndTimeMs", 0L)
            .apply()

        sceneLabel.text = "EP 01   •   SCENE 01 / 20   •   8 SEC"
        progress.progress = 1
        updateTimeLabel()
        updateNextButton()
        refreshStepIndicator()
        status.text = "● พร้อมเริ่มเรื่องใหม่ • เริ่มที่ ① TITLES"
        playTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    private fun sceneLedgerKey(ep: Int, scene: Int) = "sceneLedger_${ep}_${scene}"

    private fun saveSceneLedger(ep: Int, scene: Int) {
        val previous = if (scene > 1) "EP ${fmt(ep)} SCENE ${fmt(scene - 1)}" else if (ep > 1) "EP ${fmt(ep - 1)} SCENE 20 HANDOFF" else "STORY START"
        val ledger = "CURRENT=EP ${fmt(ep)} SCENE ${fmt(scene)}; PREVIOUS=$previous; STORY=${if (::input.isInitialized) input.text.toString().trim() else ""}; CATEGORY=${selectedCategory.orEmpty()}"
        statePrefs.edit().putString(sceneLedgerKey(ep, scene), ledger).apply()
    }

    private fun currentSceneLedger(): String =
        statePrefs.getString(sceneLedgerKey(currentEp, currentScene), "").orEmpty()

    private fun saveWorkState() {
        statePrefs.edit()
            .putString("story", if (::input.isInitialized) input.text.toString() else "")
            .putString("category", selectedCategory)
            .putInt("ep", currentEp)
            .putInt("scene", currentScene)
            .putInt("repair", repairCount)
            .putBoolean("locked", sceneLocked)
            .putInt("categoryIndex", categoryIndex)
            .putInt("activeStep", activeStep)
            .putLong("storyStartTimeMs", storyStartTimeMs)
            .putLong("storyEndTimeMs", storyEndTimeMs)
            .apply()
    }

    private fun restoreWorkState() {
        soundEnabled = statePrefs.getBoolean("soundEnabled", true)
        selectedCategory = statePrefs.getString("category", null)
        currentEp = statePrefs.getInt("ep", 1).coerceIn(1, 5)
        currentScene = statePrefs.getInt("scene", 1).coerceIn(1, 20)
        repairCount = statePrefs.getInt("repair", 0).coerceIn(0, 3)
        sceneLocked = statePrefs.getBoolean("locked", false)
        categoryIndex = statePrefs.getInt("categoryIndex", 0).coerceIn(0, storyCategories.lastIndex)
        activeStep = statePrefs.getInt("activeStep", 1).let { old ->
            when {
                old <= 1 -> 1
                old == 2 -> 2
                else -> 3
            }
        }
        storyStartTimeMs = statePrefs.getLong("storyStartTimeMs", 0L)
        storyEndTimeMs = statePrefs.getLong("storyEndTimeMs", 0L)
        updateTimeLabel()

        val savedStory = statePrefs.getString("story", "").orEmpty()
        if (savedStory.isNotBlank()) {
            input.setText(savedStory)
            input.setSelection(input.text.length)
        }

        sceneLabel.text = "EP ${fmt(currentEp)}   •   SCENE ${fmt(currentScene)} / 20   •   8 SEC"
        progress.progress = currentScene
        updateNextButton()

        if (savedStory.isNotBlank()) {
            status.text = "✓ กู้คืนงานเดิมแล้ว • SCENE ${fmt(currentScene)}"
        }
        refreshStepIndicator()
    }

    private fun generateTitles() {
        if (storyInProgress()) {
            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text = "⛔ STORY ปัจจุบันยังไม่จบ • ทำ EP 01–05 ให้ครบก่อน"
            return
        }
        if (!requireStep(1)) return
        val category = storyCategories[categoryIndex]
        selectedCategory = category
        val bank = titleBanks[categoryIndex]

        // หมวดหมู่จะหมุนเมื่อ CREATE EP สำเร็จ ไม่ใช่ตอนเพียงเปิดรายชื่อ Titles
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
                    titlePanel.visibility = android.view.View.GONE
                    setActiveStep(2)
                    saveWorkState()
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

    private fun startQuickStory() {
        if (!requireStep(2)) return
        if (!characterMastersReady()) {
            status.text = "⚠ ตั้งค่าตัวละครหลัก 3/3 ก่อนเริ่มเรื่อง"
            showCharacterMasterSetup()
            return
        }
        val story = input.text.toString().trim()
        if (story.isBlank()) { status.text = "⚠ เลือกชื่อเรื่องก่อน"; return }
        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        storyStartTimeMs = System.currentTimeMillis()
        storyEndTimeMs = 0L
        val category = selectedCategory ?: storyCategories[categoryIndex]
        selectedCategory = category
        val usedIndex = storyCategories.indexOf(category)
        categoryIndex = if (usedIndex >= 0) (usedIndex + 1) % storyCategories.size else (categoryIndex + 1) % storyCategories.size
        setActiveStep(3)
        saveSceneLedger(currentEp, currentScene)
        updateUi()
        saveWorkState()
        share(safeNormalizePrompt(buildQuickStartCommand(story, category)), characterKeys = characterNames.keys.toList())
        status.text = "⚡ FAST START • SCENE 01 • 3 CAST LOCKED"
    }

    private fun generateNextQuickScene() {
        if (!requireStep(3)) return
        if (!characterMastersReady()) { showCharacterMasterSetup(); return }

        // EP boundary is deliberate: Scene 20 creates a handoff and stops.
        // The next press starts the following EP at Scene 01 using that handoff contract.
        if (currentScene < 20) {
            currentScene++
            saveSceneLedger(currentEp, currentScene)
            updateUi()
            saveWorkState()
            share(safeNormalizePrompt(buildQuickSceneCommand()), characterKeys = sceneCastKeys(currentScene))
            status.text = "⚡ EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} • ส่งแล้ว"
            return
        }

        if (currentEp >= 5) {
            storyEndTimeMs = System.currentTimeMillis()
            selectedCategory = null
            setActiveStep(1)
            saveWorkState()
            updateUi()
            status.text = "🏁 STORY COMPLETE • 5 EP / 100 SCENES"
            return
        }

        currentEp++
        currentScene = 1
        saveSceneLedger(currentEp, currentScene)
        updateUi()
        saveWorkState()
        share(safeNormalizePrompt(buildQuickSceneCommand()), characterKeys = sceneCastKeys(currentScene))
        status.text = "🎬 HANDOFF → EP ${fmt(currentEp)} • SCENE 01"
    }

    // BUILD 694: normalize generated instructions before sharing to ChatGPT.
    // This keeps identity/continuity requirements while removing unnecessary
    // body-focused wording that is not needed to create the scene.
    private fun safeNormalizePrompt(command: String): String {
        return command
            .replace("natural body build+proportions", "overall natural appearance")
            .replace("natural body proportions", "overall natural appearance")
            .replace("รูปร่าง/สัดส่วนธรรมชาติ", "ภาพลักษณ์โดยรวมตามธรรมชาติ")
            .replace("สัดส่วนธรรมชาติ", "ภาพลักษณ์โดยรวม")
            .replace("รูปร่างระหว่างกัน", "ภาพลักษณ์ระหว่างกัน")
            .replace("รูปร่าง/สัดส่วน", "ภาพลักษณ์โดยรวม")
            .replace("เน้นสัดส่วนทางเพศ", "เน้นรายละเอียดทางกายภาพที่ไม่จำเป็น")
            .replace(Regex("(?i)FEMALE BODY ZERO-RESHAPE:[^\\n]*"), "CHARACTER APPEARANCE LOCK: preserve the same adult character identity and overall natural appearance from the attached ORIGINAL MASTER.")
            .replace(Regex("(?i)FEMALE FACE PRIORITY[^\\n]*"), "FEMALE IDENTITY PRIORITY: preserve face, hairstyle, approximate age and overall appearance from that character's ORIGINAL MASTER.")
    }

    private fun buildQuickStartCommand(story: String, category: String): String {
        return rules() + """

QUICK FLOW COMMAND:
START STORY + BUILD STORY BIBLE + GENERATE EP 01 SCENE 01 NOW

SOURCE OF TRUTH:
ชื่อเรื่อง: $story
หมวดเรื่อง: $category
ตอน: EP 01 / 05
ฉากปัจจุบัน: SCENE 01 / 20
ความยาว: 8 วินาที
รูปแบบ: 9:16 • One Scene / One Image • Flow/Veo 3.1
กล้อง: Locked-off static camera

คำสั่งบังคับ:
- ภายในคำตอบเดียว สร้าง STORY BIBLE แบบกระชับ วางเส้นเรื่อง EP 01–05 และแผน EP 01 จำนวน 20 ฉาก
- จากนั้นสร้างภาพ SCENE 01 ทันที ห้ามหยุดรอ CONFIRM
- START ATTACHMENT HANDSHAKE = REQUIRED
- คำสั่ง START นี้ต้องมี ORIGINAL CHARACTER MASTER ครบ 3 ภาพแนบมาพร้อมข้อความใน Android share payload เดียวกัน
- ATTACHMENT 1 = กวิน / MASTER 01
- ATTACHMENT 2 = รินลดา / MASTER 02
- ATTACHMENT 3 = มายด์ / MASTER 03
- ก่อนทำ STORY BIBLE ให้ตรวจว่ามองเห็นภาพแนบทั้ง 3 ภาพจริง หากไม่ครบ ให้หยุดและแจ้ง ATTACHMENT TRANSPORT FAIL ห้ามสร้างภาพโดยเดาใบหน้า
- SCENE 01 ACTIVE CAST = กวิน + รินลดา + มายด์ พร้อมกัน EXACTLY 3 PEOPLE
- ภาพ SCENE 01 ต้องมีตัวละครหลักครบ 3 คนในเฟรมเดียว: ATTACHMENT 1 = กวิน, ATTACHMENT 2 = รินลดา, ATTACHMENT 3 = มายด์
- TRIPLE CHARACTER COMPOSER = HARD REQUIREMENT: ห้ามลดเหลือ 1 หรือ 2 คน ห้ามเพิ่มคนที่ 4 ห้ามสร้างคนซ้ำ
- แต่ละคนต้องรักษา ORIGINAL IDENTITY MASTER ของตนเองโดยตรง ห้ามผสมใบหน้า/ทรงผม/รูปร่างระหว่างกัน
- COMPOSITION SAFE MODE: ใช้ภาพครึ่งตัวหรือเต็มตัวตามธรรมชาติ เสื้อผ้าปกติสุภาพ และหลีกเลี่ยง framing/คำบรรยายที่เน้นสัดส่วนทางเพศ
- LEAN IMAGE PIPELINE V1: ใช้ ORIGINAL MASTER ทั้ง 3 ภาพเป็นตัวอ้างอิงตัวละครผู้ใหญ่โดยตรง แล้วสร้าง FINAL IMAGE ทันที
- EXACT CAST: ภาพต้องมีตัวละครผู้ใหญ่ 3 คนพอดี — CHAR_01 กวิน, CHAR_02 รินลดา, CHAR_03 มายด์ — ห้ามเพิ่มคน ห้ามทำคนซ้ำ
- IDENTITY CORE: รักษาใบหน้าและทรงผมของแต่ละคนให้ใกล้ ORIGINAL MASTER และห้ามสลับ identity ระหว่างกัน
- MASTER VISUAL FIDELITY V1: ใช้ ORIGINAL MASTER ของแต่ละคนเป็น visual reference หลักและรักษาภาพลักษณ์โดยรวมของตัวละครให้เป็นธรรมชาติและสอดคล้องกับ MASTER โดยไม่แจกแจงลักษณะทางกายภาพที่ไม่จำเป็น
- MASTER FIDELITY PRIORITY V2: ลำดับความสำคัญของภาพต้องเป็น ORIGINAL MASTER identity/overall appearance → story continuity → wardrobe → cinematic styling; งานจัดแสงและความสวยงามห้ามกลบเอกลักษณ์ของ MASTER
- NO BEAUTY NORMALIZATION: ห้ามทำให้ตัวละครทั้งสามเข้าหา face/beauty template เดียวกัน ห้ามทำให้ใบหน้าคล้ายกัน และห้ามเปลี่ยนลักษณะเฉพาะของแต่ละ MASTER เพียงเพื่อความ cinematic
- WARDROBE CONTINUITY LOCK: หากยังเป็นเหตุการณ์ต่อเนื่องเดียวกัน ให้คงเสื้อผ้าของ CHAR_01/02/03 จากฉากก่อนตาม STORY CONTINUITY; เปลี่ยนชุดเฉพาะเมื่อเรื่องระบุการเปลี่ยนเวลา สถานที่ หรือมี wardrobe event ชัดเจน
- CHARACTER DISTINCTNESS: รักษาความแตกต่างของ CHAR_01/02/03 ทั้งใบหน้า ทรงผม และภาพลักษณ์โดยรวมตาม MASTER โดยไม่เพิ่มคำบรรยายทางกายภาพที่ไม่จำเป็น
- SCENE CORE: ใช้เฉพาะ story beat, action, location และ continuity ที่จำเป็นต่อฉากปัจจุบัน
- NORMAL WARDROBE: ใช้เสื้อผ้าปกติที่เหมาะกับฉากและ continuity โดยไม่บรรยายสรีระหรือรายละเอียดทางเพศ
- SIMPLE CAMERA: realistic Thai family drama, neutral eye-level medium/wide composition, vertical 9:16, เห็นตัวละครครบทั้ง 3 คน
- DIRECT FINAL IMAGE: ห้าม draft, planning placeholder, face-gate loop, policy commentary, auto-rewrite loop หรือ auto-regenerate; เมื่อข้อมูลครบให้สร้างภาพทันทีแบบ single pass
- FIXED CAST IDENTITY DNA — MASTER 02 รินลดา: ใช้ภาพ ATTACHMENT 2 เป็นแหล่งอัตลักษณ์เพียงแหล่งเดียวตลอด EP 01–05 ทุกครั้งที่รินลดาปรากฏ ต้องย้อนอ้างอิง ORIGINAL MASTER นี้ใหม่ ไม่สืบทอดใบหน้าจากภาพฉากก่อนหน้า
- ล็อกลักษณะจาก MASTER 02 เฉพาะ: facial geometry / head shape / eyes / eyebrows / nose / lips / jawline / ears / hair shape+color / skin tone / approximate age / natural body build+proportions
- ห้ามเปลี่ยนรินลดาให้สวยขึ้น อ่อนวัยขึ้น ผิวเนียนขึ้น หน้าเรียวขึ้น ตาโตขึ้น จมูกเปลี่ยน ทรงผมเปลี่ยน หรือรูปร่าง/สัดส่วนธรรมชาติเปลี่ยน แม้เพื่อ cinematic beauty
- MASTER WARDROBE FIREWALL: ชุดสีชมพู เครื่องประดับ ฉากหลังสีเขียว ท่ายืน และองค์ประกอบ portrait ใน MASTER 02 ไม่ใช่ Identity และห้ามคัดลอกมา เว้นแต่ STORY/SCENE ระบุเอง
- IDENTITY SOURCE IS IMMUTABLE: ห้ามใช้ Scene Output, ภาพแต่งงาน, background photo, storyboard, generated portrait หรือคำบรรยายข้อความแทน ORIGINAL MASTER
- IDENTITY GATE — HARD FAIL: ก่อนส่งภาพต้องตรวจตัวละครที่เห็นจริงกับ ORIGINAL MASTER ของคนนั้น หาก facial geometry / hair / facial hair / natural body proportions เปลี่ยนชัดเจน ให้ถือว่าภาพไม่ผ่านและสร้างใหม่ก่อนส่ง ห้ามยอมรับใบหน้าที่เพียงคล้าย
- MASTER-FIRST RECONSTRUCTION: ก่อนกำหนดท่าทาง/แสง/เสื้อผ้า ให้ยึด ATTACHMENT 2 เป็นฐานใบหน้า ศีรษะ ทรงผม สีผิว อายุโดยประมาณ และสัดส่วนธรรมชาติของรินลดาก่อน แล้วจึงเปลี่ยนเฉพาะ expression/pose/wardrobe ตามฉาก
- IDENTITY CHECKPOINTS: ตรวจซ้ำ 3 จุดก่อนส่ง: (1) eyes+nose+mouth geometry (2) jaw+hairline+facial hair (3) head-to-body proportions; หากข้อใด drift ชัดเจน = INVALID OUTPUT และต้องสร้างใหม่
- NO BEAUTY OVERRIDE: cinematic lighting, dramatic emotion, camera angle และ wardrobe ห้ามมีสิทธิ์เปลี่ยน identity geometry ของรินลดา
- LIKENESS FIDELITY MODE — MAX: เป้าหมายคือ “คนเดิมในสถานการณ์ใหม่” ไม่ใช่ “คนหน้าคล้าย”; ห้าม reinterpret ใบหน้าเป็นนักแสดงคนใหม่
- REFERENCE-IMAGE DOMINANCE — MAX: เมื่อ ORIGINAL MASTER ถูกแนบ ให้ข้อมูลภาพจริงมีอำนาจเหนือคำบรรยายใบหน้าทั้งหมด ห้ามให้โมเดลสร้างหน้าใหม่จากคำว่า “ชายไทย/หล่อ/วัย...” แล้วค่อยทำให้คล้าย MASTER
- FACE RECONSTRUCTION BAN: ห้าม redesign, restyle, idealize หรือสร้าง facial features ใหม่; อนุญาตเพียงคงบุคคลเดิมจาก ORIGINAL MASTER แล้วเปลี่ยนสีหน้า มุมศีรษะ และการกระทำเท่าที่ฉากต้องใช้
- DISTINCTIVE-TRAIT ANCHOR: ให้รักษาจุดจำเพาะที่เห็นจริงใน MASTER โดยเฉพาะ hairline, eyebrow shape, eye spacing, nose bridge+tip, lip contour, jaw width และ moustache/beard boundary พร้อมกัน ห้ามรักษาเพียงบางจุดแล้วปล่อยส่วนอื่น drift
- NATURAL TEXTURE LOCK: ห้าม skin retouch / beauty filter / face smoothing / symmetry correction ที่ทำให้บุคคลดูเป็นคนใหม่
- IDENTITY BEFORE CINEMA: หากความสวยของแสง มุมกล้อง depth-of-field หรืออารมณ์ขัดกับความเหมือน MASTER ให้ลดความ cinematic ลงและรักษา Identity ก่อนเสมอ
- POSE REPETITION BLOCK: ห้ามใช้ท่าเอามือค้ำคาง/แตะปาก/แตะขมับ/นั่งครุ่นคิดซ้ำจากฉากก่อน เว้นแต่ STORY BEAT ระบุชัด; NEXT SCENE ต้องเปลี่ยน blocking และ physical action ให้เห็นความคืบหน้าของเรื่อง
- PROP-ACTION RULE: พร็อพหลักต้องถูกใช้งานเพื่อเล่าเรื่องจริง ไม่ใช่เพียงวางประกอบฉาก; หลีกเลี่ยงการวน “เอกสาร + โต๊ะ + โน้ตบุ๊ก” หากไม่มีเหตุการณ์ใหม่รองรับ
- PRESERVE MICRO-IDENTITY: รักษาระยะตา ความหนา/แนวคิ้ว สันและปลายจมูก รูปริมฝีปาก แนวกราม hairline และตำแหน่ง/ความหนาหนวดเคราตาม ORIGINAL MASTER ให้ใกล้ที่สุด
- EXPRESSION DELTA ONLY: อารมณ์ของฉากเปลี่ยนได้เฉพาะกล้ามเนื้อสีหน้า/สายตา/ท่าทาง ห้ามให้อารมณ์เปลี่ยนโครงหน้า อายุ หรือความเป็นบุคคล
- CAMERA IDENTITY SAFETY: หลีกเลี่ยงเลนส์กว้างระยะใกล้ มุมกด/เงยจัด แสงแข็ง หรือ perspective ที่บิดรูปหน้า; ใช้มุมธรรมชาติและระยะที่ยังอ่านอัตลักษณ์จาก MASTER ได้ชัด
- FACE VISIBILITY GATE: เมื่อรินลดาเป็น ACTIVE CAST ต้องเห็นใบหน้าชัดพอสำหรับตรวจ Identity; ห้ามมือ/พร็อพ/เงามืดบังจุดสำคัญของตา จมูก ปาก และกรามเกินจำเป็น
- SCENE DIVERSITY FIREWALL: ห้ามวนภาพ “นั่งคิด/อ่านเอกสาร/โต๊ะทำงาน” ซ้ำโดยไม่มี STORY BEAT รองรับ; แต่ละฉากต้องมี ACTION + LOCATION/PROP PURPOSE ที่แตกต่างและเดินเรื่องจริง
- STORY-BEAT MOTION GATE: ฉากใหม่ต้องเปลี่ยน “เหตุการณ์” ไม่ใช่เพียงเปลี่ยนเสื้อ/มุม/ห้อง; ต้องระบุสิ่งที่ตัวละครกำลังทำซึ่งทำให้ข้อมูล ความสัมพันธ์ หรือความขัดแย้งเดินหน้าอย่างน้อย 1 ขั้น
- REPETITION MEMORY: ถือกิจกรรมต่อไปนี้เป็นกลุ่มเดียวกันและห้ามใช้ติดกันหรือวนซ้ำโดยไม่มีเหตุผลเรื่อง: นั่งครุ่นคิด / อ่านเอกสาร / ดูโน้ตบุ๊ก / จับกระดาษ / มือแตะหน้า-คาง-ขมับ
- ACTION-FIRST COMPOSITION: ให้เลือก physical action จาก STORY BEAT ก่อน แล้วค่อยกำหนด location, prop, wardrobe, expression และ framing; ห้ามเริ่มจาก pose หล่อ/portrait แล้วแต่งเรื่องตามภาพ
- LOCATION ROTATION: หาก STORY รองรับ ให้สลับพื้นที่ใช้งานจริง เช่น ห้องนอน ห้องนั่งเล่น ห้องครัว หน้าบ้าน ที่ทำงาน รถ ร้าน/สถานที่นัดหมาย แทนการวนโต๊ะเดิม; ห้ามย้ายสถานที่แบบไร้เหตุผลเพียงเพื่อความต่าง
- PROP CONTINUITY: พร็อพที่เคยเปิดเผยข้อมูลแล้วต้องไม่ถูกนำกลับมาเป็นจุดเด่นซ้ำ เว้นแต่มีข้อมูลใหม่หรือการกระทำใหม่ที่เปลี่ยนสถานการณ์
- VISUAL DELTA CHECK: ก่อนส่งภาพ เปรียบเทียบกับฉากก่อนในด้าน ACTION / BODY BLOCKING / LOCATION / HERO PROP; หากเหมือนกันตั้งแต่ 2 ด้านขึ้นไป ให้ปรับฉากใหม่ก่อนส่ง โดยยังรักษา STORY CONTINUITY และ ORIGINAL IDENTITY MASTER
- IDENTITY STABLE, SCENE VARIABLE: ล็อกเฉพาะอัตลักษณ์ธรรมชาติของตัวละคร; เสื้อผ้า ท่าทาง พร็อพ แสง และสถานที่ต้องเปลี่ยนตามเวลา/เหตุการณ์อย่างสมเหตุผล ห้ามใช้ความเหมือน MASTER เป็นข้ออ้างให้สร้าง composition ซ้ำ
- CINEMATIC ≠ PORTRAIT: ห้ามสร้างภาพเหมือนถ่ายโปรไฟล์; ตัวละครต้องกำลังกระทำสิ่งที่สัมพันธ์กับ STORY BEAT และสภาพแวดล้อมต้องมีข้อมูลเรื่องราว
- SCENE NOVELTY MATRIX — HARD GATE: ก่อนสร้างภาพให้กำหนด 5 ค่า ACTION / BODY POSITION / LOCATION / HERO PROP / EMOTIONAL EVENT แล้วเทียบกับฉากก่อนหน้า; ฉากใหม่ต้องต่างอย่างมีนัยสำคัญอย่างน้อย 3 จาก 5 ค่า มิฉะนั้นให้ redesign scene ก่อนสร้างภาพ
- NO COSMETIC VARIATION: การเปลี่ยนเพียงสีเสื้อ แสง มุมเฟรม หรือชนิดกระดาษ ไม่ถือว่าเป็นฉากใหม่ หากเหตุการณ์และการกระทำยังเหมือนเดิม
- ACTION VERB LOCK: ทุกฉากต้องมีคำกริยาการกระทำหลักที่ต่างจากฉากก่อน เช่น เดินออกจากห้อง / เปิดประตู / โทรศัพท์ / เก็บกระเป๋า / เผชิญหน้า / ซ่อนของ / ส่งของ / ขับรถ / พบใครบางคน; ห้ามใช้ “นั่งอ่าน/นั่งคิด/มองเอกสาร” เป็นค่าเริ่มต้น
- STORY CONSEQUENCE GATE: ภาพต้องแสดงผลของ STORY BEAT ที่เปลี่ยนสถานการณ์ให้มองเห็นได้ ไม่ใช่เพียงอารมณ์ครุ่นคิด; ถ้าเอาภาพฉากก่อนมาเปลี่ยนเสื้อแล้วเรื่องยังสื่อเหมือนเดิม = INVALID OUTPUT
- SAME-ROOM LIMIT: ห้ามใช้ห้องเดิมเกิน 2 ฉากติดกัน เว้นแต่เป็นเหตุการณ์ต่อเนื่องโดยตรง; หากต่อเนื่องในห้องเดิมต้องเปลี่ยน blocking, action และ hero prop อย่างชัดเจน
- REPEATED-PAPER FIREWALL: หลังฉากที่ใช้เอกสาร/ใบแจ้งหนี้/กระดาษเป็น HERO PROP แล้ว ห้ามใช้กระดาษหรือหน้าจอเป็น HERO PROP ซ้ำใน 2 ฉากถัดไป เว้นแต่ข้อมูลใหม่จากพร็อพนั้นเป็น turning point โดยตรง
- READING/THINKING POSE COOLDOWN — HARD: หลังฉากที่ตัวละคร “นั่งอ่านเอกสาร / ถือกระดาษ / นั่งครุ่นคิด / มือแตะคาง” ห้ามใช้ action หรือ pose กลุ่มนี้ซ้ำอีกอย่างน้อย 3 ฉาก แม้จะเปลี่ยนเสื้อผ้า ห้อง หรือมุมภาพ
- PROP EXIT RULE: เมื่อข้อมูลจากเอกสารถูกอ่านและรับรู้แล้ว ให้ลดเอกสารเป็น background/วางทิ้ง/เก็บเข้าที่ และเปลี่ยน HERO PROP ไปเป็นสิ่งที่เกิดจากผลของข้อมูล เช่น โทรศัพท์ กุญแจ กระเป๋า ประตู รถ หรือบุคคลที่ต้องเผชิญหน้า
- EVENT-FIRST COMPOSITION: ออกแบบภาพจาก “สิ่งที่กำลังเกิดขึ้น” ก่อนเลือก pose; อย่างน้อยหนึ่งการกระทำต้องมีผลทางกายภาพที่มองเห็นได้ เช่น ลุกขึ้น เดินออก เปิดประตู โทรหา เก็บของ ส่งของ ซ่อนของ เผชิญหน้า หรือออกจากสถานที่
- SCENE PURPOSE TEST — HARD FAIL: ถ้าตัดใบหน้าตัวละครออกแล้วภาพปัจจุบันยังสื่อเหตุการณ์แทบเหมือนฉากก่อน แสดงว่าฉากซ้ำ ให้ redesign ACTION + BLOCKING + HERO PROP ก่อนสร้าง
- THREE-SCENE MEMORY: เปรียบเทียบฉากปัจจุบันกับ 3 ฉากล่าสุด ไม่ใช่เฉพาะฉากก่อนหน้า; ห้ามวนกลับไปใช้ composition/action/hero prop เดิมในช่วง 3 ฉากนี้
- STORY ESCALATION: ทุก 2–3 ฉากต้องเกิดการเปลี่ยนสถานะที่มองเห็นได้อย่างน้อยหนึ่งอย่าง: สถานที่ใหม่ การตัดสินใจใหม่ การเผชิญหน้า การเดินทาง การเปิดเผยข้อมูล หรือความสัมพันธ์เปลี่ยน; ห้ามวนอยู่กับภาพคนเดียวครุ่นคิดโดยไม่มีผลลัพธ์
- FULL-BODY STORYTELLING PRIORITY: เมื่อ STORY BEAT มีการเคลื่อนไหว ให้จัดเฟรมเห็นการกระทำและสภาพแวดล้อมมากพอ หลีกเลี่ยง close portrait ครึ่งตัวที่ทำให้ทุกฉากดูซ้ำ
- CONTINUITY ≠ REPETITION: รักษา identity, knowledge, wardrobe continuity ตามเวลา และพร็อพที่จำเป็น แต่ห้ามรักษา pose/composition/action ซ้ำเพียงเพื่อความต่อเนื่อง
- OUTPUT CLEAN FRAME: ภาพฉากสุดท้ายต้องไม่มีข้อความ โลโก้ UI contact sheet หรือภาพ Master แทรกในเฟรม
- STORY BIBLE ต้องออกแบบเหตุการณ์ให้สอดคล้องกับ CAST PLAN ที่ AUTO-MOVIE กำหนดด้านล่าง ห้ามเปลี่ยนรายชื่อตัวละครหลักของแต่ละฉาก:
${sceneCastPlan()}
- สร้าง EP 01 SCENE BLUEPRINT ครบ 20 ฉาก โดยแต่ละฉากล็อก 8 ช่อง: TIME, LOCATION, ACTIVE CAST, WARDROBE, PROPS, KNOWLEDGE, EMOTION, STORY BEAT
- ทุก SCENE ต้องมี CONTINUITY FROM SCENE ก่อนหน้าแบบสั้น ห้ามเวลา/สถานที่/เสื้อผ้า/ของประกอบ/ความรู้/อารมณ์กระโดดโดยไม่มีเหตุผล
- STORY BEAT แต่ละฉากต้องไม่ซ้ำและต้องเดินเรื่องไปข้างหน้าภายใน 8 วินาที
- TURNING POINT = SCENE 05 / 10 / 15 และ SCENE 20 = EP CLIFFHANGER + EP01_HANDOFF
- EP01_HANDOFF ต้องเก็บ unresolved conflict, time/location, wardrobe, props, character knowledge, relationship/emotion state และ hook สำหรับ EP 02
- SECONDARY CAST REGISTRY: ตัวละครรองต้องมีชื่อ/บทบาท/ลักษณะคงที่ เมื่อกลับมาอีกต้องเป็นคนเดิม
- ในแต่ละฉากอนาคต AUTO-MOVIE จะส่งเฉพาะ ORIGINAL MASTER ของ ACTIVE CAST จริง เพื่อลด identity contamination
- ห้าม recast / substitute / face blend / face average / beautify / age shift และห้ามใช้ Scene Output เป็น Identity
- ORIGINAL MASTER RE-ANCHOR: ทุก NEXT SCENE ให้ใช้เฉพาะ ORIGINAL MASTER ที่แนบของ ACTIVE CAST เป็น Identity authority ใหม่อีกครั้ง ห้ามใช้หน้าจากฉากก่อนเป็น reference แม้ฉากก่อนจะดูถูกต้อง
- WARDROBE IS SCENE DATA, NOT IDENTITY: เสื้อผ้า รองเท้า เครื่องประดับ อุปกรณ์ ท่าทาง สถานที่ และแสงของ MASTER ห้ามติดตามตัวละครไปฉากใหม่; ให้ใช้ CURRENT SCENE LEDGER / continuity เท่านั้น
- ACCESSORY FIREWALL — HARD: สร้อยคอ แหวน นาฬิกา ต่างหู แว่น กระดุม/ปกเสื้อ และเครื่องประดับทุกชนิดที่เห็นใน ORIGINAL MASTER เป็นเพียงข้อมูลของภาพอ้างอิง ห้ามคัดลอกติดตัวละครในฉาก เว้นแต่ CURRENT SCENE LEDGER ระบุเครื่องประดับชิ้นนั้นอย่างชัดเจน
- MASTER POSE/COMPOSITION FIREWALL: ห้ามคัดลอกท่ายืน มุมหน้า ระยะครึ่งตัว ฉากหลังสีเขียว หรือองค์ประกอบ portrait จาก MASTER; ภาพฉากต้องจัดองค์ประกอบใหม่ตาม STORY BEAT และการกระทำของฉากเท่านั้น
- NARRATIVE FRAMING: ให้เลือก medium / medium-wide / full-body ตามการกระทำ เพื่อเห็นตัวละครกำลังทำสิ่งที่เดินเรื่องจริง หลีกเลี่ยงภาพ portrait โพสกล้องหรือจ้องกล้องโดยไม่มีเหตุผลในเนื้อเรื่อง
- BACKGROUND FACE FIREWALL: ห้ามสร้างรูปถ่าย/กรอบรูป/จอมือถือที่มีใบหน้าทางเลือกของตัวละครหลัก ถ้าจำเป็นต้องมี ให้ไม่เห็นรายละเอียดใบหน้าหรือใช้ Identity เดียวกับ ORIGINAL MASTER
- PRIORITY: ATTACHED ORIGINAL MASTER > Natural Face/Hair/Body > Story Action > Emotion > Pose > Wardrobe > Cinematic Beauty
- ตัวละครรองสร้างอัตโนมัติและล็อกลักษณะเมื่อปรากฏครั้งแรก
- Identity Master ใช้เฉพาะ Face / Skin / Natural Body / Hair / Approximate Age / Identity ห้ามใช้เสื้อผ้าจากภาพ Master
- STORY BIBLE ให้เป็นข้อความ/แผนเรื่อง ห้ามสร้าง portrait หรือ contact sheet ใหม่ของ MASTER 01/02/03
- หลัง STORY BIBLE ให้สร้างเฉพาะภาพ SCENE 01 แบบ 9:16 เต็มฉาก ห้ามทำ collage/infographic และห้ามใส่ตารางหรือข้อความลงในภาพ
- ก่อนส่งภาพ ให้เทียบ Face + Hair + Natural Body Proportions ของตัวละครหลักทุกคนกับ ORIGINAL MASTER ที่แนบมาโดยตรง
- ถ้าใบหน้า/รูปร่างไม่ใกล้ ORIGINAL MASTER ให้ INTERNAL QC = FAIL และสร้างใหม่ก่อนส่งผลสุดท้าย
- ทำ INTERNAL QC ก่อนส่งภาพ หากมีข้อผิดพลาดที่แก้ได้ให้แก้ก่อนส่งผลสุดท้าย
- ห้ามตอบ EP MASTER อย่างเดียว ต้องสร้างภาพ SCENE 01 ด้วย
- ไม่ต้องสั่ง Director กด CONFIRM / QC / REPAIR / LOCK
- ตอนท้ายใช้: EP 01 • SCENE 01 READY
        """.trimIndent()
    }

    private fun buildQuickSceneCommand(): String {
        return rules() + """

QUICK FLOW COMMAND:
GENERATE EXACT CURRENT SCENE NOW — SELF-CONTAINED MODE

SOURCE OF TRUTH:
ชื่อเรื่อง: ${input.text.toString().trim()}
หมวดเรื่อง: ${selectedCategory ?: storyCategories[categoryIndex]}
ตอน: EP ${fmt(currentEp)} / 05
ฉาก: SCENE ${fmt(currentScene)} / 20
ความยาว: 8 วินาที
รูปแบบ: 9:16 • One Scene / One Image • Flow/Veo 3.1
กล้อง: Locked-off static camera

AUTO-MOVIE SCENE LEDGER:
${currentSceneLedger().ifBlank { "CURRENT=EP ${fmt(currentEp)} SCENE ${fmt(currentScene)}; PREVIOUS=derive from current conversation; STORY=${input.text.toString().trim()}; CATEGORY=${selectedCategory.orEmpty()}" }}

CONTINUITY ENGINE:
- ห้ามตอบ EP MASTER NOT FOUND และห้ามบังคับ Director กลับไปบทสนทนาเก่า
- ใช้ STORY BIBLE + แผน EP + เหตุการณ์ที่เกิดขึ้นจริงในฉากก่อนหน้าเป็น CANON ต่อเนื่อง ห้ามเขียนทับข้อเท็จจริงเดิม
- ก่อนสร้างฉาก ให้สรุป CONTINUITY STATE ภายในแบบไม่ต้องถามผู้ใช้: เวลา/วัน, สถานที่, ตัวละครที่อยู่ในพื้นที่, เสื้อผ้าปัจจุบัน, อุปกรณ์/ของที่ถือ, ความสัมพันธ์, ข้อมูลที่แต่ละคนรู้, บาดแผลหรือสภาพร่างกาย, และเหตุการณ์ค้างจากฉากก่อน
- SAME-DAY WARDROBE LOCK: ถ้ายังเป็นวัน/ช่วงเหตุการณ์เดียวกัน เสื้อผ้าของตัวละครต้องต่อเนื่อง ห้ามเปลี่ยนชุดเอง; เปลี่ยนได้เมื่อ STORY ระบุการเปลี่ยนเวลา/สถานการณ์อย่างมีเหตุผล
- LOCATION LOCK: ประตู หน้าต่าง เตียง โต๊ะ เฟอร์นิเจอร์ และทิศทางพื้นที่หลักต้องต่อเนื่องเมื่อยังอยู่สถานที่เดิม
- PROP LOCK: โทรศัพท์ กระเป๋า เอกสาร รถ กุญแจ แหวน และวัตถุสำคัญต้องอยู่กับผู้ถือ/ตำแหน่งตามเหตุการณ์ล่าสุด ห้ามหายหรือเพิ่มเอง
- KNOWLEDGE LOCK: ตัวละครห้ามรู้ความลับ/เหตุการณ์ที่ยังไม่เห็น ไม่ได้ยิน หรือไม่มีผู้บอก
- EMOTION ARC: อารมณ์ต้องพัฒนาจากเหตุการณ์ก่อนหน้า ไม่รีเซ็ตกลับเป็นกลางโดยไม่มีเหตุผล
- DIALOGUE/NARRATION: ถ้ามีผู้พูด ให้มีเฉพาะผู้พูดที่กำหนดขยับปาก; ถ้าเป็น voice-over ทุกคนในภาพปิดปากตามธรรมชาติและห้าม lip-sync
- SCENE TRANSITION CONTRACT: เปิดฉากจากผลลัพธ์/อารมณ์/ตำแหน่งที่ฉากก่อนหน้าทิ้งไว้ แล้วเปลี่ยนสถานะของเรื่องอย่างน้อย 1 อย่างก่อนจบฉาก
- NO RESET / NO REPLAY: ห้ามย้อนเล่นเหตุการณ์เดิม ห้ามเริ่มบทสนทนาเดิมซ้ำ ห้ามรีเซ็ตตัวละครกลับจุดก่อนหน้า
- NO RANDOM JUMP: ห้ามข้ามเวลา/สถานที่แบบไม่มีเหตุผลเชื่อม ถ้าจำเป็นต้องข้าม ให้มี visual/narrative cue ที่เข้าใจได้
- ฉากต้องทำหน้าที่เดินเรื่องเพียงหนึ่ง beat ชัดเจนภายใน 8 วินาที และต้องมีเหตุผลส่งต่อไปฉากถัดไป
- SCENE BLUEPRINT LOCK: ใช้ TIME / LOCATION / ACTIVE CAST / WARDROBE / PROPS / KNOWLEDGE / EMOTION / STORY BEAT ของเลขฉากปัจจุบันจาก EP PLAN เป็น authority ห้ามสุ่มเปลี่ยนเอง
- PREVIOUS→CURRENT CHECK: ตรวจฉากปัจจุบันกับ SCENE STATE ล่าสุดก่อนสร้าง ถ้าขัดกันให้แก้รายละเอียดฉากปัจจุบันโดยคง STORY BEAT เดิม
- NO RESET RULE: NEXT SCENE ห้ามรีเซ็ตเสื้อผ้า อารมณ์ ของประกอบ ความสัมพันธ์ หรือข้อมูลที่ตัวละครรู้
- CAUSAL LINK: การกระทำแรกต้องต่อเหตุผลจากฉากก่อน และตอนจบต้องสร้างเหตุผลไปฉากถัดไป
- OUTPUT CONTRACT: ส่งตามลำดับ SCENE STATE (ข้อความสั้น) → IMAGE 9:16 → FLOW/VEO 3.1 PROMPT 8 SEC → NEXT HOOK (1 บรรทัด) เพื่อให้ฉากถัดไปมีจุดต่อที่แน่นอน
- SCENE STATE ต้องบันทึกเฉพาะสิ่งที่เห็น/เกิดขึ้นจริงในฉากนี้: END TIME, END LOCATION, ACTIVE CAST, WARDROBE, PROPS, KNOWLEDGE CHANGE, EMOTION/RELATIONSHIP CHANGE, LAST ACTION
- ถ้ามี STORY/EP context เดิมให้ยึดเป็นหลัก; ถ้า context เดิมไม่อยู่ ให้ใช้ SOURCE OF TRUTH + EP/SCENE INDEX สร้างรายละเอียดขั้นต่ำที่ไม่ขัดกับ canon แล้วสร้างฉากทันที
- BUILD 697 IDENTITY / WARDROBE DECOUPLING = ON: แยก CHARACTER IDENTITY ออกจากเสื้อผ้าใน ORIGINAL MASTER อย่างเด็ดขาด; เสื้อผ้าใน MASTER เป็น reference context ไม่ใช่ส่วนของ identity
- BODY SILHOUETTE CONTINUITY = ON: เมื่อเปลี่ยนเสื้อผ้า ให้รักษาภาพลักษณ์โดยรวมและโครงร่างตามธรรมชาติของตัวละครผู้ใหญ่จาก ORIGINAL MASTER โดยไม่เพิ่ม ลด หรือเน้นส่วนร่างกายเพื่อให้เข้ากับชุด
- WARDROBE OVERRIDE = STORY AUTHORITY: ถ้า STORY/SCENE ระบุชุดใหม่ ให้ใช้ชุดใหม่ครบทั้งชุดตามบริบททันที ห้ามดึงสี ทรง หรือรายละเอียดชุดจาก MASTER กลับมา เว้นแต่เรื่องระบุให้ใช้
- PROFESSIONAL OUTFIT INTERPRETER = ON: คำว่า “ชุดทำงาน” ให้ตีความเป็นเสื้อผ้าทำงานสุภาพที่เหมาะกับสถานที่/อาชีพ เช่น blazer, blouse/shirt, trousers หรือ skirt ตาม continuity โดยไม่คัดลอกชุด MASTER
- CLOTHING FIT RULE = NATURAL: เสื้อผ้าใหม่ต้องพอดีและตกทิ้งตามธรรมชาติของบุคคลเดิม ห้ามใช้ wardrobe เพื่อ reshape, exaggerate หรือ idealize รูปลักษณ์
- MASTER CLOTHING EXCLUSION = HARD: สีชุด neckline straps hem slit accessories และ styling ของภาพ MASTER ห้ามถือเป็น identity token; CHARACTER MASTER ใช้สำหรับบุคคล ไม่ใช่ costume template
- BUILD 696 MASTER FIDELITY CINEMA ENGINE = ON: ORIGINAL MASTER เป็น visual identity authority ของตัวละครผู้ใหญ่แต่ละคน ใช้เพื่อรักษาคนเดิมโดยไม่เพิ่มคำบรรยายรูปร่างหรือรายละเอียดทางเพศที่ไม่จำเป็น
- IDENTITY MINIMALISM = ON: ส่งคำสั่งอัตลักษณ์แบบสั้นและเป็นกลาง — same adult person, same recognizable face, hairstyle, approximate age and overall natural appearance — แล้วให้ภาพอ้างอิงทำหน้าที่หลัก
- NO TEXT-BASED FACE REDESIGN: ห้ามสร้างใบหน้าใหม่จากคำว่า หล่อ/สวย/ดารา/นางแบบ/cinematic beauty แล้วค่อยทำให้คล้าย MASTER; เริ่มจากบุคคลใน MASTER ก่อน
- MODERN CINEMA BLOCKING = ON: จัดตำแหน่งตัวละครตามการกระทำและระยะความสัมพันธ์ ใช้ foreground/midground/background ได้ ห้ามยืนเรียงหน้ากล้องเป็นค่าเริ่มต้น
- ACTION OVER POSE = ON: ทุกฉากต้องมี physical action ที่อ่านได้ทันที เช่น เดินออก หยิบของ วางของ เปิดประตู ส่งของ ถอยห่าง หรือเข้าหา ตาม STORY BEAT; ห้ามใช้เพียง pose แสดงอารมณ์
- WARDROBE EVENT GATE = ON: ถ้า END TIME/LOCATION ต่อเนื่องจากฉากก่อน ห้ามเปลี่ยนชุด; เปลี่ยนได้เฉพาะเมื่อมี time jump, location/context change หรือ wardrobe event ที่เรื่องรองรับ
- SAFE VISUAL LANGUAGE = ON: ใช้คำบรรยายเสื้อผ้า/รูปลักษณ์ที่เป็นกลาง เหมาะกับฉาก และไม่เน้นส่วนร่างกาย; หากรายละเอียดใดไม่จำเป็นต่อ identity หรือ continuity ให้ตัดออก
- CINEMA PRIORITY STACK: ORIGINAL MASTER identity → STORY CONSEQUENCE → continuity → action/blocking → wardrobe → lighting/style
- BUILD 695 DYNAMIC ACTIVE CAST = ON: ใช้จำนวนตัวละครเท่าที่ STORY BEAT ปัจจุบันต้องใช้จริง ห้ามบังคับสามคนทุกฉาก และห้ามเพิ่มตัวละครที่ไม่อยู่ใน ACTIVE CAST
- STORY CONSEQUENCE ENGINE = ON: ฉากใหม่ต้องแสดงผลจาก LAST ACTION ของฉากก่อนอย่างมองเห็นได้ และต้องเปลี่ยนสถานการณ์อย่างน้อย 1 อย่างก่อนจบฉาก
- COMPOSITION ROTATION = ON: ห้ามใช้ตำแหน่งยืน/นั่งและการจัดซ้าย-กลาง-ขวาซ้ำเป็นค่าเริ่มต้น ให้ blocking เกิดจาก action และความสัมพันธ์ของฉาก
- MINIMUM CAST RULE: ถ้า beat ใช้คนเดียวได้ให้ใช้ 1 คน; ถ้าต้องเผชิญหน้าจึงใช้ 2 คน; ใช้ 3 คนเมื่อเหตุการณ์ต้องมีทั้งสามจริง
- SELECTIVE MASTER ENGINE = ON
- ACTIVE CAST ของ SCENE ${fmt(currentScene)} = ${sceneCastNames(currentScene)}
- FEMALE MASTER VALIDATION = ON: เมื่อ ACTIVE CAST มีรินลดาหรือมายด์ ต้องยึด ORIGINAL MASTER ของผู้หญิงคนนั้นแบบ 1:1 ด้วยความเข้มเท่ากวิน ห้ามใช้ generic female face, beauty-template face, face averaging หรือสลับอัตลักษณ์ระหว่างรินลดา/มายด์
- FEMALE EARLY VALIDATION: SCENE 02 = รินลดาเท่านั้น และ SCENE 03 = มายด์เท่านั้น เพื่อบังคับตรวจ ORIGINAL MASTER ผู้หญิงทั้งสองตั้งแต่ต้น EP; ห้ามแทนด้วยกวิน ห้ามเพิ่มผู้หญิงอีกคน และห้ามสร้างหน้าผู้หญิงจากคำบรรยาย
- FEMALE FACE PRIORITY — MAX: สำหรับรินลดา/มายด์ ให้ใบหน้า ทรงผม hairline ระยะตา รูปคิ้ว จมูก ริมฝีปาก กราม สีผิว อายุโดยประมาณ และสัดส่วนธรรมชาติจาก ATTACHMENT ของคนนั้นมีอำนาจเหนือ cinematic beauty และ generic Thai female styling
- FEMALE BODY ZERO-RESHAPE: ห้ามเพิ่ม/ลดหน้าอก เอว สะโพก ความผอม ความสูง หรือปรับสัดส่วนเพื่อให้เข้ากับชุด; รักษา natural body proportions จาก ORIGINAL MASTER แล้วเลือก wardrobe ให้เข้ากับร่างกายแทน
- FEMALE HAIR IDENTITY LOCK: ทรงผม ความยาว แนวผม และสีผมเป็นส่วน Identity ของผู้หญิง ห้ามเปลี่ยนเป็นผมยาว/สั้น/ลอน/ตรงแบบอื่นเพียงเพื่อความสวย เว้นแต่ STORY ระบุการเปลี่ยนทรงผมอย่างชัดเจนและยังต้องคงใบหน้าเดิม
- FEMALE CROSS-CONTAMINATION BLOCK: ห้ามใช้คุณลักษณะจาก MASTER ผู้หญิงอีกคนมาช่วยเติมส่วนที่ไม่ชัด และห้ามใช้ภาพผู้หญิงจาก Scene Output ก่อนหน้าเป็น reference; ทุกฉากต้องย้อนกลับ ORIGINAL MASTER ที่แนบในคำสั่งปัจจุบัน
- FEMALE DISTINCTNESS GATE — HARD: รินลดาและมายด์เป็นคนละบุคคลถาวร ต้องรักษา face geometry / hair / skin tone / natural body proportions ของ MASTER ที่แนบของแต่ละคนแยกกัน ห้ามผสมใบหน้า รูปร่าง หรือทรงผมข้ามกัน
- FEMALE WARDROBE FIREWALL — HARD: ชุดเดรส สีชุด ความเว้า/ความปิด และเครื่องประดับใน MASTER ผู้หญิงเป็นเพียงสิ่งที่อยู่ในภาพอ้างอิง ไม่ใช่ wardrobe ของฉาก ห้ามถ่ายโอน เว้นแต่ CURRENT SCENE BLUEPRINT ระบุเอง
- FEMALE IDENTITY BEFORE BEAUTY: ห้ามทำหน้าเรียว ตาโต ผิวเนียน หน้าเด็ก หรือปรับสัดส่วนเพื่อความสวย หากทำให้อัตลักษณ์จาก ORIGINAL MASTER เปลี่ยน
- คำสั่งนี้แนบเฉพาะ ORIGINAL MASTER ของ ACTIVE CAST จริง ตามลำดับนี้: ${sceneCastKeys(currentScene).mapIndexed { index, key -> "ATTACHMENT ${index + 1} = ${characterNames[key]}" }.joinToString(" / ")}
- ห้ามเพิ่มตัวละครหลักคนอื่นที่ไม่ได้อยู่ใน ACTIVE CAST ของฉากนี้
- ใช้บุคคลคนเดิมจาก ATTACHMENT ของคนนั้นโดยตรง และบรรยายเฉพาะ wardrobe/action/emotion/position/gaze
- ห้ามสร้างคำบรรยายโครงหน้า ตา จมูก ปาก ความหล่อ/สวย หรือรูปลักษณ์ใหม่เพื่อแทน ATTACHMENT
- ห้าม cross-reference / face averaging / face blending / recast / substitute actor / beautify / age shift
- ทุก Scene ใช้ ORIGINAL MASTER ที่ AUTO-MOVIE แนบในคำสั่งปัจจุบัน ห้ามใช้ Scene Output ก่อนหน้าเป็น Identity Source
- PRIORITY: ATTACHED ORIGINAL MASTER > Natural Face/Hair/Body > Story Action > Emotion > Pose > Wardrobe > Cinematic Beauty
- ใช้เฉพาะตัวละครที่เหมาะกับฉาก ตัวละครรองสร้างอัตโนมัติ
- ตัวละครหลักทุกคนที่ปรากฏต้องอ้างอิง ORIGINAL MASTER ที่แนบมาโดยตรง ห้ามอ้างอิง portrait/Scene Output ที่ AI เคยสร้าง
- Identity Master ห้ามเป็นแหล่งเสื้อผ้า ใช้ Wardrobe Firewall
- ACCESSORY FIREWALL — HARD: สร้อยคอ แหวน นาฬิกา ต่างหู แว่น และเครื่องประดับทุกชนิดจาก ORIGINAL MASTER ห้ามติดตามมาที่ฉากใหม่ เว้นแต่ CURRENT SCENE LEDGER ระบุชิ้นนั้นอย่างชัดเจน; หาก Ledger ไม่ระบุ = ไม่ใส่
- MASTER POSE/COMPOSITION FIREWALL: ห้ามคัดลอกท่ายืน มุมหน้า ระยะ portrait ฉากหลังสีเขียว หรือองค์ประกอบจาก MASTER; ใช้ MASTER เฉพาะ Identity เท่านั้น
- NARRATIVE FRAMING: ภาพต้องแสดง STORY BEAT ผ่านการกระทำจริง เลือก medium / medium-wide / full-body ตามฉาก หลีกเลี่ยง portrait pose และการจ้องกล้องโดยไม่มีเหตุผล
- ภาพ Output ต้องเป็นภาพฉาก 9:16 เต็มฉาก ห้าม collage/infographic/ตาราง/character card/ข้อความทับภาพ
- IDENTITY MATCH MODE = STRUCTURE-FIRST: ให้คง facial geometry, head width/length, eye spacing, eyebrow shape, nose bridge/tip, lips, jaw/chin, ears, hairline/hairstyle, moustache+beard pattern, skin tone, approximate age และ natural body proportions จาก ORIGINAL MASTER ก่อนอารมณ์/แสง/ความสวยงาม
- EXPRESSION DELTA ONLY: เปลี่ยนได้เฉพาะกล้ามเนื้อสีหน้าที่จำเป็นต่ออารมณ์ของฉาก เช่น ขมวดคิ้ว/สายตา/มุมปาก แต่ห้ามให้อารมณ์เปลี่ยนโครงหน้า อายุ หนวดเครา ทรงผม หรือสัดส่วนศีรษะ
- NO IDENTITY DRIFT: ห้ามเพิ่มความคมของกราม/คิ้ว/จมูก ห้ามทำหน้าผอม/กว้างขึ้น ห้ามเพิ่มหรือลดหนวดเครา และห้าม stylize ใบหน้าเพราะ dramatic lighting
- CAMERA IDENTITY SAFETY: หลีกเลี่ยงเลนส์กว้างใกล้ใบหน้าและมุมที่บิดสัดส่วน; ใช้มุม/ระยะธรรมชาติที่ยังเห็นอัตลักษณ์ชัด เว้นแต่ STORY บังคับ
- BACKGROUND PORTRAIT FIREWALL: กรอบรูป/ภาพถ่าย/หน้าจอด้านหลังห้ามแสดงใบหน้าตัวละครหลักแบบละเอียด เพราะอาจสร้าง alternate identity; ให้เบลอ/หันออก/ไม่เห็นหน้าแทน
- ทำ INTERNAL QC โดยเทียบ Face + Hair + Facial Hair + Natural Body Proportions กับ ORIGINAL MASTER; ถ้าต่างชัดเจนให้ถือเป็น IDENTITY FAIL และสร้างใหม่ก่อนส่งผลสุดท้าย
- MASTER-FIRST RECONSTRUCTION: เริ่มจาก identity geometry ของ ORIGINAL MASTER ที่แนบในคำสั่งนี้ก่อนทุกครั้ง แล้วจึงใส่ expression / pose / wardrobe / lighting ของฉาก ห้ามเริ่มจากหน้าฉากก่อน
- REFERENCE-IMAGE DOMINANCE — MAX: ORIGINAL MASTER ที่แนบคือ visual authority สูงสุด ห้ามสร้างหน้าใหม่จากคำบรรยายแล้วทำให้ “คล้าย”; ต้องรักษาบุคคลเดิมจากภาพจริงก่อนทุกองค์ประกอบ
- DISTINCTIVE-TRAIT ANCHOR: รักษา hairline + eyebrow shape + eye spacing + nose bridge/tip + lip contour + jaw width + moustache/beard boundary พร้อมกัน ห้ามปล่อยบางส่วน drift
- NATURAL TEXTURE LOCK: ห้าม beauty filter / skin smoothing / symmetry correction / face idealization ที่ทำให้ Identity เปลี่ยน
- IDENTITY BEFORE CINEMA: หากมุมกล้อง แสง depth-of-field หรือ dramatic styling ทำให้หน้าเปลี่ยน ให้ลดความ cinematic และรักษาความเหมือน MASTER ก่อน
- POSE REPETITION BLOCK: ห้ามวนท่าค้ำคาง แตะปาก แตะขมับ นั่งนิ่งอ่านเอกสาร หรือมองเอกสารซ้ำจากฉากก่อน เว้นแต่ BLUEPRINT ระบุโดยตรง; ให้เปลี่ยน blocking และ physical action ตาม STORY BEAT
- RECENT-SCENE VISUAL MEMORY — HARD: ก่อนสร้างภาพ ให้ตรวจภาพ/เหตุการณ์อย่างน้อย 3 ฉากล่าสุดในบทสนทนาปัจจุบัน แล้วทำรายการภายในว่าเคยใช้ LOCATION / BODY POSE / ACTION / HERO PROP / WARDROBE SILHOUETTE อะไรไปแล้ว ห้ามทำภาพใหม่ที่ให้ความรู้สึกเป็นภาพเดิมเปลี่ยนเสื้อหรือเปลี่ยนมุม
- DOCUMENT MOTIF COOLDOWN — HARD: ถ้า 1 ใน 3 ฉากล่าสุดมีการอ่าน/ถือ/ดูเอกสาร ใบแจ้งหนี้ กระดาษ แฟ้ม หรือหน้าจอเพื่อรับข้อมูล ฉากปัจจุบันห้ามใช้สิ่งเหล่านี้เป็น HERO ACTION หรือ HERO PROP อีก เว้นแต่ CURRENT STORY BEAT จำเป็นอย่างหลีกเลี่ยงไม่ได้; หากจำเป็นต้องต่อข้อมูลเดิม ให้แสดง “ผลจากข้อมูล” ผ่านการกระทำใหม่แทนการอ่านซ้ำ
- THINKING-POSE COOLDOWN — HARD: ถ้า 1 ใน 3 ฉากล่าสุดมีมือแตะคาง/ปาก/ขมับ นั่งก้มหน้า หรือสีหน้าครุ่นคิดนิ่ง ฉากปัจจุบันต้องใช้ body blocking และ hand action คนละแบบอย่างชัดเจน เช่น ลุกเดิน เก็บของ เปิดประตู โทรหาใคร ส่งวัตถุ เผชิญหน้า หรือทำงานทางกายภาพที่ตรง STORY BEAT
- VISUAL CONSEQUENCE FIRST: หลังฉากค้นพบข้อมูล ฉากถัดไปต้องแสดง “การตัดสินใจหรือผลลัพธ์” เป็นภาพก่อน ห้ามย้อนกลับไปแสดงการอ่านข้อมูลเดิมซ้ำ
- THREE-SCENE NOVELTY GATE: เปรียบเทียบ CURRENT กับ 3 ฉากล่าสุดใน 5 แกน ACTION / BODY BLOCKING / LOCATION / HERO PROP / COMPOSITION; ต้องต่างอย่างมีนัยสำคัญอย่างน้อย 3 แกนจากทุกฉากที่เทียบ หากไม่ถึงให้ redesign ก่อนสร้างภาพ
- DEFAULT FALLBACK BAN: เมื่อ context ไม่ชัด ห้าม fallback เป็น “ผู้ชายนั่งอ่านกระดาษที่โต๊ะ/บนเตียง” หรือ “ผู้ชายนั่งครุ่นคิด”; ให้ยึด STORY BEAT และเลือกการกระทำที่เกิดผลต่อเรื่องแทน
- ANTI-LOOP OVERRIDE — HARD: หาก 2 ฉากล่าสุดมีภาพคนเดียวกันนั่ง + กระดาษ/เอกสาร + สีหน้าครุ่นคิด ไม่ว่าชุดหรือห้องต่างกัน ให้ถือว่า VISUAL LOOP เกิดขึ้น ฉากปัจจุบันห้ามมีการนั่งอ่าน/ถือเอกสาร ห้ามมือแตะคาง/ขมับ และห้ามใช้เตียง/โต๊ะเป็น blocking หลัก
- CONSEQUENCE ACTION — REQUIRED AFTER DISCOVERY: หลังตัวละครอ่าน/พบข้อมูลแล้ว ฉากถัดไปต้องแสดงสิ่งที่เขา “ทำเพราะข้อมูลนั้น” เช่น ลุกออกจากพื้นที่ โทรหา/ไปหาใคร เก็บของ เปิดตู้/ประตู ซ่อนหรือส่งวัตถุ เผชิญหน้า เดินทาง หรือเริ่มภารกิจที่ตรงกับ STORY BIBLE; ห้ามแสดงการอ่านหรือคิดซ้ำ
- ACTIVE-BODY GATE: อย่างน้อย 1 การกระทำหลักของฉากต้องเปลี่ยนตำแหน่งร่างกายหรือสถานะวัตถุอย่างเห็นได้ชัด เช่น ยืนขึ้น เดิน เปิด ปิด หยิบ วาง ส่ง เก็บ ดึง ผลัก หรือออกจากเฟรม; “มอง/คิด/อ่าน/นั่ง” อย่างเดียวไม่ผ่าน
- DRAMA ESCALATION LADDER: เลือก beat จาก EP PLAN ที่เพิ่มเดิมพันหรือเปลี่ยนความสัมพันธ์/ข้อมูล/เป้าหมายจริง ถ้า blueprint ปัจจุบันเป็นเพียง reaction ซ้ำ ให้คง CANON แต่แปลง reaction เป็นการตัดสินใจและการกระทำที่มองเห็นได้
- VISUAL RESET BAN: การเปลี่ยนเสื้อ สีเสื้อ ห้อง แสง หรือมุมกล้อง โดยยังคงคนเดิม+ท่านั่ง+กระดาษ+สีหน้าคิด ไม่ถือเป็นความต่อเนื่องใหม่และต้อง redesign
- PROP-ACTION RULE: พร็อพหลักต้องถูกใช้เพื่อทำให้สถานะเรื่องเปลี่ยน ไม่ใช่เพียงวางตกแต่ง; ห้ามวนโต๊ะ+เอกสาร+โน้ตบุ๊กเป็นภาพหลักหลายฉากติดกันโดยไม่มีเหตุการณ์ใหม่
- IDENTITY CHECKPOINTS: ก่อนส่งภาพตรวจ 3 ชั้น: eyes+nose+mouth geometry → jaw+hairline+facial hair → head/body natural proportions; drift ชั้นใดชั้นหนึ่งชัดเจน = INVALID OUTPUT และสร้างใหม่
- NO BEAUTY OVERRIDE: dramatic emotion, cinematic lighting, lens perspective และ styling ห้ามเปลี่ยน facial structure, age impression, facial hair หรือ natural build
- MASTER ACCESSORY ZERO-TRANSFER: หาก CURRENT SCENE LEDGER ไม่ได้ระบุเครื่องประดับอย่างชัดเจน ตัวละครต้องไม่มีสร้อยคอ/โซ่/แหวน/นาฬิกา/ต่างหู/แว่น แม้ ORIGINAL MASTER จะสวมอยู่; การติดเครื่องประดับจาก MASTER โดยไม่มี Scene authority = WLF-01 และต้องแก้ก่อนส่ง
- MASTER CLOTHING ZERO-TRANSFER: สี/คอเสื้อ/สูท/เสื้อเชิ้ต/ชุดเดรสและรายละเอียดการแต่งกายใน MASTER ต้องไม่ถูกนำมาเป็นค่าเริ่มต้นของฉาก ให้สร้าง wardrobe จาก SCENE BLUEPRINT เท่านั้น
- GAZE STORY LOCK: สายตาต้องมองบุคคล/วัตถุ/จุดที่สัมพันธ์กับ STORY BEAT ห้ามมองกล้องหรือโพสเหมือน portrait เว้นแต่เหตุการณ์ระบุโดยตรง
- ACTION-FIRST FRAME: ภาพต้องจับช่วงที่ตัวละครกำลังกระทำ STORY BEAT จริง มือ/อุปกรณ์/ตำแหน่งร่างกายต้องเล่าเหตุการณ์ ไม่ใช่เพียงนั่งหรือยืนแสดงอารมณ์
- FINAL VISUAL GATE: ก่อนส่งตรวจ 4 ข้อพร้อมกัน — (1) Identity ตรง ORIGINAL MASTER (2) ไม่มี wardrobe/accessory leakage (3) gaze/action ตรง STORY BEAT (4) ไม่มี alternate face ใน background; ข้อใดผิดให้ถือ OUTPUT INVALID และแก้เฉพาะข้อผิดพลาดก่อนส่ง
- ห้ามขอ CONFIRM / QC / REPAIR / LOCK
- ห้ามสร้างฉากอื่นนอกจาก SCENE ${fmt(currentScene)}
- ถ้า SCENE ${fmt(currentScene)} = SCENE 20: หลังสร้างฉาก ให้สร้าง EP${fmt(currentEp)}_HANDOFF แบบข้อความสั้นเก็บ CANON ที่ต้องส่งต่อ ได้แก่ unresolved conflict, location/time, wardrobe, props, character knowledge, relationship/emotion state และ hook ของ EP ถัดไป; จากนั้น STOP ห้ามเริ่ม EP ถัดไปเอง
- ถ้า SCENE ${fmt(currentScene)} = SCENE 01 และ EP มากกว่า 01: ให้ถือ HANDOFF จาก EP ก่อนหน้าเป็น continuity authority และเปิด EP ใหม่โดยไม่รีเซ็ตความสัมพันธ์/ความรู้/props
- ถ้า EP 05 • SCENE 20: ปิดเส้นเรื่องหลักและใช้สถานะ STORY COMPLETE หลังภาพและ HANDOFF สุดท้าย
- ตอนท้ายใช้: EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} READY
        """.trimIndent()
    }

    // ============================================================
    // CREATE EP
    // ============================================================

    private fun createEp() {

        if (storyInProgress()) {
            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text = "⛔ มี STORY กำลังทำอยู่ • ไม่สามารถสร้างทับได้"
            return
        }
        if (!requireStep(2)) return
        val story =
            input.text.toString().trim()

        if (story.isEmpty()) {

            status.text =
                "⚠ กรุณาใส่ชื่อเรื่องหรือคำสั่ง"

            return
        }

        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        storyStartTimeMs = System.currentTimeMillis()
        storyEndTimeMs = 0L
        updateTimeLabel()

        updateUi()
        saveWorkState()

        val categoryForThisEp =
            selectedCategory ?: storyCategories[categoryIndex]

        status.text =
            "🎬 กำลังเริ่ม EP • $categoryForThisEp"

        if (!characterMastersReady()) {
            status.text = "⚠ ตั้งค่าตัวละครหลัก 3 คนก่อนเริ่ม CREATE EP"
            showCharacterMasterSetup()
            return
        }

        share(
            buildMasterPrompt(story, categoryForThisEp),
            includeCharacterMasters = true
        )

        // ROTATING CATEGORY:
        // หมุน CATEGORY ครั้งเดียวเมื่อเริ่ม "เรื่องใหม่"
        // EP 01–05 ของเรื่องเดียวกันใช้ CATEGORY เดิมทั้งหมด
        val usedIndex = storyCategories.indexOf(categoryForThisEp)
        categoryIndex =
            if (usedIndex >= 0) {
                (usedIndex + 1) % storyCategories.size
            } else {
                (categoryIndex + 1) % storyCategories.size
            }

        // ล็อก CATEGORY ตลอด STORY ปัจจุบัน (EP 01–05)
        // categoryIndex ถูกเลื่อนไว้ล่วงหน้าสำหรับ "เรื่องใหม่" ครั้งถัดไปเท่านั้น
        selectedCategory = categoryForThisEp
        status.text = "⏳ ส่ง CREATE EP แล้ว • รอยืนยัน EP MASTER"
        refreshStepIndicator()
        saveWorkState()
    }

    // ============================================================
    // REPAIR
    // ============================================================

    private fun repair() {

        if (sceneLocked) {

            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text =
                "🔒 Scene นี้ถูกล็อกแล้ว"

            return
        }

        if (repairCount >= 3) {

            playTone(ToneGenerator.TONE_PROP_NACK, 120)
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
        setActiveStep(4)
    }

    // ============================================================
    // PASS & LOCK
    // ============================================================

    private fun lockScene() {

        sceneLocked = true
        playTone(ToneGenerator.TONE_PROP_ACK, 100)
        val storyCompleteNow = currentEp == 5 && currentScene == 20
        if (storyCompleteNow && storyStartTimeMs > 0L && storyEndTimeMs == 0L) {
            storyEndTimeMs = System.currentTimeMillis()
            updateTimeLabel()
        }

        if (storyCompleteNow) {
            selectedCategory = null
            setActiveStep(1)
        } else {
            setActiveStep(7)
        }

        updateNextButton()

        saveWorkState()

        status.text =
            if (currentScene == 20) {
                if (currentEp < 5) {
                    "✅ EP ${fmt(currentEp)} COMPLETE • HANDOFF → EP ${fmt(currentEp + 1)}"
                } else {
                    "🏁 EP 05 COMPLETE • STORY COMPLETE"
                }
            } else {
                "✅ PASS & LOCK — EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)}"
            }

        share(buildLockCommand())
    }

    // ============================================================
    // NEXT SCENE
    // ============================================================

    private fun nextScene() {

        if (!sceneLocked) {
            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text = "⛔ ต้อง PASS & LOCK ก่อน"
            return
        }

        if (currentScene < 20) {
            currentScene++
        } else if (currentEp < 5) {
            // Scene 20 ของ EP เดิมผ่านแล้ว: ส่งต่อด้วย HANDOFF ไป EP ถัดไป
            currentEp++
            currentScene = 1
        } else {
            status.text = "🏁 STORY COMPLETE — EP 01–05 COMPLETE"
            updateNextButton()
            return
        }

        repairCount = 0
        sceneLocked = false
        setActiveStep(3)
        updateUi()
        saveWorkState()

        status.text = "▶ EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} • AUTO-CONTEXT"
        share(buildNextSceneCommand())
    }

    // ============================================================
    // UPDATE UI
    // ============================================================

    private fun updateUi() {

        sceneLabel.text =
            "EP ${fmt(currentEp)}   •   SCENE ${fmt(currentScene)} / 20   •   8 SEC"

        progress.progress =
            currentScene

        updateNextButton()
        refreshStepIndicator()

        status.text =
            "● SYSTEM READY  •  SCENE ${fmt(currentScene)}  •  AUTO-CONTEXT ON"
    }

    // ============================================================
    // SHARE TO CHATGPT
    // ============================================================

    private fun share(
        text: String,
        includeCharacterMasters: Boolean = false,
        characterKeys: List<String>? = null
    ) {

        hideKeyboard()

        // BUILD 693 — SEND RELIABILITY GUARD
        // Prevent accidental double-dispatch while Android/ChatGPT is still
        // consuming a multi-attachment share payload.
        val nowMs = System.currentTimeMillis()
        if (shareInProgress || nowMs - lastShareAtMs < 1800L) {
            status.text = "⏳ กำลังส่งคำสั่งเดิม • กรุณารอสักครู่"
            return
        }
        shareInProgress = true
        lastShareAtMs = nowMs

        // Diagnostic/fail-safe: attachment preparation must never make START
        // fail silently. If PNG export/FileProvider fails, show the real error
        // and still allow the text payload to reach Android's share UI.
        val masterUris: ArrayList<Uri> = try {
            when {
                characterKeys != null -> characterMasterUris(characterKeys)
                includeCharacterMasters -> characterMasterUris()
                else -> arrayListOf()
            }
        } catch (e: Exception) {
            shareInProgress = false
            status.text = "⚠ CHARACTER MASTER แนบไม่สำเร็จ: ${e.javaClass.simpleName}"
            Toast.makeText(
                this,
                "แนบ CHARACTER MASTER ไม่สำเร็จ: ${e.message ?: e.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            arrayListOf()
        }

        // Validate every prepared URI before dispatch. A stale/unreadable master
        // must not be sent as a partially broken 3-image payload.
        if ((includeCharacterMasters || characterKeys != null) && masterUris.isEmpty()) {
            shareInProgress = false
            status.text = "⚠ MASTER ยังไม่พร้อม • กรุณาเลือกภาพต้นฉบับใหม่"
            return
        }
        val unreadableMaster = masterUris.firstOrNull { uri ->
            try {
                contentResolver.openInputStream(uri)?.use { it.read() } == null
            } catch (_: Exception) {
                true
            }
        }
        if (unreadableMaster != null) {
            shareInProgress = false
            status.text = "⚠ พบ MASTER ที่อ่านไม่ได้ • เลือกภาพต้นฉบับใหม่แล้วลองอีกครั้ง"
            Toast.makeText(this, "CHARACTER MASTER บางภาพอ่านไม่ได้", Toast.LENGTH_LONG).show()
            return
        }

        // START now sends all three original masters in one payload so ChatGPT
        // receives a stable fixed-cast registry before STORY BIBLE/SCENE 01.
        // One image uses ACTION_SEND; 2+ images use ACTION_SEND_MULTIPLE.
        val sendAction =
            if (masterUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND

        val sendIntent = Intent(sendAction).apply {
            type = if (masterUris.isNotEmpty()) "image/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)

            when (masterUris.size) {
                0 -> Unit
                1 -> {
                    putExtra(Intent.EXTRA_STREAM, masterUris.first())
                    clipData = android.content.ClipData.newUri(
                        contentResolver,
                        "AUTO-MOVIE CHARACTER MASTER",
                        masterUris.first()
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    try { grantUriPermission("com.openai.chatgpt", masterUris.first(), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                }
                else -> {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, masterUris)
                    val clip = android.content.ClipData.newUri(
                        contentResolver,
                        "AUTO-MOVIE CHARACTER MASTERS",
                        masterUris.first()
                    )
                    masterUris.drop(1).forEach { uri ->
                        clip.addItem(android.content.ClipData.Item(uri))
                    }
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    masterUris.forEach { uri ->
                        try { grantUriPermission("com.openai.chatgpt", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    }
                }
            }
        }

        // QUICK FLOW: open ChatGPT directly when its Android share target
        // accepts the exact payload. Keep Android Share Sheet only as fallback.
        val chatGptPackage = "com.openai.chatgpt"
        try {
            val directIntent = Intent(sendIntent).apply {
                setPackage(chatGptPackage)
            }

            if (directIntent.resolveActivity(packageManager) != null) {
                startActivity(directIntent)
                status.text = "↗ ส่งคำสั่งไป ChatGPT แล้ว • หากเครือข่ายสะดุดให้กด Retry ใน ChatGPT"
                Handler(Looper.getMainLooper()).postDelayed({
                    shareInProgress = false
                }, 2200L)
            } else {
                val chooserIntent = Intent.createChooser(
                    sendIntent,
                    "ส่งคำสั่งไปยัง ChatGPT"
                )
                startActivity(chooserIntent)
                status.text = "↗ เปิดเมนูแชร์สำรอง • เลือก ChatGPT"
                Handler(Looper.getMainLooper()).postDelayed({ shareInProgress = false }, 2200L)
            }
        } catch (directError: Exception) {
            try {
                val chooserIntent = Intent.createChooser(
                    sendIntent,
                    "ส่งคำสั่งไปยัง ChatGPT"
                )
                startActivity(chooserIntent)
                status.text = "↗ เปิด ChatGPT โดยตรงไม่ได้ • ใช้เมนูแชร์สำรอง"
                Handler(Looper.getMainLooper()).postDelayed({ shareInProgress = false }, 2200L)
            } catch (fallbackError: Exception) {
                shareInProgress = false
                status.text = "⛔ เปิด ChatGPT/เมนูแชร์ไม่สำเร็จ: ${fallbackError.javaClass.simpleName}"
                Toast.makeText(
                    this,
                    "เปิด ChatGPT ไม่สำเร็จ: ${fallbackError.message ?: fallbackError.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // MASTER PRODUCTION RULES
    // ============================================================

    private fun rules(): String {

        return """
AUTO-MOVIE ENGINE 2.0
R${appVersion()} • BUILD เวอร์ชั่น ${packageManager.getPackageInfo(packageName, 0).versionCode}
พัฒนาโดย : นายปริญญา จันทร์จักษุ
ANDROID PROFESSIONAL • AUTO-CONTEXT
WARDROBE FIREWALL

CHANNEL:
สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย

SYSTEM MODE:
AUTO-CONTEXT = ON

QUICK FLOW SYNC:
- Workflow ใหม่มีเพียง 3 ขั้นตอน: ① NEW STORY → ② START → ③ NEXT SCENE
- START ต้องสร้างโครงเรื่องและภาพ SCENE 01 ในคำสั่งเดียว
- NEXT SCENE ต้องสร้างภาพฉากปัจจุบันทันทีแบบ SELF-CONTAINED
- ห้ามสั่ง Director กลับไปกด CONFIRM / QC / REPAIR / LOCK
- QC และการแก้ข้อผิดพลาดพื้นฐานให้ทำภายในคำสั่งเดียวก่อนส่ง Output
- FAST EXECUTION MODE = ON: อ่าน SOURCE OF TRUTH + ORIGINAL MASTER แล้วลงมือทันที ห้ามเกริ่น ห้ามทวนคำสั่ง ห้ามถามยืนยันเมื่อข้อมูลพอสร้างฉาก
- OUTPUT MINIMAL: สำหรับ NEXT SCENE ให้ส่งเฉพาะข้อมูลฉากที่จำเป็น + ภาพ 9:16 + Flow/Veo prompt + NEXT HOOK แบบสั้น ห้ามอธิบายกฎระบบซ้ำ
- IMAGE-FIRST PIPELINE: เตรียม continuity/identity/QC ภายใน แล้วเริ่มสร้างภาพโดยเร็วที่สุด; กฎที่ตรวจผ่านแล้วไม่ต้องพิมพ์รายงานยาว
- SELECTIVE MASTER FAST PATH: แนบและใช้เฉพาะ ORIGINAL MASTER ของ ACTIVE CAST ใน NEXT SCENE เพื่อลด payload; START ยังคงแนบ MASTER 3/3 เพื่อสร้าง FIXED CAST REGISTRY
- MODERN SCENE ENGINE: ทุกฉากต้องมี action verb ชัดเจน ภาพดูร่วมสมัย สมจริง มี narrative blocking และหลีกเลี่ยง portrait pose/ฉากครุ่นคิดซ้ำ
- FAIL-SOFT CONTINUITY: ถ้ารายละเอียดย่อยไม่ชัด ให้เลือกค่าที่สอดคล้องกับ canon ล่าสุดและเดินเรื่องต่อทันที; หยุดเฉพาะกรณี ORIGINAL MASTER ที่จำเป็นต่อ ACTIVE CAST ไม่ถูกแนบจริง
- POLICY-SAFE STORY ENGINE: ทุกฉากต้องเป็นดราม่าชีวิตคู่/ครอบครัวที่ปลอดภัย ไม่สร้างคำสั่งเชิงเพศหรือการแต่งกายที่เน้นเรือนร่างโดยไม่จำเป็นต่อเรื่อง
- หาก Character Master มีเสื้อผ้าเปิดเผย/เซ็กซี่ ให้ถือเป็น IDENTITY DATA เท่านั้น ห้ามถ่ายโอนความโป๊ ความรัดรูป คอเสื้อลึก หรือการเน้นสัดส่วนไปยัง Scene
- DEFAULT WARDROBE = เสื้อผ้าปกติ สุภาพ สมจริง เหมาะกับบ้าน/งาน/เวลา/เหตุการณ์ และไม่ sexualize ตัวละคร
- ห้ามใช้คำสั่งที่ขอ nudity, explicit sexual content, fetishized framing, see-through clothing หรือการเน้นหน้าอก/สะโพก/เป้า
- ROMANCE SAFE MODE: ความสัมพันธ์ใช้บทสนทนา สีหน้า ระยะห่าง การทะเลาะ การคืนดี การจับมือ/กอดแบบไม่โจ่งแจ้งตามบริบท แทนการทำให้ฉากเป็นเชิงเพศ
- หาก STORY BEAT เดิมเสี่ยงชนข้อจำกัดเนื้อหา ให้ปรับเฉพาะการนำเสนอเป็นเวอร์ชันปลอดภัยที่ยังรักษาเหตุการณ์ ความขัดแย้ง และ continuity เดิม แล้วสร้างฉากต่อทันที ห้ามหยุด workflow โดยไม่จำเป็น
- ห้ามตอบ EP MASTER NOT FOUND; หากบริบทเดิมไม่มี ให้ใช้ SOURCE OF TRUTH จาก AUTO-MOVIE และดำเนินงานต่อ
- CATEGORY ล็อกตลอด STORY EP 01–05 และหมุนเฉพาะเมื่อเริ่มเรื่องใหม่

THAI DISPLAY RULE:
- เนื้อหาที่แสดงให้ Director อ่านต้องใช้ภาษาไทยเป็นหลัก
- หัวข้อ EP MASTER ให้ใช้ "ข้อมูลหลัก EP XX"
- ใช้ป้ายกำกับ: ช่อง, ชื่อเรื่อง, หมวดเรื่อง, ตอน, จำนวนฉาก, ความยาว, รูปแบบ, กล้อง, สถานะ, ตัวละครหลัก
- รายละเอียดแต่ละ Scene ให้ใช้หัวข้อไทย: ฉาก, สถานที่, ตัวละคร, ภาพ/การกระทำ, อารมณ์, บทพูด/ผู้บรรยาย, กล้อง, พรอมต์ Flow/Veo 3.1
- คำระบบที่จำเป็น เช่น EP, Flow/Veo 3.1, Locked-off, QC, PASS, FAIL, REPAIR, LOCK, NEXT SCENE ให้คงภาษาอังกฤษไว้ร่วมกับภาษาไทย
- หลีกเลี่ยงหัวข้ออังกฤษล้วน เช่น CHANNEL, TITLE, CATEGORY, EPISODE, SCENES, DURATION, FORMAT, CAMERA, STATUS ในผลลัพธ์ที่ Director อ่าน
- กฎ/คำสั่งภายในระบบยังตีความตามคำศัพท์มาตรฐานเดิม ห้ามเปลี่ยนความหมาย Workflow

PRODUCTION RULES:

- 1 STORY มี 5 EP
- EP 01–05 ต้องเป็นเรื่องเดียวกันและ CATEGORY เดียวกัน
- แต่ละ EP ต้องมี 20 ฉาก
- รวมทั้งเรื่อง 100 ฉาก
- CATEGORY หมุนเฉพาะเมื่อเริ่ม STORY ใหม่ หลัง EP 05 จบแล้ว
- จบแต่ละ EP ต้องมี EP HANDOFF FILE เฉพาะเพื่อส่งต่อ EP ถัดไป
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

VISUAL IDENTITY LOCK — FIXED CAST REGISTRY:

FIXED CAST REGISTRY — SINGLE SOURCE OF TRUTH:
- MASTER กวิน / รินลดา / มายด์ คือทะเบียนนักแสดงถาวรของ STORY
- SELECTIVE MASTER ENGINE จะเลือกแนบเฉพาะ ORIGINAL MASTER ของ ACTIVE CAST ในฉากปัจจุบัน
- หมายเลข ATTACHMENT เป็นลำดับเฉพาะของคำสั่งปัจจุบัน ให้ยึด mapping ที่ AUTO-MOVIE ระบุใน CURRENT SCENE
- MASTER ที่ไม่ได้แนบในคำสั่งปัจจุบัน = ตัวละครนั้นห้ามปรากฏในภาพฉากนี้
- ORIGINAL MASTER ที่แนบคือบุคคลจริงของบท ไม่ใช่ภาพตัวอย่างสำหรับสร้างคนใหม่
- เมื่อต้องใช้ตัวละครหลัก ให้ใช้ "บุคคลคนเดิมจาก IMAGE หมายเลขนั้น" โดยตรง
- ห้ามสร้าง visual identity ใหม่จากชื่อ อายุ บุคลิก บทบาท เนื้อเรื่อง หรือคำบรรยายความหล่อ/สวย
- ห้ามเขียนหรืออนุมานคำบรรยายใบหน้าใหม่ เช่น รูปหน้า ตา คิ้ว จมูก ปาก กราม ความหล่อ/สวย เชื้อชาติ หรือสไตล์ใบหน้า เพื่อใช้แทน IMAGE
- คำบรรยาย Scene ของตัวละครหลักอนุญาตเฉพาะ: ชื่อ/IMAGE ID, เสื้อผ้าตาม Scene, ตำแหน่ง, การกระทำ, อารมณ์ และทิศทางการมอง
- ห้าม RECAST, SUBSTITUTE ACTOR, FACE BLEND, FACE AVERAGE, BEAUTIFY, AGE SHIFT, FACE MORPH หรือ BODY RESHAPE
- ห้ามใช้ Scene Output, portrait ที่ AI สร้าง, contact sheet, character card, รูปในกรอบ, กระจก หรือจอภาพ เป็น Identity Source ใหม่
- ทุก Scene ต้องย้อนกลับไปที่ ORIGINAL MASTER ที่ AUTO-MOVIE แนบสำหรับ ACTIVE CAST เสมอ
- เสื้อผ้าใน ORIGINAL MASTER ไม่ใช่ Identity และห้ามคัดลอกตาม WARDROBE FIREWALL
- ถ้ามีหลายตัวละครในฉาก ให้รักษาแต่ละ IMAGE ID แยกจากกันแบบ 1:1 ห้ามถ่ายโอนลักษณะระหว่างคน
- PRIORITY: FIXED CAST IDENTITY > NATURAL FACE/HAIR/BODY CONTINUITY > STORY ACTION > EMOTION > POSE > WARDROBE > CINEMATIC BEAUTY
- ก่อนส่งภาพ ตรวจว่าบุคคลที่ใช้แทน กวิน/รินลดา/มายด์ ยังเป็นบุคคลคนเดิมจาก IMAGE 1/2/3 ตามลำดับ ถ้าเห็นชัดว่าเป็นคนใหม่ = INTERNAL QC FAIL
- หากระบบภาพไม่สามารถรักษาบุคคลเดิมจาก IMAGE ได้อย่างน่าเชื่อถือ ให้ระบุข้อจำกัด ห้ามประกาศ Identity PASS เท็จ

STORY BIBLE IMAGE RULE:
- STORY BIBLE เป็นข้อมูลข้อความ/แผนเรื่อง ไม่ต้องสร้างภาพ portrait ใหม่ของ MASTER 01/02/03
- ถ้าจำเป็นต้องแสดงรายชื่อตัวละคร ให้ใช้ชื่อและรหัส MASTER เท่านั้น
- ภาพที่ต้องสร้างใน START คือภาพ SCENE 01 เพียงภาพเดียวหลังวาง STORY BIBLE
- ห้ามรวม STORY BIBLE, ตาราง, portrait ตัวละคร และ SCENE 01 เป็นภาพ collage/infographic เดียว
- Output ภาพ SCENE ต้องเป็นภาพฉาก 9:16 แบบเต็มฉาก ไม่ใส่ตาราง/ตัวหนังสือ/character card ทับในภาพ

FIXED CAST COMPATIBILITY MAP:

ตัวละครหลักถาวรมีเพียง 3 คน:
- MASTER 01 = กวิน (Gawin) — ตัวละครหลักชาย
- MASTER 02 = รินลดา (Rinlada) — ตัวละครหลักหญิง
- MASTER 03 = มายด์ (Mind) — ตัวละครหลักหญิง

เมื่อคำสั่งจาก AUTO-MOVIE มีภาพแนบ 3 ภาพ:
- ให้จับคู่ตามลำดับ MASTER 01 กวิน / MASTER 02 รินลดา / MASTER 03 มายด์
- ภาพเหล่านี้คือ ORIGINAL IDENTITY MASTER ที่แอปแนบให้อัตโนมัติ
- ห้ามถาม Director ให้แนบภาพทั้ง 3 คนซ้ำ หากภาพแนบมากับคำสั่งแล้ว
- ใช้เฉพาะตัวละครหลักที่ EP MASTER / CURRENT SCENE ระบุว่าปรากฏในฉาก
- ตัวละครอื่นทั้งหมดเป็น SUPPORTING CHARACTER ให้ระบบสร้างอัตโนมัติตามบท
- SUPPORTING CHARACTER ต้องมีชื่อ/บทบาท/อายุโดยประมาณ/ลักษณะเด่นที่ล็อกไว้เมื่อปรากฏครั้งแรก
- เมื่อ SUPPORTING CHARACTER คนเดิมกลับมา ให้รักษาข้อมูลที่ล็อกไว้จาก STORY/EP CONTEXT
- ห้ามร้องขอ Identity Master จาก Director สำหรับตัวละครรองที่ระบบสร้างขึ้นเอง

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

สำหรับตัวละครหลัก กวิน / รินลดา / มายด์:
ให้ใช้ ORIGINAL IDENTITY MASTER ที่ AUTO-MOVIE แนบมากับคำสั่ง

สำหรับตัวละครรอง:
สร้างอัตโนมัติจากข้อมูล SUPPORTING CHARACTER ที่ล็อกไว้
ห้ามขอ Director แนบ Identity Master เพิ่ม

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

STORY MASTER + EP 01 MASTER:

TITLE: $story
CATEGORY: $category
TOTAL EP: 5
SCENES PER EP: 20
TOTAL STORY SCENES: 100

กฎ STORY:
- เรื่องนี้ต้องดำเนินต่อเนื่องตั้งแต่ EP 01 ถึง EP 05
- CATEGORY และ TITLE ต้องคงเดิมตลอดทั้ง 5 EP
- ห้ามหมุน CATEGORY ระหว่าง EP
- แต่ละ EP มี 20 Scene
- เมื่อจบ Scene 20 ของแต่ละ EP ต้องสร้าง EP HANDOFF FILE เฉพาะ EP เพื่อส่งต่อ EP ถัดไป

DIRECTOR COMMAND:

$story

ดำเนินการสร้าง EP 01 ทันที

สร้าง EP 01 ให้ครบ 20 Scene

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

ให้แสดงสถานะ:
SCENES 01–20 = PLANNED & LOCKED
NEXT = GENERATE SCENE 01

จากนั้น STOP
เมื่อ EP MASTER ถูกสร้างสำเร็จ ให้ตอบท้ายสุดว่า:
EP MASTER READY — RETURN TO AUTO-MOVIE AND PRESS CONFIRM
ห้ามถือว่าเข้าสู่ GENERATE SCENE จน Director ยืนยันใน AUTO-MOVIE

ห้ามแสดง SAVE_EP / HANDOFF / EP COMPLETE ในขั้น CREATE EP
เพราะคำเหล่านี้ใช้ได้เฉพาะหลัง SCENE 20 ผ่าน QC และ PASS & LOCK แล้วเท่านั้น

ห้ามสร้างภาพเองจนกว่าจะได้รับ GENERATE SCENE
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

ACTIVE WORK STATE — SOURCE OF TRUTH:
TITLE: ${input.text.toString().trim()}
CATEGORY: ${selectedCategory ?: storyCategories[categoryIndex]}
CURRENT EP: ${fmt(currentEp)} / 05
CURRENT SCENE: ${fmt(currentScene)} / 20
DURATION: 8 SEC
FORMAT: 9:16

APP ↔ CHATGPT SYNC RULE:
- ค่าจาก ACTIVE WORK STATE คือค่าที่แอปกำลังใช้งานจริง
- TITLE / CATEGORY / CURRENT SCENE ต้องตรงกับ EP MASTER ในแชท
- ห้าม ChatGPT เปลี่ยน CATEGORY, TITLE หรือเลข Scene เอง
- ถ้า EP MASTER ในแชทไม่ตรงกับ ACTIVE WORK STATE ให้หยุดและรายงาน SYNC MISMATCH ห้ามสร้างภาพผิดเรื่อง
- CATEGORY ต้องคงเดิมตลอด STORY EP 01–05 และจะหมุนเฉพาะเมื่อ STORY COMPLETE แล้ว Director เริ่มเรื่องใหม่เท่านั้น

ขั้นตอนบังคับ:

1. ใช้ ACTIVE WORK STATE ด้านบนเป็นตัวระบุงานปัจจุบันเสมอ

2. ถ้าบทสนทนานี้มี EP MASTER ของ TITLE เดียวกัน ให้ดึง SCENE ${fmt(currentScene)} จาก EP MASTER เดิมโดยอัตโนมัติ

3. เมื่อสร้างภาพ SCENE ${fmt(currentScene)} สำเร็จจริงแล้ว ให้ตอบท้ายสุดว่า:
   SCENE OUTPUT READY — RETURN TO AUTO-MOVIE AND PRESS CONFIRM
   ห้ามถือว่าเข้าสู่ QC จน Director ยืนยันใน AUTO-MOVIE

4. ถ้าไม่พบ EP MASTER ในบทสนทนานี้ ห้ามเดาหรือสร้าง Scene ใหม่ และห้ามสลับไปใช้เรื่องอื่น ให้ตอบสั้น ๆ ว่า:
   EP MASTER NOT FOUND — TITLE: ${input.text.toString().trim()}
   กรุณากลับไปยังบทสนทนาที่สร้าง EP MASTER เรื่องนี้ แล้วสั่ง GENERATE SCENE ${fmt(currentScene)}

4. ห้ามถาม Director ให้ส่งบท SCENE ${fmt(currentScene)} ซ้ำ ถ้า EP MASTER ของเรื่องนี้มีอยู่แล้วในบทสนทนา

6. ห้ามแต่ง Scene ใหม่แทน Scene เดิม

6. ต้องยึดข้อมูลจาก Scene เดิม:

   - LOCATION
   - CHARACTERS
   - VISUAL / ACTION
   - EMOTION
   - DIALOGUE / NARRATION
   - CAMERA
   - FLOW / VEO 3.1 PROMPT
   - STORY CONTINUITY

7. ตรวจว่า Scene นี้
   ต้องใช้ตัวละครใครบ้าง

8. ตรวจ ORIGINAL IDENTITY MASTER
   ของตัวละครเหล่านั้น

9. ถ้า ORIGINAL IDENTITY MASTER
   ครบทั้งหมด:

   สร้างภาพ
   SCENE ${fmt(currentScene)}
   ทันที

   ห้ามถามคำถามเพิ่มเติม

10. ถ้า ORIGINAL IDENTITY MASTER
   ไม่ครบ:

   แจ้งเฉพาะชื่อ
   ตัวละครที่ยังขาด
   ORIGINAL IDENTITY MASTER

   ห้ามถามหาบท Scene

11. Identity Master
    ใช้สำหรับ:

    - Face
    - Skin
    - Natural Body
    - Hair
    - Approximate Age
    - Identity

    เท่านั้น

12. WARDROBE FIREWALL

    ก่อนสร้างภาพ ต้องแยก IDENTITY ออกจาก WARDROBE อย่างเด็ดขาด
    ORIGINAL IDENTITY MASTER ห้ามเป็นแหล่งข้อมูลเสื้อผ้า สีชุด รูปแบบชุด รองเท้า หรือความโป๊/ความปิดของชุด

    WARDROBE SOURCE PRIORITY:
    1. CURRENT SCENE MASTER
    2. SAME-DAY CONTINUITY จาก EP MASTER
    3. STORY CONTEXT
    4. DIRECTOR COMMAND

    SCENE STATE SNAPSHOT — BUILD 692:
    ให้แปลงข้อมูลที่มีอยู่แล้วใน CURRENT SCENE MASTER / EP MASTER เป็นข้อเท็จจริงของฉากปัจจุบันแบบสั้นเท่านั้น:
    - CURRENT_LOCATION = สถานที่ของ Scene นี้
    - CURRENT_WARDROBE = ชุดของตัวละครแต่ละคนตาม Scene/continuity
    - ACTIVE_PROP = วัตถุสำคัญที่ Scene นี้ระบุว่ากำลังใช้อยู่
    - CURRENT_ACTION = การกระทำหลักของ Scene นี้
    ใช้ Snapshot นี้เป็นข้อมูลฉากธรรมดาสำหรับการสร้างภาพ ไม่ต้องเพิ่มคำสั่ง continuity, validation หรือ repair loop ใหม่ และห้ามเดาข้อมูลที่ EP MASTER ไม่ได้ระบุ

    ถ้าชุดในภาพที่จะสร้างเหมือนชุดจาก ORIGINAL IDENTITY MASTER
    โดยไม่มีแหล่งข้อมูลข้างต้นรองรับ ต้องเปลี่ยนเป็นชุดที่ถูกต้องก่อนสร้าง Output

13. ห้ามใช้ Output
    จาก Scene ก่อนหน้า
    เป็น Identity Master

14. One Scene / One Image

15. หลังสร้างภาพเสร็จ:

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

ACTIVE WORK STATE:
TITLE: ${input.text.toString().trim()}
CATEGORY: ${selectedCategory ?: storyCategories[categoryIndex]}
EP: ${fmt(currentEp)} / 05
SCENE: ${fmt(currentScene)} / 20

ก่อนล็อก ต้องยืนยันว่า QC RESULT ล่าสุดของ EP ${fmt(currentEp)} SCENE ${fmt(currentScene)} = PASS อย่างชัดเจน
ถ้า QC = FAIL, มี WLF-01 หรือ POST-REPAIR VALIDATION ยังไม่ CLEARED ให้หยุด ห้าม LOCK

เมื่อ QC = PASS:
ล็อก Output ล่าสุดเป็นภาพที่ผ่าน QC ของ EP ${fmt(currentEp)} SCENE ${fmt(currentScene)}

${if (currentScene == 20 && currentEp < 5) """
EP ${fmt(currentEp)} COMPLETE

สร้างไฟล์เฉพาะ:
EP${fmt(currentEp)}_HANDOFF

HANDOFF FILE ต้องบันทึก:
- STORY TITLE และ CATEGORY
- EP ที่จบ และ EP ถัดไป
- สรุปเหตุการณ์สำคัญของ EP นี้
- สถานะตัวละครและความสัมพันธ์ล่าสุด
- จุดค้าง/ปมที่ต้องส่งต่อ
- Timeline / วัน / เวลา / สถานที่ล่าสุด
- Wardrobe Continuity ที่ต้องส่งต่อ
- Identity Master ที่ต้องใช้ต่อ
- ข้อเท็จจริงที่ห้ามเปลี่ยน
- OPEN LOOPS สำหรับ EP ถัดไป

HANDOFF TO: EP ${fmt(currentEp + 1)}
NEXT = CREATE EP ${fmt(currentEp + 1)} FROM HANDOFF
STOP
ห้ามเปลี่ยน TITLE หรือ CATEGORY
ห้ามเริ่ม EP ถัดไปเอง
""".trimIndent()
else if (currentScene == 20 && currentEp == 5) """
EP 05 COMPLETE
สร้างไฟล์เฉพาะ: EP05_HANDOFF_FINAL
SAVE STORY MASTER
STORY COMPLETE — 5 EP / 100 SCENES
STOP
ห้ามเริ่มเรื่องใหม่เอง
""".trimIndent()
else """
STOP
NEXT = NEXT SCENE
ห้ามเริ่ม Scene ถัดไปอัตโนมัติ
""".trimIndent()}
        """.trimIndent()
    }

    // ============================================================
    // NEXT SCENE — AUTO CONTEXT
    // ============================================================

    private fun buildNextSceneCommand(): String {

        val isNewEp = currentScene == 1 && currentEp > 1
        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
${if (isNewEp) "CREATE NEXT EP FROM HANDOFF" else "NEXT SCENE"}

ACTIVE WORK STATE:
TITLE: ${input.text.toString().trim()}
CATEGORY: ${selectedCategory ?: storyCategories[categoryIndex]}
CURRENT EP: ${fmt(currentEp)} / 05
CURRENT SCENE: ${fmt(currentScene)} / 20

${if (isNewEp) """
EP TRANSITION:
- ค้นหา EP${fmt(currentEp - 1)}_HANDOFF จากบทสนทนาเดียวกัน
- ใช้ HANDOFF นั้นสร้าง EP ${fmt(currentEp)} MASTER จำนวน 20 Scene
- ต้องต่อจากเหตุการณ์/ความสัมพันธ์/Timeline/Wardrobe/Open Loops เดิม
- TITLE และ CATEGORY ต้องเหมือน STORY MASTER เดิม 100%
- ห้ามหมุน CATEGORY
- ห้ามสร้างเรื่องใหม่
- เมื่อสร้าง EP ${fmt(currentEp)} MASTER ครบ ให้ STOP
- NEXT = GENERATE SCENE 01
""".trimIndent()
else """
PREVIOUS SCENE: ${fmt(currentScene - 1)} = PASS & LOCK
- ใช้ EP ${fmt(currentEp)} MASTER เดิม
- ดึง SCENE ${fmt(currentScene)} โดยอัตโนมัติ
- รักษา Story / Character / Wardrobe / Location / Emotion / Timeline Continuity
- ตรวจ ORIGINAL IDENTITY MASTER ของตัวละครที่ต้องปรากฏ
- One Scene / One Image
- หลังดำเนินการ STOP และรอ QC CHECK
- ห้ามข้ามไป Scene ถัดไป
""".trimIndent()}
        """.trimIndent()
    }

    // ============================================================
    // APP UPDATE
    // ============================================================

    private fun checkForAppUpdate(silent: Boolean = false) {
        if (!silent) {
            status.text = "🔄 กำลังตรวจสอบเวอร์ชันล่าสุด…"
        }

        Thread {
            try {
                // Every build now has an immutable release tag such as R6.24-B642.
                // Read the release marked "latest" and cache-bust the request.
                val noCacheUrl =
                    "https://api.github.com/repos/pearparinya/auto-movie-r6-4/releases/latest?cb=" +
                    System.currentTimeMillis()
                val connection = (URL(noCacheUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    useCaches = false
                    defaultUseCaches = false
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AUTO-MOVIE-Android")
                    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("If-None-Match", "")
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("GitHub HTTP $responseCode")
                }

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val release = JSONObject(json)
                val rawTag = release.optString("tag_name")
                val tag = Regex("""(?:R|v)?(\d+(?:\.\d+)+)""", RegexOption.IGNORE_CASE)
                    .find(rawTag)?.groupValues?.getOrNull(1) ?: appVersion()
                val releaseBody = release.optString("body")
                val remoteBuild = Regex("""BUILD\s*[:#]?\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(releaseBody)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
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
                    val localBuild = packageManager.getPackageInfo(packageName, 0).versionCode
                    val versionIsNewer = isNewerVersion(tag, appVersion())
                    val sameVersionNewerBuild = !versionIsNewer &&
                        !isNewerVersion(appVersion(), tag) &&
                        remoteBuild > localBuild
                    if (!versionIsNewer && !sameVersionNewerBuild) {
                        if (!silent) {
                            status.text = "✓ AUTO-MOVIE R${appVersion()} • BUILD ${packageManager.getPackageInfo(packageName, 0).versionCode} เป็นเวอร์ชันล่าสุดแล้ว"
                        }
                    } else {
                        showUpdateDialog(tag, remoteBuild, apkUrl)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!silent) {
                        status.text = "⚠ ตรวจสอบอัปเดตไม่สำเร็จ"
                        AlertDialog.Builder(this)
                            .setTitle("ตรวจสอบอัปเดตไม่สำเร็จ")
                            .setMessage("กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง\n\n${e.message ?: ""}")
                            .setPositiveButton("ตกลง", null)
                            .show()
                    }
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

    private fun showUpdateDialog(version: String, build: Int, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("พบ AUTO-MOVIE R$version • BUILD $build")
            .setMessage("มีเวอร์ชันใหม่พร้อมใช้งาน\n\nกด “ดาวน์โหลดและติดตั้ง” เพื่อเริ่มอัปเดต")
            .setNegativeButton("ไว้ภายหลัง", null)
            .setPositiveButton("ดาวน์โหลดและติดตั้ง") { _, _ ->
                downloadAndInstallUpdate(version, apkUrl)
            }
            .show()
    }

    private fun downloadAndInstallUpdate(version: String, apkUrl: String) {
        val buildNumber = try {
            Regex("""BUILD-(\d+)""", RegexOption.IGNORE_CASE)
                .find(apkUrl)?.groupValues?.getOrNull(1) ?: "update"
        } catch (_: Exception) {
            "update"
        }
        val fileName = "AUTO-MOVIE-R$version-BUILD-$buildNumber.apk"
        val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir == null) {
            status.text = "⛔ ไม่พบพื้นที่สำหรับดาวน์โหลด"
            return
        }
        val targetFile = File(downloadDir, fileName)

        status.text = "⬇ กำลังดาวน์โหลด AUTO-MOVIE R$version…"

        Thread {
            try {
                if (targetFile.exists()) targetFile.delete()

                val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("User-Agent", "AUTO-MOVIE-Android")
                    setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IllegalStateException("ดาวน์โหลด HTTP $responseCode")
                }

                val total = connection.contentLengthLong
                var downloaded = 0L
                var lastPercent = -1L

                connection.inputStream.use { input ->
                    targetFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            downloaded += count

                            if (total > 0L) {
                                val percent = ((downloaded * 100L) / total).coerceIn(0L, 100L)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    runOnUiThread {
                                        status.text = "↓ AUTO-MOVIE R$version • $percent%"
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }
                connection.disconnect()

                if (!targetFile.exists() || targetFile.length() < 100_000L) {
                    throw IllegalStateException("ไฟล์ APK ที่ดาวน์โหลดไม่สมบูรณ์")
                }

                runOnUiThread {
                    status.text = "✓ ดาวน์โหลดเสร็จแล้ว • เปิดหน้าติดตั้ง"
                    installDownloadedApk(targetFile)
                }
            } catch (e: Exception) {
                try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                runOnUiThread {
                    status.text = "⛔ ดาวน์โหลดอัปเดตไม่สำเร็จ"
                    AlertDialog.Builder(this)
                        .setTitle("ดาวน์โหลดอัปเดตไม่สำเร็จ")
                        .setMessage("ระบบดาวน์โหลดในแอปทำงานไม่สำเร็จ\n\n${e.message ?: e.javaClass.simpleName}")
                        .setPositiveButton("ตกลง", null)
                        .show()
                }
            }
        }.start()
    }

    private fun installDownloadedApk(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            status.text = "⛔ ไม่พบไฟล์อัปเดต"
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            status.text = "⚠ กรุณาอนุญาตติดตั้งแอปจาก AUTO-MOVIE"
            AlertDialog.Builder(this)
                .setTitle("ต้องอนุญาตการติดตั้ง")
                .setMessage("กด “เปิดการตั้งค่า” แล้วเปิด “อนุญาตจากแหล่งนี้” จากนั้นกลับมาที่ AUTO-MOVIE และกด Update อีกครั้ง")
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("เปิดการตั้งค่า") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    }
                }
                .show()
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Prefer Android's system package installer directly so the
            // "Open with" chooser is skipped when multiple APK handlers exist.
            val handlers = packageManager.queryIntentActivities(installIntent, 0)
            val systemInstaller = handlers.firstOrNull { info ->
                (info.activityInfo.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            }

            if (systemInstaller != null) {
                installIntent.setClassName(
                    systemInstaller.activityInfo.packageName,
                    systemInstaller.activityInfo.name
                )
            }

            startActivity(installIntent)
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
            .setTitle("วิธีใช้งาน AUTO-MOVIE • QUICK FLOW")
            .setMessage(
                """
ใช้งานเพียง 3 ขั้นตอน

① NEW STORY
กด “สร้างเรื่องใหม่” แล้วเลือกชื่อเรื่องที่ต้องการ ระบบจะล็อกชื่อเรื่องและหมวดเรื่องไว้ตลอด EP 01–05

CHARACTER MASTER
ตั้งค่ารูป กวิน / รินลดา / มายด์ เพียงครั้งเดียว แอปเก็บรูปต้นฉบับไว้ในเครื่องและแนบให้อัตโนมัติทุกครั้งที่ START / NEXT SCENE
รูปทั้ง 3 คือ ORIGINAL IDENTITY MASTER ใช้สำหรับใบหน้า ทรงผม สีผิว และรูปร่างธรรมชาติเท่านั้น เสื้อผ้าในรูปไม่ใช่ชุดประจำตัว

② START
กดครั้งเดียว แอปส่ง Character Master + คำสั่งไป ChatGPT
จากนั้นส่งข้อความใน ChatGPT เพื่อให้สร้าง STORY BIBLE / โครงเรื่อง และสร้างภาพ SCENE 01 ต่อทันที
ไม่ต้อง CONFIRM / QC / REPAIR / LOCK

③ NEXT SCENE
เมื่อได้ภาพฉากปัจจุบันแล้ว กลับ AUTO-MOVIE และกด NEXT SCENE
ระบบจะเลื่อนไปฉากถัดไป แนบ ORIGINAL CHARACTER MASTER เดิม และรักษาความต่อเนื่องให้อัตโนมัติ
ทำซ้ำจนจบ 20 ฉากต่อ EP และต่อเนื่องถึง EP 05

สำคัญ:
• ใช้ ORIGINAL MASTER เดิมเสมอ ห้ามใช้ภาพ AI ที่สร้างภายหลังเป็น Master ใหม่
• ตัวละครรองให้ระบบสร้างและรักษาความต่อเนื่องอัตโนมัติ
• ทุกฉาก 8 วินาที • 9:16 • Flow/Veo 3.1 • Locked-off
                """.trimIndent()
            )
            .setPositiveButton("เข้าใจแล้ว", null)
            .show()
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private fun playTone(tone: Int, durationMs: Int) {
        if (!soundEnabled) return
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35).apply {
                startTone(tone, durationMs)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { release() } catch (_: Exception) {}
                }, durationMs.toLong() + 80L)
            }
        } catch (_: Exception) {
            // เสียงเป็น feedback เสริมเท่านั้น ต้องไม่รบกวน workflow หากอุปกรณ์เล่นเสียงไม่ได้
        }
    }

    private val clockTick = object : Runnable {
        override fun run() {
            updateTimeLabel()
            clockHandler.postDelayed(this, 1000L)
        }
    }

    private fun startClock() {
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    private fun updateTimeLabel() {
        if (!::timeLabel.isInitialized) return

        val now = System.currentTimeMillis()
        val dateText = SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale("th", "TH"))
            .format(Date(now))

        val elapsedMs = when {
            storyStartTimeMs <= 0L -> 0L
            storyEndTimeMs > 0L -> storyEndTimeMs - storyStartTimeMs
            else -> now - storyStartTimeMs
        }.coerceAtLeast(0L)

        val totalSeconds = elapsedMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val storyTime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        timeLabel.text =
            if (storyStartTimeMs > 0L) {
                "🕐 $dateText   •   ⏱ STORY $storyTime"
            } else {
                "🕐 $dateText   •   ⏱ STORY --:--:--"
            }
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    private fun stepAction(
        step: Int,
        title: String,
        subtitle: String,
        function: () -> Unit
    ): Button {
        return action(title, subtitle, function).also { button ->
            stepButtons[step] = button
        }
    }

    private fun migrateWorkflowStateIfNeeded() {
        val migrationKey = "workflow_gate_v618"
        if (statePrefs.getBoolean(migrationKey, false)) return

        // R6.17 และเก่ากว่าเคยบันทึก activeStep ที่กระโดดข้ามได้
        // รีเซ็ตเฉพาะ workflow งานเก่า 1 ครั้ง เพื่อให้ R6.18 เริ่มทดสอบตามลำดับจริง
        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        selectedCategory = null
        activeStep = 1
        storyStartTimeMs = 0L
        storyEndTimeMs = 0L
        input.setText("")
        titlePanel.visibility = android.view.View.GONE

        statePrefs.edit()
            .clear()
            .putBoolean(migrationKey, true)
            .putBoolean("soundEnabled", soundEnabled)
            .apply()

        updateTimeLabel()
        updateUi()
        status.text = "✓ R6.18 พร้อมใช้งาน • เริ่มตามลำดับจากขั้นตอน ①"
    }

    private fun storyInProgress(): Boolean {
        return storyStartTimeMs > 0L && storyEndTimeMs == 0L
    }

    private fun updateNextButton() {
        if (!::nextButton.isInitialized) return

        when {
            currentEp == 5 && currentScene == 20 && sceneLocked -> {
                nextButton.text = "🏁 STORY COMPLETE"
                nextButton.isEnabled = false
                nextButton.alpha = 0.38f
            }
            currentScene == 20 && currentEp < 5 && sceneLocked -> {
                nextButton.text = "→ ส่งต่อ EP ${fmt(currentEp + 1)} • HANDOFF"
                nextButton.isEnabled = activeStep == 7
                nextButton.alpha = if (nextButton.isEnabled) 1f else 0.38f
            }
            else -> {
                nextButton.text = "→ ฉากถัดไป • NEXT SCENE"
                nextButton.isEnabled = sceneLocked && activeStep == 7
                nextButton.alpha = if (nextButton.isEnabled) 1f else 0.38f
            }
        }
    }

    private fun requireStep(required: Int): Boolean {
        if (activeStep == required) return true
        rejectOutOfOrder(required)
        return false
    }

    private fun rejectOutOfOrder(requested: Int) {
        playTone(ToneGenerator.TONE_PROP_NACK, 120)
        status.text = "⛔ ทำตามลำดับก่อน • ตอนนี้อยู่ขั้นตอน $activeStep"
        refreshStepIndicator()
    }

    private fun setActiveStep(step: Int) {
        val nextStep = step.coerceIn(1, 3)
        val changed = nextStep != activeStep
        activeStep = nextStep
        refreshStepIndicator()
        if (changed && lastSoundStep != activeStep) {
            playTone(ToneGenerator.TONE_PROP_BEEP, 65)
            lastSoundStep = activeStep
        }
        if (::input.isInitialized) saveWorkState()
    }

    private fun refreshStepIndicator() {
        stepButtons.forEach { (step, button) ->
            val active = step == activeStep
            button.isEnabled = active
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (active) activeGreen else panel)
            button.setTextColor(if (active) Color.WHITE else ice)
            button.elevation = dp(if (active) 8 else 2).toFloat()
            button.alpha = if (active) 1f else 0.38f
        }
        if (::nextHint.isInitialized) {
            nextHint.text = when (activeStep) {
                1 -> "① เลือกชื่อเรื่อง • NEW STORY"
                2 -> "② START → โครงเรื่อง + ภาพฉาก 01 ในครั้งเดียว"
                3 -> "③ ได้ภาพแล้ว → กด NEXT SCENE เพื่อสร้างฉากต่อไป"
                else -> ""
            }
        }
    }

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
                dp(3),
                dp(12),
                dp(3)
            )

            minHeight = dp(34)

            setTextColor(ice)
            setTypeface(typeface, Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(panel)
            elevation = dp(2).toFloat()

            setOnClickListener {
                if (!isEnabled) {
                    playTone(ToneGenerator.TONE_PROP_NACK, 120)
                    return@setOnClickListener
                }
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
                12f

            setTextColor(gold)

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                dp(2),
                0,
                dp(2)
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
