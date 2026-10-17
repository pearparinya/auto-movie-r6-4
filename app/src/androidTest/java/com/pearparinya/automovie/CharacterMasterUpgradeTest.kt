package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterMasterUpgradeTest {
    @Test fun selectedCastMigratesOnceWithBackupsAndLeavesMediaAndNewCharactersIntact() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val old=File(root,"character_masters/gawin.master").apply { parentFile!!.mkdirs(); writeText("old") }
            val project=File(root,"studio/original").apply { mkdirs() }
            val story=File(project,"story.json").apply { writeText("""{"character_mode":"original","episodes":[{"scenes":[{"characters":[{"name":"รินรดา"}],"flow_image_revision":5,"user_review":"approved"}]}]}""") }
            val snapshot=File(project,"original_masters/rinlada.master").apply { parentFile!!.mkdirs(); writeText("old snapshot") }
            val media=File(project,"images/EP01_SC001.png").apply { parentFile!!.mkdirs(); writeText("existing scene") }
            val fresh=File(root,"studio/new/story.json").apply { parentFile!!.mkdirs(); writeText("""{"character_mode":"new","episodes":[]}""") }
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertEquals("character_masters/GAWIN_MASTER.png",old.readText())
            assertEquals("character_masters/RINLADA_MASTER.png",snapshot.readText())
            val scene=JSONObject(story.readText()).getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
            assertEquals("needs_revision",scene.getString("user_review"))
            assertFalse(scene.has("flow_image_revision"))
            assertEquals("existing scene",media.readText())
            assertFalse(File(fresh.parentFile,"original_masters").exists())
            assertTrue(File(root,"character_masters/versions/${CharacterMasterUpgrade.REVISION}").listFiles()!!.any { it.readText()=="old" })
            assertTrue(File(project,"versions/${CharacterMasterUpgrade.REVISION}").listFiles()!!.any { it.readText()=="old snapshot" })
            old.writeText("later custom reference"); snapshot.writeText("later project reference")
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertEquals("later custom reference",old.readText())
            assertEquals("later project reference",snapshot.readText())
        } finally { root.deleteRecursively() }
    }
}
