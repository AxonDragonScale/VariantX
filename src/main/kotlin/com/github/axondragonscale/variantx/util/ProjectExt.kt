package com.github.axondragonscale.variantx.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.facet.stableName

/**
 * Finds the IntelliJ [Module] whose derived Gradle path matches [gradlePath].
 */
fun Project.findModuleByStableName(stableName: String): Module? {
    return ModuleManager.getInstance(this).modules.find { module ->
        module.stableName.toString() == stableName
    }
}
