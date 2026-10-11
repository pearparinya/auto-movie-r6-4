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
        AlertDialog.Builder(this).setTitle("ภาพต้นฉบับตัวละคร")
            .setItems(names.map { (key, name) ->
                "$name • " + if (File(filesDir, "character_masters/$key.master").exists()) "มีภาพแล้ว · แตะเพื่อเปลี่ยน" else "เพิ่มภาพ"
            }.toTypedArray()) { _, index ->
                masterSlot = names[index].first
                masterPicker.launch(arrayOf("image/*"))
            }.setNegativeButton("ปิด", null).show()
    }
    private val otherGenres = listOf("ดราม่าชีวิต", "ครอบครัว", "โรแมนติก", "ตลก", "สืบสวน", "ระทึกขวัญ", "สยองขวัญ", "แฟนตาซี", "แอ็กชัน", "อบอุ่นหัวใจ")
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
        if (uris.isNotEmpty()) {
            val plans = uris.filter { displayName(it).endsWith(".json",ignoreCase = true) }
            val masters = uris.filter { contentResolver.getType(it)?.startsWith("image/") == true && Regex("^CHARACTER_\\d+\\.", RegexOption.IGNORE_CASE).containsMatchIn(displayName(it)) }
            val pictures = uris.filter { contentResolver.getType(it)?.startsWith("image/") == true && it !in masters }
            if (plans.size > 1) notice("เลือกไฟล์บท story.json ของเรื่องเดียวเท่านั้นครับ")
            else if (plans.isEmpty()) {
                if (plan == null) notice("เลือกไฟล์บท story.json พร้อมภาพของเรื่องก่อนครับ")
                else {
                    try { require(masters.isNotEmpty() || pictures.isNotEmpty()) { "ไม่พบไฟล์ภาพหรือ story.json" }; importNewMasters(masters); if (pictures.isNotEmpty()) importImages(pictures) else notice("รับภาพตัวละครแล้ว") }
                    catch (e: Exception) { notice("รับภาพตัวละครไม่สำเร็จ: ${e.message}") }
                }
            } else {
                val dialog = AlertDialog.Builder(this).setMessage("กำลังรับบททั้งเรื่อง…").setCancelable(false).show()
                Thread {
                    try {
                        val candidate = contentResolver.openInputStream(plans[0])!!.bufferedReader().use { JSONObject(it.readText().removePrefix("\uFEFF")) }
                        validate(candidate)
                        val folder = File(base,UUID.randomUUID().toString()).apply { mkdirs() }
                        File(folder,"story.json").writeText(candidate.toString(2))
                        importNewMasters(masters, folder, candidate)
                        prefs.edit().putString("project",folder.name).commit()
                        runOnUiThread { dialog.dismiss(); render(); if (pictures.isNotEmpty()) importImages(pictures) else notice("รับบทแล้ว เมื่อได้ภาพให้กดรับบทและภาพอีกครั้ง เลือกภาพทั้งหมดได้เลยครับ") }
                    } catch (e: Exception) { runOnUiThread { dialog.dismiss(); notice("รับบทไม่สำเร็จ: ${e.message}") } }
                }.start()
            }
        }
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
        for (i in 0 until eps.length()) {
            val ep = eps.getJSONObject(i)
            require(ep.getInt("ep") == i + 1) { "EP ต้องเรียงจาก 1" }
            val scenes = ep.getJSONArray("scenes")
            require(scenes.length() > 0) { "EP ไม่มีฉาก" }
            for (j in 0 until scenes.length()) {
                val s = scenes.getJSONObject(j)
                require(s.getInt("scene") == j + 1) { "ฉากต้องเรียงจาก 1 ภายในแต่ละ EP" }
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
        root = LinearLayout(this).apply { orientation = 1; setPadding(dp(14),dp(4),dp(14),dp(8)) }
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
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = backdrop(Color.rgb(43,38,81), navy, Color.rgb(141,112,62)); elevation = dp(6).toFloat()
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { marginEnd = dp(12) }
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
        brandLine("DIRECTOR X 1.13", 24, 16, gold, true, 1)
        brandLine("ผู้กำกับอัจฉริยะ", 20, 16, ice, true, 3)
        brandLine("รุ่น 013 • พร้อมใช้ใน Flow", 12, 10, muted, false, 1)
        header.addView(heading)
        root.addView(header)
        label("สร้างละครได้ทุกแนว • เลือกช่องก่อนเริ่ม", 13)
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
            summaryText("🎬 พร้อมสร้างเรื่องใหม่ • ครบทุก EP",14f,ice,true)
        } else {
            val scenes = rows(p); val count = scenes.count { image(it).exists() }
            summaryText(p.getString("title"),20f,ice,true)
            summaryText(p.optString("channel", originalChannel),12f,muted)
            summaryText(p.getString("category"),12f,muted)
            summaryText("${p.getJSONArray("episodes").length()} EP  •  ${scenes.size} ฉาก  •  ภาพ $count/${scenes.size}",14f,gold,true)
            summary.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max = scenes.size; progress = count
                progressTintList = ColorStateList.valueOf(Color.rgb(35,219,195))
                progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(58,53,92))
                layoutParams = LinearLayout.LayoutParams(-1,dp(8)).apply { topMargin = dp(8) }
            })
        }
        root.addView(summary)
        label("สร้างหนึ่งเรื่องจนจบ • รับภาพ • พร้อมใช้ใน Flow", 14, true)
        stepCard(1, "🎬", "สร้างหนังหนึ่งเรื่อง", "เลือกช่องและแนว • สร้างบทครบทุก EP") { planning() }
        stepCard(2, "🎨", "รับบทและภาพทั้งหมด", "นำเข้า story.json พร้อมภาพ • จัดฉากอัตโนมัติ") { packagePicker.launch(arrayOf("*/*")) }
        stepCard(3, "📦", "ดาวน์โหลดชุด Flow", "ZIP ทั้งเรื่อง • ภาพ + บทพูด + คำสั่ง Flow") {
            val current = plan
            if (current == null) notice("ยังไม่มีบทและภาพ กดรับบทและภาพทั้งหมดก่อนครับ")
            else {
                val missing = rows(current).filter { !image(it).exists() }
                if (missing.isNotEmpty()) notice("ยังขาดภาพ ${missing.size} ฉาก: ${missing.joinToString { key(it) }}\nกดรับบทและภาพทั้งหมดเพื่อเพิ่มภาพครับ")
                else zipPicker.launch("DIRECTOR-X-${System.currentTimeMillis()}.zip")
            }
        }
        button("↻ Update DIRECTOR X",false) { updater.check() }
        button("⚙ ตัวละคร • ตัวเลือก",false) { toolsMenu() }
    }
    private fun stepCard(number: Int, icon: String, title: String, detail: String, action: () -> Unit) {
        val colors = when (number) {
            1 -> intArrayOf(Color.rgb(255,218,112),Color.rgb(255,173,34))
            2 -> intArrayOf(Color.rgb(46,189,252),Color.rgb(30,101,221))
            else -> intArrayOf(Color.rgb(182,107,255),Color.rgb(107,56,204))
        }
        val ink = if (number == 1) navy else ice
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(14),dp(12),dp(14))
            background = backdrop(colors[0],colors[1],Color.argb(110,255,255,255))
            elevation = dp(5).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(8); bottomMargin = dp(8) }
            isClickable = true; isFocusable = true
            val attribute = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground,attribute,true)
            foreground = getDrawable(attribute.resourceId)
            contentDescription = "ขั้นตอน $number $title $detail"
            setOnClickListener { if (!importing) action() else Toast.makeText(this@StudioActivity,"กำลังนำเข้าภาพ กรุณารอสักครู่",Toast.LENGTH_SHORT).show() }
        }
        card.addView(TextView(this).apply {
            text = icon; textSize = 30f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44),dp(52))
        })
        val words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = dp(6) }
        }
        words.addView(TextView(this).apply {
            text = "$number  $title"; textSize = 18f; setTypeface(null,Typeface.BOLD); setTextColor(ink)
        })
        words.addView(TextView(this).apply {
            text = detail; textSize = 11f; setTextColor(ink); setPadding(0,dp(5),0,0)
        })
        card.addView(words)
        card.addView(TextView(this).apply {
            text = "›"; textSize = 27f; setTextColor(ink); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(18),-2)
        })
        root.addView(card)
    }
    private fun toolsMenu() {
        AlertDialog.Builder(this).setTitle("ตัวเลือก").setItems(arrayOf("ภาพต้นฉบับตัวละคร", "ดูภาพและบทในเรื่องนี้", "เลือกเรื่องที่บันทึก", "ส่งคำสั่งภาพทั้งเรื่อง", "วิธีใช้งาน")) { _, index ->
            when (index) {
                0 -> characterMasters()
                1 -> review()
                2 -> {
                    val folders = base.listFiles()?.filter { File(it,"story.json").exists() } ?: emptyList()
                    AlertDialog.Builder(this).setTitle("เรื่องของคุณ").setItems(folders.map { JSONObject(File(it,"story.json").readText()).getString("title") }.toTypedArray()) { _, i -> prefs.edit().putString("project",folders[i].name).apply(); render() }.show()
                }
                3 -> wholeStoryCommands()
                4 -> notice("1. ตั้งภาพต้นฉบับตัวละคร แล้วกดสร้างหนังหนึ่งเรื่อง เลือกช่องและแนวละคร ส่งไป ChatGPT ช่องเดิมใช้ตัวละครเดิม ช่องอื่นสร้างใบหน้าใหม่โดยอ้างอิงรูปร่าง และใช้ MASTER ใหม่ต่อเนื่องทั้งเรื่อง\n2. บันทึก story.json และภาพแต่ละฉากจาก ChatGPT สำหรับช่องอื่นบันทึกภาพ CHARACTER_01.png เป็นต้นฉบับใหม่ด้วย กดรับบทและภาพทั้งหมด เลือกไฟล์ได้พร้อมกัน\n3. เมื่อภาพครบ ดาวน์โหลด ZIP และนำภาพกับคำสั่งไปใช้ใน Flow / Veo 3.1 Lite 8 วินาที\n\nChatGPT เป็นผู้สร้างบทและภาพ แอปจัดไฟล์และตรวจฉากที่ขาด ไม่ได้สร้างภาพเองผ่าน Plus อัตโนมัติ")
            }
        }.show()
    }

    private fun label(value: String, size: Int, bold: Boolean = false) {
        root.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(if (bold) gold else muted)
            setPadding(dp(4),dp(4),dp(4),dp(4)); if (bold) setTypeface(null, Typeface.BOLD)
        })
    }
    private fun button(value: String, primary: Boolean = true, action: () -> Unit) {
        val colors = when {
            !primary -> intArrayOf(Color.rgb(38,36,76),Color.rgb(27,28,59))
            value.startsWith("①") -> intArrayOf(Color.rgb(255,204,86),Color.rgb(255,174,38))
            value.startsWith("②") -> intArrayOf(Color.rgb(42,153,246),Color.rgb(27,101,211))
            value.startsWith("③") -> intArrayOf(Color.rgb(164,96,246),Color.rgb(111,65,209))
            value.startsWith("④") -> intArrayOf(Color.rgb(40,220,164),Color.rgb(10,183,133))
            value.startsWith("⑤") -> intArrayOf(Color.rgb(35,205,216),Color.rgb(14,163,182))
            else -> intArrayOf(Color.rgb(255,203,81),Color.rgb(255,166,39))
        }
        val darkText = primary && !value.startsWith("②") && !value.startsWith("③")
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
    private fun notice(text: String) { AlertDialog.Builder(this).setMessage(text).setPositiveButton("ตกลง", null).show() }
    private fun background(block: () -> Unit) {
        val dialog = AlertDialog.Builder(this).setMessage("กำลังจัดไฟล์…").setCancelable(false).show()
        Thread {
            try { block(); runOnUiThread { dialog.dismiss(); render(); notice("เสร็จเรียบร้อย") } }
            catch (e: Exception) { runOnUiThread { dialog.dismiss(); notice("ดำเนินการไม่สำเร็จ: ${e.message}") } }
        }.start()
    }
    private fun shareText(text: String, useNewMasters: Boolean = plan?.optString("character_mode") == "new") {
        val uris = arrayListOf<Uri>()
        val sources = if (useNewMasters) File(project, "masters").listFiles()?.toList() ?: emptyList()
            else listOf("gawin", "rinlada", "mind").map { File(filesDir, "character_masters/$it.master") }
        for (source in sources) {
            val name = if (useNewMasters) source.nameWithoutExtension else source.nameWithoutExtension + "_MASTER"
            if (source.exists()) {
                val out = File(cacheDir,"character_master_exports/${name.uppercase()}.png")
                out.parentFile!!.mkdirs(); source.copyTo(out, overwrite = true)
                uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",out))
            }
        }
        val intent = Intent(if (uris.isEmpty()) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (uris.isEmpty()) "text/plain" else "image/*"
            putExtra(Intent.EXTRA_TEXT,text)
            if (uris.isNotEmpty()) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                clipData = ClipData.newUri(contentResolver,"MASTER",uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(Intent.createChooser(intent,"ส่งไป ChatGPT"))
    }
    private fun wholeStoryCommands() {
        val p = plan ?: return notice("นำเข้าแผนเรื่องก่อนครับ")
        val commands = rows(p).joinToString("\n\n") { s -> "${key(s)}.png\n${s.getString("image_prompt")}" }
        shareText("เรื่อง ${p.getString("title")}\n${if (p.optString("character_mode") == "new") "ใช้ CHARACTER MASTER ใหม่ของเรื่องนี้ คงใบหน้าใหม่และรูปร่างตรงรายคน ถ้ายังไม่ได้แนบ MASTER ใหม่ ให้ขอไฟล์ CHARACTER ก่อนสร้างภาพ" else "ใช้ภาพ MASTER ตามชื่อของแต่ละคน คงใบหน้า ผม รูปร่าง"} กำหนดชุดตามคำสั่งแต่ละฉาก ภาพ 9:16 หนึ่งฉากหนึ่งภาพ ไม่ทำภาพรวมหลายฉาก เรียงสร้างตามรายการครบทุก EP หากติดข้อจำกัดให้แจ้งฉากล่าสุดเพื่อทำต่อ ไม่ข้ามฉาก\n\n$commands")
    }
    private fun planning() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
            background = backdrop(navy, Color.rgb(27,24,57))
        }
        panel.addView(TextView(this).apply {
            text = "🎬 เลือกช่องสร้างเรื่อง"; textSize = 20f
            setTextColor(gold); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14))
        })
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        fun option(title: String, detail: String, colors: IntArray, action: () -> Unit) {
            panel.addView(TextView(this).apply {
                text = title; textSize = 17f; setTextColor(navy)
                setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
                setSingleLine(true)
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 12, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                setPadding(dp(14), dp(12), dp(14), dp(12)); minHeight = dp(54)
                background = backdrop(colors[0], colors[1])
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setOnClickListener { dialog.dismiss(); action() }
            })
            panel.addView(TextView(this).apply {
                text = detail; textSize = 12f; setTextColor(muted)
                setPadding(dp(4), dp(5), dp(4), dp(16))
            })
        }
        option("① 🏠 สตอรี่หลังบ้าน", "ดราม่าผัวเมีย • ใช้ตัวละครเดิม", intArrayOf(Color.rgb(255,204,86), Color.rgb(255,174,38))) {
            chooseGenre(originalChannel, "original")
        }
        option("② 🎭 ช่องอื่น • ทุกแนว", "ตั้งชื่อช่องเอง • สร้างใบหน้าตัวละครใหม่", intArrayOf(Color.rgb(73,218,242), Color.rgb(32,169,228))) {
            val name = EditText(this).apply {
                hint = "ชื่อช่องของคุณ"; setText(prefs.getString("other_channel", ""))
            }
            AlertDialog.Builder(this).setTitle("ชื่อช่องอื่น").setView(name)
                .setPositiveButton("เลือกแนวละคร") { _, _ ->
                    val channel = name.text.toString().trim().ifBlank { "ช่องละครของฉัน" }
                    prefs.edit().putString("other_channel", channel).apply()
                    chooseGenre(channel, "new")
                }.setNegativeButton("ยกเลิก", null).show()
        }
        panel.addView(TextView(this).apply {
            text = "ยกเลิก"; textSize = 14f; setTextColor(muted)
            gravity = Gravity.CENTER; minHeight = dp(48)
            setOnClickListener { dialog.dismiss() }
        })
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    }
    private fun chooseGenre(channel: String, mode: String) {
        val choices = if (mode == "original") categories else otherGenres
        AlertDialog.Builder(this).setTitle("เลือกแนว • $channel")
            .setItems(choices.toTypedArray()) { _, index -> planningForCategory(choices[index], channel, mode) }
            .setNegativeButton("ยกเลิก", null).show()
    }
    private fun planningForCategory(category: String, channel: String, mode: String) {
        val idea = EditText(this).apply { hint = "แนวคิดเรื่อง (เว้นว่างให้คิดอัตโนมัติ)" }
        AlertDialog.Builder(this).setTitle(category).setMessage("สร้างเรื่องจนจบครบทุก EP\nเว้นแนวคิดว่างได้ ระบบจะคิดเรื่องในหมวดที่เลือก").setView(idea).setPositiveButton("ส่งคำสั่ง") { _, _ ->
            shareText("""วางเรื่องละครให้ช่อง $channel จนจบทุก EP ในชุดเดียว
แนวคิด: ${idea.text.toString().ifBlank { "คิดเรื่องใหม่อัตโนมัติ" }}
หมวดหมู่ที่ผู้ใช้เลือก: $category
สร้างชื่อเรื่องและพล็อตให้ตรงหมวดนี้ ใช้ค่า category ใน story.json เป็น "$category" ตรงทุกตัวอักษร
ประเมินจำนวน EP และฉากตามเนื้อหาเอง สำหรับ Flow / Veo 3.1 Lite ทุกฉาก 8 วินาที แนวตั้ง 9:16 Locked-off static camera ${if (mode == "original") "ตัวละครหลัก กวิน รินลดา มายด์ อ้างอิง MASTER ของแต่ละคนเพื่อคงใบหน้า ผม รูปร่าง" else "สร้างชื่อและใบหน้าตัวละครใหม่สำหรับเรื่องนี้ ใช้ภาพที่แนบอ้างอิงเฉพาะรูปร่างและสัดส่วนของตัวละครผู้ใหญ่ ไม่ใช้ชื่อหรือใบหน้าเดิม สร้างภาพ MASTER ใหม่ก่อนสร้างฉาก ตั้งชื่อ CHARACTER_01.png, CHARACTER_02.png ตามจำนวนที่ต้องใช้ แล้วใช้ใบหน้าใหม่เดิมอย่างต่อเนื่องทุกฉาก เก็บรายละเอียดตัวละครและชื่อไฟล์ MASTER ใน bible"} ระบุชุดใหม่ สถานที่ การกระทำอย่างกระชับ รักษาความต่อเนื่อง จบปมครบทุกเรื่อง ผู้พูดแต่ละฉากชัดเจน
ส่งไฟล์ story.json เป็น JSON ไม่มี markdown โครงสร้าง:
{"channel":${JSONObject.quote(channel)},"character_mode":"$mode","title":"ชื่อเรื่อง","category":"ชื่อหมวดเต็มตรงรายการ","bible":"ความสัมพันธ์และพล็อต","episodes":[{"ep":1,"scenes":[{"scene":1,"action":"เหตุการณ์","dialogue":"บทพูดภาษาไทย","image_prompt":"คำสั่งภาพพร้อมระบุ MASTER ชุด สถานที่ การกระทำ","flow_prompt":"Flow / Veo 3.1 Lite: English prompt + Thai speech, 8 seconds, 9:16, locked-off static camera"}]}]}
เพิ่ม EP และฉากเท่าที่เรื่องต้องใช้ เรียงเลขจาก 1 ไม่กำหนดจำนวนตายตัว หลังวางบททั้งเรื่อง ส่งไฟล์ story.json แล้วสร้างภาพแยกหนึ่งฉากหนึ่งไฟล์ตามลำดับครบทุก EP จนจบ ตั้งชื่อ EP01_SC001.png ตามเลข EP และฉาก ไม่ทำภาพรวมหลายฉาก ${if (mode == "original") "ใช้ MASTER ที่แนบให้ตรงรายคน" else "ใช้ CHARACTER MASTER ใหม่ที่สร้างสำหรับเรื่องนี้ตรงรายคน"} พร้อมคำสั่งสำหรับ Flow / Veo 3.1 Lite ทุกฉาก ผู้ใช้ต้องการบทและภาพพร้อมนำไปทำวิดีโอ""", useNewMasters = false)
        }.setNegativeButton("ยกเลิก",null).show()
    }
    private fun sceneCommands() {
        val p = plan ?: return notice("นำเข้าแผนเรื่องก่อนครับ")
        val scenes = rows(p)
        AlertDialog.Builder(this).setTitle("เลือกฉากเพื่อส่งคำสั่งภาพ").setItems(scenes.map { key(it) }.toTypedArray()) { _, index ->
            val s = scenes[index]
            shareText("สร้างภาพฉาก ${key(s)} เรื่อง ${p.getString("title")}\n${s.getString("image_prompt")}\nใช้ภาพ MASTER ที่แนบของแต่ละคน คงใบหน้า ทรงผม และรูปร่าง เปลี่ยนชุดตามฉาก ภาพแนวตั้ง 9:16 ละครไทยสมจริง ไม่มีข้อความในภาพ บันทึกชื่อ ${key(s)}.png\nหากยังไม่มี MASTER ให้ขอภาพต้นฉบับก่อนสร้าง")
        }.show()
    }
    @Deprecated("Android legacy back callback")
    override fun onBackPressed() { if (!importing) super.onBackPressed() }
    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null,null,null)?.use { if (it.moveToFirst()) it.getString(0) else "" } ?: ""
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
            val match = Regex("EP(\\d+)[ _-]*SC(?:ENE)?(\\d+)",RegexOption.IGNORE_CASE).find(displayName(uri))
            val found = match?.let { m -> scenes.find { it.getInt("ep") == m.groupValues[1].toIntOrNull() && it.getInt("scene") == m.groupValues[2].toIntOrNull() } }
            if (found == null) unknown.add(uri) else assignments[uri] = found
        }
        if (assignments.values.map { key(it) }.distinct().size != assignments.size) return notice("มีไฟล์ชื่อฉากซ้ำในชุดที่เลือก กรุณาเลือกหนึ่งภาพต่อฉากครับ")
        val occupied = assignments.values.map { key(it) }.toSet()
        val available = scenes.filter { key(it) !in occupied && !image(it).exists() }
        if (unknown.size > available.size) return notice("ไฟล์ที่ไม่มีเลขฉากมากกว่าฉากที่ยังขาด กรุณาตั้งชื่อ EP01_SC001.png เพื่อจับคู่ให้ถูกต้องครับ")
        unknown.forEachIndexed { i, uri -> assignments[uri] = available[i] }
        if (unknown.isNotEmpty() && !confirmedOrder) {
            val list = uris.joinToString("\n") { "${displayName(it)} → ${key(assignments.getValue(it))}" }
            val content = TextView(this).apply { text = "บางภาพไม่มีเลขฉาก แอปเสนอเรียงตามลำดับไฟล์ที่เลือก กรุณาตรวจให้ตรงก่อนนำเข้า\n\n$list"; setPadding(dp(16),dp(12),dp(16),dp(12)) }
            AlertDialog.Builder(this).setTitle("ตรวจลำดับภาพครั้งเดียว").setView(ScrollView(this).apply { addView(content) }).setPositiveButton("ลำดับถูกต้อง") { _,_ -> importImages(uris,true) }.setNegativeButton("ยกเลิก",null).show()
            return
        }
        val importFolder = project
        importing = true
        val remaining = uris.toMutableList()
        fun next() {
            if (remaining.isEmpty()) { importing = false; render(); return }
            val uri = remaining.removeAt(0)
            val name = displayName(uri)
            val match = Regex("EP(\\d+)[ _-]*SC(?:ENE)?(\\d+)",RegexOption.IGNORE_CASE).find(name)
            val found = match?.let { m -> scenes.find { it.getInt("ep") == m.groupValues[1].toIntOrNull() && it.getInt("scene") == m.groupValues[2].toIntOrNull() } }
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
                if (image(s).exists()) AlertDialog.Builder(this).setMessage("${key(s)} มีภาพแล้ว แทนด้วย $name หรือไม่?").setPositiveButton("แทนภาพ") { _,_ -> save(s) }.setNegativeButton("ข้าม") { _,_ -> next() }.setCancelable(false).show()
                else save(s)
            }
            if (assignments[uri] != null) assign(assignments.getValue(uri))
            else AlertDialog.Builder(this).setTitle("จับคู่ภาพ: $name").setItems(scenes.map { key(it) + if (image(it).exists()) " ✓" else " • ขาดภาพ" }.toTypedArray()) { _, i -> assign(scenes[i]) }.setNegativeButton("ข้าม") { _,_ -> next() }.setCancelable(false).show()
        }
        next()
    }
    private fun review() {
        val p = plan ?: return notice("ยังไม่มีแผนเรื่อง")
        val scenes = rows(p)
        AlertDialog.Builder(this).setTitle("ตรวจทุก EP").setItems(scenes.map { key(it) + if (image(it).exists()) " ✓" else " • ขาดภาพ" }.toTypedArray()) { _, i ->
            val s = scenes[i]
            val view = LinearLayout(this).apply { orientation = 1; setPadding(20,20,20,20) }
            if (image(s).exists()) view.addView(ImageView(this).apply { setImageURI(Uri.fromFile(image(s))); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(-1,500) })
            view.addView(TextView(this).apply { text = "${s.getString("action")}\n${s.optString("dialogue")}\n\n${s.getString("flow_prompt")}" })
            AlertDialog.Builder(this).setTitle(key(s)).setView(ScrollView(this).apply { addView(view) }).setPositiveButton("ปิด",null).show()
        }.show()
    }
    private fun export(uri: Uri) {
        val p = plan!!
        contentResolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            put("story.json",p.toString(2).toByteArray())
            File(project, "masters").listFiles()?.forEach { master -> put("characters/${master.name}", master.readBytes()) }
            put("README.txt","${p.getString("title")}\n${p.getString("category")}\nนำภาพและ flow.txt ของแต่ละฉากไปใช้ใน Flow • 8 วินาที • 9:16".toByteArray())
            for (s in rows(p)) {
                val path = "EP%02d/%s".format(s.getInt("ep"),key(s))
                val file = image(s)
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val extension = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                zip.putNextEntry(ZipEntry("$path.$extension")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                put("$path-script.txt","${s.getString("action")}\n${s.optString("dialogue")}".toByteArray())
                put("$path-flow.txt",s.getString("flow_prompt").toByteArray())
            }
        } }
    }
}
