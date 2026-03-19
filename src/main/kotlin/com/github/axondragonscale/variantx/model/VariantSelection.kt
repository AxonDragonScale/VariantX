package com.github.axondragonscale.variantx.model

/**
 * Represents the user's current selection in the VariantX dialog.
 */
data class VariantSelection(
    var selectedModuleGradlePath: String = "",
    var flavorSelections: MutableMap<String, String> = mutableMapOf(),
    var selectedBuildType: String = "debug",
) {
    /**
     * Composes the variant name by concatenating flavors (in dimension order)
     * and the build type in camelCase, matching the Android Gradle Plugin convention:
     * - First flavor: lowercase first char (e.g. "staging")
     * - Subsequent flavors: uppercase first char (e.g. "Free", "MinApi21")
     * - Build type: always uppercase first char (e.g. "Debug")
     *
     * Example: dimensions=["environment","tier"], flavors={"environment":"staging","tier":"free"},
     *          buildType="debug" → "stagingFreeDebug"
     *
     * If no flavor dimensions exist, returns the build type as-is (e.g. "debug").
     */
    fun composeVariantName(dimensionOrder: List<String>): String {
        if (dimensionOrder.isEmpty()) return selectedBuildType

        val flavorPart = dimensionOrder.mapIndexed { index, dim ->
            val flavor = flavorSelections[dim] ?: return@mapIndexed ""
            if (index == 0) flavor.replaceFirstChar { it.lowercase() }
            else flavor.replaceFirstChar { it.uppercase() }
        }.joinToString("")

        val buildTypePart = selectedBuildType.replaceFirstChar { it.uppercase() }
        return "$flavorPart$buildTypePart"
    }
}

