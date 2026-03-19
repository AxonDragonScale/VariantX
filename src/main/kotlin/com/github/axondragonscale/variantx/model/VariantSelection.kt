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
     * and the build type in camelCase.
     *
     * Example: dimensions=["environment","tier"], flavors={"environment":"staging","tier":"free"},
     *          buildType="debug" → "stagingFreeDebug"
     */
    fun composeVariantName(dimensionOrder: List<String>): String {
        val flavorPart = dimensionOrder.mapIndexed { index, dim ->
            val flavor = flavorSelections[dim] ?: ""
            if (index == 0) flavor.replaceFirstChar { it.lowercase() }
            else flavor.replaceFirstChar { it.uppercase() }
        }.joinToString("")

        val buildTypePart = selectedBuildType.replaceFirstChar { it.uppercase() }
        return if (flavorPart.isEmpty()) selectedBuildType
        else "$flavorPart$buildTypePart"
    }
}

