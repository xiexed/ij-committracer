package com.example.ijcommittracer.actions

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.services.NotificationService
import com.example.ijcommittracer.services.YouTrackApiService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * Action for configuring YouTrack authentication token.
 */
class ConfigureYouTrackTokenAction : AnAction(), DumbAware {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Get YouTrack service
        val youTrackService = project.service<YouTrackApiService>()
        
        // Request token from user
        val token = youTrackService.getOrRequestToken()
        if (token.isNullOrBlank()) {
            NotificationService.showWarning(
                project,
                CommitTracerBundle.message("youtrack.token.canceled"),
                CommitTracerBundle.message("dialog.youtrack.auth")
            )
            return
        }
        
        // Validate token in background
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            CommitTracerBundle.message("youtrack.token.validating"),
            true
        ) {
            private var isValid = false
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                isValid = youTrackService.validateToken(token)
            }
            
            override fun onSuccess() {
                if (isValid) {
                    // Token is valid, store it
                    youTrackService.storeToken(token)
                    NotificationService.showInfo(
                        project,
                        CommitTracerBundle.message("youtrack.token.stored"),
                        CommitTracerBundle.message("dialog.youtrack.auth")
                    )
                } else {
                    NotificationService.showError(
                        project,
                        CommitTracerBundle.message("youtrack.token.invalid"),
                        CommitTracerBundle.message("dialog.youtrack.auth")
                    )
                }
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        // Use EDT since we're just checking if a project is open and updating UI
        return ActionUpdateThread.EDT
    }
}
