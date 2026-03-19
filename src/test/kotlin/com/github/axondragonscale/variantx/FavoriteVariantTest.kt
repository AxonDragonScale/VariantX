package com.github.axondragonscale.variantx

import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.github.axondragonscale.variantx.model.FavoriteVariant
import com.github.axondragonscale.variantx.model.VariantSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FavoriteVariant.matches] and [FavoriteVariant.isValid].
 */
class FavoriteVariantTest {

    private val sampleModuleInfo = AndroidModuleInfo(
        stableName = "<app>",
        name = "app",
        gradlePath = ":app",
        isAppModule = true,
        flavorDimensions = listOf("environment", "tier"),
        flavorsPerDimension = mapOf(
            "environment" to listOf("staging", "production"),
            "tier" to listOf("free", "premium"),
        ),
        buildTypes = listOf("debug", "release"),
        availableVariants = setOf(
            "stagingFreeDebug", "stagingFreeRelease",
            "stagingPremiumDebug", "stagingPremiumRelease",
            "productionFreeDebug", "productionFreeRelease",
            "productionPremiumDebug", "productionPremiumRelease",
        ),
        currentVariant = "stagingFreeDebug",
    )

    // ── matches ──

    @Test
    fun `matches returns true for identical selection`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "tier" to "free"),
            buildType = "debug",
            variantName = "stagingFreeDebug",
        )
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "tier" to "free"),
            selectedBuildType = "debug",
        )
        assertTrue(fav.matches(selection))
    }

    @Test
    fun `matches returns false for different module`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging"),
            buildType = "debug",
            variantName = "stagingDebug",
        )
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app2",
            flavorSelections = mutableMapOf("environment" to "staging"),
            selectedBuildType = "debug",
        )
        assertFalse(fav.matches(selection))
    }

    @Test
    fun `matches returns false for different flavor`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging"),
            buildType = "debug",
            variantName = "stagingDebug",
        )
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "production"),
            selectedBuildType = "debug",
        )
        assertFalse(fav.matches(selection))
    }

    @Test
    fun `matches returns false for different build type`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging"),
            buildType = "debug",
            variantName = "stagingDebug",
        )
        val selection = VariantSelection(
            selectedModuleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging"),
            selectedBuildType = "release",
        )
        assertFalse(fav.matches(selection))
    }

    // ── isValid ──

    @Test
    fun `isValid returns true for valid favorite`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "tier" to "free"),
            buildType = "debug",
            variantName = "stagingFreeDebug",
        )
        assertTrue(fav.isValid(sampleModuleInfo))
    }

    @Test
    fun `isValid returns false for null module info`() {
        val fav = FavoriteVariant(variantName = "stagingFreeDebug")
        assertFalse(fav.isValid(null))
    }

    @Test
    fun `isValid returns false when variant name not in available variants`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "tier" to "free"),
            buildType = "debug",
            variantName = "nonExistentVariant",
        )
        assertFalse(fav.isValid(sampleModuleInfo))
    }

    @Test
    fun `isValid returns false when flavor dimension does not exist`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "nonexistent" to "value"),
            buildType = "debug",
            variantName = "stagingFreeDebug",
        )
        assertFalse(fav.isValid(sampleModuleInfo))
    }

    @Test
    fun `isValid returns false when flavor value does not exist in dimension`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "tier" to "enterprise"),
            buildType = "debug",
            variantName = "stagingFreeDebug",
        )
        assertFalse(fav.isValid(sampleModuleInfo))
    }

    @Test
    fun `isValid returns false when build type does not exist`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "tier" to "free"),
            buildType = "beta",
            variantName = "stagingFreeDebug",
        )
        assertFalse(fav.isValid(sampleModuleInfo))
    }

    // ── toVariantSelection ──

    @Test
    fun `toVariantSelection creates correct selection`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging", "tier" to "free"),
            buildType = "debug",
            variantName = "stagingFreeDebug",
        )
        val selection = fav.toVariantSelection()
        assertTrue(fav.matches(selection))
    }

    @Test
    fun `toVariantSelection returns independent copy of flavor selections`() {
        val fav = FavoriteVariant(
            moduleGradlePath = ":app",
            flavorSelections = mutableMapOf("environment" to "staging"),
            buildType = "debug",
            variantName = "stagingDebug",
        )
        val selection = fav.toVariantSelection()
        // Mutating the selection's map should not affect the favorite
        selection.flavorSelections["environment"] = "production"
        assertTrue(fav.flavorSelections["environment"] == "staging")
    }
}

