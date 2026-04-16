package com.github.axondragonscale.variantx.model

/**
 * Represents a detected Android module with its variant configuration.
 *
 * @property moduleName   The IntelliJ [com.intellij.openapi.module.Module.getName] value,
 *                        used to look up the module instance later (e.g. when applying variants).
 * @property displayName  Human-readable name derived from the Gradle path (e.g. "app", "feature:login").
 * @property gradlePath   Full Gradle project path, e.g. ":app" or ":feature:login".
 */
data class AndroidModuleInfo(
    val moduleName: String,
    val displayName: String,
    val gradlePath: String,
    val isAppModule: Boolean,
    val flavorDimensions: List<String>,
    val flavorsPerDimension: Map<String, List<String>>,
    val buildTypes: List<String>,
    val availableVariants: Set<String>,
    val currentVariant: String?,
)
