package com.github.axondragonscale.variantx

import com.github.axondragonscale.variantx.model.VariantSelection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [VariantSelection.composeVariantName].
 */
class VariantSelectionTest {

    @Test
    fun `no dimensions returns build type only`() {
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            selectedBuildType = "debug",
        )
        assertEquals("debug", selection.composeVariantName(emptyList()))
    }

    @Test
    fun `single dimension composes correctly`() {
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging"),
            selectedBuildType = "debug",
        )
        assertEquals("stagingDebug", selection.composeVariantName(listOf("environment")))
    }

    @Test
    fun `multiple dimensions composes in order`() {
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf(
                "environment" to "staging",
                "tier" to "free",
            ),
            selectedBuildType = "release",
        )
        assertEquals(
            "stagingFreeRelease",
            selection.composeVariantName(listOf("environment", "tier")),
        )
    }

    @Test
    fun `three dimensions composes correctly`() {
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf(
                "environment" to "staging",
                "tier" to "premium",
                "api" to "minApi21",
            ),
            selectedBuildType = "debug",
        )
        assertEquals(
            "stagingPremiumMinApi21Debug",
            selection.composeVariantName(listOf("environment", "tier", "api")),
        )
    }

    @Test
    fun `release build type`() {
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf("env" to "prod"),
            selectedBuildType = "release",
        )
        assertEquals("prodRelease", selection.composeVariantName(listOf("env")))
    }
}
