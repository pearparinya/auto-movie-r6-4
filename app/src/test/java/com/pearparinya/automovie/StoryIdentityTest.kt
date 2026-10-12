package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class StoryIdentityTest {
    @Test fun namesAlwaysUseTheirOwnMaster() {
        assertEquals("RINLADA_MASTER.png", StoryIdentity.masterFor("รินลดา"))
        assertEquals("MIND_MASTER.png", StoryIdentity.masterFor("มายด์"))
        assertEquals("gawin", StoryIdentity.keyFor("กวิน"))
        assertEquals("rinlada", StoryIdentity.keyFor("รินรดา"))
        assertEquals("mind", StoryIdentity.keyFor("เมย์"))
        assertTrue(StoryIdentity.matchesMaster("เมย์", "MIND_MASTER.jpg"))
        assertFalse(StoryIdentity.matchesMaster("เมย์", "RINLADA_MASTER.png"))
        assertNull(StoryIdentity.keyFor("ตัวละครใหม่"))
        assertTrue(StoryIdentity.matchesMaster("รินลดา", "RINLADA_MASTER.jpg"))
        assertFalse(StoryIdentity.matchesMaster("รินลดา", "MIND_MASTER.png"))
        assertFalse(StoryIdentity.matchesMaster("รินลดา", "folder/RINLADA_MASTER.png"))
    }
    @Test fun legacySceneSelectsOnlyMentionedActorsWithoutDuplicates() {
        assertEquals(listOf("rinlada"), StoryIdentity.keysFromPrompt("รินลดา ใช้ RINLADA_MASTER.png ทำอาหาร"))
        assertEquals(listOf("gawin", "mind"), StoryIdentity.keysFromPrompt("GAWIN_MASTER.png และ MIND_MASTER.png"))
        assertEquals(emptyList<String>(), StoryIdentity.keysFromPrompt("คนหนึ่งอยู่ในสวน"))
    }
}
