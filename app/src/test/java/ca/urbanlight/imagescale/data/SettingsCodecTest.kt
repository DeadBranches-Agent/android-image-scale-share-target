package ca.urbanlight.imagescale.data

import ca.urbanlight.imagescale.data.SettingsCodec.DecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    private fun decodeOk(text: String): DecodeResult.Success =
        SettingsCodec.decode(text, newId = { "generated-id" }) as DecodeResult.Success

    @Test
    fun `v2 round-trip preserves all fields`() {
        val config = AppConfig(
            targets = listOf(
                ShareTargetConfig(
                    id = "a", label = "Blog images", iconName = "camera",
                    quality = 70, scaleFactorPercent = 33, discardMetadata = true,
                    outputTreeUri = "content://tree/x", hidden = true,
                ),
                ShareTargetConfig(id = "b"),
            )
        )
        val result = decodeOk(SettingsCodec.encode(config))
        assertEquals(config, result.config)
        assertFalse(result.migratedFromV1)
    }

    @Test
    fun `numeric values are clamped on import`() {
        val text = """{"version":2,"targets":[
            {"id":"a","quality":0,"scaleFactorPercent":150},
            {"id":"b","quality":999,"scaleFactorPercent":5}]}"""
        val config = decodeOk(text).config
        assertEquals(1, config.targets[0].quality)
        assertEquals(100, config.targets[0].scaleFactorPercent)
        assertEquals(100, config.targets[1].quality)
        assertEquals(10, config.targets[1].scaleFactorPercent)
    }

    @Test
    fun `unknown keys are ignored`() {
        val text = """{"version":2,"future_field":true,"targets":[{"id":"a","surprise":[1,2]}]}"""
        assertEquals("a", decodeOk(text).config.targets.single().id)
    }

    @Test
    fun `newer version is rejected`() {
        val result = SettingsCodec.decode("""{"version":3,"targets":[]}""")
        assertTrue(result is DecodeResult.Failure)
        assertTrue((result as DecodeResult.Failure).message.contains("newer"))
    }

    @Test
    fun `malformed json fails without throwing`() {
        assertTrue(SettingsCodec.decode("not json {") is DecodeResult.Failure)
        assertTrue(SettingsCodec.decode("""{"version":2,"targets":"nope"}""") is DecodeResult.Failure)
    }

    @Test
    fun `v1 file migrates to a single target`() {
        val text = """{"version":1,"quality":92,"scaleFactorPercent":25,
            "discardMetadata":true,"outputTreeUri":"content://tree/y"}"""
        val result = decodeOk(text)
        assertTrue(result.migratedFromV1)
        val target = result.config.targets.single()
        assertEquals("generated-id", target.id)
        assertEquals(92, target.quality)
        assertEquals(25, target.scaleFactorPercent)
        assertTrue(target.discardMetadata)
        assertEquals("content://tree/y", target.outputTreeUri)
    }

    @Test
    fun `missing version is treated as v1`() {
        val result = decodeOk("""{"quality":42}""")
        assertTrue(result.migratedFromV1)
        assertEquals(42, result.config.targets.single().quality)
        assertNull(result.config.targets.single().outputTreeUri)
    }

    @Test
    fun `duplicate target ids are dropped and blank ids regenerated`() {
        val text = """{"version":2,"targets":[{"id":"a"},{"id":"a","label":"dup"},{"id":""}]}"""
        val config = decodeOk(text).config
        assertEquals(2, config.targets.size)
        assertEquals("a", config.targets[0].id)
        assertEquals("generated-id", config.targets[1].id)
    }
}
