package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SceneWardrobeTest {
    @Test fun explicitStoryOutfitWinsOverDefaultForEveryCastMember() {
        listOf("gawin", "rinlada", "mind", "CHARACTER_01").forEach { key ->
            val sceneOutfit = "เสื้อเชิ้ตสีขาวกับกางเกงขายาวสีน้ำเงิน"
            assertEquals(sceneOutfit, SceneWardrobe.forScene(key, sceneOutfit))
        }
    }

    @Test fun missingOutfitFallsBackByCharacterIncludingUnknownCast() {
        listOf("gawin", "rinlada", "mind", "CHARACTER_01").forEach { key ->
            assertEquals(SceneWardrobe.defaultFor(key), SceneWardrobe.forScene(key, ""))
            assertEquals(SceneWardrobe.defaultFor(key), SceneWardrobe.forScene(key, "  "))
        }
        assertNotEquals(SceneWardrobe.forScene("gawin", ""), SceneWardrobe.forScene("rinlada", ""))
        assertNotEquals(SceneWardrobe.forScene("mind", ""), SceneWardrobe.forScene("rinlada", ""))
    }
}
