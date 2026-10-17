package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class WardrobeTimelineTest {
    private val meeting=WardrobeBeat("rinlada","DAY01","MEETING","meeting","GROUP01","เสื้อเชิ้ตขาวกับกางเกงดำ")
    private val history=listOf(WardrobeEntry(meeting,meeting.outfit))
    @Test fun casualFallbackNamesAnActualOutfit() {
        val outfit=WardrobeTimeline.resolve(meeting.copy(character="gawin",activity="casual",outfit=""),emptyList())
        assertTrue(outfit.contains("กางเกงขายาว"))
        assertFalse(outfit.contains("หรือ"))
    }
    @Test fun explicitWardrobeAlwaysWins() {
        assertEquals("ชุดที่ผู้ใช้เลือก",WardrobeTimeline.resolve(meeting.copy(day="DAY02",outfit="ชุดที่ผู้ใช้เลือก"),history))
    }
    @Test fun sameEventKeepsClothesAcrossLocationChanges() {
        assertEquals(meeting.outfit,WardrobeTimeline.resolve(meeting.copy(group="ROOM02",outfit=""),history))
    }
    @Test fun changedDayAndChangedActivityDoNotLeakMeetingOutfit() {
        assertNotEquals(meeting.outfit,WardrobeTimeline.resolve(meeting.copy(day="DAY02",outfit=""),history))
        val sleep=WardrobeTimeline.resolve(meeting.copy(activity="sleep",outfit=""),history)
        assertTrue(sleep.contains("ชุดนอน"))
        assertFalse(sleep.contains("เสื้อเชิ้ต"))
        val home=WardrobeTimeline.resolve(meeting.copy(activity="home",outfit=""),history)
        assertTrue(home.contains("อยู่บ้าน"))
    }
    @Test fun otherCharactersNeverSupplyWardrobeAndExplicitChangeResetsInheritance() {
        assertNotEquals(meeting.outfit,WardrobeTimeline.resolve(meeting.copy(character="mind",outfit=""),history))
        assertNotEquals(meeting.outfit,WardrobeTimeline.resolve(meeting.copy(change=true,outfit=""),history))
    }
    @Test fun returningToOldGroupDoesNotResurrectAnOutfitAfterChangingClothes() {
        val night=meeting.copy(event="BEDTIME",activity="sleep",outfit="ชุดนอนสีฟ้า")
        assertNotEquals(meeting.outfit,WardrobeTimeline.resolve(meeting.copy(outfit=""),history+WardrobeEntry(night,night.outfit)))
    }
    @Test fun legacySameGroupWorksButUnknownGroupDoesNotInventContinuity() {
        val legacy=meeting.copy(day="",event="")
        assertEquals(meeting.outfit,WardrobeTimeline.resolve(legacy.copy(outfit=""),listOf(WardrobeEntry(legacy,meeting.outfit))))
        assertNotEquals(meeting.outfit,WardrobeTimeline.resolve(legacy.copy(group="",outfit=""),listOf(WardrobeEntry(legacy,meeting.outfit))))
    }
}
