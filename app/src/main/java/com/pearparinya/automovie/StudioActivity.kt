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
    private val imagesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }
    private val zipPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) background { export(uri) }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
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
        require(p.getString("category") in categories) { "หมวดหมู่ไม่ตรงกับ 5 หมวดที่กำหนด" }
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
        val scroll = ScrollView(this).apply { setBackgroundColor(navy); isFillViewport = true; clipToPadding = false }
        root = LinearLayout(this).apply { orientation = 1; setPadding(dp(14),dp(4),dp(14),dp(8)) }
        scroll.addView(root)
        val host = LinearLayout(this).apply { orientation = 1; setBackgroundColor(navy) }
        host.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        host.addView(TextView(this).apply {
            text = "Flow • Veo 3.1 Lite • 8 วินาที • 9:16\nพัฒนาโดย : นายปริญญา จันทร์จักษุ"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(muted)
            setPadding(dp(10),dp(6),dp(10),dp(6))
            background = backdrop(Color.rgb(26,26,53),navy)
        })
        setContentView(host)
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(host)
        val header = LinearLayout(this).apply {
            orientation = 0; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10),dp(8),dp(10),dp(8))
            background = backdrop(Color.rgb(28,30,66),navy,Color.rgb(64,62,108))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd = dp(12) }
        })
        val heading = LinearLayout(this).apply { orientation = 1; layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
        heading.addView(TextView(this).apply { text = "STORY STUDIO"; textSize = 21f; setTextColor(gold); setTypeface(null,Typeface.BOLD) })
        heading.addView(TextView(this).apply { text = "ระบบจัดหนังครบทั้งเรื่อง"; textSize = 13f; setTextColor(ice); setPadding(0,dp(3),0,dp(3)) })
        heading.addView(TextView(this).apply { text = "STUDIO 1.2 • BUILD 722"; textSize = 12f; setTextColor(gold) })
        header.addView(heading); root.addView(header)
        label("สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย", 13)
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
        label("✨ สร้างเรื่อง • จัดภาพ • ส่งออก", 14, true)
        button("① ✨ วางแผนเรื่องทั้งหมด") { planning() }
        button("② 📋 นำเข้าแผนเรื่องครบทุก EP") { planPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
        button("③ 🎨 สร้างภาพใน ChatGPT") { wholeStoryCommands() }
        button("เลือกคำสั่งภาพรายฉาก", false) { sceneCommands() }
        button("④ 🖼 นำเข้าภาพทั้งเรื่อง") { if (plan == null) notice("นำเข้าแผนเรื่องก่อนครับ") else imagesPicker.launch(arrayOf("image/*")) }
        button("⑤ ✓ ตรวจงานทั้งเรื่อง") { review() }
        button("⑥ 📦 ดาวน์โหลด ZIP ทั้งเรื่อง") {
            val current = plan
            if (current == null) notice("นำเข้าแผนเรื่องก่อนครับ")
            else if (rows(current).any { !image(it).exists() }) notice("ยังมีฉากขาดภาพ ตรวจงานและนำเข้าให้ครบก่อนส่งออก")
            else zipPicker.launch("STORY-STUDIO-${System.currentTimeMillis()}.zip")
        }
        label("เครื่องมือและงานที่บันทึก", 14, true)
        button("📂 เปิดเรื่องที่บันทึกไว้", false) {
            val folders = base.listFiles()?.filter { File(it,"story.json").exists() } ?: emptyList()
            AlertDialog.Builder(this).setTitle("เรื่องของคุณ").setItems(folders.map { JSONObject(File(it,"story.json").readText()).getString("title") }.toTypedArray()) { _, i -> prefs.edit().putString("project",folders[i].name).apply(); render() }.show()
        }
        button("👥 ตัวละคร MASTER • งานเดิม • Update", false) { startActivity(Intent(this, MainActivity::class.java)) }
        button("❓ วิธีใช้งาน", false) { notice("ให้ ChatGPT วางเรื่องครบทุก EP และส่ง story.json กลับมา นำเข้าแผนก่อนสร้างภาพ จากนั้นนำเข้าภาพทุก EP พร้อมกัน ชื่อไฟล์ EP01_SC001.png จะจับคู่ได้อัตโนมัติ ไฟล์ชื่ออื่นให้เลือกฉากเอง ภาพถูกเก็บในแอปและส่งออก ZIP ได้เมื่อครบทุกฉาก\n\nการสร้างบทและภาพทำใน ChatGPT โดยผู้ใช้ แอปนี้ไม่ได้เรียก API และไม่รับประกันความเหมือนตัวละคร 100%") }
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
    private fun shareText(text: String) {
        val uris = arrayListOf<Uri>()
        for (name in listOf("gawin", "rinlada", "mind")) {
            val source = File(filesDir,"character_masters/$name.master")
            if (source.exists()) {
                val out = File(cacheDir,"character_master_exports/${name.uppercase()}_MASTER.png")
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
        shareText("เรื่อง ${p.getString("title")}\nใช้ภาพ MASTER ตามชื่อของแต่ละคน คงใบหน้า ผม รูปร่าง กำหนดชุดตามคำสั่งแต่ละฉาก ภาพ 9:16 หนึ่งฉากหนึ่งภาพ ไม่ทำภาพรวมหลายฉาก เรียงสร้างตามรายการครบทุก EP หากติดข้อจำกัดให้แจ้งฉากล่าสุดเพื่อทำต่อ ไม่ข้ามฉาก\n\n$commands")
    }
    private fun planning() {
        val idea = EditText(this).apply { hint = "แนวคิดเรื่อง (เว้นว่างให้คิดอัตโนมัติ)" }
        AlertDialog.Builder(this).setTitle("สร้างเรื่องใหม่ทั้งเรื่อง").setView(idea).setPositiveButton("ส่งคำสั่ง") { _, _ ->
            shareText("""วางเรื่องละครไทยสมจริงให้ช่อง สตอรี่หลังบ้าน - ซีรีส์สั้นดราม่าผัวเมีย จนจบทุก EP ในชุดเดียว
แนวคิด: ${idea.text.toString().ifBlank { "คิดเรื่องใหม่อัตโนมัติ" }}
เลือกชื่อเรื่องและหนึ่งหมวดจาก: ${categories.joinToString("\n")}
ประเมินจำนวน EP และฉากตามเนื้อหาเอง สำหรับ Flow / Veo 3.1 Lite ทุกฉาก 8 วินาที แนวตั้ง 9:16 Locked-off static camera ตัวละครหลัก กวิน รินลดา มายด์ อ้างอิง MASTER ของแต่ละคนเพื่อคงใบหน้า ผม รูปร่าง ระบุชุดใหม่ สถานที่ การกระทำอย่างกระชับ รักษาความต่อเนื่อง จบปมครบทุกเรื่อง ผู้พูดแต่ละฉากชัดเจน
ส่งไฟล์ story.json เป็น JSON ไม่มี markdown โครงสร้าง:
{"title":"ชื่อเรื่อง","category":"ชื่อหมวดเต็มตรงรายการ","bible":"ความสัมพันธ์และพล็อต","episodes":[{"ep":1,"scenes":[{"scene":1,"action":"เหตุการณ์","dialogue":"บทพูดภาษาไทย","image_prompt":"คำสั่งภาพพร้อมระบุ MASTER ชุด สถานที่ การกระทำ","flow_prompt":"Flow / Veo 3.1 Lite: English prompt + Thai speech, 8 seconds, 9:16, locked-off static camera"}]}]}
เพิ่ม EP และฉากเท่าที่เรื่องต้องใช้ เรียงเลขจาก 1 ไม่กำหนดจำนวนตายตัว ขั้นนี้วางแผนและบททั้งเรื่อง ยังไม่สร้างภาพ""")
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
    private fun importImages(uris: List<Uri>) {
        val scenes = rows(plan!!)
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
            if (found != null) assign(found)
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
