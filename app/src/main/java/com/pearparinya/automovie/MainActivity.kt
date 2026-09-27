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
            text = "ระบบสร้างหนังอัตโนมัติ R${appVersion()}"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(
                9, 15, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
            setPadding(0, 0, 0, 0)
        }, full())

        headerText.addView(TextView(this).apply {
            val buildNumber = packageManager.getPackageInfo(packageName, 0).versionCode
            text = "BUILD เวอร์ชั่น $buildNumber"
            textSize = 11f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
        }, full())

        headerText.addView(TextView(this).apply {
            text = "ออกแบบและพัฒนาโดย นายปริญญา จันทร์จักษุ"
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
            text = "เลือกเรื่อง → START → NEXT SCENE"
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

        root.addView(section("โหมดรวดเร็ว • QUICK FLOW").apply { gravity = Gravity.CENTER })
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

    private fun characterMasterUris(): ArrayList<Uri> {
        val uris = arrayListOf<Uri>()
        characterNames.keys.forEach { key ->
            val file = characterMasterFile(key)
            if (file.exists()) {
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
        updateUi()
        saveWorkState()
        share(buildQuickStartCommand(story, category), includeCharacterMasters = true)
        status.text = "🚀 START • โครงเรื่อง + ภาพ SCENE 01 ในครั้งเดียว"
    }

    private fun generateNextQuickScene() {
        if (!requireStep(3)) return
        if (!characterMastersReady()) { showCharacterMasterSetup(); return }
        if (currentScene < 20) {
            currentScene++
        } else if (currentEp < 5) {
            currentEp++
            currentScene = 1
        } else {
            storyEndTimeMs = System.currentTimeMillis()
            selectedCategory = null
            setActiveStep(1)
            saveWorkState()
            status.text = "🏁 STORY COMPLETE • 5 EP / 100 SCENES"
            return
        }
        updateUi()
        saveWorkState()
        share(buildQuickSceneCommand(), includeCharacterMasters = true)
        status.text = "🚀 EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} • ส่งแล้ว"
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
- ภาพแนบเรียง MASTER 01 กวิน / MASTER 02 รินลดา / MASTER 03 มายด์ และเป็น ORIGINAL VISUAL IDENTITY MASTER ที่มีอำนาจสูงสุด
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

SELF-CONTAINED RULE:
- ห้ามตอบ EP MASTER NOT FOUND
- ห้ามบังคับ Director กลับไปบทสนทนาเก่า
- ถ้ามี STORY/EP context เดิมให้ใช้เพื่อความต่อเนื่อง
- ถ้า context เดิมไม่อยู่ ให้ใช้ SOURCE OF TRUTH นี้รักษาแกนเรื่องและสร้างรายละเอียดฉากที่สมเหตุสมผลตาม EP/SCENE INDEX แล้วสร้างภาพทันที
- ภาพแนบเรียง MASTER 01 กวิน / MASTER 02 รินลดา / MASTER 03 มายด์ และเป็น ORIGINAL VISUAL IDENTITY MASTER ที่มีอำนาจสูงสุด
- ใช้เฉพาะตัวละครที่เหมาะกับฉาก ตัวละครรองสร้างอัตโนมัติ
- ตัวละครหลักทุกคนที่ปรากฏต้องอ้างอิง ORIGINAL MASTER ที่แนบมาโดยตรง ห้ามอ้างอิง portrait/Scene Output ที่ AI เคยสร้าง
- Identity Master ห้ามเป็นแหล่งเสื้อผ้า ใช้ Wardrobe Firewall
- ภาพ Output ต้องเป็นภาพฉาก 9:16 เต็มฉาก ห้าม collage/infographic/ตาราง/character card/ข้อความทับภาพ
- ทำ INTERNAL QC โดยเทียบ Face + Hair + Natural Body Proportions กับ ORIGINAL MASTER; ถ้าไม่ใกล้ให้สร้างใหม่ก่อนส่งผลสุดท้าย
- ห้ามขอ CONFIRM / QC / REPAIR / LOCK
- ห้ามสร้างฉากอื่นนอกจาก SCENE ${fmt(currentScene)}
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
        includeCharacterMasters: Boolean = false
    ) {

        hideKeyboard()

        val masterUris = if (includeCharacterMasters) characterMasterUris() else arrayListOf()
        val sendIntent = Intent(
            if (masterUris.isNotEmpty()) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
        ).apply {
            type = if (masterUris.isNotEmpty()) "image/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (masterUris.isNotEmpty()) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, masterUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
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

                    // ใช้ ChatGPT task เดิมเมื่อมีอยู่
                    // ห้าม MULTIPLE_TASK / NEW_DOCUMENT เพราะจะทำให้แตกเป็นหลาย ChatGPT task
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
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
            val chooserIntent = Intent.createChooser(
                sendIntent,
                "ส่งคำสั่งไปยัง ChatGPT"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooserIntent)
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
R${appVersion()} • BUILD เวอร์ชั่น ${packageManager.getPackageInfo(packageName, 0).versionCode}
ออกแบบ/พัฒนา: นายปริญญา จันทร์จักษุ
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

VISUAL IDENTITY LOCK — HIGHEST PRIORITY:

- ภาพแนบ MASTER 01 / 02 / 03 คือแหล่งอ้างอิงอัตลักษณ์ทางภาพเพียงชุดเดียวของตัวละครหลัก และมีลำดับความสำคัญเหนือคำบรรยายตัวละครด้วยข้อความ
- ห้ามสร้าง ตีความ ออกแบบ หรือแทนที่ใบหน้าใหม่จากชื่อ อายุ บทบาท บุคลิก หรือเนื้อเรื่อง
- ทุกครั้งที่ตัวละครหลักปรากฏ ต้องอ้างอิง ORIGINAL IDENTITY MASTER ของคนนั้นโดยตรง ไม่ใช่ภาพที่ AI สร้างขึ้นภายหลัง
- ต้องรักษาโครงหน้า รูปตา คิ้ว จมูก ปาก กราม สีผิว ทรงผม/แนวผม อายุโดยประมาณ และ NATURAL BODY PROPORTIONS ให้ใกล้ ORIGINAL MASTER มากที่สุด
- ห้ามทำให้ผอมลง อ้วนขึ้น เพิ่ม/ลดกล้าม เปลี่ยนช่วงไหล่ อก เอว สะโพก หรือสัดส่วนร่างกายเพื่อให้เข้ากับฉากหรือเสื้อผ้า
- เสื้อผ้าใน MASTER ไม่ใช่ Identity และต้องถูกแยกออกด้วย WARDROBE FIREWALL
- ห้ามใช้ portrait / contact sheet / character card / STORY BIBLE portrait ที่สร้างโดย AI เป็น MASTER ใหม่
- ห้ามใช้ภาพ Scene ก่อนหน้าเป็น MASTER ใหม่
- ถ้าความเหมือนของใบหน้า/รูปร่างกับ ORIGINAL MASTER ไม่เพียงพอ ให้ถือว่า INTERNAL QC = FAIL และสร้างใหม่ก่อนส่ง Output
- หากระบบสร้างภาพไม่สามารถยึดภาพแนบเป็น visual reference ได้อย่างน่าเชื่อถือ ห้ามอ้างว่า Identity Lock สำเร็จ ให้รายงานข้อจำกัดแทนการสร้างคนใหม่แล้วเรียกว่าเป็นตัวละครเดิม

STORY BIBLE IMAGE RULE:
- STORY BIBLE เป็นข้อมูลข้อความ/แผนเรื่อง ไม่ต้องสร้างภาพ portrait ใหม่ของ MASTER 01/02/03
- ถ้าจำเป็นต้องแสดงรายชื่อตัวละคร ให้ใช้ชื่อและรหัส MASTER เท่านั้น
- ภาพที่ต้องสร้างใน START คือภาพ SCENE 01 เพียงภาพเดียวหลังวาง STORY BIBLE
- ห้ามรวม STORY BIBLE, ตาราง, portrait ตัวละคร และ SCENE 01 เป็นภาพ collage/infographic เดียว
- Output ภาพ SCENE ต้องเป็นภาพฉาก 9:16 แบบเต็มฉาก ไม่ใส่ตาราง/ตัวหนังสือ/character card ทับในภาพ

CHARACTER MASTER REGISTRY:

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
                val connection = (URL(
                    "https://api.github.com/repos/pearparinya/auto-movie-r6-4/releases/latest"
                ).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
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
                            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (state == DownloadManager.STATUS_RUNNING && total > 0L) {
                                val percent = ((downloaded * 100L) / total).coerceIn(0L, 100L)
                                runOnUiThread {
                                    status.text = "↓ AUTO-MOVIE R$version • $percent%"
                                }
                            }
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
                    if (!finished) Thread.sleep(1000)
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
