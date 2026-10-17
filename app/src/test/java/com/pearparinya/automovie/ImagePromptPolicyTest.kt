package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class ImagePromptPolicyTest {
    @Test fun productionReferenceSeparatesIdentityFromStandardClothesAndRequiresActualReview() {
        val prompt=ImagePromptPolicy.productionMaster("รินรดา","RINLADA_MASTER.png","ผมยาวลอนสีเข้ม")
        assertTrue(prompt.contains("RINLADA_MASTER.png"))
        assertTrue(prompt.contains("ผมยาวลอนสีเข้ม"))
        assertTrue(prompt.contains("เสื้อยืดแขนสั้นผ้าทึบสีเทา"))
        assertTrue(prompt.contains("ศีรษะจรดเท้า"))
        assertTrue(prompt.contains("ก่อนเลือกเป็น MASTER ใหม่"))
        assertTrue(prompt.contains("ไม่ขยายหรือลดสัดส่วนเอง"))
        assertFalse(prompt.contains("100%"))
    }
    @Test fun actionFramingAndNightLightingOverrideGenericPortraitDefaults() {
        val prompt=ImagePromptPolicy.scene("รินรดา → RINLADA_MASTER.jpg | ชุด: เดรสกรมท่า", "ห้องนอนกลางคืน", "คุกเข่าหยิบซองจากใต้เตียง",
            aspect="16:9",framing="เต็มตัว เห็นมือและกล่องใต้เตียง",lighting="แสงโคมไฟหัวเตียง",filename="EP01_SC001.png")
        assertTrue(prompt.contains("เต็มตัว เห็นมือและกล่องใต้เตียง"))
        assertTrue(prompt.contains("แสงโคมไฟหัวเตียง"))
        assertTrue(prompt.contains("16:9"))
        assertFalse(prompt.contains("waist-up"))
        assertFalse(prompt.contains("แสงธรรมชาติ"))
        assertTrue(prompt.indexOf("ตัวละครและชุด:") < prompt.indexOf("สถานที่:"))
        assertTrue(prompt.indexOf("สถานที่:") < prompt.indexOf("การกระทำ:"))
    }
    @Test fun wardrobeOnlyEditKeepsSourceSceneAndDoesNotInventANewLocation() {
        val prompt=ImagePromptPolicy.edit("ภาพ MASTER", "เสื้อผ้าถักสีดำพอดีตัว")
        assertTrue(prompt.contains("เปลี่ยนเฉพาะ: เสื้อผ้าถักสีดำพอดีตัว"))
        assertTrue(prompt.contains("องค์ประกอบที่ไม่ได้ขอเปลี่ยน"))
        assertFalse(prompt.contains("สถานที่:"))
        assertFalse(prompt.contains("100%"))
    }
    @Test fun newCharacterAndCoverHaveDistinctIdentityAndTextRules() {
        val newPerson=ImagePromptPolicy.newCharacter("GAWIN_MASTER.png", "ร้านกาแฟ")
        assertTrue(newPerson.contains("คนใหม่"))
        assertTrue(newPerson.contains("เฉพาะสไตล์ภาพ"))
        val cover=ImagePromptPolicy.coverReference("ภาพฉาก EP01_SC001")
        assertTrue(cover.contains("พื้นที่ข้อความ"))
        assertFalse(cover.contains("ไม่มีข้อความ"))
    }
}
