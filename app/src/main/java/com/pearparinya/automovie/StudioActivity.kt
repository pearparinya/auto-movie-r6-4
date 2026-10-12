package com.pearparinya.automovie

import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Whole-story workspace. Legacy preferences and character masters stay untouched. */
class StudioActivity : AppCompatActivity() {
    private val base get() = File(filesDir, "studio").apply { mkdirs() }
    private val prefs get() = getSharedPreferences("studio", MODE_PRIVATE)
    private val project get() = File(base, prefs.getString("project", "draft")!!).apply { mkdirs() }
    private var plan: JSONObject? = null
    private var importing = false
    private val updater = StudioUpdater(this)
    private lateinit var root: LinearLayout
    private val navy = Color.rgb(12,15,38)
    private val gold = Color.rgb(255,190,74)
    private val ice = Color.rgb(248,247,255)
    private val muted = Color.rgb(190,188,214)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun backdrop(top: Int, bottom: Int, border: Int? = null) = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(top,bottom)).apply {
        cornerRadius = dp(16).toFloat()
        if (border != null) setStroke(dp(1),border)
    }
    private val menuColors = arrayOf(
        intArrayOf(Color.rgb(255,218,112),Color.rgb(255,177,58)),
        intArrayOf(Color.rgb(119,232,250),Color.rgb(63,184,235)),
        intArrayOf(Color.rgb(219,195,255),Color.rgb(176,143,244)),
        intArrayOf(Color.rgb(128,240,202),Color.rgb(63,200,160)),
        intArrayOf(Color.rgb(255,185,209),Color.rgb(245,139,175)))
    private fun menuIcon(index: Int) = listOf("🎭", "🎨", "📚", "🎬", "💡")[index % 5]
    private fun choiceMenu(title: String, choices: List<String>, cancel: String = "✖️ ยกเลิก",
        cancelable: Boolean = true, onCancel: (() -> Unit)? = null, action: (Int) -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(18),dp(16),dp(12))
            background = backdrop(navy,Color.rgb(32,28,65),Color.rgb(110,99,155))
        }
        panel.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(gold); setTypeface(null,Typeface.BOLD)
            setPadding(0,0,0,dp(14))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        panel.addView(scroll,LinearLayout.LayoutParams(-1,-2))
        val dialog = AlertDialog.Builder(this).setView(panel).setCancelable(cancelable).create()
        choices.forEachIndexed { index, value ->
            val shades = menuColors[index % menuColors.size]
            list.addView(TextView(this).apply {
                text = value; textSize = 16f; setTextColor(navy); setTypeface(null,Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL; minHeight = dp(60)
                setPadding(dp(14),dp(14),dp(14),dp(14))
                background = backdrop(shades[0],shades[1],Color.argb(160,255,255,255))
                layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(10) }
                isClickable = true; isFocusable = true
                val attr = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground,attr,true)
                foreground = getDrawable(attr.resourceId)
                setOnClickListener { dialog.dismiss(); action(index) }
            })
        }
        if (choices.isEmpty()) list.addView(TextView(this).apply {
            text = "📭 ยังไม่มีรายการ"; textSize = 16f; setTextColor(ice); setPadding(0,dp(12),0,dp(12))
        })
        panel.addView(TextView(this).apply {
            text = cancel; textSize = 16f; setTextColor(ice); gravity = Gravity.CENTER
            minHeight = dp(48); background = backdrop(Color.rgb(64,65,97),Color.rgb(45,45,76))
            isFocusable = true; setOnClickListener { dialog.dismiss(); onCancel?.invoke() }
        })
        dialog.setOnCancelListener { onCancel?.invoke() }
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(24),-2)
        panel.post {
            val maximum = (resources.displayMetrics.heightPixels * 0.85f).toInt()
            val available = maximum - (panel.height - scroll.height)
            if (scroll.height > available) scroll.layoutParams = LinearLayout.LayoutParams(-1,available.coerceAtLeast(dp(80)))
        }
    }
    private val categories = listOf(
        "The Broken Vows | บันทึกรอยร้าวชีวิตคู่",
        "Sweet Revenge | เกมรักซ่อนลวง บทเรียนรสแซ่บ",
        "Family Secrets | ความลับใต้ชายคา...ที่ไม่มีใครกล้าพูด",
        "Twisted Truth | พล็อตหักมุม...ความจริงใต้เตียง",
        "Healing Hearts | ซ่อมรักหลังบ้าน ซีรีส์ฮีลใจ")
    private var masterSlot: String?
        get() = prefs.getString("master_slot", null)
        set(value) { prefs.edit().putString("master_slot", value).apply() }
    private val masterPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = masterSlot
        if (uri != null && slot in listOf("gawin", "rinlada", "mind")) {
            try {
                val folder = File(filesDir, "character_masters").apply { mkdirs() }
                val temporary = File(folder, "$slot.tmp")
                contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์นี้ไม่ใช่ภาพที่อ่านได้" }
                require(temporary.renameTo(File(folder, "$slot.master"))) { "บันทึกภาพไม่ได้" }
                notice("บันทึกภาพตัวละครแล้ว ใช้เป็นต้นฉบับสำหรับเรื่องใหม่ได้เลยครับ")
            } catch (e: Exception) { notice("รับภาพไม่สำเร็จ: ${e.message}") }
        }
        masterSlot = null
    }
    private fun characterMasters() {
        val names = listOf("gawin" to "กวิน", "rinlada" to "รินลดา", "mind" to "มายด์")
        choiceMenu("🎭 ภาพต้นฉบับตัวละคร", names.map { (key, name) ->
                "${if (key == "gawin") "👨" else "👩"} $name • " + if (File(filesDir, "character_masters/$key.master").exists()) "มีภาพแล้ว · แตะเพื่อเปลี่ยน" else "เพิ่มภาพ"
            }) { index ->
                masterSlot = names[index].first
                masterPicker.launch(arrayOf("image/*"))
            }
    }
    private val otherGenres = listOf("ดราม่าชีวิต", "ครอบครัว", "โรแมนติก", "ตลก", "สืบสวน", "ระทึกขวัญ", "สยองขวัญ", "แฟนตาซี", "แอ็กชัน", "อบอุ่นหัวใจ")
    private val smartDirector = "ระบบกำกับละครอัจฉริยะ"
    private val genreLabels = listOf(
        "🎭 ดราม่าชีวิต\nต่อสู้ชีวิตและการเริ่มต้นใหม่",
        "🏡 ครอบครัว\nความรักและความผูกพันในบ้าน",
        "💕 โรแมนติก\nเรื่องรักและความสัมพันธ์",
        "😂 ตลก\nเรื่องสนุก ชวนยิ้มและหัวเราะ",
        "🔎 สืบสวน\nตามเบาะแส เปิดเผยความจริง",
        "😱 ระทึกขวัญ\nลุ้นทุกฉาก ปริศนาและอันตราย",
        "👻 สยองขวัญ\nเรื่องลี้ลับและความน่ากลัว",
        "✨ แฟนตาซี\nโลกจินตนาการและพลังวิเศษ",
        "🔥 แอ็กชัน\nภารกิจ การไล่ล่าและการต่อสู้",
        "🌷 อบอุ่นหัวใจ\nความหวัง การช่วยเหลือและกำลังใจ")
    private val originalChannel = "สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย"
    private val planPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) background {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val candidate = JSONObject(text.removePrefix("\uFEFF"))
            validate(candidate)
            val id = UUID.randomUUID().toString()
            val folder = File(base, id).apply { mkdirs() }
            File(folder, "story.json").writeText(candidate.toString(2))
            prefs.edit().putString("project", id).commit()
        }
    }
    private val packagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) receivePackage(uris)
    }
    private fun receivePackage(selected: List<Uri>) {
        importing = true
        val dialog = AlertDialog.Builder(this).setMessage("กำลังตรวจบทและภาพ…").setCancelable(false).showDirector()
        Thread {
            try {
                File(cacheDir,"story_import").deleteRecursively()
                val staging = File(cacheDir, "story_import/${UUID.randomUUID()}").apply { mkdirs() }
                val files = mutableListOf<Uri>()
                for (uri in selected) {
                    if (displayName(uri).endsWith(".zip", true) || contentResolver.getType(uri) in listOf("application/zip", "application/x-zip-compressed")) {
                        contentResolver.openInputStream(uri)!!.use { input -> files.addAll(StoryFiles.unpack(input, File(staging, files.size.toString())).map { Uri.fromFile(it) }) }
                    } else files.add(uri)
                }
                val scripts = files.filter { displayName(it).equals("story.json", true) }
                require(scripts.size <= 1) { "เลือก story.json ของเรื่องเดียวเท่านั้น" }
                val pictures = files.filter { displayName(it).substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp") || contentResolver.getType(it)?.startsWith("image/") == true }
                val masters = pictures.filter { Regex("^CHARACTER_\\d+\\.", RegexOption.IGNORE_CASE).containsMatchIn(displayName(it)) }
                val covers = pictures.filter { StoryFiles.coverName(displayName(it)) != null }
                val scenes = pictures.filter { !StoryFiles.isMaster(displayName(it)) } - covers.toSet()
                require(scripts.isNotEmpty() || pictures.isNotEmpty()) { "ไม่พบ story.json หรือภาพในไฟล์ที่เลือก กรุณาเลือก ZIP ชุดงาน หรือ JSON/PNG/JPG" }
                val candidate = scripts.firstOrNull()?.let { contentResolver.openInputStream(it)!!.bufferedReader().use { reader -> JSONObject(reader.readText().removePrefix("\uFEFF")) } }
                val destinationStory = candidate ?: plan ?: error("ยังไม่มีบท กรุณานำเข้า story.json ก่อน")
                val checkedCovers = covers.map { uri ->
                    val name = StoryFiles.coverName(displayName(uri))!!
                    require(name in coverNames(destinationStory)) { "ปก $name ไม่ตรงกับ EP ในเรื่อง" }
                    val temp = File(staging,"checked/$name.img").apply { parentFile!!.mkdirs() }
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                    val ratio = if (name.endsWith("9x16")) 9.0 / 16 else 16.0 / 9
                    require(bounds.outHeight > 0 && kotlin.math.abs(bounds.outWidth.toDouble()/bounds.outHeight - ratio) < 0.04) { "$name ต้องมีสัดส่วน ${if (name.endsWith("9x16")) "9:16" else "16:9"}" }
                    name to temp
                }
                require(checkedCovers.map { it.first }.distinct().size == covers.size) { "มีไฟล์ปกซ้ำ กรุณาเลือกหนึ่งภาพต่อสัดส่วนต่อ EP" }
                if (candidate != null) {
                    validate(candidate)
                    // Reimporting the same story must retain images already received.
                    val sameStory = plan?.let { old -> old.optString("title") == candidate.optString("title") && old.optString("category") == candidate.optString("category") && rows(old).map { it.toString() } == rows(candidate).map { it.toString() } } == true
                    val folder = if (sameStory) project else File(base, UUID.randomUUID().toString()).apply { mkdirs() }
                    importNewMasters(masters, folder, candidate)
                    File(folder,"story.json").writeText(candidate.toString(2))
                    prefs.edit().putString("project",folder.name).commit()
                } else {
                    require(plan != null) { "ยังไม่มีบท กรุณานำเข้า story.json หรือ ZIP ที่มีบทก่อน" }
                    importNewMasters(masters)
                }
                checkedCovers.forEach { (name, temp) -> coverFile(name).apply { parentFile!!.mkdirs() }.let { temp.copyTo(it,overwrite=true) } }
                runOnUiThread {
                    importing = false; dialog.dismiss(); render()
                    if (scenes.isNotEmpty()) importImages(scenes)
                    else if (covers.isNotEmpty()) notice("รับปกแล้ว ${covers.size} ไฟล์ ตรวจจำนวนภาพและปกบนหน้าแรกได้เลย")
                    else notice(if (candidate != null) "รับบทแล้ว ขั้นตอน 2: เพิ่มภาพฉากได้เลย ไม่ต้องเลือก story.json ซ้ำ" else "รับภาพ MASTER แล้ว เพิ่มภาพฉากในขั้นตอน 2 ได้เลย")
                }
            } catch (e: Exception) { runOnUiThread { importing = false; dialog.dismiss(); render(); notice("นำเข้าบทและภาพไม่สำเร็จ: ${e.message}") } }
        }.start()
    }
    private val imagesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }
    private val zipPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) background { export(uri) }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() {
        super.onResume()
        if (!importing) updater.checkAutomatically()
    }
    private fun rows(p: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val eps = p.getJSONArray("episodes")
        for (i in 0 until eps.length()) {
            val ep = eps.getJSONObject(i)
            val scenes = ep.getJSONArray("scenes")
            for (j in 0 until scenes.length()) result.add(JSONObject(scenes.getJSONObject(j).toString()).put("ep", ep.getInt("ep")))
        }
        return result
    }
    private fun key(s: JSONObject) = "EP%02d_SC%03d".format(s.getInt("ep"), s.getInt("scene"))
    private fun image(s: JSONObject) = File(project, "images/${key(s)}.img")
    private fun validate(p: JSONObject) {
        require(p.getString("title").isNotBlank()) { "ไม่มีชื่อเรื่อง" }
        val mode = p.optString("character_mode", "original")
        require(mode in listOf("original", "new")) { "รูปแบบตัวละครไม่ถูกต้อง" }
        require(p.getString("category") in (if (mode == "new") otherGenres else categories)) { "หมวดหมู่ไม่ตรงกับรูปแบบช่อง" }
        if (mode == "new") require(p.optString("channel").isNotBlank()) { "กรุณาระบุชื่อช่องในบท" }
        val eps = p.getJSONArray("episodes")
        require(eps.length() > 0) { "ไม่มี EP" }
        if (p.optInt("schema_version", 1) >= 2) {
            require(p.optString("channel").isNotBlank()) { "บทขาดชื่อช่อง" }
            require(p.optString("tagline").isNotBlank()) { "บทขาดคำโปรยเรื่อง" }
            require(p.getInt("episode_count") == eps.length()) { "จำนวน EP ไม่ตรงกับบท" }
            require(p.getInt("scene_count") == (0 until eps.length()).sumOf { eps.getJSONObject(it).getJSONArray("scenes").length() }) { "จำนวนซีนรวมไม่ตรงกับบท" }
        }
        for (i in 0 until eps.length()) {
            val ep = eps.getJSONObject(i)
            require(ep.getInt("ep") == i + 1) { "EP ต้องเรียงจาก 1" }
            val scenes = ep.getJSONArray("scenes")
            require(scenes.length() > 0) { "EP ไม่มีฉาก" }
            if (p.optInt("schema_version", 1) >= 2) {
                require(ep.optString("title").isNotBlank()) { "EP ${i + 1} ขาดชื่อตอน" }
                require(ep.optString("tagline").isNotBlank()) { "EP ${i + 1} ขาดคำโปรย" }
                require(ep.getInt("scene_count") == scenes.length()) { "จำนวนซีน EP ${i + 1} ไม่ตรงกับบท" }
            }
            for (j in 0 until scenes.length()) {
                val s = scenes.getJSONObject(j)
                require(s.getInt("scene") == j + 1) { "ฉากต้องเรียงจาก 1 ภายในแต่ละ EP" }
                if (p.optInt("schema_version", 1) >= 3) {
                    require(s.optString("location").isNotBlank() && s.optString("continuity_group").isNotBlank()) { "ฉากขาดสถานที่หรือรหัสเหตุการณ์ต่อเนื่อง" }
                    val cast = s.getJSONArray("characters")
                    require(cast.length() > 0) { "ฉากขาดรายการตัวละคร" }
                    val names = mutableSetOf<String>()
                    for (c in 0 until cast.length()) {
                        val person = cast.getJSONObject(c)
                        require(person.optString("name").isNotBlank() && names.add(person.getString("name"))) { "ชื่อหรือรายการตัวละครไม่ถูกต้อง" }
                        require(person.optString("outfit").isNotBlank()) { "ฉากขาดชุดของ ${person.optString("name")}" }
                        val master = person.optString("master")
                        require(if (mode == "original") StoryIdentity.matchesMaster(person.getString("name"), master) else Regex("^CHARACTER_[0-9]+[.]png$").matches(master)) { "MASTER ไม่ตรงกับตัวละคร ${person.optString("name")}" }
                    }
                }
                for (field in listOf("action", "image_prompt", "flow_prompt")) require(s.getString(field).isNotBlank()) { "ฉากขาด $field" }
            }
        }
    }
    private fun render() {
        plan = File(project, "story.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        window.statusBarColor = navy
        window.navigationBarColor = navy
        ViewCompat.getWindowInsetsController(window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val scroll = ScrollView(this).apply { background = backdrop(Color.rgb(19,23,54), Color.rgb(9,12,29)); isFillViewport = true; clipToPadding = false }
        root = LinearLayout(this).apply { orientation = 1; setPadding(dp(10),dp(2),dp(10),dp(4)) }
        scroll.addView(root)
        val host = LinearLayout(this).apply { orientation = 1; setBackgroundColor(navy) }
        host.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        host.addView(TextView(this).apply {
            text = "Flow • Veo 3.1 Lite • 8 วินาที • 9:16\nพัฒนาโดย : นายปริญญา จันทร์จักษุ"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(muted)
            setPadding(dp(10),dp(6),dp(10),dp(6))
            background = backdrop(Color.rgb(35,32,65),navy,Color.rgb(99,80,45))
        })
        setContentView(host)
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(host)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = backdrop(Color.rgb(43,38,81), navy, Color.rgb(141,112,62)); elevation = dp(6).toFloat()
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.director_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(104), dp(104)).apply { marginEnd = dp(8) }
            contentDescription = "DIRECTOR X"
        })
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        fun brandLine(value: String, maximum: Int, minimum: Int, color: Int, bold: Boolean, spacing: Int) {
            heading.addView(TextView(this).apply {
                text = value
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setSingleLine(true)
                textSize = maximum.toFloat()
                setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(spacing), 0, dp(spacing))
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, minimum, maximum, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            })
        }
        brandLine("DIRECTOR X 1.45", 20, 14, gold, true, 1)
        brandLine("ผู้กำกับอัจฉริยะ", 16, 12, ice, true, 0)
        brandLine("รุ่น 045 • พร้อมใช้ใน Flow", 12, 10, muted, false, 1)
        header.addView(heading)
        root.addView(header)
        
        val p = plan
        val summary = LinearLayout(this).apply {
            orientation = 1; setPadding(dp(10),dp(6),dp(10),dp(6))
            background = backdrop(Color.rgb(46,35,87),Color.rgb(26,27,58))
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(4),0,dp(6)) }
        }
        fun summaryText(value: String, size: Float, color: Int, bold: Boolean = false) {
            summary.addView(TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0,dp(3),0,dp(3)); if (bold) setTypeface(null,Typeface.BOLD) })
        }
        if (p == null) {
            summaryText(if (File(project,"command_sent").exists()) "ส่งไป ChatGPT แล้ว • รอรับบทและภาพในขั้นตอน 2" else "🎬 พร้อมสร้างเรื่องใหม่ • ครบทุก EP",14f,ice,true)
        } else {
            val scenes = rows(p); val count = scenes.count { image(it).exists() }
            summaryText(p.getString("title"),17f,ice,true)
            summaryText(p.optString("channel", originalChannel),12f,muted)
            summaryText(p.getString("category"),12f,muted)
            p.optString("tagline").takeIf { it.isNotBlank() }?.let { summaryText(it,13f,ice) }
            val episodes = p.getJSONArray("episodes")
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                summaryText("EP ${ep.getInt("ep")} • ${ep.optString("title")} • ${ep.getJSONArray("scenes").length()} ซีน",12f,muted)
            }
            summaryText("${p.getJSONArray("episodes").length()} EP  •  ${scenes.size} ฉาก  •  ภาพ $count/${scenes.size}",14f,gold,true)
            val names = coverNames(p)
            summaryText("ปก ${names.count { coverFile(it).exists() }}/${names.size} • ทุก EP มี 9:16 และ 16:9",12f,gold,true)
            summary.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max = scenes.size; progress = count
                progressTintList = ColorStateList.valueOf(Color.rgb(35,219,195))
                progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(58,53,92))
                layoutParams = LinearLayout.LayoutParams(-1,dp(8)).apply { topMargin = dp(8) }
            })
        }
        root.addView(summary)
        val sent = p != null || File(project,"command_sent").exists()
        val complete = p != null && rows(p).all { image(it).exists() } && coverNames(p).all { coverFile(it).exists() }
        label(if (complete) "ภาพและปกครบแล้ว • บันทึกชุดงานล่าสุดได้" else if (sent) "ขั้นตอน 2 • รับไฟล์จาก ChatGPT กลับเข้าแอป" else "ขั้นตอน 1 • เลือกเรื่องแล้วส่งไป ChatGPT", 14, true)
        stepCard(1, "🎬", if (sent) "ส่งคำสั่งแล้ว ✓" else "สร้างเรื่องและส่งไป ChatGPT", if (sent) "ล็อกการสร้างซ้ำ • เริ่มเรื่องใหม่ใช้ปุ่มด้านล่าง" else "เลือกช่องและแนว • ชื่อเรื่องภาษาไทย", enabled = !sent) { planning() }
        stepCard(2, "🎨", if (p == null) "รับบทและภาพจาก ChatGPT" else "เพิ่มภาพฉาก / รับ ZIP", "เลือก ZIP หรือ story.json และภาพ • มีบทแล้วเพิ่มเฉพาะภาพได้") { packagePicker.launch(arrayOf("*/*")) }
        stepCard(3, "📦", "บันทึกชุดงานล่าสุด", if (complete) "รวมบท ภาพฉาก และปกเป็น ZIP" else "ปลดล็อกเมื่อภาพฉากและปกทั้งสองแบบครบ", enabled = complete) {
            val current = plan ?: return@stepCard
            val title = current.getString("title")
            if (!Regex("[ก-๙]").containsMatchIn(title)) {
                val input = EditText(this).apply { hint = "ชื่อเรื่องภาษาไทย" }
                AlertDialog.Builder(this).setTitle("ตั้งชื่อเรื่องภาษาไทยก่อนบันทึก").setView(input).setPositiveButton("บันทึกชื่อ") { _, _ ->
                    val thai = input.text.toString().trim()
                    if (!Regex("[ก-๙]").containsMatchIn(thai)) notice("กรุณาใส่ชื่อเรื่องภาษาไทย")
                    else { current.put("title",thai); File(project,"story.json").writeText(current.toString(2)); render(); zipPicker.launch(StoryFiles.zipName(thai)) }
                }.setNegativeButton("ยกเลิก",null).showDirector()
            } else zipPicker.launch(StoryFiles.zipName(title))
        }
        if (p != null) button("🎨 สร้าง / ปรับปกจากภาพฉาก",false) { sendCoverCommands() }
        splitActions("🔄 อัปเดต DIRECTOR X", "✨ เริ่มเรื่องใหม่", { updater.check() }, {
            AlertDialog.Builder(this).setTitle("เริ่มเรื่องใหม่?").setMessage("เรื่องปัจจุบันเก็บไว้ในรายการเรื่องที่บันทึก สามารถกลับมาทำต่อได้")
                .setPositiveButton("เริ่มเรื่องใหม่") { _,_ -> prefs.edit().putString("project", UUID.randomUUID().toString()).apply(); render() }.setNegativeButton("ทำเรื่องเดิมต่อ",null).showDirector()
        }, colors = listOf(menuColors[3], menuColors[4]))
        splitActions("🎭 ตัวละคร • ตัวเลือก", "🖼️ ทดลองสร้างรูป", { toolsMenu() }, { testImageMenu() })
    }
    private fun splitActions(left: String, right: String, leftAction: () -> Unit, rightAction: () -> Unit, colors: List<IntArray> = listOf(menuColors[1], menuColors[2])) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(3); bottomMargin = dp(3) }
        }
        listOf(left to leftAction, right to rightAction).forEachIndexed { index, item ->
            row.addView(Button(this).apply {
                text = item.first; isAllCaps = false; textSize = 12f
                setTextColor(navy); setTypeface(null,Typeface.BOLD); gravity = Gravity.CENTER
                minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
                setPadding(dp(4),dp(5),dp(4),dp(5))
                backgroundTintList = null
                background = backdrop(colors[index][0],colors[index][1],Color.argb(150,255,255,255))
                layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { if (index == 0) marginEnd = dp(3) else marginStart = dp(3) }
                setOnClickListener { if (!importing) item.second() }
            })
        }
        root.addView(row)
    }
    private fun testImageMenu() {
        choiceMenu("🖼️ ทดลองสร้างรูป 1 ภาพ", listOf("🎭 ตัวละครเดิม • เลือกคนที่ต้องการ", "✨ ตัวละครสมมุติ • สร้างคนใหม่")) { choice ->
            if (choice == 0) choiceMenu("🎭 เลือกตัวละครในภาพ", listOf("👨 กวิน • คนเดียว", "👩 รินลดา • คนเดียว", "👩 มายด์ • คนเดียว", "👥 ทั้ง 3 คนในภาพเดียว")) { index ->
                val keys = listOf("gawin", "rinlada", "mind")
                sendTestImage(if (index == 3) keys else listOf(keys[index]))
            }
            else shareText("สร้างภาพหนึ่งภาพตอนนี้ด้วยเครื่องมือสร้างภาพ และแสดงภาพที่สร้างในแชต ตัวละครสมมุติผู้ใหญ่หนึ่งคน ตั้งชื่อภาษาไทยและออกแบบใบหน้าใหม่ เลือกชุดสุภาพใหม่ เลือกสถานที่และสร้างเหตุการณ์สมมุติให้ตัวละคร เช่น ทำอาหารในบ้าน อ่านหนังสือในร้านกาแฟ หรือรดน้ำต้นไม้ในสวน เลือกหนึ่งเหตุการณ์ ออกแบบฉากและสิ่งของให้เหมาะสม กำหนดการกระทำของตัวละครให้ชัดเจน ภาพละครไทยสมจริง แนวตั้ง 9:16 เห็นใบหน้าและรูปร่างตั้งแต่ศีรษะถึงต้นขา แสงชัด สถานที่และเหตุการณ์เป็นเรื่องแต่ง ภาพไม่มีข้อความหรือตราสัญลักษณ์ หลังแสดงภาพจริงแล้วจึงแจ้งชื่อตัวละคร ชุด สถานที่ และเหตุการณ์สั้น ๆ หากสร้างภาพไม่ได้ให้แจ้งตามจริง", useNewMasters = false, includeMasters = false)
        }
    }
    private fun sendTestImage(selectedKeys: List<String>) {
        val cast = listOf("gawin" to "กวิน", "rinlada" to "รินลดา", "mind" to "มายด์")
        val selected = cast.filter { it.first in selectedKeys }
        require(selected.isNotEmpty())
        if (selected.any { !File(filesDir,"character_masters/${it.first}.master").exists() }) {
            AlertDialog.Builder(this).setTitle("เพิ่มภาพ MASTER ก่อนทดสอบ")
                .setMessage("ต้องมีต้นฉบับของ ${selected.joinToString { it.second }} เพื่อเปรียบเทียบใบหน้าและรูปร่าง")
                .setPositiveButton("เพิ่มภาพตัวละคร") { _,_ -> characterMasters() }.setNegativeButton("ยกเลิก",null).showDirector()
            return
        }
        val names = selected.joinToString { it.second }
        val wardrobe = selected.joinToString("; ") { (_, name) ->
            when (name) {
                "รินลดา" -> "รินลดาใส่เสื้อผ้าถักเนื้อละเอียดสีครีมแขนสามส่วน กระดุมหน้า คอเสื้อโค้งระดับพอดี ผ้าทึบ ทรงพอดีลำตัวและเอว กับกระโปรงสีเบจยาวถึงเข่าและผ้ากันเปื้อนสั้นสีเบจ กำลังหั่นมะเขือเทศบนเขียงไม้"
                "มายด์" -> "มายด์ใส่เสื้อผ้าถักเนื้อละเอียดสีชมพูอ่อนแขนสามส่วน กระดุมหน้า คอเสื้อโค้งระดับพอดี ผ้าทึบ ทรงพอดีลำตัวและเอว กับกระโปรงสีครีมยาวถึงเข่าและผ้ากันเปื้อนสีครีม กำลังผัดผักในกระทะ"
                else -> "กวินใส่เสื้อโปโลสีกรมท่าพอดีตัวกับกางเกงขายาวสีเบจ กำลังจัดผักลงในชาม"
            }
        }
        shareText("สร้างภาพหนึ่งภาพตอนนี้ด้วยเครื่องมือสร้างภาพ และแสดงภาพจริงในแชต ตัวละครผู้ใหญ่ที่เลือก: $names ใช้ภาพ MASTER ที่แนบตรงรายคน คงใบหน้า ผม รูปร่าง และสัดส่วนร่างกายตามต้นฉบับ ชุดใหม่: $wardrobe ฉากห้องครัวบ้านสมัยใหม่ เคาน์เตอร์สีเข้ม ตู้ไม้ หน้าต่างรับแสงเช้า ผักสด ชามและอุปกรณ์ทำอาหาร จัดท่ายืนเป็นธรรมชาติ เห็นลำตัวและเอวชัด เสื้อผ้าพอดีกับรูปร่างเดิม การทำอาหารชัดเจน สีหน้าเป็นมิตร ภาพละครไทยสมจริงแนวตั้ง 9:16 เห็นศีรษะถึงต้นขา แสงธรรมชาติ ภาพไม่มีข้อความหรือตราสัญลักษณ์ หลังแสดงภาพจริงแล้วแจ้งชื่อ ชุด สถานที่ และเหตุการณ์สั้น ๆ หากสร้างภาพไม่ได้ให้แจ้งตามจริง", useNewMasters = false, selectedMasterKeys = selected.map { it.first })
    }
    private fun stepCard(number: Int, icon: String, title: String, detail: String, enabled: Boolean = true, action: () -> Unit) {
        val colors = when (number) {
            1 -> intArrayOf(Color.rgb(255,218,112),Color.rgb(255,173,34))
            2 -> intArrayOf(Color.rgb(46,189,252),Color.rgb(30,101,221))
            else -> intArrayOf(Color.rgb(182,107,255),Color.rgb(107,56,204))
        }
        val ink = if (number == 1) navy else ice
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10),dp(8),dp(10),dp(8))
            background = backdrop(colors[0],colors[1],Color.argb(110,255,255,255))
            elevation = dp(5).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(3); bottomMargin = dp(3) }
            isEnabled = enabled; isClickable = enabled; isFocusable = enabled; alpha = if (enabled) 1f else 0.45f
            val attribute = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground,attribute,true)
            foreground = getDrawable(attribute.resourceId)
            contentDescription = "ขั้นตอน $number $title $detail"
            setOnClickListener { if (enabled && !importing) action() else Toast.makeText(this@StudioActivity,"กำลังนำเข้าภาพ กรุณารอสักครู่",Toast.LENGTH_SHORT).show() }
        }
        card.addView(ImageView(this).apply {
            setImageResource(when (number) { 1 -> R.drawable.studio_movie; 2 -> R.drawable.studio_images; else -> R.drawable.studio_package }); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36),dp(36))
            contentDescription = title
        })
        val words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = dp(6) }
        }
        words.addView(TextView(this).apply {
            text = "$number  $title"; textSize = 16f; setTypeface(null,Typeface.BOLD); setTextColor(ink)
        })
        words.addView(TextView(this).apply {
            text = detail; textSize = 11f; setTextColor(ink); setPadding(0,dp(5),0,0)
        })
        card.addView(words)
        card.addView(TextView(this).apply {
            text = if (enabled) "›" else "🔒"; textSize = 27f; setTextColor(ink); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(18),-2)
        })
        root.addView(card)
    }
    private fun toolsMenu() {
        choiceMenu("⚙️ ตัวเลือก", listOf("🎭 ภาพต้นฉบับตัวละคร", "🔎 ดูภาพและบทในเรื่องนี้", "📚 เลือกเรื่องที่บันทึก", "🎬 ส่งคำสั่งภาพทั้งเรื่อง", "💡 วิธีใช้งาน")) { index ->
            when (index) {
                0 -> characterMasters()
                1 -> review()
                2 -> {
                    val folders = base.listFiles()?.filter { File(it,"story.json").exists() } ?: emptyList()
                    choiceMenu("📚 เรื่องของคุณ", folders.map { "📖 " + JSONObject(File(it,"story.json").readText()).getString("title") }) { i -> prefs.edit().putString("project",folders[i].name).apply(); render() }
                }
                3 -> wholeStoryCommands()
                4 -> notice("1. ตั้งภาพต้นฉบับตัวละคร แล้วกดสร้างเรื่องและส่งไป ChatGPT เลือกช่องและแนวละคร ส่งไป ChatGPT ช่องเดิมใช้ตัวละครเดิม ช่องอื่นสร้างใบหน้าใหม่โดยอ้างอิงรูปร่าง และใช้ MASTER ใหม่ต่อเนื่องทั้งเรื่อง\n2. บันทึก story.json และภาพแต่ละฉากจาก ChatGPT สำหรับช่องอื่นบันทึกภาพ CHARACTER_01.png เป็นต้นฉบับใหม่ด้วย กดขั้นตอน 2 เลือก ZIP ทั้งชุด หรือ story.json พร้อมภาพ ถ้ามีบทแล้วเพิ่มเฉพาะภาพได้ ไม่ต้องเลือกบทซ้ำ\n3. เมื่อภาพครบ ปุ่มบันทึกชุดงานล่าสุด จะเปิดให้บันทึก ZIP ชื่อเรื่องภาษาไทย และนำภาพกับคำสั่งไปใช้ใน Flow / Veo 3.1 Lite 8 วินาที\n\nChatGPT เป็นผู้สร้างบทและภาพ แอปจัดไฟล์และตรวจฉากที่ขาด ไม่ได้สร้างภาพเองผ่าน Plus อัตโนมัติ")
            }
        }
    }

    private fun label(value: String, size: Int, bold: Boolean = false) {
        root.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(if (bold) gold else muted)
            setPadding(dp(4),dp(4),dp(4),dp(4)); if (bold) setTypeface(null, Typeface.BOLD)
        })
    }
    private fun button(value: String, primary: Boolean = true, action: () -> Unit) {
        val colors = when {
            !primary -> if (value.contains("Update")) menuColors[3] else if (value.contains("ปก")) menuColors[1] else menuColors[4]
            value.startsWith("①") -> intArrayOf(Color.rgb(255,204,86),Color.rgb(255,174,38))
            value.startsWith("②") -> intArrayOf(Color.rgb(42,153,246),Color.rgb(27,101,211))
            value.startsWith("③") -> intArrayOf(Color.rgb(164,96,246),Color.rgb(111,65,209))
            value.startsWith("④") -> intArrayOf(Color.rgb(40,220,164),Color.rgb(10,183,133))
            value.startsWith("⑤") -> intArrayOf(Color.rgb(35,205,216),Color.rgb(14,163,182))
            else -> intArrayOf(Color.rgb(255,203,81),Color.rgb(255,166,39))
        }
        val darkText = !value.startsWith("②") && !value.startsWith("③")
        root.addView(Button(this).apply {
            text = value; isAllCaps = false; textSize = if (primary) 16f else 14f
            setTypeface(null,if (primary) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (darkText) navy else ice)
            backgroundTintList = null
            background = backdrop(colors[0],colors[1],if (primary) null else Color.rgb(62,58,106))
            elevation = dp(if (primary) 3 else 0).toFloat()
            minHeight = dp(if (primary) 46 else 38); minimumHeight = minHeight
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(4),0,dp(4)) }
            setPadding(dp(10),dp(6),dp(10),dp(6))
            setOnClickListener { if (!importing) action() else Toast.makeText(this@StudioActivity,"กำลังนำเข้าภาพ กรุณารอสักครู่",Toast.LENGTH_SHORT).show() }
        })
    }
    private fun notice(text: String) { AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage(text).setPositiveButton("ตกลง", null).showDirector() }
    private fun background(block: () -> Unit) {
        val dialog = AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage("กำลังจัดไฟล์…").setCancelable(false).showDirector()
        Thread {
            try { block(); runOnUiThread { dialog.dismiss(); render(); notice("เสร็จเรียบร้อย") } }
            catch (e: Exception) { runOnUiThread { dialog.dismiss(); notice("ดำเนินการไม่สำเร็จ: ${e.message}") } }
        }.start()
    }
    private fun shareText(text: String, lockCreation: Boolean = false, useNewMasters: Boolean = plan?.optString("character_mode") == "new", includeMasters: Boolean = true, selectedMasterKeys: List<String>? = null, referenceImages: List<Pair<String, File>> = emptyList()) {
        val uris = arrayListOf<Uri>()
        val transferId = UUID.randomUUID().toString()
        val sources = if (!includeMasters) emptyList<File>() else if (useNewMasters) File(project, "masters").listFiles()?.toList() ?: emptyList()
            else (selectedMasterKeys ?: listOf("gawin", "rinlada", "mind")).map { File(filesDir, "character_masters/$it.master") }
        if (includeMasters && (sources.isEmpty() || sources.any { !it.exists() })) {
            notice("ภาพ MASTER ยังไม่ครบสำหรับคำสั่งนี้ กรุณาเพิ่มต้นฉบับตัวละครก่อนส่ง เพื่อไม่สร้างตัวละครผิดคน")
            return
        }
        for (source in sources) {
            val name = if (useNewMasters) source.nameWithoutExtension else source.nameWithoutExtension + "_MASTER"
            if (source.exists()) {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(source.path, bounds)
                if (bounds.outWidth <= 0) { notice("อ่านภาพ MASTER $name ไม่ได้ กรุณาเลือกภาพตัวละครใหม่"); return }
                val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                val out = File(cacheDir,"character_master_exports/$transferId/${name.uppercase()}.$extension")
                out.parentFile!!.mkdirs(); source.copyTo(out, overwrite = true)
                uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",out))
            }
        }
        for ((name, source) in referenceImages) {
            if (!source.exists()) { notice("ไม่พบภาพอ้างอิง $name กรุณานำเข้าภาพฉากก่อน"); return }
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(source.path, options)
            if (options.outWidth <= 0) { notice("อ่านภาพอ้างอิง $name ไม่ได้"); return }
            val extension = when (options.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
            val out = File(cacheDir, "scene_reference_exports/$transferId/$name.$extension")
            out.parentFile!!.mkdirs(); source.copyTo(out, overwrite = true)
            uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",out))
        }
        val intent = Intent(if (uris.size <= 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (uris.isEmpty()) "text/plain" else {
                val extensions = uris.map { it.lastPathSegment?.substringAfterLast('.')?.lowercase() }.distinct()
                when (extensions.singleOrNull()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/*" }
            }
            putExtra(Intent.EXTRA_TEXT,text)
            if (uris.isNotEmpty()) {
                if (uris.size == 1) putExtra(Intent.EXTRA_STREAM,uris[0])
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                clipData = ClipData.newUri(contentResolver,"MASTER",uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("คำสั่ง DIRECTOR X", text))
        val chatGptPackages = listOf("com.openai.chatgpt", "com.openai.chatgpt.beta")
        for (targetPackage in chatGptPackages) {
            val scoped = Intent(intent).setPackage(targetPackage)
            val receivers = packageManager.queryIntentActivities(scoped, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName == targetPackage && it.activityInfo.exported && it.activityInfo.enabled }
                .sortedByDescending { it.priority }
            for (receiver in receivers) {
                try {
                    val direct = Intent(intent).apply {
                        component = android.content.ComponentName(receiver.activityInfo.packageName, receiver.activityInfo.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    uris.forEach { grantUriPermission(targetPackage, it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    startActivity(direct)
                    if (lockCreation) { File(project,"command_sent").writeText("sent"); render() }
                    return
                } catch (_: android.content.ActivityNotFoundException) {
                    // Try another receiver that advertised support for this payload.
                } catch (_: SecurityException) {
                    // Fall back to the system resolver if this receiver is unavailable.
                }
            }
        }
        AlertDialog.Builder(this).setTitle("ส่งคำสั่งและภาพไป ChatGPT")
            .setMessage("ส่งตรงไม่ได้ เพราะไม่พบหน้ารับแชร์ที่ใช้งานได้ของ ChatGPT\nเตรียมภาพ ${uris.size} ภาพพร้อมคำสั่งแล้ว\nกดเปิดเมนูแชร์ แล้วเลือก ChatGPT\nหากภาพเข้าแต่ข้อความว่าง ให้กดค้างที่ช่องข้อความแล้วเลือกวาง คำสั่งคัดลอกไว้แล้ว")
            .setPositiveButton("เปิดเมนูแชร์") { _, _ ->
                try {
                    // Let Android resolve the actual share receiver instead of forcing a package launcher.
                    val chooser = Intent.createChooser(intent, "เลือก ChatGPT เพื่อรับคำสั่งและภาพ").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = intent.clipData
                    }
                    startActivity(chooser)
                    if (lockCreation) { File(project,"command_sent").writeText("sent"); render() }
                } catch (_: android.content.ActivityNotFoundException) {
                    notice("ไม่พบแอปที่รับการแชร์ได้ คำสั่งคัดลอกแล้ว สามารถเปิด ChatGPT แล้ววางและแนบภาพเองได้")
                }
            }.setNegativeButton("ยกเลิก", null).showDirector()
    }

    private fun storyVisualGuide(mode: String): String {
        val master = if (mode == "new")
            "ใช้ CHARACTER MASTER ของเรื่องนี้ตรงรายคน คงใบหน้า ผม รูปร่าง และสัดส่วนร่างกายตามต้นฉบับ"
        else "ใช้ภาพ MASTER ที่แนบตรงรายคน คงใบหน้า ผม รูปร่าง และสัดส่วนร่างกายตามต้นฉบับ"
        return "$master เปลี่ยนเป็นชุดใหม่ตามบทให้เหมาะกับบทบาท เลือกทรงเสื้อผ้าพอดีตัวตามรูปร่างเดิม ระบุชุด สถานที่ สิ่งของ และการกระทำของแต่ละคนให้ชัดเจน ภาพละครไทยสมจริง แนวตั้ง 9:16 เห็นใบหน้าและรูปร่างตั้งแต่ศีรษะถึงต้นขา ปรับระยะให้เห็นการกระทำครบ สีหน้าและสายตาตามเหตุการณ์ รักษาชุดและสถานที่เดิมเมื่อเป็นเหตุการณ์ต่อเนื่อง ภาพไม่มีข้อความหรือตราสัญลักษณ์ สร้างด้วยเครื่องมือสร้างภาพและแสดงภาพจริงในแชต หากสร้างไม่ได้ให้แจ้งตามจริง"
    }
    private fun sceneCastDescription(scene: JSONObject): String {
        val cast = scene.optJSONArray("characters") ?: return ""
        return (0 until cast.length()).joinToString("\n") { i ->
            val person = cast.getJSONObject(i)
            "${person.optString("name")} → ${person.optString("master")} | ชุด: ${person.optString("outfit")}" }
    }
    private fun storySceneCommand(p: JSONObject, scene: JSONObject): String {
        val scenes = rows(p)
        val index = scenes.indexOfFirst { key(it) == key(scene) }
        val previous = scenes.getOrNull(index - 1)?.optString("action").orEmpty()
        val details = sceneCastDescription(scene)
        return "สร้างภาพหนึ่งภาพฉาก ${key(scene)} เรื่อง ${p.getString("title")}\n${storyVisualGuide(p.optString("character_mode","original"))}\n$details\nสถานที่: ${scene.optString("location")}\nเหตุการณ์ต่อเนื่อง: ${scene.optString("continuity_group")}\nรายละเอียดฉาก: ${scene.getString("image_prompt")}\nการกระทำ: ${scene.getString("action")}\nบทพูดและอารมณ์: ${scene.optString("dialogue")}\nเหตุการณ์ก่อนหน้า: $previous\nภาพฉากก่อนหน้าที่แนบใช้เฉพาะความต่อเนื่องของชุด สถานที่ และสิ่งของ ตัวตนแต่ละคนยึด MASTER ต้นฉบับเป็นหลัก สร้างเฉพาะตัวละครในรายการฉากนี้ ตั้งชื่อ ${key(scene)}.png"
    }
    private fun sendSceneImage(p: JSONObject, scene: JSONObject) {
        val cast = scene.optJSONArray("characters")
        val keys = if (cast != null) (0 until cast.length()).mapNotNull { StoryIdentity.keyFor(cast.getJSONObject(it).optString("name")) }
            else StoryIdentity.keysFromPrompt(scene.optString("image_prompt"))
        val original = p.optString("character_mode", "original") == "original"
        if (original && keys.isEmpty()) { notice("ฉากนี้ยังจับคู่ MASTER ไม่ได้ กรุณาอัปเดตบทให้มีชื่อตัวละครใน image_prompt หรือ characters"); return }
        val scenes = rows(p)
        val index = scenes.indexOfFirst { key(it) == key(scene) }
        val group = scene.optString("continuity_group")
        val previous = if (group.isBlank()) null else scenes.take(index).lastOrNull {
            it.optString("continuity_group") == group && image(it).exists()
        }
        val references = previous?.let { listOf(key(it) to image(it)) } ?: emptyList()
        shareText(storySceneCommand(p,scene), selectedMasterKeys = if (original) keys else null, referenceImages = references)
    }
    private fun wholeStoryCommands() {
        val p = plan ?: return notice("นำเข้าแผนเรื่องก่อนครับ")
        val episodes = p.getJSONArray("episodes")
        val commands = (0 until episodes.length()).joinToString("\n\n") { i ->
            val ep = episodes.getJSONObject(i)
            rows(p).filter { it.getInt("ep") == ep.getInt("ep") }.joinToString("\n\n") { s -> storySceneCommand(p,s) } + "\n\n" + coverCommand(p,ep)
        }
        shareText("สร้างและแสดงภาพจริงตามรายการของเรื่อง ${p.getString("title")} เรียงครบทุก EP หนึ่งฉากหนึ่งภาพ เมื่อสร้างภาพฉากสุดท้ายของแต่ละ EP แล้วสร้างปกตามคำสั่งปกแยกต่างหาก หากติดข้อจำกัดให้แจ้งฉากล่าสุดเพื่อทำต่อ\n\n$commands")
    }
    private fun coverNames(p: JSONObject): List<String> {
        val eps = p.getJSONArray("episodes")
        return (0 until eps.length()).flatMap { i -> listOf("9x16", "16x9").map { ratio -> "EP%02d_COVER_%s".format(eps.getJSONObject(i).getInt("ep"), ratio) } }
    }
    private fun coverFile(name: String, folder: File = project) = File(folder, "covers/$name.img")
    private fun coverCommand(p: JSONObject, ep: JSONObject): String {
        val number = ep.getInt("ep")
        val title = ep.optString("title").ifBlank { "ตั้งชื่อตอนภาษาไทยจากเหตุการณ์ของ EP นี้ ใช้ชื่อเดียวกันทั้งสองปก" }
        return "สร้างปกของ EP $number แยก 2 ไฟล์ EP%02d_COVER_9x16.png (9:16) และ EP%02d_COVER_16x9.png (16:9) จัดองค์ประกอบใหม่ตามสัดส่วนแต่ละภาพ ใช้ MASTER รายคน คงใบหน้า ผม รูปร่าง สัดส่วนอก เอว สะโพกตามต้นฉบับ เปลี่ยนชุดและสถานที่ตามตอน สื่อเหตุการณ์เด่น: %s ข้อความ 3 บรรทัดจัดกึ่งกลางในพื้นที่กลางภาพ เว้นขอบซ้ายขวา 10%% ขอบบนล่าง 20%% อ่านชัดบนมือถือ ไม่ทับใบหน้า บรรทัด 1 (เล็กกว่า): %s | บรรทัด 2 (ใหญ่ที่สุด): %s | บรรทัด 3 (ขนาดรอง): EP %02d • %s ปรับขนาดอักษรให้ข้อความครบในแต่ละบรรทัด".format(number,number,ep.getJSONArray("scenes").getJSONObject(ep.getJSONArray("scenes").length()-1).optString("action"),p.optString("channel",originalChannel),p.getString("title"),number,title)
    }
    private fun sendCoverCommands() {
        val p = plan ?: return notice("นำเข้าบทและภาพฉากจาก ZIP ก่อนสร้างปก")
        val eps = p.getJSONArray("episodes")
        choiceMenu("🎨 สร้าง / ปรับปกจากภาพฉาก", listOf("สร้างปกทุก EP") + (0 until eps.length()).map { i ->
            val ep = eps.getJSONObject(i)
            "EP ${ep.getInt("ep")} • ${ep.optString("title")}" }) { index ->
            if (index == 0) {
                val references = (0 until eps.length()).map { i ->
                    rows(p).lastOrNull { it.getInt("ep") == eps.getJSONObject(i).getInt("ep") && image(it).exists() }
                }
                if (references.any { it == null }) notice("ยังมี EP ที่ไม่มีภาพฉาก กรุณานำเข้า ZIP หรือภาพฉากก่อน")
                else sendReferencedCovers(p, references.filterNotNull())
            } else {
                val ep = eps.getJSONObject(index - 1)
                val available = rows(p).filter { it.getInt("ep") == ep.getInt("ep") && image(it).exists() }
                if (available.isEmpty()) notice("EP นี้ยังไม่มีภาพฉาก กรุณานำเข้าภาพก่อน")
                else choiceMenu("เลือกภาพฉากต้นแบบของปก", available.map { "${key(it)} • ${it.optString("action")}" }) { selected ->
                    sendReferencedCovers(p, listOf(available[selected]))
                }
            }
        }
    }
    private fun sendReferencedCovers(p: JSONObject, references: List<JSONObject>) {
        val edits = EditText(this).apply { hint = "สิ่งที่อยากปรับ เช่น สี ตัวอักษร ตำแหน่งข้อความ (เว้นว่างได้)" }
        AlertDialog.Builder(this).setTitle("ปกจากภาพฉากที่นำเข้า")
            .setMessage("แนบภาพฉาก ${references.size} ภาพ ใช้ตัวละคร ชุด และสถานที่จากภาพเหล่านี้ สร้างปก 9:16 และ 16:9")
            .setView(edits).setPositiveButton("ส่งไป ChatGPT") { _, _ ->
                val eps = p.getJSONArray("episodes")
                val commands = references.joinToString("\n\n") { scene ->
                    val ep = (0 until eps.length()).map { eps.getJSONObject(it) }.first { it.getInt("ep") == scene.getInt("ep") }
                    val number = ep.getInt("ep")
                    "สร้างปก EP $number จากภาพ ${key(scene)} ที่แนบ เป็น 2 ไฟล์ EP%02d_COVER_9x16.png และ EP%02d_COVER_16x9.png จัดองค์ประกอบใหม่ให้เหมาะกับแต่ละสัดส่วน ใช้ตัวละครเดิม ใบหน้า ผม รูปร่าง ชุด สถานที่ และสิ่งของจากภาพฉากนี้ รักษาเหตุการณ์: %s รายละเอียดบทอ้างอิง: %s ข้อความไทย 3 บรรทัดในพื้นที่กลางภาพ เว้นขอบซ้ายขวา 10%% บนล่าง 20%% ไม่ทับใบหน้า: บรรทัด 1 ขนาดเล็ก %s | บรรทัด 2 ใหญ่ที่สุด %s | บรรทัด 3 EP %02d • %s ปรับขนาดให้ครบ อ่านชัดบนมือถือ".format(number,number,scene.optString("action"),scene.optString("image_prompt"),p.optString("channel",originalChannel),p.getString("title"),number,ep.optString("title").ifBlank { "ตั้งชื่อตอนภาษาไทยตามเหตุการณ์" })
                }
                shareText("ใช้ภาพฉากที่แนบจากชุดงานที่นำเข้าเป็นต้นแบบปก แก้จากภาพเดิม โดยคงชุดและสถานที่ของภาพนั้น\nสิ่งที่ผู้ใช้ขอปรับ: ${edits.text.toString().ifBlank { "ออกแบบปกให้สวยงาม อ่านชัด" }}\n$commands\nแสดงปกจริงแยกไฟล์ และรวมเฉพาะปกที่สร้างใน ZIP สำหรับนำเข้ากลับ DIRECTOR X หากสร้างไม่ได้ให้แจ้งตามจริง", includeMasters = false, referenceImages = references.map { key(it) to image(it) })
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun planning() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
            background = backdrop(navy, Color.rgb(27,24,57))
        }
        panel.addView(TextView(this).apply {
            text = "🎬 เลือกช่องสร้างหนัง"; textSize = 20f
            setTextColor(gold); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14))
        })
        val dialog = AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setView(panel).create()
        fun option(mainChannel: Boolean, title: String, colors: IntArray, action: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = backdrop(colors[0], colors[1], Color.argb(150,255,255,255))
                clipToOutline = true; elevation = dp(5).toFloat()
                layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(14) }
                isClickable = true; isFocusable = true
                contentDescription = title.replace("\n", " ")
                setOnClickListener { dialog.dismiss(); action() }
            }
            card.addView(ImageView(this).apply {
                setImageResource(if (mainChannel) R.drawable.channel_cast_banner_v2 else R.drawable.channel_genres_banner)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                contentDescription = if (mainChannel) "กวิน รินลดา และมายด์ ภาพครึ่งตัวในฉากเดียวกัน"
                    else "ภาพแนวสยองขวัญ ตลก และโรแมนติก"
            })
            card.addView(TextView(this).apply {
                text = title; textSize = 18f; setTextColor(navy)
                setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                setPadding(dp(10), dp(12), dp(10), dp(14))
                layoutParams = LinearLayout.LayoutParams(-1,-2)
                setSingleLine(false); maxLines = 2
                setAutoSizeTextTypeUniformWithConfiguration(14, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            })
            panel.addView(card)
        }
        option(true, "🎬 สตอรี่หลังบ้าน\nซีรีส์สั้นดราม่าผัวเมีย", intArrayOf(Color.rgb(255,204,86), Color.rgb(255,174,38))) {
            chooseGenre(originalChannel, "original")
        }
        option(false, "🎬 ระบบกำกับละครอัจฉริยะ\nสร้างละครครบทุกแนว", intArrayOf(Color.rgb(73,218,242), Color.rgb(32,169,228))) {
            chooseGenre(prefs.getString("other_channel", "")?.takeIf { it.isNotBlank() } ?: smartDirector, "new")
        }
        panel.addView(TextView(this).apply {
            text = "ยกเลิก"; textSize = 14f; setTextColor(muted)
            gravity = Gravity.CENTER; minHeight = dp(48)
            setOnClickListener { dialog.dismiss() }
        })
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(24), -2)
    }
    private fun chooseGenre(channel: String, mode: String) {
        val choices = if (mode == "original") categories else otherGenres
        val labels = if (mode == "original") choices.mapIndexed { index, category -> "${menuIcon(index)} $category" } else genreLabels
        val title = if (mode == "original") "🎬 เลือกหมวดหมู่\n$channel" else "🎬 $smartDirector\nเลือกแนวละคร • 10 แนว"
        choiceMenu(title, labels) { index ->
            planningForCategory(choices[index],channel,mode)
        }
    }
    private fun planningForCategory(category: String, channel: String, mode: String) {
        val idea = EditText(this).apply { hint = "แนวคิดเรื่อง (เว้นว่างให้คิดอัตโนมัติ)" }
        AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setTitle(category).setMessage("สร้างเรื่องจนจบครบทุก EP\nเว้นแนวคิดว่างได้ ระบบจะคิดเรื่องในหมวดที่เลือก").setView(idea).setPositiveButton("ส่งคำสั่ง") { _, _ ->
            shareText("""วางบทหนัง/ละครให้ช่อง $channel จนจบครบทุก EP ตามแนวที่เลือก
แนวคิด: ${idea.text.toString().ifBlank { "คิดเรื่องใหม่อัตโนมัติ" }}
หมวดหมู่ที่ผู้ใช้เลือก: $category
ก่อนเริ่มสร้างภาพ ต้องแสดงข้อมูลโครงการในแชตให้ครบ ห้ามข้าม: ชื่อช่อง "$channel", ชื่อหมวด "$category", ชื่อเรื่องภาษาไทย, คำโปรยเรื่องสั้น 1–2 ประโยค, จำนวน EP ทั้งหมด และจำนวนซีนรวม
ตามด้วยตารางทุก EP: เลข EP | ชื่อตอนภาษาไทย | คำโปรยตอนสั้น | จำนวนซีน | เวลารวม (จำนวนซีน × 8 วินาที) ต้องระบุจำนวนจริง ไม่ใช้คำว่าแล้วแต่เนื้อหาแทนตัวเลข
ชื่อช่องและหมวดใช้ค่าจากแอปตรงตามนี้ ไม่เปลี่ยนชื่อช่องเอง ตั้งชื่อเรื่องและชื่อทุก EP ใหม่ให้เข้ากับเนื้อหา ${if (mode == "original") "รูปแบบซีรีส์สั้นสตอรี่หลังบ้าน ดราม่าชีวิตคู่ มีจุดเปิดเรื่อง ปมและจุดจบชัดเจนแต่ละ EP พร้อมเส้นเรื่องต่อเนื่อง" else "รูปแบบหนัง/ละครหลายแนว ให้โทนเรื่อง จังหวะ ความสัมพันธ์ และบทสรุปตรงกับแนวที่เลือก ถ้าเรื่องจบในตอนเดียวใช้ 1 EP ถ้าต้องต่อเนื่องแบ่งหลาย EP อย่างมีเหตุผล"}
วางบทครบทุก EP ก่อนสร้างภาพ เก็บข้อมูลสรุปเดียวกันใน story.json และตรวจตัวเลขให้ตรงกับจำนวนสมาชิก episodes และ scenes จริง ห้ามส่ง JSON ตัวอย่างที่มีแค่ฉากเดียวแทนบทเต็ม
ตั้งชื่อเรื่องเป็นภาษาไทยเท่านั้น ใส่ชื่อภาษาไทยในค่า title ของ story.json และตั้งชื่อไฟล์ ZIP เป็นชื่อเรื่องภาษาไทย.zip ห้ามใช้ชื่อหมวดภาษาอังกฤษเป็นชื่อเรื่องหรือชื่อ ZIP
สร้างชื่อเรื่องและพล็อตให้ตรงหมวดนี้ ใช้ค่า category ใน story.json เป็น "$category" ตรงทุกตัวอักษร
ประเมินจำนวน EP และฉากตามเนื้อหาเอง สำหรับ Flow / Veo 3.1 Lite ทุกฉาก 8 วินาที แนวตั้ง 9:16 Locked-off static camera ${if (mode == "original") "ตัวละครหลัก กวิน รินลดา มายด์ อ้างอิง MASTER ของแต่ละคนเพื่อคงใบหน้า ผม รูปร่าง และสัดส่วนร่างกายตาม MASTER รายคน" else "สร้างชื่อและใบหน้าตัวละครใหม่สำหรับเรื่องนี้ ใช้ภาพที่แนบอ้างอิงเฉพาะรูปร่างและสัดส่วนของตัวละครผู้ใหญ่ ไม่ใช้ชื่อหรือใบหน้าเดิม สร้างภาพ MASTER ใหม่ก่อนสร้างฉาก ตั้งชื่อ CHARACTER_01.png, CHARACTER_02.png ตามจำนวนที่ต้องใช้ แล้วใช้ใบหน้าใหม่เดิมอย่างต่อเนื่องทุกฉาก เก็บรายละเอียดตัวละครและชื่อไฟล์ MASTER ใน bible"}\n${storyVisualGuide(mode)}\nใช้วิธีเดียวกับการทดสอบสร้างภาพ: MASTER รายคนเป็นต้นฉบับของตัวละคร ชุด สถานที่และการกระทำเป็นข้อมูลใหม่ตามบท เขียน image_prompt พร้อมใช้ของแต่ละฉากอย่างกระชับ: ตัวละครและ MASTER รายคน → ชุดรายคน → สถานที่และสิ่งของ → การกระทำ → สีหน้าและสายตา ระบุชนิด สี และทรงชุดพอดีตัวให้เหมาะกับบทบาท รวมทั้งสถานที่จริงในแต่ละฉากเพื่อสร้างซ้ำได้ ชุดเปลี่ยนตามวันหรือเหตุการณ์ และคงเดิมในเหตุการณ์ต่อเนื่อง ใช้คำอธิบายเชิงบวกสั้น ๆ โดยไม่เพิ่มรายการข้อห้ามยาวซ้ำทุกฉาก เหตุการณ์ทุกฉากมาจากบทของเรื่องนี้ จบปมครบทุกเรื่อง ผู้พูดแต่ละฉากชัดเจน
แผนความต่อเนื่องของตัวละคร: เก็บชื่อ → ไฟล์ MASTER ตายตัวใน bible: กวิน → GAWIN_MASTER, รินลดา → RINLADA_MASTER, มายด์ → MIND_MASTER ใช้นามสกุล .png/.jpg/.webp ตามไฟล์ภาพที่แนบ โดยชื่อฐานจับคู่ตรงคน สำหรับช่องตัวละครใหม่ใช้ชื่อใหม่ → CHARACTER_01.png ตามที่สร้าง แต่ละฉากมี characters เฉพาะคนที่ปรากฏ พร้อม name, master, outfit ระบุ location และ continuity_group เหตุการณ์เดียวกันใช้รหัสกลุ่มเดียวกัน ชุดและรายละเอียดสถานที่เหมือนเดิม เมื่อเปลี่ยนวันหรือเหตุการณ์จึงเปลี่ยนกลุ่ม
ก่อนใช้เครื่องมือสร้างภาพแต่ละฉาก อ้างอิง MASTER ต้นฉบับตรงรายคนอีกครั้ง ใช้ภาพฉากก่อนหน้าในกลุ่มเดียวกันเป็นตัวช่วยเรื่องชุด สถานที่ และสิ่งของ โดยตัวตนยึด MASTER เป็นหลัก ตรวจชื่อและชุดของแต่ละคนก่อนส่งสร้าง ไม่ใช้ภาพที่คลาดเคลื่อนแทน MASTER หากขาดต้นฉบับให้แจ้งก่อนสร้าง
ส่งไฟล์ story.json เป็น JSON ไม่มี markdown โครงสร้าง:
{"schema_version":3,"channel":${JSONObject.quote(channel)},"character_mode":"$mode","title":"ชื่อเรื่อง","category":${JSONObject.quote(category)},"tagline":"คำโปรยเรื่อง","episode_count":1,"scene_count":1,"bible":"ความสัมพันธ์และพล็อต","episodes":[{"ep":1,"title":"ชื่อตอนภาษาไทย","tagline":"คำโปรยตอน","scene_count":1,"scenes":[{"scene":1,"characters":[{"name":"ชื่อตัวละครที่อยู่ในฉาก","master":"ชื่อไฟล์ MASTER ตรงรายคน","outfit":"ชนิด สี และรายละเอียดชุด"}],"location":"สถานที่และสิ่งของหลัก","continuity_group":"DAY01_LOCATION01_EVENT01","action":"เหตุการณ์","dialogue":"บทพูดภาษาไทย","image_prompt":"ตัวละครและ MASTER ตรงรายคน คงใบหน้า ผม รูปร่างตามต้นฉบับ; ชุดใหม่รายคนระบุชนิดสีและทรง; สถานที่และสิ่งของ; การกระทำ สีหน้าและสายตาตามบท; ภาพละครไทยสมจริง 9:16 เห็นการกระทำครบ ไม่มีข้อความ","flow_prompt":"Flow / Veo 3.1 Lite: English prompt + Thai speech, 8 seconds, 9:16, locked-off static camera"}]}]}
การส่งชุดงานสุดท้าย: รวม story.json ภาพฉาก และปกใน ZIP ชื่อเรื่องภาษาไทย ใช้ MASTER เป็นภาพอ้างอิงระหว่างสร้าง แต่ไม่ใส่ไฟล์ภาพ MASTER หรือโฟลเดอร์ MASTER/characters ลงใน ZIP ที่ส่งกลับ คำสั่ง Flow รวมหนึ่งไฟล์ต่อ EP: Flow/EP01_FLOW.txt, Flow/EP02_FLOW.txt ตามจำนวนตอนจริง แต่ละไฟล์มีชื่อตอนและทุกซีนเรียงเลข แต่ละซีนแสดงเลขฉาก เหตุการณ์ บทพูด และคำสั่ง Flow แยกเป็นบล็อกสำหรับคัดลอกทีละซีน ไม่แยกเป็นไฟล์ Flow รายซีน คำสั่ง flow_prompt ใน story.json ยังต้องครบทุกซีนตามเดิม
ตัวเลข 1 ในตัวอย่างเป็นเพียงรูปแบบ ต้องแทนด้วยจำนวนจริงของบทเต็ม เพิ่ม EP และฉากเท่าที่เรื่องต้องใช้ เรียงเลขจาก 1 ไม่กำหนดจำนวนตายตัว หลังวางบททั้งเรื่อง ส่งไฟล์ story.json แล้วใช้เครื่องมือสร้างภาพและแสดงภาพจริงในแชตแยกหนึ่งฉากหนึ่งไฟล์ตามลำดับครบทุก EP จนจบ ตั้งชื่อ EP01_SC001.png ตามเลข EP และฉาก ไม่ทำภาพรวมหลายฉาก ${if (mode == "original") "ใช้ MASTER ที่แนบให้ตรงรายคน" else "ใช้ CHARACTER MASTER ใหม่ที่สร้างสำหรับเรื่องนี้ตรงรายคน"} พร้อมคำสั่งสำหรับ Flow / Veo 3.1 Lite ทุกฉาก ผู้ใช้ต้องการบทและภาพพร้อมนำไปทำวิดีโอ
หลังสร้างฉากสุดท้ายของแต่ละ EP ให้สร้างปกแยก 2 ไฟล์ทันที: EP01_COVER_9x16.png ขนาด 9:16 และ EP01_COVER_16x9.png ขนาด 16:9 เปลี่ยนเลขตาม EP จัดองค์ประกอบแต่ละสัดส่วนใหม่ ใช้ตัวละครจาก MASTER รายคน สื่อเหตุการณ์เด่นของตอนนั้น ข้อความภาษาไทย 3 บรรทัดจัดกึ่งกลางในพื้นที่กลางภาพ เว้นขอบซ้ายขวาอย่างน้อย 10% และขอบบนล่าง 20%: บรรทัด 1 ชื่อช่อง (เล็กกว่า), บรรทัด 2 ชื่อเรื่อง (ใหญ่ที่สุด), บรรทัด 3 EP 01 • ชื่อตอน (ขนาดรอง) ปรับขนาดอักษรให้ครบทั้งบรรทัด อ่านชัดบนมือถือ ไม่ทับใบหน้า ชื่อเรื่องและชื่อตอนต้องตรง story.json รวมปกทุก EP ใน ZIP ชื่อเรื่องภาษาไทยด้วย""", lockCreation = true, useNewMasters = false)
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun sceneCommands() {
        val p = plan ?: return notice("นำเข้าแผนเรื่องก่อนครับ")
        val scenes = rows(p)
        choiceMenu("🎬 เลือกฉากเพื่อส่งคำสั่งภาพ", scenes.map { "🎞️ " + key(it) }) { index ->
            val s = scenes[index]
            sendSceneImage(p,s)
        }
    }
    @Deprecated("Android legacy back callback")
    override fun onBackPressed() { if (!importing) super.onBackPressed() }
    private fun displayName(uri: Uri): String = if (uri.scheme == "file") File(uri.path!!).name else contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null,null,null)?.use { if (it.moveToFirst()) it.getString(0) else "" } ?: ""
    private fun importNewMasters(uris: List<Uri>, folder: File = project, story: JSONObject? = plan) {
        if (uris.isEmpty()) return
        require(story?.optString("character_mode") == "new") { "ภาพ CHARACTER ใช้กับช่องอื่นเท่านั้น" }
        val dir = File(folder, "masters").apply { mkdirs() }
        for (uri in uris) {
            val index = Regex("^CHARACTER_(\\d+)\\.", RegexOption.IGNORE_CASE).find(displayName(uri))!!.groupValues[1]
            val temporary = File(dir, "CHARACTER_$index.tmp")
            contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์ MASTER ไม่ใช่ภาพ" }
            require(temporary.renameTo(File(dir, "CHARACTER_$index.png"))) { "บันทึก MASTER ไม่สำเร็จ" }
        }
    }
    private fun importImages(uris: List<Uri>, confirmedOrder: Boolean = false) {
        val scenes = rows(plan!!)
        val assignments = linkedMapOf<Uri,JSONObject>()
        val unknown = mutableListOf<Uri>()
        for (uri in uris) {
            val match = StoryFiles.sceneNumber(displayName(uri))
            val found = match?.let { m -> scenes.find { it.getInt("ep") == m.first && it.getInt("scene") == m.second } }
            if (match != null && found == null) return notice("${displayName(uri)} ไม่ตรงกับฉากในเรื่องนี้ กรุณาเลือกภาพของเรื่องปัจจุบัน")
            if (found == null) unknown.add(uri) else assignments[uri] = found
        }
        if (assignments.values.map { key(it) }.distinct().size != assignments.size) return notice("มีไฟล์ชื่อฉากซ้ำในชุดที่เลือก กรุณาเลือกหนึ่งภาพต่อฉากครับ")
        val occupied = assignments.values.map { key(it) }.toSet()
        val available = scenes.filter { key(it) !in occupied && !image(it).exists() }
        if (unknown.size > available.size) {
            val named = uris.filter { StoryFiles.sceneNumber(displayName(it)) != null }
            val skipped = unknown.joinToString("\n") { displayName(it) }
            if (named.isEmpty()) return notice("ยังจับคู่ภาพไม่ได้ กรุณาใช้ชื่อ EP01_SC001.png หรือโฟลเดอร์ EP01/SC001.png\nไฟล์ที่ต้องตรวจ:\n$skipped")
            AlertDialog.Builder(this).setTitle("พบภาพอื่นในชุดงาน")
                .setMessage("นำเข้าภาพที่ตรงเลขฉากได้ ${named.size} ไฟล์ ส่วน ${unknown.size} ไฟล์ต่อไปนี้ยังจับคู่ไม่ได้ จะเว้นไว้เพื่อไม่ใส่ผิดฉาก:\n$skipped")
                .setPositiveButton("นำเข้าภาพที่จับคู่ได้") { _, _ -> importImages(named) }
                .setNegativeButton("ยกเลิก", null).showDirector()
            return
        }
        unknown.forEachIndexed { i, uri -> assignments[uri] = available[i] }
        if (unknown.isNotEmpty() && !confirmedOrder) {
            val list = uris.joinToString("\n") { "${displayName(it)} → ${key(assignments.getValue(it))}" }
            val content = TextView(this).apply { text = "บางภาพไม่มีเลขฉาก แอปเสนอเรียงตามลำดับไฟล์ที่เลือก กรุณาตรวจให้ตรงก่อนนำเข้า\n\n$list"; setPadding(dp(16),dp(12),dp(16),dp(12)) }
            AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setTitle("ตรวจลำดับภาพครั้งเดียว").setView(ScrollView(this).apply { addView(content) }).setPositiveButton("ลำดับถูกต้อง") { _,_ -> importImages(uris,true) }.setNegativeButton("ยกเลิก",null).showDirector()
            return
        }
        val importFolder = project
        importing = true
        val remaining = uris.toMutableList()
        fun next() {
            if (remaining.isEmpty()) { importing = false; render(); val missing = rows(plan!!).count { !image(it).exists() }; val missingCovers = coverNames(plan!!).count { !coverFile(it).exists() }; notice(if (missing == 0 && missingCovers == 0) "รับภาพและปกครบแล้ว ขั้นตอน 3: บันทึกชุดงานล่าสุดได้เลย" else if (missing == 0) "ภาพฉากครบแล้ว ยังขาดปก $missingCovers ไฟล์ กดสร้างปกทุก EP แล้วรับไฟล์กลับในขั้นตอน 2" else "รับภาพแล้ว ยังขาด $missing ฉาก เพิ่มภาพต่อในขั้นตอน 2 ได้เลย"); return }
            val uri = remaining.removeAt(0)
            val name = displayName(uri)
            val match = StoryFiles.sceneNumber(name)
            val found = match?.let { m -> scenes.find { it.getInt("ep") == m.first && it.getInt("scene") == m.second } }
            fun save(s: JSONObject) {
                Thread {
                    try {
                        val target = File(importFolder,"images/${key(s)}.img"); target.parentFile!!.mkdirs()
                        val tmp = File(target.parentFile,"${target.name}.tmp")
                        contentResolver.openInputStream(uri)!!.use { src -> tmp.outputStream().use { src.copyTo(it) } }
                        require(tmp.length() > 0) { "ไฟล์ภาพว่าง" }
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(tmp.path,bounds)
                        require(bounds.outWidth > 0) { "อ่านภาพไม่ได้" }
                        check(tmp.renameTo(target)) { "บันทึกภาพไม่ได้" }
                        runOnUiThread { next() }
                    } catch (e: Exception) { runOnUiThread { notice("$name: ${e.message}"); next() } }
                }.start()
            }
            fun assign(s: JSONObject) {
                if (image(s).exists()) AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage("${key(s)} มีภาพแล้ว แทนด้วย $name หรือไม่?").setPositiveButton("แทนภาพ") { _,_ -> save(s) }.setNegativeButton("ข้าม") { _,_ -> next() }.setCancelable(false).showDirector()
                else save(s)
            }
            if (assignments[uri] != null) assign(assignments.getValue(uri))
            else choiceMenu("🧩 จับคู่ภาพ: $name", scenes.map { "🎞️ " + key(it) + if (image(it).exists()) " ✓" else " • ขาดภาพ" }, cancel = "⏭️ ข้าม", cancelable = false, onCancel = { next() }) { i -> assign(scenes[i]) }
        }
        next()
    }
    private fun review() {
        val p = plan ?: return notice("ยังไม่มีแผนเรื่อง")
        val scenes = rows(p)
        choiceMenu("🔎 ตรวจทุก EP", scenes.map { "🎞️ " + key(it) + if (image(it).exists()) " ✓" else " • ขาดภาพ" }) { i ->
            val s = scenes[i]
            val view = LinearLayout(this).apply { orientation = 1; setPadding(20,20,20,20) }
            if (image(s).exists()) view.addView(ImageView(this).apply { setImageURI(Uri.fromFile(image(s))); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(-1,500) })
            view.addView(TextView(this).apply { text = "${s.getString("action")}\n${s.optString("dialogue")}\n\n${s.getString("flow_prompt")}" })
            AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setTitle(key(s)).setView(ScrollView(this).apply { addView(view) }).setPositiveButton("ปิด",null).showDirector()
        }
    }
    private fun export(uri: Uri) {
        val p = plan!!
        contentResolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            put("story.json",p.toString(2).toByteArray())
            put("README.txt","${p.getString("title")}\n${p.getString("category")}\nนำภาพฉากและคำสั่งใน Flow/EPxx_FLOW.txt ไปใช้ใน Flow • 8 วินาที • 9:16".toByteArray())
            for (name in coverNames(p)) {
                val file = coverFile(name)
                require(file.exists()) { "ยังขาดปก $name" }
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val ext = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                put("covers/$name.$ext",file.readBytes())
            }
            val episodes = p.getJSONArray("episodes")
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                val number = ep.getInt("ep")
                val scenes = rows(p).filter { it.getInt("ep") == number }
                val commands = StoryFiles.episodeFlow(number, ep.optString("title"), scenes.map {
                    Triple(key(it), "${it.getString("action")}\n${it.optString("dialogue")}", it.getString("flow_prompt"))
                })
                put("Flow/EP%02d_FLOW.txt".format(number), commands.toByteArray())
            }
            for (s in rows(p)) {
                val path = "EP%02d/%s".format(s.getInt("ep"),key(s))
                val file = image(s)
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val extension = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                zip.putNextEntry(ZipEntry("$path.$extension")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()

            }
        } }
    }
}


