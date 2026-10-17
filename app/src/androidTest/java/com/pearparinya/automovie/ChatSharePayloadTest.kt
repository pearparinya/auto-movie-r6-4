package com.pearparinya.automovie

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatSharePayloadTest {
    @Test fun identityGuideKeepsUserStyleAndSceneWardrobeInAConciseBrief() {
        val guide = neutralIdentityGuide("ภาพ MASTER ที่แนบ","9:16")
        assertTrue(guide.contains("ใบหน้า") && guide.contains("ทรงผม"))
        assertFalse(guide.contains("ความเซ็กซี่"))
        assertTrue(guide.contains("สัดส่วนรูปร่างโดยรวม"))
        assertFalse(guide.contains("100%"))
        assertTrue(guide.contains("ตัวละครผู้ใหญ่"))
        assertTrue(guide.contains("ชุดยึดบทของฉากปัจจุบัน"))
        assertTrue(guide.contains("สีผิว") && guide.contains("รูปร่าง"))
        assertTrue(guide.contains("ภาพ MASTER ที่แนบ"))
        assertFalse(guide.contains("ความสัมพันธ์ของไหล่ ทรวงอก"))
        assertTrue(neutralIdentityGuide("CHARACTER MASTER ที่แนบ", "16:9").contains("16:9"))
    }

    @Test fun textAndAllImagesArePresentInEverySupportedShareField() {
        val images = arrayListOf(
            Uri.parse("content://director-x/GAWIN_MASTER.png"),
            Uri.parse("content://director-x/RINLADA_MASTER.png"),
            Uri.parse("content://director-x/MIND_MASTER.png")
        )
        val intent = buildChatShareIntent("สร้างภาพทดสอบ",images)

        assertEquals(Intent.ACTION_SEND_MULTIPLE,intent.action)
        assertEquals("image/png",intent.type)
        assertEquals("สร้างภาพทดสอบ",intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(images,intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(4,intent.clipData!!.itemCount)
        assertEquals("สร้างภาพทดสอบ",intent.clipData!!.getItemAt(0).text.toString())
        images.forEachIndexed { index, uri -> assertEquals(uri,intent.clipData!!.getItemAt(index + 1).uri) }
    }

    @Test fun textOnlyPayloadUsesSendAndClipboardCompatibleClipData() {
        val intent = buildChatShareIntent("คำสั่งเดียว", arrayListOf())
        assertEquals(Intent.ACTION_SEND,intent.action)
        assertEquals("text/plain",intent.type)
        assertEquals("คำสั่งเดียว",intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("คำสั่งเดียว",intent.clipData!!.getItemAt(0).text.toString())
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
