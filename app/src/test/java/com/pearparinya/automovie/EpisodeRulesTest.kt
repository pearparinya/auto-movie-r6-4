package com.pearparinya.automovie
import org.junit.Test
import org.junit.Assert.*
class EpisodeRulesTest {
    @Test fun filenameKeepsStoryEpisodeAndEpisodeTitle() {
        assertEquals("รักซ้อน_EP01_คืนแรก.mp4",EpisodeRules.filename("รักซ้อน",1,"คืนแรก"))
    }
    @Test fun filenameRemovesUnsafePathAndFitsUtf8Filesystem() {
        val name=EpisodeRules.filename("ชื่อ/เรื่อง:".repeat(100),12,"ตอนใหม่")
        assertFalse(name.contains('/')); assertFalse(name.contains(':'))
        assertTrue(name.toByteArray().size<=204); assertTrue(name.endsWith(".mp4")); assertTrue(name.contains("_EP12_")); assertTrue(name.contains("ตอนใหม่"))
    }
    @Test fun includesSpaceForSnapshotAndTwoOutputPasses() {
        assertTrue(EpisodeRules.estimateBytes(163000,24000000,100000000)>100000000+163000L*24000000/8000)
    }
}
