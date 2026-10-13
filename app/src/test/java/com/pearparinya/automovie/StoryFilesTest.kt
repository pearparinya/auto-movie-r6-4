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
    @Test fun coversAreSeparateFromSceneImages() {
        assertEquals("EP03_COVER_9x16", StoryFiles.coverName("EP03_COVER_9x16.png"))
        assertEquals("EP01_COVER_16x9", StoryFiles.coverName("ep1_cover_16X9.JPG"))
        assertNull(StoryFiles.coverName("EP01_SC001.png"))
        assertNull(StoryFiles.coverName("EP00_COVER_9x16.png"))
        assertNull(StoryFiles.sceneNumber("EP01_COVER_9x16.png"))
    }
    @Test fun flexiblePackageNamesKeepSceneAndCoverRoles() {
        assertEquals(1 to 2, StoryFiles.sceneNumber("EP_01_SC_002.png"))
        assertEquals(3 to 4, StoryFiles.sceneNumber("EP 03 Scene 004.jpg"))
        assertEquals("EP02_COVER_9x16", StoryFiles.coverName("EP02-cover-9-16.png"))
        assertEquals("EP01_SC001.png", StoryFiles.packageName("เรื่อง/EP01/SC001.png"))
        assertEquals("EP03_COVER_16x9.png", StoryFiles.packageName("EP03/COVER_16x9.png"))
        assertTrue(StoryFiles.isMaster("GAWIN_MASTER.png"))
        assertTrue(StoryFiles.isMaster("RINLADA_MASTER(1).png"))
        assertTrue(StoryFiles.isMaster("CHARACTER_01.png"))
        assertFalse(StoryFiles.isMaster("EP01_SC001.png"))
        assertNull(StoryFiles.sceneNumber("EP00_SC001.png"))
    }
    @Test fun episodeFlowKeepsEverySceneAndItsDialogueInOrder() {
        val text = StoryFiles.episodeFlow(1, "คืนครบรอบ", listOf(
            Triple("EP01_SC001", "รินลดาพูดว่า: กลับมาแล้ว", "Prompt first"),
            Triple("EP01_SC002", "กวินพูดว่า: ขอโทษ", "Prompt second")))
        assertTrue(text.contains("EP 01 • คืนครบรอบ"))
        assertTrue(text.contains("รินลดาพูดว่า: กลับมาแล้ว"))
        assertTrue(text.indexOf("EP01_SC001") < text.indexOf("EP01_SC002"))
        assertTrue(text.indexOf("Prompt first") < text.indexOf("Prompt second"))
        assertFalse(text.contains("EP02_"))
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
    @Test fun downloadedArchiveUsesCentralDirectory() {
        val dir = Files.createTempDirectory("studio-test").toFile()
        try {
            val archiveFile = java.io.File(dir, "download.zip").apply { writeBytes(archive("story.json" to "{}", "EP01/EP01_SC001.png" to "image")) }
            val result = StoryFiles.unpackFile(archiveFile, java.io.File(dir, "out"))
            assertEquals(listOf("story.json", "EP01_SC001.png"), result.files.map { it.name })
            assertTrue(result.failedEntries.isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun archiveWithoutEndDirectorySalvagesLocalEntries() {
        val dir = Files.createTempDirectory("studio-test").toFile()
        try {
            val complete = archive("story.json" to "{}", "EP01/EP01_SC001.png" to "image")
            val central = complete.indices.first { i -> i + 3 < complete.size && complete[i] == 0x50.toByte() && complete[i + 1] == 0x4b.toByte() && complete[i + 2] == 0x01.toByte() && complete[i + 3] == 0x02.toByte() }
            val broken = java.io.File(dir, "missing-end.zip").apply { writeBytes(complete.copyOf(central)) }
            val result = StoryFiles.unpackFile(broken, java.io.File(dir, "out"))
            assertEquals(listOf("story.json", "EP01_SC001.png"), result.files.map { it.name })
            assertTrue(result.failedEntries.first().contains("กู้ข้อมูล"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun truncatedFinalEntryStillSalvagesEarlierImages() {
        val dir = Files.createTempDirectory("studio-test").toFile()
        try {
            val complete = archive("EP01_SC001.png" to "complete-image", "EP01_SC002.png" to "last-image-is-truncated", "story.json" to "{}")
            fun signature(from: Int): Int = (from until complete.size - 3).first { i ->
                complete[i] == 0x50.toByte() && complete[i + 1] == 0x4b.toByte() && complete[i + 2] == 0x03.toByte() && complete[i + 3] == 0x04.toByte()
            }
            val second = signature(signature(0) + 4)
            val broken = java.io.File(dir, "truncated.zip").apply { writeBytes(complete.copyOf(second + 55)) }
            val result = StoryFiles.unpackFile(broken, java.io.File(dir, "out"))
            assertEquals(listOf("EP01_SC001.png"), result.files.map { it.name })
            assertTrue(result.failedEntries.any { it.contains("EP01_SC002.png") })
            assertFalse(result.files.any { it.name == "story.json" })
        } finally { dir.deleteRecursively() }
    }
}
