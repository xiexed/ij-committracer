package com.example.ijcommittracer.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Service for displaying notifications in the IDE.
 */
object NotificationService {

    private const val GROUP_ID = "Commit Tracer Notifications"

    /**
     * Shows an information notification.
     */
    fun showInfo(project: Project?, message: String, title: String = "Commit Tracer") {
        notify(project, message, title, NotificationType.INFORMATION)
    }

    /**
     * Shows a warning notification.
     */
    fun showWarning(project: Project?, message: String, title: String = "Commit Tracer") {
        notify(project, message, title, NotificationType.WARNING)
    }

    /**
     * Shows an error notification.
     */
    fun showError(project: Project?, message: String, title: String = "Commit Tracer") {
        notify(project, message, title, NotificationType.ERROR)
    }

    private fun notify(project: Project?, message: String, title: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, message, type)
            .notify(project)
    }
}
