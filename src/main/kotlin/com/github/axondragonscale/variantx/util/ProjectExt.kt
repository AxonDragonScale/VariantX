package com.github.axondragonscale.variantx.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

/**
 * Finds the IntelliJ [Module] by its [name][Module.getName].
 * Uses [ModuleManager.findModuleByName] for an O(1) lookup.
 */
fun Project.findModuleByName(moduleName: String): Module? =
    ModuleManager.getInstance(this).findModuleByName(moduleName)
