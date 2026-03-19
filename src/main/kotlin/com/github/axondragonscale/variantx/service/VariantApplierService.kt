package com.github.axondragonscale.variantx.service

import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.github.axondragonscale.variantx.VariantXBundle
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.github.axondragonscale.variantx.model.VariantSelection
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Applies a variant selection to the project by delegating to
 * Android Studio's [BuildVariantUpdater].
 */
@Service(Service.Level.PROJECT)
class VariantApplierService(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Apply the given [selection] to the [moduleInfo] module.
     * [BuildVariantUpdater] will propagate compatible variants to library modules.
     *
     * @return `true` if the variant was applied successfully.
     */
    fun applyVariant(selection: VariantSelection, moduleInfo: AndroidModuleInfo): Boolean {
        val variantName = selection.composeVariantName(moduleInfo.flavorDimensions)

        if (variantName !in moduleInfo.availableVariants && moduleInfo.availableVariants.isNotEmpty()) {
            logger.warn("Variant '$variantName' is not available for module '${moduleInfo.name}'")
            notify(
                VariantXBundle.message("notification.variantSetFailed", "Invalid variant: $variantName"),
                NotificationType.WARNING,
            )
            return false
        }

        // Find the IntelliJ Module object by matching its external (file-system) path to the gradle path
        val projectBasePath = project.basePath ?: run {
            logger.error("Project base path is null")
            return false
        }
        val module = ModuleManager.getInstance(project).modules.find { m ->
            val extPath = ExternalSystemApiUtil.getExternalProjectPath(m) ?: return@find false
            val relative = extPath.removePrefix(projectBasePath)
            val derivedPath = if (relative.isEmpty()) ":"
            else relative.replace(File.separatorChar, ':').let {
                if (it.startsWith(':')) it else ":$it"
            }
            derivedPath == moduleInfo.gradlePath
        }

        if (module == null) {
            logger.error("Module with gradle path '${moduleInfo.gradlePath}' not found in project")
            notify(
                VariantXBundle.message("notification.variantSetFailed", "Module not found: ${moduleInfo.gradlePath}"),
                NotificationType.ERROR,
            )
            return false
        }

        return try {
            logger.info("Setting variant '$variantName' on module '${moduleInfo.name}' (${moduleInfo.gradlePath})")
            BuildVariantUpdater.getInstance(project)
                .updateSelectedBuildVariant(module, variantName)
            notify(
                VariantXBundle.message("notification.variantSet", variantName),
                NotificationType.INFORMATION,
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to set variant '$variantName' on module '${moduleInfo.name}'", e)
            notify(
                VariantXBundle.message("notification.variantSetFailed", e.message ?: "Unknown error"),
                NotificationType.ERROR,
            )
            false
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VariantX")
            .createNotification(message, type)
            .notify(project)
    }
}
