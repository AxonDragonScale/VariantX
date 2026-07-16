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

    // ── fromVariantName ──

    @Test
    fun `fromVariantName decomposes no-flavor variant`() {
        val selection = VariantSelection.fromVariantName(
            variantName = "debug",
            moduleGradlePath = ":app",
            dimensionOrder = emptyList(),
            flavorsPerDimension = emptyMap(),
            buildTypes = listOf("debug", "release"),
        )
        assertEquals("debug", selection?.selectedBuildType)
        assertEquals(emptyMap<String, String>(), selection?.flavorSelections)
    }

    @Test
    fun `fromVariantName decomposes single dimension`() {
        val selection = VariantSelection.fromVariantName(
            variantName = "stagingDebug",
            moduleGradlePath = ":app",
            dimensionOrder = listOf("environment"),
            flavorsPerDimension = mapOf("environment" to listOf("staging", "prod")),
            buildTypes = listOf("debug", "release"),
        )
        assertEquals("staging", selection?.flavorSelections?.get("environment"))
        assertEquals("debug", selection?.selectedBuildType)
    }

    @Test
    fun `fromVariantName decomposes multiple dimensions`() {
        val selection = VariantSelection.fromVariantName(
            variantName = "stagingFreeRelease",
            moduleGradlePath = ":app",
            dimensionOrder = listOf("environment", "tier"),
            flavorsPerDimension = mapOf(
                "environment" to listOf("staging", "prod"),
                "tier" to listOf("free", "premium"),
            ),
            buildTypes = listOf("debug", "release"),
        )
        assertEquals("staging", selection?.flavorSelections?.get("environment"))
        assertEquals("free", selection?.flavorSelections?.get("tier"))
        assertEquals("release", selection?.selectedBuildType)
    }

    @Test
    fun `fromVariantName prefers longest flavor match to avoid prefix ambiguity`() {
        val selection = VariantSelection.fromVariantName(
            variantName = "proPlusDebug",
            moduleGradlePath = ":app",
            dimensionOrder = listOf("tier"),
            flavorsPerDimension = mapOf("tier" to listOf("pro", "proPlus")),
            buildTypes = listOf("debug"),
        )
        assertEquals("proPlus", selection?.flavorSelections?.get("tier"))
        assertEquals("debug", selection?.selectedBuildType)
    }

    @Test
    fun `fromVariantName round-trips composeVariantName`() {
        val dimensions = listOf("environment", "tier", "api")
        val flavorsPerDimension = mapOf(
            "environment" to listOf("staging", "prod"),
            "tier" to listOf("free", "premium"),
            "api" to listOf("minApi21", "minApi24"),
        )
        val original = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf(
                "environment" to "staging",
                "tier" to "premium",
                "api" to "minApi21",
            ),
            selectedBuildType = "release",
        )
        val variantName = original.composeVariantName(dimensions)
        val decomposed = VariantSelection.fromVariantName(
            variantName = variantName,
            moduleGradlePath = ":app",
            dimensionOrder = dimensions,
            flavorsPerDimension = flavorsPerDimension,
            buildTypes = listOf("debug", "release"),
        )
        assertEquals(original.flavorSelections, decomposed?.flavorSelections)
        assertEquals(original.selectedBuildType, decomposed?.selectedBuildType)
    }

    @Test
    fun `fromVariantName returns null for unknown variant`() {
        val selection = VariantSelection.fromVariantName(
            variantName = "unknownDebug",
            moduleGradlePath = ":app",
            dimensionOrder = listOf("environment"),
            flavorsPerDimension = mapOf("environment" to listOf("staging", "prod")),
            buildTypes = listOf("debug", "release"),
        )
        assertEquals(null, selection)
    }
}
