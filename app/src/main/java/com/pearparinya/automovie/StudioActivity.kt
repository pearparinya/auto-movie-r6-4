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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun buildChatShareIntent(text: String, uris: ArrayList<Uri>): Intent =
    Intent(if (uris.size <= 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (uris.isEmpty()) "text/plain" else {
            val extensions = uris.map { it.lastPathSegment?.substringAfterLast('.')?.lowercase() }.distinct()
            when (extensions.singleOrNull()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/*" }
        }
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"คำสั่งจาก DIRECTOR X")
        putExtra(Intent.EXTRA_TITLE,"DIRECTOR X")
        clipData = ClipData.newPlainText("คำสั่ง DIRECTOR X",text).apply {
            uris.forEach { addItem(ClipData.Item(it)) }
        }
        if (uris.isNotEmpty()) {
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM,uris[0])
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Keep ChatGPT in its own Android task. Without NEW_TASK, some Android
        // launchers place the ChatGPT screen inside DIRECTOR X's Recents card,
        // so selecting DIRECTOR X only reopens ChatGPT.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Whole-story workspace. Legacy preferences and character masters stay untouched. */
class StudioActivity : AppCompatActivity() {
    private val base get() = File(filesDir, "studio").apply { mkdirs() }
    private val prefs get() = getSharedPreferences("studio", MODE_PRIVATE)
    private val project get() = File(base, prefs.getString("project", "draft")!!).apply { mkdirs() }
    private var plan: JSONObject? = null
    private var importing = false
    private var pendingStoryShare: (() -> Unit)? = null
    private var pendingStorySummary = ""
    private var pendingReceipt: (() -> Unit)? = null
    private var exportEpisode: Int?
        get() = prefs.getInt("export_episode",0).takeIf { it>0 }
        set(value) { prefs.edit().putInt("export_episode",value ?: 0).apply() }
    private var backupMode: Boolean
        get() = prefs.getBoolean("backup_mode",false)
        set(value) { prefs.edit().putBoolean("backup_mode",value).apply() }
    private val updater = StudioUpdater(this)
    private lateinit var root: LinearLayout
    private val navy = Color.rgb(18,29,49)
    private val gold = Color.rgb(232,180,82)
    private val ice = Color.rgb(248,246,239)
    private val muted = Color.rgb(164,164,169)
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
            background = backdrop(Color.rgb(24,24,27),navy,Color.rgb(74,61,39))
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
            list.addView(TextView(this).apply {
                text = value; textSize = 16f; setTextColor(navy); setTypeface(null,Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL; minHeight = dp(60)
                setPadding(dp(14),dp(14),dp(14),dp(14))
                background = backdrop(Color.rgb(248,246,239),Color.rgb(226,234,243),Color.rgb(173,187,204))
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
            minHeight = dp(48); background = backdrop(Color.rgb(36,36,40),Color.rgb(28,28,32))
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
        val names = listOf("gawin" to "กวิน", "rinlada" to "รินรดา", "mind" to "เมย์")
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
    private fun receivePackage(selected: List<Uri>, confirmed: Boolean = false) {
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
                val videos = files.filter { displayName(it).endsWith(".mp4",true) || contentResolver.getType(it)=="video/mp4" }
                val pictures = files.filter { displayName(it).substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp") || contentResolver.getType(it)?.startsWith("image/") == true }
                val masters = pictures.filter { Regex("^CHARACTER_\\d+\\.", RegexOption.IGNORE_CASE).containsMatchIn(displayName(it)) }
                val covers = pictures.filter { StoryFiles.coverName(displayName(it)) != null }
                val scenes = pictures.filter { !StoryFiles.isMaster(displayName(it)) } - covers.toSet()
                require(scripts.isNotEmpty() || pictures.isNotEmpty() || videos.isNotEmpty()) { "ไม่พบ story.json หรือภาพในไฟล์ที่เลือก กรุณาเลือก ZIP ชุดงาน หรือ JSON/PNG/JPG" }
                val candidate = scripts.firstOrNull()?.let { contentResolver.openInputStream(it)!!.bufferedReader().use { reader -> JSONObject(reader.readText().removePrefix("\uFEFF")) } }
                val destinationStory = candidate ?: plan ?: error("ยังไม่มีบท กรุณานำเข้า story.json ก่อน")
                val checkedVideos = videos.map { uri ->
                    val number=StoryFiles.sceneNumber(displayName(uri)) ?: error("วิดีโอต้องชื่อ EP01_SC001.mp4: ${displayName(uri)}")
                    val scene=rows(destinationStory).firstOrNull { it.getInt("ep")==number.first && it.getInt("scene")==number.second } ?: error("วิดีโอไม่ตรงกับบท: ${displayName(uri)}")
                    val temp=File(staging,"checked_videos/${key(scene)}.mp4").apply { parentFile!!.mkdirs() }
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val reader=android.media.MediaMetadataRetriever()
                    try { reader.setDataSource(temp.path); require(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it>0 }==true) { "วิดีโออ่านไม่ได้" } } finally { reader.release() }
                    key(scene) to temp
                }
                require(checkedVideos.map { it.first }.distinct().size==videos.size) { "มีวิดีโอฉากซ้ำ" }
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
                if (candidate != null) validate(candidate)
                if (!confirmed) {
                    val details = "เรื่อง: ${destinationStory.optString("title")}\n" + files.joinToString("\n") { uri ->
                        val filename=displayName(uri); val number=StoryFiles.sceneNumber(filename)
                        filename + if(number != null) " → EP${number.first} ฉาก ${number.second}" else ""
                    } + "\n\nตรวจจับคู่ก่อนนำเข้า • ภาพฉากเดิมจะถามก่อนแทน ภาพ MASTER และปกที่ซ้ำจะเก็บเวอร์ชันก่อนแทน"
                    runOnUiThread {
                        importing=false; dialog.dismiss()
                        val preview=TextView(this).apply { text=details; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                        AlertDialog.Builder(this).setTitle("ตรวจชุดไฟล์ก่อนนำเข้า").setView(ScrollView(this).apply { addView(preview) })
                            .setPositiveButton("นำเข้าชุดนี้") { _,_ -> receivePackage(selected,true) }.setNegativeButton("ยกเลิก",null).showDirector()
                    }
                    return@Thread
                }
                if (candidate != null) {
                    validate(candidate)
                    // Reimporting the same story must retain images already received.
                    val sameStory = plan?.let { old -> old.optString("title") == candidate.optString("title") && old.optString("category") == candidate.optString("category") && rows(old).map { it.toString() } == rows(candidate).map { it.toString() } } == true
                    val folder = if (sameStory) project else File(base, UUID.randomUUID().toString()).apply { mkdirs() }
                    importNewMasters(masters, folder, candidate)
                    if (candidate.optString("character_mode","original") == "original") {
                        listOf("gawin","rinlada","mind").forEach { id ->
                            val target=File(folder,"original_masters/$id.master")
                            val source=File(filesDir,"character_masters/$id.master")
                            if(!target.exists() && source.exists()) { target.parentFile!!.mkdirs(); source.copyTo(target) }
                        }
                    }
                    File(folder,"story.json").writeText(candidate.toString(2))
                    prefs.edit().putString("project",folder.name).commit()
                } else {
                    require(plan != null) { "ยังไม่มีบท กรุณานำเข้า story.json หรือ ZIP ที่มีบทก่อน" }
                    importNewMasters(masters)
                }
                checkedVideos.forEach { (id,temp) ->
                    val target=File(project,"videos/$id.mp4").apply { parentFile!!.mkdirs() }
                    if(target.exists()) archiveVersion(target,project,id,"video")
                    temp.copyTo(target,overwrite=true)
                }
                checkedCovers.forEach { (name, temp) -> coverFile(name).apply { parentFile!!.mkdirs() }.let { if(it.exists()) archiveVersion(it,project,name,"cover"); temp.copyTo(it,overwrite=true) } }
                runOnUiThread {
                    importing = false; dialog.dismiss(); render()
                    if (scenes.isNotEmpty()) importImages(scenes)
                    else if (videos.isNotEmpty()) notice("รับวิดีโอแล้ว ${videos.size} ฉาก")
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
        if (uri != null) { val backup = backupMode; backupMode = false; background { if (backup) contentResolver.openOutputStream(uri)!!.use { StudioArchive.write(project,it) } else export(uri) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val masterError = runCatching { installCharacterMasters() }.exceptionOrNull()
        render()
        if (masterError != null) notice("ติดตั้ง MASTER ตัวละครไม่สำเร็จ กรุณาเปิดแอปอีกครั้ง")
    }
    private fun installCharacterMasters() {
        for ((key, asset) in listOf("gawin" to "GAWIN_MASTER.png", "rinlada" to "RINLADA_MASTER.png", "mind" to "MIND_MASTER.png")) {
            val revision = if (key == "gawin") "gawin_master_20261012_1841" else "${key}_master_20261012_1826"
            val target = File(filesDir, "character_masters/$key.master")
            if (prefs.getBoolean(revision, false) && target.exists()) continue
            target.parentFile!!.mkdirs()
            val temp = File(target.parentFile, "$key.master.pending")
            assets.open("character_masters/$asset").use { input -> temp.outputStream().use { input.copyTo(it) } }
            require(masterExportName(temp, false) != null) { "อ่าน MASTER ไม่ได้" }
            if (target.exists()) target.copyTo(File(target.parentFile, "$key.previous"), overwrite = true)
            temp.copyTo(target, overwrite = true)
            temp.delete()
            prefs.edit().putBoolean(revision, true).apply()
        }
    }
    override fun onResume() {
        super.onResume()
        val retry = pendingStoryShare
        if (retry != null) {
            pendingStoryShare = null
            AlertDialog.Builder(this).setTitle("ตรวจข้อมูลใน ChatGPT")
                .setMessage("$pendingStorySummary\nตรวจว่าช่องพิมพ์ใน ChatGPT มีคำสั่งและภาพครบก่อนกดส่ง\nหากเป็นแชทเปล่า ให้กลับมากด ‘คัดลอกแล้วเปิดใหม่’ จากนั้นแตะช่องพิมพ์ค้างและเลือก วาง")
                .setPositiveButton("ข้อมูลเข้าครบแล้ว") { _, _ -> pendingReceipt?.invoke(); pendingReceipt = null; render() }
                .setNeutralButton("ส่งข้อมูลอีกครั้ง") { _, _ -> retry() }
                .setNegativeButton("คัดลอกแล้วเปิดใหม่") { _, _ ->
                    val command = File(project,"last_command.txt").takeIf { it.exists() }
                        ?: File(project,"last_story_command.txt").takeIf { it.exists() }
                    command?.let { copyCommand(it.readText()) }
                    retry()
                }.showDirector()
        } else if (!importing) updater.checkAutomatically()
    }
    private fun rows(p: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val eps = p.getJSONArray("episodes")
        for (i in 0 until eps.length()) {
            val ep = eps.getJSONObject(i)
            val scenes = ep.getJSONArray("scenes")
            for (j in 0 until scenes.length()) result.add(scenes.getJSONObject(j).put("ep", ep.getInt("ep")))
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
            require(ep.getInt("ep") == i + 1 || p.optString("export_scope") == "episode" && eps.length() == 1 && ep.getInt("ep") == p.optInt("source_episode")) { "EP ต้องเรียงจาก 1" }
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
        if (plan?.optString("character_mode","original") == "original") {
            listOf("gawin","rinlada","mind").forEach { id ->
                val target=File(project,"original_masters/$id.master"); val source=File(filesDir,"character_masters/$id.master")
                if(!target.exists() && source.exists()) { target.parentFile!!.mkdirs(); source.copyTo(target) }
            }
        }
        window.statusBarColor = navy
        window.navigationBarColor = navy
        ViewCompat.getWindowInsetsController(window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val scroll = ScrollView(this).apply { background = backdrop(Color.rgb(18,29,49),Color.rgb(30,49,73)); isFillViewport = true; clipToPadding = false }
        root = LinearLayout(this).apply { orientation = 1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        scroll.addView(root)
        val host = LinearLayout(this).apply { orientation = 1; setBackgroundColor(navy) }
        host.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        host.addView(TextView(this).apply {
            text = "Flow • Veo 3.1 Lite • ${plan?.optInt("duration_seconds",8) ?: 8} วินาที • ${plan?.optString("aspect_ratio","9:16") ?: "9:16"}\nพัฒนาโดย : นายปริญญา จันทร์จักษุ"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(muted)
            setPadding(dp(10),dp(6),dp(10),dp(6))
            background = backdrop(navy,navy)
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
            background = backdrop(Color.rgb(24,24,27), Color.rgb(17,17,20), Color.rgb(68,56,36)); elevation = dp(6).toFloat()
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.director_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { marginEnd = dp(8) }
            contentDescription = "DIRECTOR X"
        })
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        fun brandLine(value: String, maximum: Int, minimum: Int, color: Int, bold: Boolean, spacing: Int) {
            heading.addView(TextView(this).apply {
                text = value
                gravity = Gravity.START
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
        brandLine("DIRECTOR X ${packageManager.getPackageInfo(packageName, 0).versionName}", 20, 14, gold, true, 1)
        brandLine("ผู้กำกับอัจฉริยะ", 16, 12, ice, true, 0)
        brandLine("พร้อมใช้ใน Flow", 12, 10, muted, false, 1)
        header.addView(heading)
        root.addView(header)
        
        val p = plan
        val summary = LinearLayout(this).apply {
            orientation = 1; setPadding(dp(10),dp(6),dp(10),dp(6))
            background = backdrop(Color.rgb(245,243,235),Color.rgb(225,233,240),Color.rgb(175,190,205))
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(12),0,dp(16)) }
        }
        fun summaryText(value: String, size: Float, color: Int, bold: Boolean = false) {
            summary.addView(TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0,dp(3),0,dp(3)); if (bold) setTypeface(null,Typeface.BOLD) })
        }
        if (p != null) {
            val hero=coverNames(p).map { coverFile(it) }.firstOrNull { it.exists() } ?: rows(p).map { image(it) }.firstOrNull { it.exists() }
            if(hero != null) summary.addView(ImageView(this).apply {
                setImageBitmap(previewBitmap(Uri.fromFile(hero))); scaleType=ImageView.ScaleType.CENTER_CROP
                layoutParams=LinearLayout.LayoutParams(-1,dp(170)); contentDescription="ภาพหนังที่กำลังสร้าง"
                setOnClickListener { fullImage(hero,p.optString("title")) }
            })
            val last=prefs.getString("last_scene_${project.name}",null) ?: File(project,"last_scene.txt").takeIf { it.exists() }?.readText()
            if(last != null) summaryText("ทำล่าสุด • $last",12f,Color.rgb(71,85,106),true)
        }
        if (p == null) {
            summaryText(if (File(project,"command_sent").exists()) "ส่งไป ChatGPT แล้ว • รอรับบทและภาพในขั้นตอน 2" else "🎬 พร้อมสร้างเรื่องใหม่ • ครบทุก EP",14f,navy,true)
        } else {
            val scenes = rows(p); val count = scenes.count { image(it).exists() }
            summaryText(p.getString("title"),17f,navy,true)
            summaryText(p.optString("channel", originalChannel),12f,Color.rgb(71,85,106))
            summaryText(p.getString("category"),12f,Color.rgb(71,85,106))
            p.optString("tagline").takeIf { it.isNotBlank() }?.let { summaryText(it,13f,navy) }
            val episodes = p.getJSONArray("episodes")
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                summaryText("EP ${ep.getInt("ep")} • ${ep.optString("title")} • ${ep.getJSONArray("scenes").length()} ซีน",12f,Color.rgb(71,85,106))
            }
            summaryText("${p.getJSONArray("episodes").length()} EP  •  ${scenes.size} ฉาก  •  ภาพ $count/${scenes.size}",14f,Color.rgb(134,87,20),true)
            val names = coverNames(p)
            summaryText("วิดีโอ ${scenes.count { video(it).exists() }}/${scenes.size} • ตรวจรับ ${scenes.count { it.optString("user_review") == "approved" }}/${scenes.size}",12f,Color.rgb(71,85,106))
            summaryText("ปก ${names.count { coverFile(it).exists() }}/${names.size} • ทุก EP มี 9:16 และ 16:9",12f,Color.rgb(134,87,20),true)
            summary.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max = scenes.size; progress = count
                progressTintList = ColorStateList.valueOf(gold)
                progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(48,46,40))
                layoutParams = LinearLayout.LayoutParams(-1,dp(8)).apply { topMargin = dp(8) }
            })
        }
        root.addView(summary)
        if (p != null) {
            button("▶ ทำต่อจากฉากล่าสุด") { resumeScene() }
            button("🎞️ EP และฉาก • ภาพ / วิดีโอ", false) { episodesMenu() }
            button("🔎 ตรวจงานที่ขาด", false) { auditStory() }
        }
        val sent = p != null || File(project,"command_sent").exists()
        val complete = p != null && rows(p).all { image(it).exists() && video(it).exists() } && coverNames(p).all { coverFile(it).exists() }
        label(if (complete) "ภาพ วิดีโอ และปกครบแล้ว • ส่งออกชุดงานได้" else if (sent) "ขั้นตอน 2 • รับไฟล์จาก ChatGPT กลับเข้าแอป" else "ขั้นตอน 1 • เลือกเรื่องแล้วส่งไป ChatGPT", 14, true)
        stepCard(1, "🎬", if (sent) "ส่งคำสั่งแล้ว ✓" else "สร้างเรื่องและส่งไป ChatGPT", if (sent) "ล็อกการสร้างซ้ำ • เริ่มเรื่องใหม่ใช้ปุ่มด้านล่าง" else "เลือกช่องและแนว • ชื่อเรื่องภาษาไทย", enabled = !sent, completed = sent) { planning() }
        stepCard(2, "🎭", "ตัวละครและ MASTER", "ตรวจต้นฉบับรายคน • บทบาทและรูปร่าง") { castMenu() }
        stepCard(3, "🎨", if (p == null) "รับบทและภาพจาก ChatGPT" else "เพิ่มภาพฉาก / รับ ZIP", "เลือก ZIP หรือ story.json และภาพ • มีบทแล้วเพิ่มเฉพาะภาพได้") { packagePicker.launch(arrayOf("*/*")) }
        stepCard(4, "🎥", "วิดีโอผ่าน Flow / Veo", "นำเข้า MP4 • รวม EP พร้อมปกเปิด 3 วินาที", enabled = p != null, completed = p != null && rows(p).all { video(it).exists() }) { moviesMenu() }
        stepCard(5, "⬇", "ดาวน์โหลดหนัง EP เป็น MP4", "ชื่อเรื่อง + เลข EP + ชื่อตอน • ZIP อยู่ในสำรอง", enabled = p != null) { moviesMenu() }
        if (p != null) button("📦 ส่งออกชุดงาน / สำรอง ZIP", false) {
            val current = plan ?: return@button
            exportEpisode = null; backupMode = false
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
        splitActions("⚙️ ตั้งค่า / สำรอง", "✨ เริ่มเรื่องใหม่", { settingsMenu() }, {
            AlertDialog.Builder(this).setTitle("เริ่มเรื่องใหม่?").setMessage("เรื่องปัจจุบันเก็บไว้ในรายการเรื่องที่บันทึก สามารถกลับมาทำต่อได้")
                .setPositiveButton("เริ่มเรื่องใหม่") { _,_ -> prefs.edit().putString("project", UUID.randomUUID().toString()).apply(); render() }.setNegativeButton("ทำเรื่องเดิมต่อ",null).showDirector()
        }, colors = listOf(menuColors[3], menuColors[4]))
        splitActions("🎭 ตัวละครรายเรื่อง", "🖼️ ทดลองสร้างรูป", { castMenu() }, { testImageMenu() })
        splitActions("📚 เรื่องของฉัน", "🏠 หน้าหลัก", { storiesMenu() }, { render() })
    }
    private fun splitActions(left: String, right: String, leftAction: () -> Unit, rightAction: () -> Unit, colors: List<IntArray> = listOf(menuColors[1], menuColors[2])) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(3); bottomMargin = dp(3) }
        }
        listOf(left to leftAction, right to rightAction).forEachIndexed { index, item ->
            row.addView(Button(this).apply {
                text = item.first; isAllCaps = false; textSize = 12f
                setTextColor(gold); setTypeface(null,Typeface.BOLD); gravity = Gravity.CENTER
                minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
                setPadding(dp(4),dp(5),dp(4),dp(5))
                backgroundTintList = null
                background = backdrop(Color.rgb(25,25,28),Color.rgb(19,19,22),Color.rgb(63,55,39))
                layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { if (index == 0) marginEnd = dp(3) else marginStart = dp(3) }
                setOnClickListener { if (!importing) item.second() }
            })
        }
        root.addView(row)
    }
    private fun testImageMenu() {
        choiceMenu("🖼️ ทดลองสร้างรูป 1 ภาพ", listOf("🎭 ตัวละครเดิม • เลือกคนที่ต้องการ", "✨ ตัวละครสมมุติ • ใช้รูปผมหรือสร้างคนใหม่")) { choice ->
            if (choice == 0) choiceMenu("🎭 เลือกตัวละครในภาพ", listOf("👨 กวิน • คนเดียว", "👩 รินรดา • คนเดียว", "👩 เมย์ • คนเดียว", "👥 ทั้ง 3 คนในภาพเดียว")) { index ->
                val keys = listOf("gawin", "rinlada", "mind")
                val selected = if (index == 3) keys else listOf(keys[index])
                choiceMenu("🖼️ เลือกวิธีทดสอบ", listOf("🎯 คงตัวละครและชุดเดิม • สถานที่อัตโนมัติ", "👗 เปลี่ยนชุดตามบท • ทดสอบความต่อเนื่อง")) { mode ->
                    sendTestImage(selected, preserveMaster = mode == 0)
                }
            }
            else choiceMenu("✨ ตัวละครสมมุติ", listOf("👨 ใช้รูปผม • เปลี่ยนชุดและฉากอัตโนมัติ", "✨ สร้างคนใหม่")) { mode ->
                if (mode == 0) personalTestImageMenu()
                else shareText("สร้างภาพหนึ่งภาพตอนนี้ด้วยเครื่องมือสร้างภาพ และแสดงภาพที่สร้างในแชต ตัวละครสมมุติผู้ใหญ่หนึ่งคน ตั้งชื่อภาษาไทยและออกแบบใบหน้าใหม่ เลือกชุดสุภาพใหม่ เลือกสถานที่และสร้างเหตุการณ์สมมุติให้ตัวละคร เช่น ทำอาหารในบ้าน อ่านหนังสือในร้านกาแฟ หรือรดน้ำต้นไม้ในสวน เลือกหนึ่งเหตุการณ์ ออกแบบฉากและสิ่งของให้เหมาะสม กำหนดการกระทำของตัวละครให้ชัดเจน ภาพละครไทยสมจริง แนวตั้ง 9:16 เห็นใบหน้าและรูปร่างตั้งแต่ศีรษะถึงต้นขา แสงชัด สถานที่และเหตุการณ์เป็นเรื่องแต่ง ภาพไม่มีข้อความหรือตราสัญลักษณ์ หลังแสดงภาพจริงแล้วจึงแจ้งชื่อตัวละคร ชุด สถานที่ และเหตุการณ์สั้น ๆ หากสร้างภาพไม่ได้ให้แจ้งตามจริง", useNewMasters = false, includeMasters = false)
            }
        }
    }
    private val personalTestPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val temporary = File(filesDir, "personal_test_reference.tmp")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { input.copyTo(it) }
                } ?: error("เปิดภาพไม่ได้")
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์นี้ไม่ใช่ภาพที่อ่านได้" }
                require(temporary.renameTo(File(filesDir, "personal_test_reference.master"))) { "บันทึกภาพไม่ได้" }
                sendPersonalTestImage()
            } catch (e: Exception) {
                temporary.delete()
                notice("รับรูปผมไม่สำเร็จ: ${e.message}")
            }
        }
    }
    private fun personalTestImageMenu() {
        val reference = File(filesDir, "personal_test_reference.master")
        if (!reference.exists()) {
            try {
                assets.open("test_references/PARINYA_REFERENCE.png").use { input ->
                    reference.outputStream().use { input.copyTo(it) }
                }
            } catch (e: Exception) {
                reference.delete()
                notice("เตรียมรูปเริ่มต้นไม่สำเร็จ: ${e.message}")
                return
            }
            sendPersonalTestImage()
            return
        }
        choiceMenu("👨 รูปผมสำหรับทดสอบ", listOf("🖼️ ใช้รูปที่บันทึกไว้ • สร้างภาพทดสอบ", "📷 เปลี่ยนรูปผม")) { choice ->
            if (choice == 0) sendPersonalTestImage()
            else personalTestPicker.launch(arrayOf("image/*"))
        }
    }
    private fun sendPersonalTestImage() {
        try {
            val reference = File(filesDir, "personal_test_reference.master")
            if (!reference.exists()) {
                personalTestPicker.launch(arrayOf("image/*"))
                return
            }
            val outfits = listOf("เสื้อโปโลสีกรมท่าพอดีตัวกับกางเกงขายาวสีเบจ", "เสื้อเชิ้ตสีเทาอ่อนพับแขนกับกางเกงขายาวสีดำ", "เสื้อยืดสีขาวกับเสื้อคลุมสีน้ำตาลและกางเกงขายาวสีครีม")
            val last = prefs.getInt("personal_test_outfit_last", -1)
            val index = outfits.indices.filter { it != last }.random()
            prefs.edit().putInt("personal_test_outfit_last", index).apply()
            val location = nextTestLocation()
            val command = "สร้างภาพหนึ่งภาพตอนนี้ด้วยเครื่องมือสร้างภาพ และแสดงภาพจริงในแชต\nตัวละคร: ปริญญา ใช้ภาพ PARINYA_REFERENCE ที่แนบเป็นต้นฉบับ คงใบหน้า ผม รูปร่าง และสัดส่วนร่างกาย\nชุดใหม่: ${outfits[index]}\nสถานที่: $location\nการกระทำ: กำลังจัดผักสดลงในชามบนโต๊ะเตรียมอาหาร สีหน้าเป็นมิตร ท่ายืนเป็นธรรมชาติ\nภาพสมจริง แนวตั้ง 9:16 เห็นศีรษะถึงต้นขา แสงชัด เหตุการณ์สมมุติ ไม่มีข้อความหรือตราสัญลักษณ์ หลังแสดงภาพแจ้งชุด สถานที่ และกิจกรรมสั้น ๆ หากสร้างไม่ได้ให้แจ้งตามจริง"
            shareText(command, useNewMasters = false, includeMasters = false,
                referenceImages = listOf("PARINYA_REFERENCE" to reference))
        } catch (e: Exception) {
            notice("เตรียมภาพอ้างอิงไม่สำเร็จ: ${e.message}")
        }
    }
    private fun nextTestLocation(): String {
        val places = listOf(
            "ครัวบ้านสไตล์ญี่ปุ่น ตู้ไม้สีอ่อน หน้าต่างชมสวนหิน แสงเช้า",
            "ระเบียงบ้านริมสวน โต๊ะเตรียมอาหารไม้และเตาพกพา มีต้นไม้และแสงธรรมชาติ",
            "ครัวร้านกาแฟเล็ก ผนังอิฐ ชั้นวางถ้วยและเคาน์เตอร์เตรียมอาหาร",
            "ห้องเรียนทำอาหาร โต๊ะสเตนเลส อุปกรณ์เรียบร้อย หน้าต่างรับแสงกลางวัน",
            "ลานทำอาหารในสวนชนบท ซุ้มไม้ โต๊ะเตรียมอาหาร มีแปลงผักด้านหลัง",
            "ครัวบ้านพักริมทะเล ตู้สีขาว หน้าต่างมองเห็นทะเล แสงบ่ายนุ่ม",
            "ครัวกระท่อมบนภูเขา เคาน์เตอร์ไม้ หน้าต่างมองเห็นต้นสน แสงธรรมชาติ",
            "ครัวบนดาดฟ้าอาคาร โต๊ะเตรียมอาหาร เตาพกพา มีสวนกระถางและวิวเมือง"
        )
        val last = prefs.getInt("test_location_last", -1)
        val used = prefs.getStringSet("test_location_used", emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }.filter { it in places.indices }.toSet()
        val available = places.indices.filter { it !in used }
        val round = if (available.isEmpty()) emptySet<Int>() else used
        val choices = (if (available.isEmpty()) places.indices.toList() else available).filter { it != last }
        val index = choices.random()
        prefs.edit().putInt("test_location_last", index)
            .putStringSet("test_location_used", (round + index).map { it.toString() }.toSet()).apply()
        return places[index]
    }
    private fun sendTestImage(selectedKeys: List<String>, preserveMaster: Boolean) {
        val cast = listOf("gawin" to "กวิน", "rinlada" to "รินรดา", "mind" to "เมย์")
        val selected = cast.filter { it.first in selectedKeys }
        require(selected.isNotEmpty())
        if (selected.any { !File(filesDir,"character_masters/${it.first}.master").exists() }) {
            AlertDialog.Builder(this).setTitle("เพิ่มภาพ MASTER ก่อนทดสอบ")
                .setMessage("ต้องมีต้นฉบับของ ${selected.joinToString { it.second }} เพื่อเปรียบเทียบใบหน้าและรูปร่าง")
                .setPositiveButton("เพิ่มภาพตัวละคร") { _,_ -> characterMasters() }.setNegativeButton("ยกเลิก",null).showDirector()
            return
        }
        val scene = JSONObject()
        val characters = JSONArray()
        val actions = mutableListOf<String>()
        for ((key, name) in selected) {
            if (StoryIdentity.keyFor(name) != key) { notice("จับคู่ตัวละครกับ MASTER ไม่ได้"); return }
            val source = File(filesDir,"character_masters/$key.master")
            val masterName = masterExportName(source, false)
            if (masterName == null || !StoryIdentity.matchesMaster(name, masterName)) {
                notice("อ่านภาพ MASTER ของ $name ไม่ได้ กรุณาเพิ่มภาพต้นฉบับใหม่"); return
            }
            val outfit = if (preserveMaster) "คงเครื่องแต่งกายจาก MASTER ของคนนี้" else when (name) {
                "รินรดา" -> "เสื้อผ้าถักเนื้อละเอียดสีครีมแขนสามส่วน กระดุมหน้า คอเสื้อโค้งระดับพอดี ผ้าทึบ ทรงพอดีลำตัวและเอว กระโปรงสีเบจยาวถึงเข่า และผ้ากันเปื้อนสั้นสีเบจ"
                "เมย์" -> "เสื้อผ้าถักเนื้อละเอียดสีชมพูอ่อนแขนสามส่วน กระดุมหน้า คอเสื้อโค้งระดับพอดี ผ้าทึบ ทรงพอดีลำตัวและเอว กระโปรงสีครีมยาวถึงเข่า และผ้ากันเปื้อนสีครีม"
                else -> "เสื้อโปโลสีกรมท่าพอดีตัว และกางเกงขายาวสีเบจ"
            }
            val action = if (preserveMaster) "$name คงท่าทาง สีหน้า สายตา และกิจกรรมตาม MASTER ของตนเอง" else when (name) {
                "รินรดา" -> "รินรดากำลังหั่นมะเขือเทศบนเขียงไม้"
                "เมย์" -> "เมย์กำลังผัดผักในกระทะ"
                else -> "กวินกำลังจัดผักลงในชาม"
            }
            characters.put(JSONObject().put("name", name).put("master", masterName).put("outfit", outfit))
            actions.add(action)
        }
        scene.put("characters", characters)
        val location = nextTestLocation()
        val command = if (preserveMaster) "แก้ไขภาพ MASTER ที่แนบด้วยเครื่องมือแก้ไขภาพ และแสดงผลหนึ่งภาพจริงในแชต\nตัวละครผู้ใหญ่และไฟล์อ้างอิง:\n${sceneCastDescription(scene)}\nใช้ตัวละครจากภาพรายคนเป็นต้นแบบโดยตรง คงใบหน้า ผม รูปร่าง สัดส่วน เครื่องแต่งกาย ท่าทาง สีหน้า และสายตาตามภาพนั้น\nเปลี่ยนเฉพาะสถานที่เป็น $location คงอุปกรณ์ที่ตัวละครถือและพื้นที่ทำอาหารรอบมือจากภาพอ้างอิง\n${if (selected.size == 1) "คงระยะกล้องและมุมมองตามภาพต้นฉบับ" else "จัดแต่ละคนที่เคาน์เตอร์แยกกันในภาพเดียว เห็นใบหน้าและกิจกรรมครบ จับคู่ลักษณะของแต่ละคนกับ MASTER ของตนเอง"}\nภาพสมจริงแนวตั้ง 9:16 ไม่มีข้อความหรือตราสัญลักษณ์ หลังแสดงภาพแจ้งชื่อตัวละครและฉากหลังสั้น ๆ หากแก้ไขภาพไม่ได้ให้แจ้งตามจริง" else "สร้างภาพหนึ่งภาพตอนนี้ด้วยเครื่องมือสร้างภาพ และแสดงภาพจริงในแชต\n${storyVisualGuide("original")}\nตัวละครและชุดรายคน:\n${sceneCastDescription(scene)}\nสถานที่: $location จัดผักสด ชามและอุปกรณ์ทำอาหารให้เหมาะกับกิจกรรม\nการกระทำ: ${actions.joinToString("; ")}\nจัดท่ายืนเป็นธรรมชาติ สีหน้าเป็นมิตร แสงธรรมชาติ เหตุการณ์สมมุติหนึ่งเหตุการณ์ สร้างเฉพาะตัวละครในรายการนี้ ยึด MASTER ต้นฉบับรายคนเป็นหลัก ภาพทดสอบนี้เป็นฉากใหม่ ใช้ชุดและสถานที่ตามคำสั่งนี้\nหลังแสดงภาพจริงแล้วแจ้งชื่อ ชุด สถานที่ และเหตุการณ์สั้น ๆ หากสร้างภาพไม่ได้ให้แจ้งตามจริง"
        shareText(command, useNewMasters = false, selectedMasterKeys = selected.map { it.first })
    }
    private fun stepCard(number: Int, icon: String, title: String, detail: String, enabled: Boolean = true, completed: Boolean = false, action: () -> Unit) {
        val active = enabled && !completed
        val status = if (completed) "เสร็จแล้ว" else if (enabled) "พร้อมใช้งาน" else "ล็อก • รอขั้นตอนก่อนหน้า"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1,-2)
        }
        row.addView(object : android.view.View(this) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val x = width / 2f; val y = height / 2f; val radius = dp(14).toFloat()
                paint.color = Color.rgb(61,54,39); paint.strokeWidth = dp(1).toFloat()
                if (number > 1) canvas.drawLine(x,0f,x,y-radius,paint)
                if (number < 5) canvas.drawLine(x,y+radius,x,height.toFloat(),paint)
                paint.color = if (completed) Color.rgb(75,177,140) else if(active) gold else Color.rgb(85,102,125)
                canvas.drawCircle(x,y,radius,paint)
                paint.color = if (active || completed) navy else muted
                paint.textSize = dp(12).toFloat(); paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.typeface = Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
                canvas.drawText(if (completed) "✓" else number.toString(),x,y-(paint.ascent()+paint.descent())/2,paint)
            }
        }, LinearLayout.LayoutParams(dp(34),-1).apply { marginEnd = dp(10) })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16),dp(15),dp(16),dp(15))
            background = backdrop(if (active) Color.rgb(255,242,211) else Color.rgb(237,241,245), Color.rgb(224,232,241), if (active) gold else Color.rgb(167,183,201))
            layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { topMargin = dp(6); bottomMargin = dp(6) }
            minimumHeight = dp(108)
            isEnabled = enabled; isClickable = enabled; isFocusable = enabled
            val attribute = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground,attribute,true)
            foreground = getDrawable(attribute.resourceId)
            contentDescription = "ขั้นตอน $number $title $status $detail"
            setOnClickListener { if (enabled && !importing) action() else if (importing) Toast.makeText(this@StudioActivity,"กำลังนำเข้าภาพ กรุณารอสักครู่",Toast.LENGTH_SHORT).show() }
        }
        card.addView(TextView(this).apply {
            text = "ขั้นตอน $number  •  $status"; textSize = 10f
            setTextColor(if(completed) Color.rgb(25,113,80) else if(active) Color.rgb(139,88,19) else Color.rgb(85,97,115))
            setTypeface(null,Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "$title  ${if (enabled) "›" else if (completed) "✓" else "🔒"}"; textSize = 16f
            setTypeface(null,Typeface.BOLD); setTextColor(navy)
            setPadding(0,dp(7),0,0)
        })
        card.addView(TextView(this).apply {
            text = detail; textSize = 12f; setTextColor(Color.rgb(79,91,110)); setPadding(0,dp(6),0,0)
        })
        row.addView(card)
        root.addView(row)
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
            setTextColor(if (primary) navy else gold)
            backgroundTintList = null
            background = if (primary) backdrop(gold,Color.rgb(208,151,54)) else backdrop(Color.rgb(25,25,28),Color.rgb(19,19,22),Color.rgb(63,55,39))
            elevation = dp(if (primary) 3 else 0).toFloat()
            minHeight = dp(if (primary) 46 else 38); minimumHeight = minHeight
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(4),0,dp(4)) }
            setPadding(dp(10),dp(6),dp(10),dp(6))
            setOnClickListener { if (!importing) action() else Toast.makeText(this@StudioActivity,"กำลังนำเข้าภาพ กรุณารอสักครู่",Toast.LENGTH_SHORT).show() }
        })
    }
    private fun notice(text: String) { AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage(text).setPositiveButton("ตกลง", null).showDirector() }
    private fun background(block: () -> Unit) {
        importing = true
        val dialog = AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage("กำลังจัดไฟล์…").setCancelable(false).showDirector()
        Thread {
            try { block(); runOnUiThread { importing = false; dialog.dismiss(); render(); notice("เสร็จเรียบร้อย") } }
            catch (e: Exception) { File(filesDir,"last_error.txt").writeText("${java.util.Date()}\n${e.stackTraceToString()}"); runOnUiThread { importing = false; dialog.dismiss(); notice("ดำเนินการไม่สำเร็จ: ${e.message}") } }
        }.start()
    }
    private fun masterExportName(source: File, useNewMasters: Boolean): String? {
        if (!source.isFile) return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
        val name = if (useNewMasters) source.nameWithoutExtension else source.nameWithoutExtension + "_MASTER"
        return "${name.uppercase()}.$extension"
    }
    private fun shareText(inputText: String, lockCreation: Boolean = false, useNewMasters: Boolean = plan?.optString("character_mode") == "new", includeMasters: Boolean = true, selectedMasterKeys: List<String>? = null, referenceImages: List<Pair<String, File>> = emptyList()) {
        val text = if (lockCreation && !inputText.contains("ค่าตั้งต้นผู้ใช้ (ใช้แทนค่าตัวอย่าง)")) inputText + "\nค่าตั้งต้นผู้ใช้ (ใช้แทนค่าตัวอย่าง): EP=${prefs.getString("plan_episodes", "").orEmpty().ifBlank { "AI ประเมิน" }} ฉาก/EP=${prefs.getString("plan_scenes", "").orEmpty().ifBlank { "AI ประเมิน" }} เวลา=${prefs.getString("plan_duration", "8")} วินาที สัดส่วน=${prefs.getString("plan_ratio", "9:16")} กำหนด duration_seconds และ aspect_ratio ใน story.json และคำสั่งทุกฉากตามนี้" else inputText
        val uris = arrayListOf<Uri>()
        val transferId = UUID.randomUUID().toString()
        val sources = if (!includeMasters) emptyList<File>() else if (useNewMasters) File(project, "masters").listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.endsWith(".pending") } ?: emptyList()
            else (selectedMasterKeys ?: listOf("gawin", "rinlada", "mind")).map { File(project, "original_masters/$it.master").takeIf { f -> f.exists() } ?: File(filesDir, "character_masters/$it.master") }
        if (includeMasters && (sources.isEmpty() || sources.any { !it.exists() })) {
            notice("ภาพ MASTER ยังไม่ครบสำหรับคำสั่งนี้ กรุณาเพิ่มต้นฉบับตัวละครก่อนส่ง เพื่อไม่สร้างตัวละครผิดคน")
            return
        }
        for (source in sources) {
            if (source.exists()) {
                val name = masterExportName(source, useNewMasters)
                if (name == null) { notice("อ่านภาพ MASTER ไม่ได้ กรุณาเลือกภาพตัวละครใหม่"); return }
                val out = File(cacheDir,"character_master_exports/$transferId/$name")
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
        // ChatGPT Android builds do not all read the same share field. Keep
        // the command in both EXTRA_TEXT and the first ClipData item, then
        // append every granted image URI to that same ClipData payload.
        val intent = buildChatShareIntent(text,uris)
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("คำสั่ง DIRECTOR X", text))
        val destination = project
        val sceneId = prefs.getString("last_scene_${destination.name}", "")!!
        val commandFile = File(destination, if (lockCreation) "last_story_command.txt" else "last_command.txt")
        commandFile.writeText(text)
        File(destination,"last_payload.json").writeText(JSONObject().put("text",text).put("creation",lockCreation)
            .put("images",JSONArray(uris.map { it.toString() })).toString())
        val imageList = uris.joinToString("\n") { it.lastPathSegment.orEmpty() }
        pendingStorySummary = "คำสั่ง ${text.length} ตัวอักษร • ภาพ ${uris.size} ภาพ"
        fun launchShare() {
            pendingReceipt = {
                if (lockCreation) File(destination,"command_sent").writeText("confirmed")
                else File(destination,"receipts/${UUID.randomUUID()}.txt").apply { parentFile!!.mkdirs() }
                    .writeText("$sceneId\n${System.currentTimeMillis()}\n$text")
            }
            var opened = false
            for (target in listOf("com.openai.chatgpt", "com.openai.chatgpt.beta")) {
                try {
                    uris.forEach { grantUriPermission(target,it,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    startActivity(Intent(intent).setPackage(target))
                    opened = true; break
                } catch (_: android.content.ActivityNotFoundException) { }
                  catch (_: SecurityException) { }
            }
            if (!opened) {
                try {
                    startActivity(Intent.createChooser(intent,"เลือก ChatGPT • ตรวจรูปและวางคำสั่งก่อนส่ง").apply {
                        clipData = intent.clipData; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                    opened = true
                } catch (e: Exception) { pendingReceipt = null; notice("เปิดหน้ารับแชร์ไม่ได้: ${e.message}\nคำสั่งคัดลอกไว้แล้ว") }
            }
            if (opened) pendingStoryShare = { launchShare() }
        }
        val preview = LinearLayout(this).apply { orientation = 1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        preview.addView(TextView(this).apply {
            this.text = "$pendingStorySummary\n$imageList\nระบบส่งคำสั่งซ้ำทั้งข้อความแชร์และคลิปบอร์ด ตรวจภาพและข้อความใน ChatGPT ก่อนกดส่ง หากช่องว่างให้แตะค้างแล้วเลือก วาง"
            textSize = 13f
        })
        uris.forEach { uri -> preview.addView(ImageView(this).apply {
            setImageBitmap(previewBitmap(uri)); adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(-1,dp(120)).apply { bottomMargin = dp(8) }
            contentDescription = uri.lastPathSegment
        }) }
        preview.addView(TextView(this).apply { this.text = text; textSize = 14f; setTextIsSelectable(true) })
        AlertDialog.Builder(this).setTitle("ตรวจคำสั่งและภาพก่อนส่ง")
            .setView(ScrollView(this).apply { addView(preview) })
            .setPositiveButton("ส่งและเปิด ChatGPT") { _,_ -> launchShare() }
            .setNeutralButton("คัดลอก") { _,_ -> copyCommand(text) }
            .setNegativeButton("ยกเลิก",null).showDirector()
    }

    private fun storyVisualGuide(mode: String): String {
        val aspect = plan?.optString("aspect_ratio","9:16") ?: "9:16"
        val master = if (mode == "new")
            "ใช้ CHARACTER MASTER ของเรื่องนี้ตรงรายคน คงใบหน้า ผม ไหล่ ลำตัว เอว สะโพก แขน ขา และสัดส่วนร่างกายตามต้นฉบับ สำหรับตัวละครหญิงผู้ใหญ่คงสัดส่วนหน้าอกตาม MASTER"
        else "ใช้ภาพ MASTER ที่แนบตรงรายคน คงใบหน้า ผม ไหล่ ลำตัว เอว สะโพก แขน ขา และสัดส่วนร่างกายตามต้นฉบับ สำหรับตัวละครหญิงผู้ใหญ่คงสัดส่วนหน้าอกตาม MASTER"
        return "$master เปลี่ยนเป็นชุดใหม่ตามบทให้เหมาะกับบทบาท เลือกทรงเสื้อผ้าพอดีตัวตามรูปร่างเดิม ระบุชุด สถานที่ สิ่งของ และการกระทำของแต่ละคนให้ชัดเจน ภาพละครไทยสมจริง สัดส่วน $aspect เห็นใบหน้าและรูปร่างตั้งแต่ศีรษะถึงต้นขา ปรับระยะให้เห็นการกระทำครบ สีหน้าและสายตาตามเหตุการณ์ รักษาชุดและสถานที่เดิมเมื่อเป็นเหตุการณ์ต่อเนื่อง ภาพไม่มีข้อความหรือตราสัญลักษณ์ สร้างด้วยเครื่องมือสร้างภาพและแสดงภาพจริงในแชต หากสร้างไม่ได้ให้แจ้งตามจริง"
    }
    private fun sceneCastDescription(scene: JSONObject): String {
        val cast = scene.optJSONArray("characters") ?: return ""
        return (0 until cast.length()).joinToString("\n") { i ->
            val person = cast.getJSONObject(i)
            val master=masterFor(person)
            val exported=masterExportName(master,person.optString("master").startsWith("CHARACTER",true)) ?: person.optString("master")
            "${person.optString("name")} → $exported | ชุด: ${person.optString("outfit")}" }
    }
    private fun storySceneCommand(p: JSONObject, scene: JSONObject): String {
        val scenes = rows(p)
        val index = scenes.indexOfFirst { key(it) == key(scene) }
        val previous = scenes.getOrNull(index - 1)?.optString("action").orEmpty()
        val details = sceneCastDescription(scene)
        return "สร้างภาพหนึ่งภาพฉาก ${key(scene)} เรื่อง ${p.getString("title")}\n${storyVisualGuide(p.optString("character_mode","original"))}\n$details\nบทบาท: ${p.optJSONObject("cast_roles") ?: ""}\nสถานที่: ${scene.optString("location")}\nเวลาในเรื่อง: ${scene.optString("story_time")}\nอุปกรณ์: ${scene.optString("props")}\nผู้พูด: ${scene.optString("speaker")}\nเสียงบรรยาย: ${scene.optString("narration")}\nเหตุการณ์ต่อเนื่อง: ${scene.optString("continuity_group")}\nรายละเอียดฉาก: ${scene.getString("image_prompt")}\nการกระทำ: ${scene.getString("action")}\nบทพูดและอารมณ์: ${scene.optString("dialogue")}\nเหตุการณ์ก่อนหน้า: $previous\nภาพฉากก่อนหน้าที่แนบใช้เฉพาะความต่อเนื่องของชุด สถานที่ และสิ่งของ ตัวตนแต่ละคนยึด MASTER ต้นฉบับเป็นหลัก สร้างเฉพาะตัวละครในรายการฉากนี้ ตั้งชื่อ ${key(scene)}.png"
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
        val masterRefs = if(cast != null) (0 until cast.length()).map { cast.getJSONObject(it) }.map { person ->
            val source=masterFor(person)
            val exported=masterExportName(source,!original) ?: run { notice("ขาด MASTER ของ ${person.optString("name")}"); return }
            exported.substringBeforeLast('.') to source
        } else keys.map { id -> val source=File(project,"original_masters/$id.master").takeIf { it.exists() } ?: File(filesDir,"character_masters/$id.master"); "${id.uppercase()}_MASTER" to source }
        rememberScene(scene)
        shareText(storySceneCommand(p,scene),includeMasters=false,referenceImages=masterRefs+references)
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
                contentDescription = if (mainChannel) "กวิน รินรดา และเมย์ ภาพครึ่งตัวในฉากเดียวกัน"
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
ประเมินจำนวน EP และฉากตามเนื้อหาเอง สำหรับ Flow / Veo 3.1 Lite ทุกฉาก 8 วินาที แนวตั้ง 9:16 Locked-off static camera ${if (mode == "original") "ตัวละครหลัก กวิน รินรดา เมย์ อ้างอิง MASTER ของแต่ละคนเพื่อคงใบหน้า ผม รูปร่าง และสัดส่วนร่างกายตาม MASTER รายคน" else "สร้างชื่อและใบหน้าตัวละครใหม่สำหรับเรื่องนี้ ใช้ภาพที่แนบอ้างอิงเฉพาะรูปร่างและสัดส่วนของตัวละครผู้ใหญ่ ไม่ใช้ชื่อหรือใบหน้าเดิม สร้างภาพ MASTER ใหม่ก่อนสร้างฉาก ตั้งชื่อ CHARACTER_01.png, CHARACTER_02.png ตามจำนวนที่ต้องใช้ แล้วใช้ใบหน้าใหม่เดิมอย่างต่อเนื่องทุกฉาก เก็บรายละเอียดตัวละครและชื่อไฟล์ MASTER ใน bible"}\n${storyVisualGuide(mode)}\nใช้วิธีเดียวกับการทดสอบสร้างภาพ: MASTER รายคนเป็นต้นฉบับของตัวละคร ชุด สถานที่และการกระทำเป็นข้อมูลใหม่ตามบท เขียน image_prompt พร้อมใช้ของแต่ละฉากอย่างกระชับ: ตัวละครและ MASTER รายคน → ชุดรายคน → สถานที่และสิ่งของ → การกระทำ → สีหน้าและสายตา ระบุชนิด สี และทรงชุดพอดีตัวให้เหมาะกับบทบาท รวมทั้งสถานที่จริงในแต่ละฉากเพื่อสร้างซ้ำได้ ชุดเปลี่ยนตามวันหรือเหตุการณ์ และคงเดิมในเหตุการณ์ต่อเนื่อง ใช้คำอธิบายเชิงบวกสั้น ๆ โดยไม่เพิ่มรายการข้อห้ามยาวซ้ำทุกฉาก เหตุการณ์ทุกฉากมาจากบทของเรื่องนี้ จบปมครบทุกเรื่อง ผู้พูดแต่ละฉากชัดเจน แยก speaker และ narration ผู้บรรยายชายเป็นเสียงนอกภาพ ตัวละครไม่ขยับปากตามเสียงบรรยาย
แผนความต่อเนื่องของตัวละคร: เก็บชื่อ → ไฟล์ MASTER ตายตัวใน bible: กวิน → GAWIN_MASTER, รินรดา → RINLADA_MASTER, เมย์ → MIND_MASTER ใช้นามสกุล .png/.jpg/.webp ตามไฟล์ภาพที่แนบ โดยชื่อฐานจับคู่ตรงคน สำหรับช่องตัวละครใหม่ใช้ชื่อใหม่ → CHARACTER_01.png ตามที่สร้าง แต่ละฉากมี characters เฉพาะคนที่ปรากฏ พร้อม name, master, outfit ระบุ location และ continuity_group เหตุการณ์เดียวกันใช้รหัสกลุ่มเดียวกัน ชุดและรายละเอียดสถานที่เหมือนเดิม เมื่อเปลี่ยนวันหรือเหตุการณ์จึงเปลี่ยนกลุ่ม
ก่อนใช้เครื่องมือสร้างภาพแต่ละฉาก อ้างอิง MASTER ต้นฉบับตรงรายคนอีกครั้ง ใช้ภาพฉากก่อนหน้าในกลุ่มเดียวกันเป็นตัวช่วยเรื่องชุด สถานที่ และสิ่งของ โดยตัวตนยึด MASTER เป็นหลัก ตรวจชื่อและชุดของแต่ละคนก่อนส่งสร้าง ไม่ใช้ภาพที่คลาดเคลื่อนแทน MASTER หากขาดต้นฉบับให้แจ้งก่อนสร้าง
ส่งไฟล์ story.json เป็น JSON ไม่มี markdown โครงสร้าง:
{"schema_version":3,"channel":${JSONObject.quote(channel)},"character_mode":"$mode","title":"ชื่อเรื่อง","category":${JSONObject.quote(category)},"tagline":"คำโปรยเรื่อง","episode_count":1,"scene_count":1,"bible":"ความสัมพันธ์และพล็อต","episodes":[{"ep":1,"title":"ชื่อตอนภาษาไทย","tagline":"คำโปรยตอน","scene_count":1,"scenes":[{"scene":1,"characters":[{"name":"ชื่อตัวละครที่อยู่ในฉาก","master":"ชื่อไฟล์ MASTER ตรงรายคน","outfit":"ชนิด สี และรายละเอียดชุด"}],"location":"สถานที่และสิ่งของหลัก","continuity_group":"DAY01_LOCATION01_EVENT01","action":"เหตุการณ์","dialogue":"บทพูดภาษาไทย","speaker":"ชื่อผู้พูดหรือว่างเมื่อไม่มีบทพูด","narration":"เสียงบรรยายชายหรือว่าง","image_prompt":"ตัวละครและ MASTER ตรงรายคน คงใบหน้า ผม รูปร่างตามต้นฉบับ; ชุดใหม่รายคนระบุชนิดสีและทรง; สถานที่และสิ่งของ; การกระทำ สีหน้าและสายตาตามบท; ภาพละครไทยสมจริง 9:16 เห็นการกระทำครบ ไม่มีข้อความ","flow_prompt":"Flow / Veo 3.1 Lite: English prompt + Thai speech, 8 seconds, 9:16, locked-off static camera"}]}]}
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
            val target=File(dir,"CHARACTER_$index.png")
            if(target.exists()) archiveVersion(target,folder,"CHARACTER_$index","master")
            require(temporary.renameTo(target)) { "บันทึก MASTER ไม่สำเร็จ" }
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
                        if (target.exists()) archiveVersion(target, importFolder, key(s), "image")
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
    private fun previewBitmap(uri: Uri): android.graphics.Bitmap? {
        return runCatching {
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
            val options=android.graphics.BitmapFactory.Options().apply { inSampleSize=1 }
            while(bounds.outWidth/options.inSampleSize>1200 || bounds.outHeight/options.inSampleSize>1600) options.inSampleSize *= 2
            contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
        }.getOrNull()
    }
    private fun copyCommand(text: String) {
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("DIRECTOR X",text))
        notice("คัดลอกแล้ว เปิด ChatGPT หรือ Flow แล้วเลือกวาง")
    }
    private fun saveStory() {
        val p = plan ?: return
        val target = File(project,"story.json")
        if (target.exists()) target.copyTo(File(project,"story.previous.json"),overwrite=true)
        val tmp = File(project,"story.pending")
        tmp.writeText(p.toString(2)); check(tmp.renameTo(target)) { "บันทึกบทไม่ได้" }
    }
    private fun rememberScene(s: JSONObject) {
        prefs.edit().putString("last_scene_${project.name}",key(s)).apply()
        File(project,"last_scene.txt").writeText(key(s))
    }
    private fun video(s: JSONObject) = File(project,"videos/${key(s)}.mp4")
    private fun archiveVersion(source: File, folder: File, id: String, kind: String) {
        val dst = File(folder,"versions/$id/$kind/${System.currentTimeMillis()}_${UUID.randomUUID()}.${source.extension}")
        dst.parentFile!!.mkdirs(); source.copyTo(dst)
    }
    private fun resumeScene() {
        val p = plan ?: return notice("สร้างหรือนำเข้าบทก่อนครับ")
        val scenes = rows(p)
        val last = prefs.getString("last_scene_${project.name}",null) ?: File(project,"last_scene.txt").takeIf { it.exists() }?.readText().orEmpty()
        sceneMenu(scenes.firstOrNull { key(it) == last } ?: scenes.firstOrNull { !image(it).exists() } ?: scenes.first())
    }
    private fun episodesMenu() {
        val p = plan ?: return notice("ยังไม่มีบท • เริ่มเรื่องใหม่หรือรับ story.json")
        val episodes = p.getJSONArray("episodes")
        choiceMenu("🎞️ ${p.optString("title")}",(0 until episodes.length()).map { i ->
            val ep = episodes.getJSONObject(i); val scenes = rows(p).filter { it.getInt("ep") == ep.getInt("ep") }
            "EP ${ep.getInt("ep")} • ${ep.optString("title")}\nภาพ ${scenes.count { image(it).exists() }}/${scenes.size} • วิดีโอ ${scenes.count { video(it).exists() }}/${scenes.size}"
        }) { index ->
            val ep = episodes.getJSONObject(index).getInt("ep")
            val scenes = rows(p).filter { it.getInt("ep") == ep }
            choiceMenu("EP $ep • เลือกฉาก",scenes.map { "${key(it)} • ${if (image(it).exists()) "มีภาพ" else "ขาดภาพ"} • ${if (video(it).exists()) "มีวิดีโอ" else "รอวิดีโอ"}" } + "📦 ส่งออก EP นี้") { i ->
                if (i == scenes.size) { exportEpisode = ep; backupMode = false; zipPicker.launch(StoryFiles.zipName(p.optString("title")+"_EP%02d".format(ep))) }
                else sceneMenu(scenes[i])
            }
        }
    }
    private fun flowCommand(s: JSONObject): String {
        val speaker=s.optString("speaker")
        val narration=s.optString("narration")
        return s.optString("flow_prompt") + "\nCurrent scene: ${s.optString("action")}\nLocation: ${s.optString("location")}\nWardrobe: ${sceneCastDescription(s)}" +
            (if(speaker.isNotBlank()) "\nOnly $speaker speaks this Thai dialogue: ${s.optString("dialogue")}. Other characters react naturally without speaking." else "\nThai dialogue: ${s.optString("dialogue")}. Follow the speaker assignments in the script.") +
            (if(narration.isNotBlank()) "\nMale narrator voice-over in Thai: $narration. On-screen characters do not lip-sync the narration." else "")
    }
    private fun sceneMenu(s: JSONObject) {
        rememberScene(s)
        val choices = listOf("🖼️ ดูภาพ / เทียบ MASTER", "🎬 สร้างภาพผ่าน ChatGPT", "✏️ แก้บท ชุด และสถานที่", "🛠️ แก้ภาพเฉพาะส่วนผ่าน ChatGPT", "📋 คัดลอกคำสั่ง Flow", "📥 นำเข้าภาพฉากนี้", "🎥 นำเข้าวิดีโอฉากนี้", "▶ ดูวิดีโอ", "🗂️ เวอร์ชันภาพ / วิดีโอ", "✅ ตรวจรับภาพ / ต้องแก้", "➡ ฉากถัดไป")
        choiceMenu("${key(s)} • ${s.optString("location")}",choices) { i -> when(i) {
            0 -> compareScene(s)
            1 -> sendSceneImage(plan!!,s)
            2 -> editScene(s)
            3 -> repairScene(s)
            4 -> copyCommand(flowCommand(s))
            5 -> { mediaSlot = key(s); mediaKind = "image"; mediaProject = project.name; sceneMediaPicker.launch(arrayOf("image/*")) }
            6 -> { mediaSlot = key(s); mediaKind = "video"; mediaProject = project.name; sceneMediaPicker.launch(arrayOf("video/mp4")) }
            7 -> playVideo(s)
            8 -> versionsMenu(s)
            9 -> choiceMenu("ตรวจรับ ${key(s)}",listOf("ผ่าน • ผู้ใช้ตรวจแล้ว", "ต้องแก้ • ยังไม่ผ่าน")) { result ->
                if (!image(s).exists()) notice("ยังไม่มีภาพให้ตรวจรับ") else {
                    s.put("user_review",if(result == 0) "approved" else "needs_revision"); saveStory(); render()
                }
            }
            10 -> { val scenes = rows(plan!!); val next = scenes.indexOfFirst { key(it) == key(s) }+1
                if (next in scenes.indices) sceneMenu(scenes[next]) else notice("ถึงฉากสุดท้ายแล้ว") }
        } }
    }
    private fun compareScene(s: JSONObject) {
        val panel = LinearLayout(this).apply { orientation = 1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        fun picture(title: String, file: File) {
            panel.addView(TextView(this).apply { text = title; textSize = 15f; setTypeface(null,Typeface.BOLD) })
            if (file.exists()) panel.addView(ImageView(this).apply {
                setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(-1,dp(280))
                setOnClickListener { fullImage(file,title) }
            }) else panel.addView(TextView(this).apply { text = "ยังไม่มีไฟล์" })
        }
        val cast = s.optJSONArray("characters") ?: JSONArray()
        for (i in 0 until cast.length()) {
            val person = cast.getJSONObject(i); picture("MASTER • ${person.optString("name")}",masterFor(person))
        }
        picture("ผลลัพธ์ • ${key(s)} • ${s.optString("user_review","รอตรวจ")}",image(s))
        panel.addView(TextView(this).apply { text = "เป้าหมายความเหมือน 95% • ยังไม่มีการวัดเปอร์เซ็นต์อัตโนมัติ\n${sceneCastDescription(s)}\n${s.optString("action")}\n${s.optString("dialogue")}" })
        AlertDialog.Builder(this).setTitle("เปรียบเทียบต้นฉบับ").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("ปิด",null).showDirector()
    }
    private fun fullImage(file: File, title: String) {
        val imageView = ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(imageView).setPositiveButton("ปิด",null).create()
        dialog.show(); dialog.window?.setLayout(-1,-1)
    }
    private fun editScene(s: JSONObject) {
        val draftFile=File(project,"drafts/${key(s)}.json")
        val draft=runCatching { if(draftFile.exists()) JSONObject(draftFile.readText()) else s }.getOrDefault(s)
        val previousGroup=s.optString("continuity_group")
        val fields = listOf("action" to "เหตุการณ์", "dialogue" to "บทพูด", "speaker" to "ผู้พูด", "narration" to "เสียงบรรยาย", "location" to "สถานที่", "continuity_group" to "กลุ่มความต่อเนื่อง", "props" to "อุปกรณ์", "story_time" to "เวลาในเรื่อง", "image_prompt" to "คำสั่งภาพ", "flow_prompt" to "คำสั่ง Flow")
        val panel = LinearLayout(this).apply { orientation = 1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        val inputs = fields.map { (key,label) -> key to EditText(this).apply { hint = label; setText(draft.optString(key,s.optString(key))); panel.addView(this) } }
        val cast = s.optJSONArray("characters") ?: JSONArray()
        val outfits = (0 until cast.length()).map { i -> i to EditText(this).apply {
            hint = "ชุด ${cast.getJSONObject(i).optString("name")}"; setText(draft.optJSONArray("characters")?.optJSONObject(i)?.optString("outfit") ?: cast.getJSONObject(i).optString("outfit")); panel.addView(this)
        } }
        fun persistDraft() {
            val copy=JSONObject(s.toString())
            inputs.forEach { (key,input) -> copy.put(key,input.text.toString()) }
            val people=copy.optJSONArray("characters")
            outfits.forEach { (i,input) -> people?.optJSONObject(i)?.put("outfit",input.text.toString()) }
            draftFile.parentFile!!.mkdirs(); draftFile.writeText(copy.toString(2))
        }
        val watcher=object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) { persistDraft() }
            override fun afterTextChanged(text: android.text.Editable?) {}
        }
        (inputs.map { it.second }+outfits.map { it.second }).forEach { it.addTextChangedListener(watcher) }
        AlertDialog.Builder(this).setTitle("แก้บท ${key(s)} • เก็บร่างอัตโนมัติ")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("บันทึก") { _,_ ->
                if(inputs.any { it.first in listOf("action","image_prompt","flow_prompt") && it.second.text.isBlank() }) { notice("เหตุการณ์ คำสั่งภาพ และคำสั่ง Flow ต้องไม่ว่าง"); return@setPositiveButton }
                inputs.forEach { (key,input) -> s.put(key,input.text.toString().trim()) }
                outfits.forEach { (i,input) -> cast.getJSONObject(i).put("outfit",input.text.toString().trim()) }
                val group = s.optString("continuity_group")
                val affected = rows(plan!!).filter { key(it) == key(s) || (group.isNotBlank() && it.optString("continuity_group") == group || previousGroup.isNotBlank() && it.optString("continuity_group") == previousGroup) }
                affected.forEach { it.put("user_review","needs_revision") }; saveStory(); draftFile.delete(); render()
                notice("บันทึกแล้ว • ตรวจความต่อเนื่อง ${affected.joinToString { key(it) }}\nเก็บภาพและวิดีโอเดิมไว้แล้ว ต้องตรวจคำสั่ง Flow ให้ตรงบทที่แก้")
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun masterFor(person: JSONObject): File {
        if (person.optString("master").startsWith("CHARACTER",true)) {
            val requested=person.optString("master"); val exact=File(project,"masters/$requested")
            return if(exact.exists()) exact else File(project,"masters").listFiles()?.firstOrNull { it.nameWithoutExtension.equals(requested.substringBeforeLast('.'),true) } ?: exact
        }
        val id = StoryIdentity.keyFor(person.optString("name")) ?: return File(project,"missing.master")
        return File(project,"original_masters/$id.master").takeIf { it.exists() } ?: File(filesDir,"character_masters/$id.master")
    }
    private fun repairScene(s: JSONObject) {
        if (!image(s).exists()) return notice("ต้องนำเข้าภาพฉากก่อนแก้ภาพ")
        val input = EditText(this).apply { hint = "ส่วนที่ต้องแก้ เช่น ชุดใหม่ / ฉากหลัง / สัดส่วนตาม MASTER" }
        AlertDialog.Builder(this).setTitle("แก้เฉพาะส่วน ${key(s)}").setView(input).setPositiveButton("ตรวจและส่ง") { _,_ ->
            val change = input.text.toString().trim()
            if (change.isBlank()) { notice("ระบุส่วนที่ต้องแก้ก่อน"); return@setPositiveButton }
            val cast = s.optJSONArray("characters") ?: JSONArray()
            val refs = (0 until cast.length()).map { cast.getJSONObject(it) }.map { (masterExportName(masterFor(it),it.optString("master").startsWith("CHARACTER",true)) ?: it.optString("master")).substringBeforeLast('.') to masterFor(it) } + (key(s) to image(s))
            shareText("แก้ภาพ ${key(s)} ที่แนบด้วยเครื่องมือแก้ไขภาพ แสดงภาพจริงหนึ่งภาพ\n$change\nคงใบหน้า ผม รูปร่างและสัดส่วนตาม MASTER ตรงรายคน คงองค์ประกอบส่วนอื่นของภาพฉาก\n${sceneCastDescription(s)}\nตั้งชื่อ ${key(s)}.png",includeMasters=false,referenceImages=refs)
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private var mediaSlot: String?
        get() = prefs.getString("media_slot",null)
        set(value) { prefs.edit().putString("media_slot",value).apply() }
    private var mediaKind: String
        get() = prefs.getString("media_kind","image")!!
        set(value) { prefs.edit().putString("media_kind",value).apply() }
    private var mediaProject: String
        get() = prefs.getString("media_project",project.name)!!
        set(value) { prefs.edit().putString("media_project",value).apply() }
    private val sceneMediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = mediaSlot
        if (uri != null && id != null) {
            val folder = File(base,mediaProject); val kind = mediaKind
            val target = File(folder,if(kind == "video") "videos/$id.mp4" else "images/$id.img")
            fun accept() { background {
                target.parentFile!!.mkdirs(); val temp = File(target.parentFile,target.name+".pending")
                try {
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    require(temp.length() > 0) { "ไฟล์ว่าง" }
                    if (kind == "image") {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        android.graphics.BitmapFactory.decodeFile(temp.path,bounds); require(bounds.outWidth > 0) { "อ่านภาพไม่ได้" }
                    } else {
                        val retriever = android.media.MediaMetadataRetriever()
                        try { retriever.setDataSource(temp.path); require(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it > 0 } == true) { "อ่านวิดีโอไม่ได้" } } finally { retriever.release() }
                    }
                    if (target.exists()) archiveVersion(target,folder,id,kind)
                    check(temp.renameTo(target)) { "บันทึกไม่ได้" }
                    val storyFile = File(folder,"story.json")
                    val story = JSONObject(storyFile.readText()); rows(story).firstOrNull { key(it) == id }?.put("user_review","pending")
                    storyFile.writeText(story.toString(2))
                } finally { temp.delete() }
            } }
            AlertDialog.Builder(this).setTitle("ตรวจไฟล์ก่อนนำเข้า").setMessage("${displayName(uri)} → $id\nชนิด: $kind\n${if(target.exists()) "ไฟล์เดิมจะเก็บเป็นเวอร์ชันก่อนแทน" else "เพิ่มไฟล์ใหม่"}")
                .setPositiveButton("นำเข้า") { _,_ -> accept() }.setNegativeButton("ยกเลิก",null).showDirector()
        }
        mediaSlot = null
    }
    private fun playVideo(s: JSONObject) {
        val file = video(s); if (!file.exists()) return notice("ยังไม่มีวิดีโอฉากนี้ • สร้างใน Flow แล้วนำเข้ากลับ")
        val view = VideoView(this).apply {
            setVideoPath(file.path); val controls = MediaController(this@StudioActivity); controls.setAnchorView(this); setMediaController(controls)
            setOnPreparedListener { start() }; setOnErrorListener { _,_,_ -> notice("เล่นไฟล์นี้ไม่ได้ กรุณานำเข้าวิดีโอ MP4 ที่รองรับ"); true }
        }
        val dialog = AlertDialog.Builder(this).setTitle(key(s)).setView(view).setPositiveButton("ปิด",null).create()
        dialog.setOnDismissListener { view.stopPlayback() }; dialog.show(); dialog.window?.setLayout(-1,dp(560))
    }
    private fun versionsMenu(s: JSONObject) {
        choiceMenu("เวอร์ชัน ${key(s)}",listOf("ภาพ", "วิดีโอ")) { choice ->
            val kind = if(choice == 0) "image" else "video"
            val files = File(project,"versions/${key(s)}/$kind").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            choiceMenu("เลือกเวอร์ชัน $kind",files.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())) }) { i ->
                val source = files[i]; val target = if(kind == "image") image(s) else video(s)
                AlertDialog.Builder(this).setMessage("ใช้เวอร์ชันนี้? เวอร์ชันปัจจุบันจะเก็บไว้")
                    .setPositiveButton("ใช้เวอร์ชันนี้") { _,_ ->
                        if(target.exists()) archiveVersion(target,project,key(s),kind)
                        target.parentFile!!.mkdirs(); source.copyTo(target,overwrite=true); s.put("user_review","pending"); saveStory(); render()
                    }.setNegativeButton("ยกเลิก",null).showDirector()
            }
        }
    }
    private fun auditStory() {
        val p = plan ?: return notice("ยังไม่มีบท")
        val report = rows(p).map { s ->
            val cast = s.optJSONArray("characters") ?: JSONArray()
            val absent = (0 until cast.length()).map { cast.getJSONObject(it) }.filter { !masterFor(it).exists() }.map { it.optString("name") }
            "${key(s)} • ภาพ ${if(image(s).exists()) "✓" else "ขาด"} • วิดีโอ ${if(video(s).exists()) "✓" else "ขาด"} • ตรวจรับ ${s.optString("user_review","รอตรวจ")}\n" +
                (if(absent.isEmpty()) "" else "ขาด MASTER: ${absent.joinToString()}\n") +
                (if(s.optString("dialogue").length > 55) "ตรวจบทพูด: อาจยาวเกิน 8 วินาที\n" else "")
        }.joinToString("\n")
        val text = TextView(this).apply { this.text = report; setTextIsSelectable(true); setPadding(dp(16),dp(12),dp(16),dp(12)) }
        AlertDialog.Builder(this).setTitle("ตรวจงาน • ${p.optString("title")}").setView(ScrollView(this).apply { addView(text) }).setPositiveButton("ปิด",null).setNeutralButton("คัดลอก") { _,_ -> copyCommand(report) }.showDirector()
    }
    private fun storiesMenu() {
        val folders = base.listFiles()?.filter { File(it,"story.json").exists() }?.sortedByDescending { File(it,"story.json").lastModified() }.orEmpty()
        choiceMenu("📚 เรื่องของฉัน",folders.map { f ->
            runCatching { JSONObject(File(f,"story.json").readText()).optString("title") }.getOrDefault(f.name) + if(File(f,"archived").exists()) " • เก็บแล้ว" else ""
        }) { i ->
            prefs.edit().putString("project",folders[i].name).apply(); render()
            choiceMenu("จัดการเรื่อง",listOf("▶ ทำต่อ", "✏️ เปลี่ยนชื่อ", "📑 ทำสำเนา", "📁 เก็บ / เปิดใช้งาน", "🗑️ ลบเรื่อง")) { action -> when(action) {
                0 -> resumeScene()
                1 -> { val input = EditText(this).apply { setText(plan?.optString("title")) }
                    AlertDialog.Builder(this).setTitle("ชื่อเรื่อง").setView(input).setPositiveButton("บันทึก") { _,_ ->
                        if(input.text.isNotBlank()) { plan?.put("title",input.text.toString().trim()); saveStory(); render() }
                    }.setNegativeButton("ยกเลิก",null).showDirector() }
                2 -> background { val source = project; val target = File(base,UUID.randomUUID().toString()); check(source.copyRecursively(target))
                    val p = JSONObject(File(target,"story.json").readText()); p.put("title",p.optString("title")+" (สำเนา)"); File(target,"story.json").writeText(p.toString(2)); prefs.edit().putString("project",target.name).commit() }
                3 -> { val file = File(project,"archived"); if(file.exists()) file.delete() else file.writeText("archived"); render() }
                4 -> { val folder = project; AlertDialog.Builder(this).setMessage("ลบเรื่องและไฟล์ทั้งหมด? สำรอง ZIP ก่อนลบ")
                    .setPositiveButton("ลบเรื่อง") { _,_ -> folder.deleteRecursively(); prefs.edit().putString("project",UUID.randomUUID().toString()).apply(); render() }
                    .setNegativeButton("ยกเลิก",null).showDirector() }
            } }
        }
    }
    private fun settingsMenu() {
        choiceMenu("⚙️ ตั้งค่าและดูแลงาน",listOf("🔄 อัปเดตแอป", "📦 ส่งออกงานทั้งหมด / สำรอง", "📥 กู้คืนชุดสำรอง", "🎭 ตัวเลือกเดิม / MASTER", "💾 พื้นที่และข้อผิดพลาด", "↶ กู้คืนบทก่อนแก้ล่าสุด", "🎛️ ตั้งค่าการสร้างเรื่อง", "🛟 สำรองทั้งเรื่อง รวม MASTER และเวอร์ชัน", "↗ ส่งคำสั่งล่าสุดซ้ำ")) { i -> when(i) {
            0 -> updater.check()
            1 -> { if(plan == null) notice("ยังไม่มีบท") else { exportEpisode=null; backupMode=false; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title"))) } }
            2 -> restorePicker.launch(arrayOf("application/zip"))
            3 -> toolsMenu()
            4 -> { val used = base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val message = "DIRECTOR X ${packageManager.getPackageInfo(packageName,0).versionName}\nงานทั้งหมด ${used/1024/1024} MB\nพื้นที่ว่าง ${filesDir.usableSpace/1024/1024} MB\nเรื่อง ${plan?.optString("title").orEmpty()}\n${File(filesDir,"last_error.txt").takeIf { it.exists() }?.readText().orEmpty()}"
                AlertDialog.Builder(this).setMessage(message).setPositiveButton("ปิด",null).setNeutralButton("คัดลอก") { _,_ -> copyCommand(message) }.showDirector() }
            5 -> { val previous=File(project,"story.previous.json"); if(!previous.exists()) notice("ยังไม่มีบทก่อนแก้") else AlertDialog.Builder(this).setMessage("กู้คืนบทก่อนแก้ล่าสุด? ไฟล์ภาพยังอยู่")
                .setPositiveButton("กู้คืน") { _,_ -> val old=previous.readText(); saveStory(); File(project,"story.json").writeText(old); render() }.setNegativeButton("ยกเลิก",null).showDirector() }
            6 -> planningSettings()
            8 -> retryLastCommand()
            7 -> { if(plan == null) notice("ยังไม่มีบท") else { backupMode=true; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title")+"_สำรอง")) } }
        } }
    }
    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) AlertDialog.Builder(this).setTitle("กู้คืนชุดสำรอง")
            .setMessage("${displayName(uri)}\nจะกู้คืนเป็นเรื่องใหม่ ไม่เขียนทับเรื่องเดิม รวม MASTER ภาพ วิดีโอ และเวอร์ชันที่มีในชุดสำรอง")
            .setPositiveButton("กู้คืน") { _,_ -> background {
                val staging=File(cacheDir,"restore_${UUID.randomUUID()}")
                try {
                    contentResolver.openInputStream(uri)!!.use { StudioArchive.read(it,staging) }
                    val p=JSONObject(File(staging,"story.json").readText()); validate(p)
                    val folder=File(base,UUID.randomUUID().toString()); check(staging.copyRecursively(folder))
                    prefs.edit().putString("project",folder.name).commit()
                } finally { staging.deleteRecursively() }
            } }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun retryLastCommand() {
        val file=File(project,"last_payload.json")
        if(!file.exists()) return notice("ยังไม่มีคำสั่งที่เตรียมส่ง")
        runCatching {
            val payload=JSONObject(file.readText()); val images=payload.getJSONArray("images")
            val references=(0 until images.length()).map { i ->
                val uri=Uri.parse(images.getString(i))
                // Resolve only the app's own export roots, never an external path.
                require(uri.authority=="$packageName.fileprovider") { "แหล่งภาพส่งซ้ำไม่ถูกต้อง" }
                val relative=uri.path?.trimStart('/') ?: error("ไม่มีเส้นทางภาพ")
                require(relative.startsWith("character_master_exports/") || relative.startsWith("scene_reference_exports/")) { "ภาพไม่ได้อยู่ในพื้นที่ส่งออก" }
                val source=File(cacheDir,relative)
                require(source.canonicalPath.startsWith(cacheDir.canonicalPath+File.separator) && source.isFile) { "ภาพส่งซ้ำหมดอายุ กรุณาส่งจากเมนูฉากอีกครั้ง" }
                source.nameWithoutExtension to source
            }
            shareText(payload.getString("text"),lockCreation=payload.optBoolean("creation"),includeMasters=false,referenceImages=references)
        }.onFailure { notice(it.message ?: "ส่งซ้ำไม่ได้") }
    }
    private fun planningSettings() {
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        val fields=listOf("episodes" to "จำนวน EP (ว่าง = AI ประเมิน)", "scenes" to "ฉากต่อ EP (ว่าง = AI ประเมิน)", "duration" to "วินาทีต่อฉาก", "ratio" to "สัดส่วนภาพ 9:16 หรือ 16:9")
        val inputs=fields.map { (id,label) -> id to EditText(this).apply { hint=label; setText(prefs.getString("plan_$id",if(id=="duration") "8" else if(id=="ratio") "9:16" else "")); panel.addView(this) } }
        AlertDialog.Builder(this).setTitle("ค่าตั้งต้นสำหรับเรื่องใหม่").setView(panel).setPositiveButton("บันทึก") { _,_ ->
            val values=inputs.associate { it.first to it.second.text.toString().trim() }
            if(values["ratio"] !in listOf("9:16","16:9") || values["duration"]?.toIntOrNull()?.let { it in 1..60 } != true || listOf("episodes","scenes").any { values[it]!!.isNotBlank() && values[it]?.toIntOrNull()?.let { n -> n in 1..100 } != true }) notice("ตรวจจำนวน 1–100 ระยะเวลา 1–60 และสัดส่วน 9:16/16:9")
            else { val edit=prefs.edit(); values.forEach { (k,v) -> edit.putString("plan_$k",v) }; edit.apply(); notice("บันทึกค่าตั้งต้นแล้ว ใช้กับคำสั่งสร้างเรื่องใหม่") }
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private var castTarget: Pair<String,File>?
        get() { val name=prefs.getString("cast_target_name",null) ?: return null; val path=prefs.getString("cast_target_path",null) ?: return null; return name to File(path) }
        set(value) { prefs.edit().putString("cast_target_name",value?.first).putString("cast_target_path",value?.second?.path).apply() }
    private val castPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target=castTarget; castTarget=null
        if(uri!=null && target!=null) background {
            val file=target.second; file.parentFile!!.mkdirs(); val temp=File(file.parentFile,file.name+".pending")
            try {
                contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }; android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                require(bounds.outWidth>0) { "อ่าน MASTER ไม่ได้" }
                if(file.exists()) archiveVersion(file,project,target.first,"master")
                check(temp.renameTo(file))
                val affected=rows(plan!!).filter { scene -> scene.optJSONArray("characters")?.let { a -> (0 until a.length()).any { a.getJSONObject(it).optString("name")==target.first } } == true }
                affected.forEach { it.put("user_review","needs_revision") }; saveStory()
            } finally { temp.delete() }
        }
    }
    private fun castMenu() {
        val p=plan ?: return characterMasters()
        val people=rows(p).flatMap { scene -> scene.optJSONArray("characters")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty() }.distinctBy { it.optString("name") }
        choiceMenu("🎭 ตัวละครเรื่องนี้",people.map { "${it.optString("name")} • ${if(masterFor(it).exists()) "มี MASTER" else "ขาด MASTER"}" }) { i ->
            val person=people[i]; val name=person.optString("name")
            choiceMenu(name,listOf("🖼️ ดู MASTER", "📷 เปลี่ยน MASTER รายเรื่อง", "✏️ บทบาทตัวละคร")) { action -> when(action) {
                0 -> { val file=masterFor(person); if(file.exists()) fullImage(file,name) else notice("ยังไม่มี MASTER") }
                1 -> { val id=StoryIdentity.keyFor(name)
                    val file=if(p.optString("character_mode","original")=="new") masterFor(person) else if(id!=null) File(project,"original_masters/$id.master") else return@choiceMenu
                    AlertDialog.Builder(this).setMessage("เปลี่ยน MASTER ของ $name เฉพาะเรื่องนี้ • ฉากที่ใช้คนนี้จะถูกระบุว่าต้องตรวจใหม่")
                        .setPositiveButton("เลือกรูป") { _,_ -> castTarget=name to file; castPicker.launch(arrayOf("image/*")) }.setNegativeButton("ยกเลิก",null).showDirector() }
                2 -> { val input=EditText(this).apply { hint="บทบาทและลักษณะตัวละคร"; setText(p.optJSONObject("cast_roles")?.optString(name)) }
                    AlertDialog.Builder(this).setTitle(name).setView(input).setPositiveButton("บันทึก") { _,_ -> val roles=p.optJSONObject("cast_roles") ?: JSONObject(); roles.put(name,input.text.toString()); p.put("cast_roles",roles); saveStory() }.setNegativeButton("ยกเลิก",null).showDirector() }
            } }
        }
    }

    private fun moviesMenu() {
        val p=plan ?: return notice("นำเข้าบทและคลิปก่อนครับ")
        val eps=p.getJSONArray("episodes")
        choiceMenu("🎬 รวมหนังและดาวน์โหลด MP4",(0 until eps.length()).map { i -> val ep=eps.getJSONObject(i); "EP ${ep.getInt("ep")} • ${ep.optString("title")}" }) { i -> movieEpisode(eps.getJSONObject(i)) }
    }
    private fun movieEpisode(ep: JSONObject) {
        choiceMenu("EP ${ep.getInt("ep")} • ${ep.optString("title")}",listOf("🎬 ตรวจคลิปและรวม EP", "⏳ ดูความคืบหน้า / ยกเลิก", "▶ ดูหนังที่รวมแล้ว", "⬇ ดาวน์โหลด MP4", "↗ แชร์ MP4", "🗂️ หนังเวอร์ชันก่อนหน้า", "🎞️ จัดการคลิปฉาก", "↕ จัดลำดับคลิป")) { i -> when(i) {
            0 -> prepareMovie(ep)
            1 -> movieProgress(ep.getInt("ep"))
            2 -> latestMovie(ep.getInt("ep"))?.let { playMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            3 -> latestMovie(ep.getInt("ep"))?.let { downloadMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            4 -> latestMovie(ep.getInt("ep"))?.let { shareMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            5 -> { val jobs=movieJobs(ep.getInt("ep")).filter { File(it,"output.mp4").exists() && readMovieState(it).optString("state")=="complete" }
                choiceMenu("เลือกเวอร์ชัน",jobs.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())) }) { n ->
                    choiceMenu("หนังเวอร์ชันนี้",listOf("ดูหนัง", "ดาวน์โหลด MP4", "แชร์")) { action -> when(action) { 0 -> playMovie(jobs[n]); 1 -> downloadMovie(jobs[n]); 2 -> shareMovie(jobs[n]) } }
                }
            }
            6 -> episodesMenu()
            7 -> reorderMovie(ep)
        } }
    }
    private fun orderedMovieScenes(ep: JSONObject): List<JSONObject> {
        val scenes=rows(plan!!).filter { it.getInt("ep")==ep.getInt("ep") }.sortedBy { it.getInt("scene") }
        val order=ep.optJSONArray("render_order") ?: return scenes
        val keys=(0 until order.length()).map { order.getString(it) }
        require(keys.size==scenes.size && keys.toSet()==scenes.map { key(it) }.toSet()) { "ลำดับคลิปไม่ตรงบท กรุณาจัดลำดับใหม่หรือคืนลำดับเลขฉาก" }
        return keys.map { id -> scenes.first { key(it)==id } }
    }
    private fun reorderMovie(ep: JSONObject) {
        val scenes=rows(plan!!).filter { it.getInt("ep")==ep.getInt("ep") }.sortedBy { it.getInt("scene") }
        val ordered=runCatching { orderedMovieScenes(ep) }.getOrDefault(scenes)
        val ids=ordered.map { key(it) }.toMutableList()
        choiceMenu("จัดลำดับ • เลือกคลิปเพื่อเลื่อน",ids.mapIndexed { i,id -> "${i+1}. $id" }+"คืนลำดับเลขฉาก") { index ->
            if(index==ids.size) { ep.remove("render_order"); saveStory(); reorderMovie(ep) }
            else choiceMenu(ids[index],listOf("เลื่อนขึ้น", "เลื่อนลง")) { action ->
                val destination=if(action==0) index-1 else index+1
                if(destination !in ids.indices) notice("อยู่ปลายลำดับแล้ว") else {
                    val item=ids.removeAt(index); ids.add(destination,item); ep.put("render_order",JSONArray(ids)); saveStory(); reorderMovie(ep)
                }
            }
        }
    }
    private fun movieJobs(ep: Int) = File(project,"movies/EP%02d".format(ep)).listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }.orEmpty()
    private fun readMovieState(job: File): JSONObject = runCatching { JSONObject(File(job,"status.json").readText()) }.getOrDefault(JSONObject())
    private fun latestMovie(ep: Int) = movieJobs(ep).firstOrNull { readMovieState(it).optString("state")=="complete" && File(it,"output.mp4").isFile }
    private fun movieFingerprint(ep: Int): String {
        val episodes=plan!!.getJSONArray("episodes")
        val episode=(0 until episodes.length()).map { episodes.getJSONObject(it) }.first { it.getInt("ep")==ep }
        val scenes=orderedMovieScenes(episode)
        val cover=coverFile("EP%02d_COVER_9x16".format(ep))
        return scenes.joinToString(",") { key(it) }+"|"+(listOf(cover)+scenes.map { video(it) }).joinToString("|") { if(it.exists()) EpisodeMedia.digest(it) else "missing" }
    }
    private fun prepareMovie(ep: JSONObject) {
        if(EpisodeRenderService.running) return movieProgress(ep.getInt("ep"))
        val number=ep.getInt("ep"); val scenes=runCatching { orderedMovieScenes(ep) }.getOrElse { notice(it.message ?: "ตรวจลำดับคลิป"); return }
        val missing=scenes.filter { !video(it).exists() }
        if(missing.isNotEmpty()) return notice("ยังขาดคลิป ${missing.joinToString { key(it) }}\nนำเข้าให้ครบก่อนรวม")
        val cover=coverFile("EP%02d_COVER_9x16".format(number))
        if(!cover.exists()) return notice("ยังขาดปกแนวตั้ง EP $number • นำเข้าปกก่อนรวม")
        val dialog=AlertDialog.Builder(this).setMessage("กำลังตรวจขนาด เสียง และไฟล์คลิป…").setCancelable(false).showDirector()
        val folder=project; val p=plan!!
        Thread {
            try {
                val infos=scenes.map { EpisodeMedia.inspect(video(it)) }
                val duration=3000L+infos.sumOf { it.durationMs }
                val rate=kotlin.math.round(infos.first().fps).toInt().coerceIn(1,120)
                val bitrate=maxOf(24_000_000,infos.maxOf { it.bitrate }).coerceAtMost(80_000_000)
                val needed=EpisodeRules.estimateBytes(duration,bitrate,infos.sumOf { it.file.length() })
                require(folder.usableSpace>needed) { "พื้นที่ไม่พอ ต้องมีว่างประมาณ ${needed/1024/1024} MB" }
                val fingerprint=movieFingerprint(number)
                val report="ปกเปิด 3 วินาที → ${scenes.size} ฉาก\nผลลัพธ์ 1080 × 1920 • $rate fps • ${duration/1000} วินาที\nพื้นที่ประมาณ ${needed/1024/1024} MB\n\n"+infos.mapIndexed { i,v -> "${key(scenes[i])}: ${v.width} × ${v.height}, ${v.fps} fps, ${v.durationMs/1000.0} วินาที, ${v.videoMime}, เสียง ${v.audioMime ?: "ไม่มี"}" }.joinToString("\n")
                runOnUiThread {
                    dialog.dismiss()
                    val view=TextView(this).apply { text=report; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                    AlertDialog.Builder(this).setTitle("ตรวจลำดับและคุณภาพก่อนรวม").setView(ScrollView(this).apply { addView(view) })
                        .setPositiveButton("เลือกรูปแบบรวม") { _,_ ->
                            choiceMenu("วิธีรวม • เก็บต้นฉบับทุกคลิป",listOf("คุณภาพสูง • ปรับไฟล์ให้เข้ากัน", "ไม่เข้ารหัสคลิปใหม่ • ต้องตรงทุกค่า")) { mode ->
                                fun start(crop: Boolean) {
                                    if(EpisodeRenderService.running) { notice("มีงานรวมกำลังทำอยู่"); return }
                                    val job=File(folder,"movies/EP%02d/%d_%s".format(number,System.currentTimeMillis(),UUID.randomUUID())).apply { mkdirs() }
                                    val spec=JSONObject().put("ep",number).put("title",p.optString("title")).put("filename",EpisodeRules.filename(p.optString("title"),number,ep.optString("title")))
                                        .put("cover",cover.path).put("clips",JSONArray(infos.map { it.file.path })).put("fps",rate).put("bitrate",bitrate).put("duration_ms",duration).put("fingerprint",fingerprint).put("input_hashes",JSONArray(fingerprint.split('|').drop(1))).put("lossless",mode==1).put("crop",crop).put("has_audio",infos.any { it.audioMime!=null })
                                    File(job,"spec.json").writeText(spec.toString(2))
                                    File(job,"status.json").writeText(JSONObject().put("state","queued").put("message","กำลังเริ่มรวม").toString())
                                    try { androidx.core.content.ContextCompat.startForegroundService(this,Intent(this,EpisodeRenderService::class.java).putExtra("job",job.path)); movieProgress(number) }
                                    catch(e: Exception) { File(job,"status.json").writeText(JSONObject().put("state","failed").put("message",e.message).toString()); notice("เริ่มรวมไม่ได้: ${e.message}") }
                                }
                                if(mode==1 && infos.any { it.width!=1080 || it.height!=1920 || it.rotation!=0 || kotlin.math.abs(it.fps-infos.first().fps)>0.1f }) { notice("ขนาด/เฟรมเรตต่างกัน ต้องเลือกปรับไฟล์คุณภาพสูง"); return@choiceMenu }
                                val message=if(mode==0) "ปกและคลิปจะเข้ารหัสใหม่เป็น H.264 คุณภาพสูง ${bitrate/1_000_000} Mbps เสียง AAC รักษาเสียงเดิม ไม่มีดนตรีเพิ่ม คุณภาพอาจเปลี่ยนเล็กน้อย\nไฟล์ขนาดเล็กกว่าจะถูกขยาย แต่ไม่เพิ่มรายละเอียดจริง" else "สร้างปกเปิดก่อน แล้วตรวจ codec/config และเสียง ถ้าไม่ตรงจะหยุดและแจ้ง ไม่แปลงคลิปเอง"
                                AlertDialog.Builder(this).setTitle("ยืนยันวิธีรวม").setMessage(message).setPositiveButton("ดำเนินการ") { _,_ ->
                                    if(mode==0 && infos.any { it.width.toDouble()/it.height !in 0.55..0.58 || it.rotation!=0 }) choiceMenu("จัดภาพต่างสัดส่วน",listOf("เว้นขอบ • เห็นภาพครบ", "ครอปกึ่งกลาง • เต็มจอ อาจตัดขอบ")) { layout -> movieLayoutPreview(infos.first { it.width.toDouble()/it.height !in 0.55..0.58 || it.rotation!=0 }.file,layout==1) { start(layout==1) } }
                                    else start(false)
                                }.setNegativeButton("ยกเลิก",null).showDirector()
                            }
                        }.setNegativeButton("ยกเลิก",null).showDirector()
                }
            } catch(e: Exception) { runOnUiThread { dialog.dismiss(); notice("ตรวจคลิปไม่ผ่าน: ${e.message}") } }
        }.start()
    }
    private fun movieLayoutPreview(file: File, crop: Boolean, proceed: () -> Unit) {
        val reader=android.media.MediaMetadataRetriever()
        val bitmap=runCatching {
            reader.setDataSource(file.path)
            if(android.os.Build.VERSION.SDK_INT>=27) reader.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,540,960) else reader.getFrameAtTime(0)
        }.getOrNull()
        reader.release()
        val view=ImageView(this).apply {
            setImageBitmap(bitmap); setBackgroundColor(Color.BLACK)
            scaleType=if(crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            layoutParams=LinearLayout.LayoutParams(dp(270),dp(480))
        }
        val panel=LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER; addView(view) }
        val dialog=AlertDialog.Builder(this).setTitle(if(crop) "ตัวอย่างครอปกึ่งกลาง 9:16" else "ตัวอย่างเว้นขอบ 9:16").setView(panel)
            .setPositiveButton("ใช้รูปแบบนี้") { _,_ -> proceed() }.setNegativeButton("ยกเลิก",null).create()
        dialog.setOnDismissListener { view.setImageDrawable(null); bitmap?.recycle() }; dialog.show()
    }
    private fun movieProgress(ep: Int) {
        val job=movieJobs(ep).firstOrNull() ?: return notice("ยังไม่มีงานรวม EP นี้")
        val text=TextView(this).apply { setPadding(dp(16),dp(16),dp(16),dp(16)); textSize=16f }
        val dialog=AlertDialog.Builder(this).setTitle("รวมหนัง EP $ep").setView(text).setPositiveButton("ปิดหน้าต่าง",null).setNeutralButton("ยกเลิกงาน") { _,_ ->
            if(EpisodeRenderService.running) startService(Intent(this,EpisodeRenderService::class.java).setAction(EpisodeRenderService.CANCEL).putExtra("job",job.path))
        }.create()
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        var ticks=0
        val update=object : Runnable { override fun run() {
            if(!dialog.isShowing) return
            val status=readMovieState(job); val state=status.optString("state")
            val interrupted=state in listOf("preparing","rendering","queued") && !EpisodeRenderService.running && ++ticks>5
            text.text=if(interrupted) "งานถูกหยุดก่อนเสร็จ กรุณารวมใหม่ • ต้นฉบับยังอยู่" else "${status.optString("message")}\n${status.optInt("progress")}%\n${if(state=="complete") "กลับเมนู EP เพื่อดูหนังหรือดาวน์โหลด MP4" else "สลับแอปได้ งานยังทำต่อผ่านบริการเบื้องหน้า"}"
            handler.postDelayed(this,1000)
        } }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }; dialog.show(); handler.post(update)
    }
    private fun checkMovieChanges(job: File, proceed: () -> Unit) {
        val ep=JSONObject(File(job,"spec.json").readText()).getInt("ep")
        val old=JSONObject(File(job,"result.json").readText()).optString("fingerprint")
        val dialog=AlertDialog.Builder(this).setMessage("ตรวจว่าคลิปและปกเปลี่ยนหรือไม่…").setCancelable(false).showDirector()
        Thread { val changed=runCatching { movieFingerprint(ep)!=old }.getOrDefault(true); runOnUiThread {
            dialog.dismiss()
            if(changed) AlertDialog.Builder(this).setMessage("คลิปหรือปกเปลี่ยนหลังรวม หนังนี้เป็นเวอร์ชันเดิม ต้องรวมใหม่เพื่อใช้การแก้ล่าสุด")
                .setPositiveButton("ใช้เวอร์ชันเดิม") { _,_ -> proceed() }.setNegativeButton("ยกเลิก",null).showDirector() else proceed()
        } }.start()
    }
    private fun playMovie(job: File) {
        val file=File(job,"output.mp4"); val view=VideoView(this).apply { setVideoPath(file.path); val controls=MediaController(this@StudioActivity); controls.setAnchorView(this); setMediaController(controls); setOnPreparedListener { start() }; setOnErrorListener { _,_,_ -> notice("เล่นหนังไฟล์นี้ไม่ได้"); true } }
        val dialog=AlertDialog.Builder(this).setTitle("หนัง EP").setView(view).setPositiveButton("ปิด",null).create(); dialog.setOnDismissListener { view.stopPlayback() }; dialog.show(); dialog.window?.setLayout(-1,dp(560))
    }
    private val movieDownloadPicker=registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val path=prefs.getString("movie_download_job",null)
        if(uri!=null && path!=null) {
            val dialog=AlertDialog.Builder(this).setMessage("กำลังบันทึก MP4 ลงตำแหน่งที่เลือก…").setCancelable(false).showDirector()
            Thread {
                try {
                    val job=File(path); val file=File(job,"output.mp4"); require(file.isFile) { "ไม่พบ MP4" }
                    contentResolver.openOutputStream(uri,"wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
                    val size=contentResolver.openFileDescriptor(uri,"r")?.use { it.statSize }
                    require(size==null || size<0 || size==file.length()) { "บันทึกไฟล์ไม่ครบ" }
                    File(job,"downloaded.json").writeText(JSONObject().put("uri",uri.toString()).put("time",System.currentTimeMillis()).toString())
                    runOnUiThread { dialog.dismiss(); notice("ดาวน์โหลด MP4 สำเร็จ • ${displayName(uri)}") }
                } catch(e: Exception) { runOnUiThread { dialog.dismiss(); notice("ดาวน์โหลดไม่สำเร็จ: ${e.message} • ไฟล์ที่ตำแหน่งปลายทางอาจยังไม่ครบ กรุณาบันทึกใหม่") } }
            }.start()
        }
        prefs.edit().remove("movie_download_job").apply()
    }
    private fun downloadMovie(job: File) = checkMovieChanges(job) {
        val filename=JSONObject(File(job,"result.json").readText()).getString("filename")
        prefs.edit().putString("movie_download_job",job.path).apply(); movieDownloadPicker.launch(filename)
    }
    private fun shareMovie(job: File) = checkMovieChanges(job) {
        val file=File(job,"output.mp4")
        val name=JSONObject(File(job,"result.json").readText()).getString("filename")
        val export=File(cacheDir,"movie_exports/${UUID.randomUUID()}/$name").apply { parentFile!!.mkdirs() }
        background { file.copyTo(export); runOnUiThread {
            val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",export)
            val intent=Intent(Intent.ACTION_SEND).apply { type="video/mp4"; putExtra(Intent.EXTRA_STREAM,uri); clipData=ClipData.newUri(contentResolver,"หนัง EP",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching { startActivity(Intent.createChooser(intent,"แชร์หนัง EP")) }.onFailure { notice("เปิดแชร์ไม่ได้: ${it.message}") }
        } }
    }

    private fun review() { episodesMenu() }
    private fun export(uri: Uri) {
        val p = plan!!
        contentResolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            val delivered = JSONObject(p.toString())
            if(exportEpisode != null) {
                val selected = JSONArray(); val all = p.getJSONArray("episodes")
                for(i in 0 until all.length()) if(all.getJSONObject(i).getInt("ep")==exportEpisode) selected.put(all.getJSONObject(i))
                delivered.put("export_scope","episode").put("source_episode",exportEpisode).put("episodes",selected).put("episode_count",selected.length()).put("scene_count",rows(delivered).size)
            }
            put("story.json",delivered.toString(2).toByteArray())
            put("MISSING.txt", rows(p).filter { exportEpisode == null || it.getInt("ep") == exportEpisode }.joinToString("\n") { "${key(it)}: ภาพ=${image(it).exists()} วิดีโอ=${video(it).exists()}" }.toByteArray())
            put("README.txt","${p.getString("title")}\n${p.getString("category")}\nนำภาพฉากและคำสั่งใน Flow/EPxx_FLOW.txt ไปใช้ใน Flow • 8 วินาที • 9:16".toByteArray())
            for (name in coverNames(p)) {
                val file = coverFile(name)
                if (!file.exists()) continue
                if (exportEpisode != null && StoryFiles.sceneNumber(name + "_SC001")?.first != exportEpisode && !name.startsWith("EP%02d_".format(exportEpisode))) continue
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val ext = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                put("covers/$name.$ext",file.readBytes())
            }
            val episodes = p.getJSONArray("episodes")
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                val number = ep.getInt("ep")
                if (exportEpisode != null && number != exportEpisode) continue
                val scenes = rows(p).filter { it.getInt("ep") == number }
                val commands = StoryFiles.episodeFlow(number, ep.optString("title"), scenes.map {
                    Triple(key(it), "${it.getString("action")}\n${it.optString("dialogue")}", flowCommand(it))
                })
                put("Flow/EP%02d_FLOW.txt".format(number), commands.replace("8 วินาทีต่อซีน • 9:16", "${p.optInt("duration_seconds",8)} วินาทีต่อซีน • ${p.optString("aspect_ratio","9:16")}").toByteArray())
            }
            for (s in rows(p)) {
                if (exportEpisode != null && s.getInt("ep") != exportEpisode) continue
                val path = "EP%02d/%s".format(s.getInt("ep"),key(s))
                val file = image(s)
                val video = video(s)
                if (video.exists()) { zip.putNextEntry(ZipEntry("videos/${key(s)}.mp4")); video.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                if (!file.exists()) continue
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val extension = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                zip.putNextEntry(ZipEntry("$path.$extension")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()

            }
        } }
    }
}


