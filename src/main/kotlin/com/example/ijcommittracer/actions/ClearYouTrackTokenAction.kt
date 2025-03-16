package com.example.ijcommittracer.actions

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.services.NotificationService
import com.example.ijcommittracer.services.YouTrackApiService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Action for clearing stored YouTrack authentication token.
 */
class ClearYouTrackTokenAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Ask for confirmation
        val result = Messages.showYesNoDialog(
            project,
            CommitTracerBundle.message("youtrack.token.clear.confirmation"),
            CommitTracerBundle.message("youtrack.token.clear.title"),
            CommitTracerBundle.message("youtrack.token.clear.yes"),
            CommitTracerBundle.message("youtrack.token.clear.no"),
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            // Get YouTrack service and clear token in the background
            val youTrackService = project.service<YouTrackApiService>()
            
            // Clear token in background thread to avoid slow operations on EDT
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                youTrackService.clearToken()
                
                // Show notification on UI thread after clearing
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    NotificationService.showInfo(
                        project,
                        CommitTracerBundle.message("youtrack.token.cleared"),
                        CommitTracerBundle.message("dialog.youtrack.auth")
                    )
                }
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        // Use EDT since we're just checking if a project is open and updating UI
        return ActionUpdateThread.EDT
    }
}
