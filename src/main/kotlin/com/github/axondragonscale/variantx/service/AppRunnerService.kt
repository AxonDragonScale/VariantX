package com.github.axondragonscale.variantx.service

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.github.axondragonscale.variantx.VariantXBundle
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.github.axondragonscale.variantx.model.VariantSelection
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Triggers Gradle tasks (assemble / install) for a given Android app module + variant.
 * Handles the sync-then-run timing when a variant change triggers a Gradle sync.
 */
@Service(Service.Level.PROJECT)
class AppRunnerService(private val project: Project) : Disposable {

    private val logger = thisLogger()

    override fun dispose() = Unit

    // ── Gradle task execution ──

    /**
     * Execute `:module:assemble{Variant}` — builds without installing or running.
     */
    fun assembleApp(moduleInfo: AndroidModuleInfo, variantName: String) {
        val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
        val taskPath = "${moduleInfo.gradlePath}:assemble$capitalizedVariant"
        logger.info("Assembling Gradle task: $taskPath")
        runGradleTask(taskPath)
    }

    /**
     * Execute `:module:install{Variant}` — builds, installs, and runs the app.
     */
    fun runApp(moduleInfo: AndroidModuleInfo, variantName: String) {
        val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
        val taskPath = "${moduleInfo.gradlePath}:install$capitalizedVariant"
        logger.info("Running Gradle task: $taskPath")
        runGradleTask(taskPath)
    }

    private fun runGradleTask(taskPath: String) {
        val taskSettings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = project.basePath
            taskNames = listOf(taskPath)
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }
        ApplicationManager.getApplication().invokeLater {
            ExternalSystemUtil.runTask(
                taskSettings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
            )
        }
    }

    // ── Apply + Assemble ──

    /**
     * Apply the variant, wait for any triggered sync, then assemble (no install/run).
     */
    fun applyAndAssemble(
        selection: VariantSelection,
        moduleInfo: AndroidModuleInfo,
        applierService: VariantApplierService,
    ) {
        val applied = applierService.applyVariant(selection, moduleInfo)
        if (!applied) return

        val variantName = selection.composeVariantName(moduleInfo.flavorDimensions)
        notify(
            VariantXBundle.message("notification.building", moduleInfo.name, variantName),
            NotificationType.INFORMATION,
        )

        waitForSyncIfNeeded(
            logTag = "VariantX:AssembleSyncListener",
            onSyncSucceeded = { assembleApp(moduleInfo, variantName) },
        )
    }

    // ── Apply + Run ──

    /**
     * Apply the variant, wait for any triggered sync, then install and run the app.
     */
    fun applyAndRun(
        selection: VariantSelection,
        moduleInfo: AndroidModuleInfo,
        applierService: VariantApplierService,
    ) {
        val applied = applierService.applyVariant(selection, moduleInfo)
        if (!applied) return

        val variantName = selection.composeVariantName(moduleInfo.flavorDimensions)
        notify(
            VariantXBundle.message("notification.running", moduleInfo.name, variantName),
            NotificationType.INFORMATION,
        )

        waitForSyncIfNeeded(
            logTag = "VariantX:SyncListener",
            onSyncSucceeded = { runApp(moduleInfo, variantName) },
        )
    }

    // ── Helpers ──

    private fun waitForSyncIfNeeded(logTag: String, onSyncSucceeded: () -> Unit) {
        if (GradleSyncState.getInstance(project).isSyncInProgress) {
            logger.info("Sync in progress after variant change, waiting ($logTag)…")
            val disposable = Disposer.newDisposable(this, logTag)
            GradleSyncState.subscribe(project, object : GradleSyncListener {
                override fun syncSucceeded(project: Project) {
                    logger.info("Sync succeeded, proceeding with task ($logTag)")
                    Disposer.dispose(disposable)
                    onSyncSucceeded()
                }

                override fun syncFailed(project: Project, errorMessage: String) {
                    logger.warn("Sync failed after variant change: $errorMessage")
                    Disposer.dispose(disposable)
                    notify(
                        VariantXBundle.message("notification.variantSetFailed", "Gradle sync failed: $errorMessage"),
                        NotificationType.ERROR,
                    )
                }
            }, disposable)
        } else {
            onSyncSucceeded()
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VariantX")
            .createNotification(message, type)
            .notify(project)
    }
}
