package com.github.axondragonscale.variantx.service

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.github.axondragonscale.variantx.VariantXBundle
import com.github.axondragonscale.variantx.model.AndroidModuleInfo
import com.github.axondragonscale.variantx.model.VariantSelection
import com.github.axondragonscale.variantx.util.notifyVariantX
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
    private val applierService by lazy { project.service<VariantApplierService>() }

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

    // ── Apply + Execute ──

    /**
     * Apply the variant, wait for any triggered sync, then assemble (no install/run).
     */
    fun applyAndAssemble(selection: VariantSelection, moduleInfo: AndroidModuleInfo) {
        applyAndExecute(selection, moduleInfo, "notification.building") { variantName ->
            assembleApp(moduleInfo, variantName)
        }
    }

    /**
     * Apply the variant, wait for any triggered sync, then install and run the app.
     */
    fun applyAndRun(selection: VariantSelection, moduleInfo: AndroidModuleInfo) {
        applyAndExecute(selection, moduleInfo, "notification.running") { variantName ->
            runApp(moduleInfo, variantName)
        }
    }

    /**
     * Shared logic: apply variant → notify → wait for sync if needed → execute task.
     */
    private fun applyAndExecute(
        selection: VariantSelection,
        moduleInfo: AndroidModuleInfo,
        notificationKey: String,
        task: (variantName: String) -> Unit,
    ) {
        val applied = applierService.applyVariant(selection, moduleInfo)
        if (!applied) return

        val variantName = selection.composeVariantName(moduleInfo.flavorDimensions)
        project.notifyVariantX(
            VariantXBundle.message(notificationKey, moduleInfo.name, variantName),
            NotificationType.INFORMATION,
        )

        waitForSyncIfNeeded { task(variantName) }
    }

    // ── Helpers ──

    /**
     * If a Gradle sync is in progress, subscribes a one-shot listener to run [onSyncSucceeded]
     * after it completes. Otherwise runs [onSyncSucceeded] immediately.
     *
     * Subscribes *before* checking sync state to avoid a race where sync finishes
     * between the check and the subscribe call.
     */
    private fun waitForSyncIfNeeded(onSyncSucceeded: () -> Unit) {
        val disposable = Disposer.newDisposable(this, "VariantX:SyncListener")
        var handled = false

        GradleSyncState.subscribe(project, object : GradleSyncListener {
            override fun syncSucceeded(project: Project) {
                if (!handled) {
                    handled = true
                    Disposer.dispose(disposable)
                    logger.info("Sync succeeded, proceeding with task")
                    onSyncSucceeded()
                }
            }

            override fun syncFailed(project: Project, errorMessage: String) {
                if (!handled) {
                    handled = true
                    Disposer.dispose(disposable)
                    logger.warn("Sync failed after variant change: $errorMessage")
                    project.notifyVariantX(
                        VariantXBundle.message("notification.variantSetFailed", "Gradle sync failed: $errorMessage"),
                        NotificationType.ERROR,
                    )
                }
            }
        }, disposable)

        // If no sync is in progress, the listener is unnecessary — run immediately
        if (!GradleSyncState.getInstance(project).isSyncInProgress) {
            if (!handled) {
                handled = true
                Disposer.dispose(disposable)
                onSyncSucceeded()
            }
        }
    }
}
