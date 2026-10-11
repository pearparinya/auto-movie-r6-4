package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StoryFilesTest {
    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry() } }
        return out.toByteArray()
    }
    @Test fun thaiTitleSurvivesExport() {
        assertEquals("กุญแจบ้านที่ให้คนอื่น.zip", StoryFiles.zipName("กุญแจบ้านที่ให้คนอื่น"))
        assertEquals("รัก_ร้าว_ครั้งสุดท้าย.zip", StoryFiles.zipName("รัก/ร้าว:ครั้งสุดท้าย"))
    }
    @Test fun sceneMatchingSupportsAllEpisodesAndRejectsOverflow() {
        assertEquals(3 to 6, StoryFiles.sceneNumber("EP03_SC006.png"))
        assertEquals(12 to 123, StoryFiles.sceneNumber("ep12_scene123.JPG"))
        assertNull(StoryFiles.sceneNumber("MASTER.png"))
        assertNull(StoryFiles.sceneNumber("EP999999999999_SC001.png"))
    }
    @Test fun nestedPackageImportsStoryAndSeparateSceneImages() {
        val dir = Files.createTempDirectory("studio-test").toFile()
        try {
            val files = StoryFiles.unpack(ByteArrayInputStream(archive("ชุดงาน/story.json" to "{}", "EP01/EP01_SC001.png" to "image1", "EP03/EP03_SC006.jpg" to "image18", "README.txt" to "ignored")), dir)
            assertEquals(listOf("story.json", "EP01_SC001.png", "EP03_SC006.jpg"), files.map { it.name })
            assertEquals("image18", files.last().readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun archivePathsCannotEscapeImportDirectory() {
        val dir = Files.createTempDirectory("studio-test").toFile()
        try {
            val files = StoryFiles.unpack(ByteArrayInputStream(archive("../../outside.png" to "x", "a/EP01_SC001.png" to "a", "b/EP01_SC001.png" to "b")), dir)
            assertTrue(files.all { it.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator) })
            assertEquals(3, files.map { it.canonicalPath }.distinct().size)
        } finally { dir.deleteRecursively() }
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedArchiveIsRejected() {
        val dir = Files.createTempDirectory("studio-test").toFile()
        try { StoryFiles.unpack(ByteArrayInputStream(archive("story.json" to "123456")), dir, 5) } finally { dir.deleteRecursively() }
    }
}
