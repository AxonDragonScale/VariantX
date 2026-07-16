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

    companion object {
        /**
         * Attempts to decompose a composed [variantName] (e.g. "stagingFreeDebug") back into
         * a [VariantSelection], reversing [composeVariantName].
         *
         * This is used to seed the dialog with the variant that Android Studio *actually* has
         * selected, rather than a potentially stale persisted selection.
         *
         * @return the decomposed selection, or `null` if [variantName] cannot be unambiguously
         *         mapped onto the given [buildTypes] and [flavorsPerDimension].
         */
        fun fromVariantName(
            variantName: String,
            moduleGradlePath: String,
            dimensionOrder: List<String>,
            flavorsPerDimension: Map<String, List<String>>,
            buildTypes: List<String>,
        ): VariantSelection? {
            if (variantName.isEmpty()) return null

            // Prefer longer build type names first so more specific suffixes win.
            for (buildType in buildTypes.sortedByDescending { it.length }) {
                if (dimensionOrder.isEmpty()) {
                    // No flavors: the variant name is exactly the build type.
                    if (variantName == buildType) {
                        return VariantSelection(
                            selectedModuleGradlePath = moduleGradlePath,
                            flavorSelections = mutableMapOf(),
                            selectedBuildType = buildType,
                        )
                    }
                    continue
                }

                val suffix = buildType.replaceFirstChar { it.uppercase() }
                if (!variantName.endsWith(suffix) || variantName.length <= suffix.length) continue

                val flavorPart = variantName.dropLast(suffix.length)
                val flavors = decomposeFlavors(flavorPart, dimensionOrder, flavorsPerDimension) ?: continue
                return VariantSelection(
                    selectedModuleGradlePath = moduleGradlePath,
                    flavorSelections = flavors.toMutableMap(),
                    selectedBuildType = buildType,
                )
            }
            return null
        }

        /**
         * Splits [flavorPart] (e.g. "stagingFree") into a flavor per dimension, matching
         * the camelCase convention used by [composeVariantName]. Prefers the longest flavor
         * match at each position to avoid prefix ambiguity (e.g. "pro" vs "proPlus").
         */
        private fun decomposeFlavors(
            flavorPart: String,
            dimensionOrder: List<String>,
            flavorsPerDimension: Map<String, List<String>>,
        ): Map<String, String>? {
            val result = mutableMapOf<String, String>()
            var remaining = flavorPart
            dimensionOrder.forEachIndexed { index, dimension ->
                val candidates = flavorsPerDimension[dimension] ?: return null
                val match = candidates.sortedByDescending { it.length }.firstOrNull { flavor ->
                    remaining.startsWith(flavorSegment(flavor, index))
                } ?: return null
                remaining = remaining.substring(flavorSegment(match, index).length)
                result[dimension] = match
            }
            return if (remaining.isEmpty()) result else null
        }

        private fun flavorSegment(flavor: String, index: Int): String =
            if (index == 0) flavor.replaceFirstChar { it.lowercase() }
            else flavor.replaceFirstChar { it.uppercase() }
    }
}

