package com.github.axondragonscale.variantx.model

/**
 * Represents a pinned/favorite variant combination for quick recall.
 * All properties use `var` with defaults for XML serialization compatibility
 * with [com.intellij.openapi.components.PersistentStateComponent].
 */
data class FavoriteVariant(
    var moduleGradlePath: String = "",
    var flavorSelections: MutableMap<String, String> = mutableMapOf(),
    var buildType: String = "debug",
    var variantName: String = "",
    var pinnedAt: Long = 0L,
) {
    /**
     * Convert this favorite back into a [VariantSelection] for applying.
     */
    fun toVariantSelection(): VariantSelection = VariantSelection(
        selectedModuleGradlePath = moduleGradlePath,
        flavorSelections = flavorSelections.toMutableMap(),
        selectedBuildType = buildType,
    )

    /**
     * Check if this favorite matches the given current selection.
     */
    fun matches(selection: VariantSelection): Boolean =
        moduleGradlePath == selection.selectedModuleGradlePath &&
            flavorSelections == selection.flavorSelections &&
            buildType == selection.selectedBuildType

    /**
     * Validate this favorite against current module info.
     * Returns true if all referenced dimensions/flavors/buildType still exist.
     */
    fun isValid(moduleInfo: AndroidModuleInfo?): Boolean {
        if (moduleInfo == null) return false
        if (variantName !in moduleInfo.availableVariants) return false
        return flavorSelections.all { (dim, flavor) ->
            moduleInfo.flavorsPerDimension[dim]?.contains(flavor) == true
        } && buildType in moduleInfo.buildTypes
    }
}
