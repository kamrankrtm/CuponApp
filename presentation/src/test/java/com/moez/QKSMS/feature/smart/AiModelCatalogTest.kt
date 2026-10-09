package com.moez.QKSMS.feature.smart

import com.moez.QKSMS.feature.smart.ai.AiModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelCatalogTest {
    @Test fun `service model list skips invalid entries and duplicates`() {
        val response = """{"data":[{"id":" model-b "},{"id":"model-a"},{"id":"model-a"},{"id":""},{"name":"missing"},{"id":null},{"id":123},null]}"""
        assertEquals(listOf("model-a", "model-b"), AiModelCatalog.parse(response))
    }

    @Test fun `missing model list leaves manual entry available`() {
        assertTrue(AiModelCatalog.parse("{}").isEmpty())
        assertEquals(listOf("my-custom-model"), AiModelCatalog.withCurrent(emptyList(), "my-custom-model"))
    }

    @Test fun `current custom model is preserved even when absent from server`() {
        assertEquals(listOf("custom", "remote"), AiModelCatalog.withCurrent(listOf("remote"), "custom"))
        assertEquals(listOf("remote"), AiModelCatalog.withCurrent(listOf("remote"), "remote"))
    }
}
