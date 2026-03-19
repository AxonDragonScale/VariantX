package com.github.axondragonscale.variantx.action

import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.github.axondragonscale.variantx.VariantXBundle
import com.github.axondragonscale.variantx.service.ModuleDetectionService
import com.github.axondragonscale.variantx.ui.VariantXDialog
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Action triggered by `Cmd+Shift+X` / `Ctrl+Shift+X` that opens the VariantX dialog.
 * Validates that the project has Android modules and is not mid-sync before opening.
 */
class ShowVariantXAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Check if Gradle sync is in progress
        if (GradleSyncState.getInstance(project).isSyncInProgress) {
            notify(project, VariantXBundle.message("notification.syncInProgress"), NotificationType.WARNING)
            return
        }

        // Detect Android app modules
        val moduleService = project.service<ModuleDetectionService>()
        val appModules = moduleService.getAppModules()

        if (appModules.isEmpty()) {
            notify(project, VariantXBundle.message("notification.noModules"), NotificationType.WARNING)
            return
        }

        // Open the dialog
        VariantXDialog(project, appModules).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VariantX")
            .createNotification(message, type)
            .notify(project)
    }
}

