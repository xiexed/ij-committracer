package com.example.ijcommittracer.actions

import com.example.ijcommittracer.services.HiBobApiService
import com.example.ijcommittracer.services.NotificationService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to clear the HiBob API token and cached data.
 */
class ClearHiBobTokenAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Clear token and cache
        val hibobService = HiBobApiService.getInstance(project)
        hibobService.setApiCredentials("") // Set empty token
        hibobService.clearCache()
        
        NotificationService.showInfo(
            project,
            "HiBob API token cleared and cache invalidated",
            "Commit Tracer"
        )
    }
}