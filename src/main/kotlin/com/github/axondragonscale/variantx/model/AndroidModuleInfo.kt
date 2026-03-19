package com.github.axondragonscale.variantx.model

/**
 * Represents a detected Android module with its variant configuration.
 */
data class AndroidModuleInfo(
    val name: String,               // display name = androidProject.name (last Gradle path segment, e.g. "login")
    val gradlePath: String,         // full Gradle project path, e.g. ":app" or ":feature:login"
    val isAppModule: Boolean,
    val flavorDimensions: List<String>,
    val flavorsPerDimension: Map<String, List<String>>,
    val buildTypes: List<String>,
    val availableVariants: Set<String>,
    val currentVariant: String?,
)
