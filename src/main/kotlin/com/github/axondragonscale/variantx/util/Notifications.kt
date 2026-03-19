package com.github.axondragonscale.variantx.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/** Notification group ID registered in plugin.xml. */
const val NOTIFICATION_GROUP_ID = "VariantX"

/**
 * Shows a balloon notification using the VariantX notification group.
 * Centralizes the notification boilerplate used across the plugin.
 */
fun Project.notifyVariantX(message: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(message, type)
        .notify(this)
}

