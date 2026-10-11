package com.pearparinya.automovie

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** File formats shared by ChatGPT packages and Studio exports. */
internal object StoryFiles {
    fun zipName(title: String): String = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100).ifBlank { "เรื่องใหม่" } + ".zip"
    fun sceneNumber(name: String): Pair<Int, Int>? { return Regex("EP(\\d+)[ _-]*SC(?:ENE)?(\\d+)", RegexOption.IGNORE_CASE).find(name)?.let {
        val ep = it.groupValues[1].toIntOrNull() ?: return null
        val scene = it.groupValues[2].toIntOrNull() ?: return null
        ep to scene
    } }
    fun coverName(name: String): String? = Regex("^EP(\\d+)_COVER_(9x16|16x9)\\.(png|jpe?g|webp)$", RegexOption.IGNORE_CASE).matchEntire(name)?.let {
        val ep = it.groupValues[1].toIntOrNull()
        if (ep == null || ep < 1) null else "EP%02d_COVER_%s".format(ep, it.groupValues[2].lowercase())
    }
    fun unpack(input: InputStream, staging: File, maxBytes: Long = 512L * 1024 * 1024): List<File> {
        val files = mutableListOf<File>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                require(++count <= 2000) { "ZIP มีไฟล์มากเกินไป" }
                // Flatten each entry into its own directory: archive paths cannot escape staging.
                val name = entry.name.replace('\\', '/').substringAfterLast('/')
                if (!entry.isDirectory && (name.endsWith(".json", true) || name.substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp"))) {
                    val target = File(staging, "${files.size}/$name").apply { parentFile!!.mkdirs() }
                    target.outputStream().use { out ->
                        val buffer = ByteArray(65536)
                        var n = zip.read(buffer)
                        while (n >= 0) {
                            total += n
                            require(total <= maxBytes) { "ชุดไฟล์ใหญ่เกินขนาดที่รองรับ" }
                            out.write(buffer, 0, n)
                            n = zip.read(buffer)
                        }
                    }
                    files.add(target)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return files
    }
}
