package com.github.axondragonscale.variantx.service

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

/**
 * Detects Android modules in the project and reads their variant configuration
 * (flavor dimensions, product flavors, build types, available variants).
 */
@Service(Service.Level.PROJECT)
class ModuleDetectionService(private val project: Project) {

    private val logger = thisLogger()

    companion object {
        /**
         * IntelliJ Gradle integration creates sub-modules for every Gradle source set
         * (e.g. app.main, app.unitTest, app.androidTest).  We want only the top-level
         * Gradle project module, so we skip any module whose last dot-separated component
         * matches a known source-set name.
         */
        private val SOURCE_SET_SUFFIXES = setOf(
            "main", "test", "unitTest", "androidTest",
            "testDebug", "testRelease",
            "debugAndroidTest", "releaseAndroidTest",
            "testFixtures",
        )
    }

    /**
     * Find all Android modules in the project with their variant info.
     */
    fun findAndroidModules(): List<AndroidModuleInfo> {
        return ModuleManager.getInstance(project).modules.mapNotNull { module ->
            try {
                // Skip source-set sub-modules such as app.main, app.unitTest, app.androidTest
                if (module.name.contains('.')) {
                    val suffix = module.name.substringAfterLast('.')
                    if (suffix in SOURCE_SET_SUFFIXES) return@mapNotNull null
                }

                AndroidFacet.getInstance(module) ?: return@mapNotNull null
                val androidModel = GradleAndroidModel.get(module) ?: return@mapNotNull null
                val androidProject = androidModel.androidProject

                val isApp = androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP

                // Use GradleAndroidModel's convenience getters
                val flavorsPerDimension = androidModel.productFlavorNamesByFlavorDimension
                val dimensions = androidProject.flavorDimensions.toList()
                val buildTypes = androidModel.buildTypeNames.toList()
                val variants = androidModel.filteredVariantNames.toSet()
                val currentVariant = androidModel.selectedVariantName
                val gradlePath = androidProject.projectPath.projectPath

                AndroidModuleInfo(
                    name = gradlePath.removePrefix(":").ifEmpty { module.name },
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
        }
    }

    /**
     * Find only app modules (not libraries) in the project.
     */
    fun getAppModules(): List<AndroidModuleInfo> =
        findAndroidModules().filter { it.isAppModule }

    /**
     * Derive the Gradle project path (e.g. ":app", ":feature:login") from the module's
     * filesystem path relative to the project root.
     */
    private fun deriveGradleModulePath(module: Module): String {
        val externalPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        val projectBasePath = project.basePath
        return if (externalPath != null && projectBasePath != null) {
            val relative = externalPath.removePrefix(projectBasePath)
            if (relative.isEmpty()) {
                ":"
            } else {
                relative.replace(File.separatorChar, ':').let {
                    if (it.startsWith(':')) it else ":$it"
                }
            }
        } else {
            ":${module.name}"
        }
    }
}
