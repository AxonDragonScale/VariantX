package com.github.axondragonscale.variantx.service

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * Detects Android modules in the project and reads their variant configuration
 * (flavor dimensions, product flavors, build types, available variants).
 */
@Service(Service.Level.PROJECT)
class ModuleDetectionService(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Find all Android modules in the project with their variant info.
     *
     * Uses [GradleUtil.findGradleModuleData] to reliably identify top-level Gradle
     * project modules. Source-set sub-modules (e.g. `app.main`, `app.unitTest`) do not
     * have their own Gradle module data node and are automatically excluded — no
     * hardcoded suffix list needed.
     */
    fun findAndroidModules(): List<AndroidModuleInfo> {
        return ModuleManager.getInstance(project).modules.mapNotNull { module ->
            try {
                // Only top-level Gradle project modules have Gradle module data.
                // Source-set sub-modules (app.main, app.unitTest, etc.) do not,
                // so this single check replaces the old hardcoded suffix list.
                GradleUtil.findGradleModuleData(module) ?: return@mapNotNull null

                AndroidFacet.getInstance(module) ?: return@mapNotNull null
                val androidModel = GradleAndroidModel.get(module) ?: return@mapNotNull null
                val androidProject = androidModel.androidProject

                val isApp = androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP
                val flavorsPerDimension = androidModel.productFlavorNamesByFlavorDimension
                val dimensions = androidProject.flavorDimensions.toList()
                val buildTypes = androidModel.buildTypeNames.toList()
                val variants = androidModel.filteredVariantNames.toSet()
                val currentVariant = androidModel.selectedVariantName
                val gradlePath = androidProject.projectPath.projectPath

                AndroidModuleInfo(
                    moduleName = module.name,
                    displayName = gradlePath.removePrefix(":").ifEmpty { module.name },
                    gradlePath = gradlePath,
                    isAppModule = isApp,
                    flavorDimensions = dimensions,
                    flavorsPerDimension = flavorsPerDimension,
                    buildTypes = buildTypes,
                    availableVariants = variants,
                    currentVariant = currentVariant,
                )
            } catch (e: Exception) {
                logger.warn("Failed to read variant info for module ${module.name}", e)
                null
            }
        }.distinctBy { it.gradlePath }
    }

    /**
     * Find only app modules (not libraries) in the project.
     */
    fun getAppModules(): List<AndroidModuleInfo> =
        findAndroidModules().filter { it.isAppModule }
}
