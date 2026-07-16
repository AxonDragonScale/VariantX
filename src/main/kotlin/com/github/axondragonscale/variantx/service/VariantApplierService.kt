package com.github.axondragonscale.variantx.service

import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.github.axondragonscale.variantx.VariantXBundle
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.github.axondragonscale.variantx.model.VariantSelection
import com.github.axondragonscale.variantx.util.findModuleByName
import com.github.axondragonscale.variantx.util.notifyVariantX
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

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
            logger.warn("Variant '$variantName' is not available for module '${moduleInfo.displayName}'")
            project.notifyVariantX(
                VariantXBundle.message("notification.variantSetFailed", "Invalid variant: $variantName"),
                NotificationType.WARNING,
            )
            return false
        }

        // Already the selected variant — skip the apply so we don't trigger an unnecessary
        // Gradle sync (e.g. when Build / Install is pressed for the current variant).
        if (variantName == moduleInfo.currentVariant) {
            logger.info("Variant '$variantName' already selected on module '${moduleInfo.displayName}', skipping apply")
            return true
        }

        val module = project.findModuleByName(moduleInfo.moduleName)
        if (module == null) {
            logger.error("Module '${moduleInfo.moduleName}' not found in project")
            project.notifyVariantX(
                VariantXBundle.message("notification.variantSetFailed", "Module not found: ${moduleInfo.moduleName}"),
                NotificationType.ERROR,
            )
            return false
        }

        return try {
            logger.info("Setting variant '$variantName' on module '${moduleInfo.displayName}' (${moduleInfo.gradlePath})")
            BuildVariantUpdater.getInstance(project)
                .updateSelectedBuildVariant(module, variantName)
            project.notifyVariantX(
                VariantXBundle.message("notification.variantSet", variantName),
                NotificationType.INFORMATION,
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to set variant '$variantName' on module '${moduleInfo.displayName}'", e)
            project.notifyVariantX(
                VariantXBundle.message("notification.variantSetFailed", e.message ?: "Unknown error"),
                NotificationType.ERROR,
            )
            false
        }
    }
}
