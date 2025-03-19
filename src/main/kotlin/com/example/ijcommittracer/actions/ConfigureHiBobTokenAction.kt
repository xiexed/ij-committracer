package com.example.ijcommittracer.actions

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.services.HiBobApiService
import com.example.ijcommittracer.services.NotificationService
import com.example.ijcommittracer.services.TokenStorageService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPasswordField

/**
 * Action to configure HiBob API token.
 * Uses IntelliJ UI DSL for improved layout and secure password field.
 */
class ConfigureHiBobTokenAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val dialog = HiBobTokenDialog(project)
        if (dialog.showAndGet()) {
            val serviceUserId = dialog.getServiceUserId()
            val token = dialog.getToken()
            val baseUrl = dialog.getBaseUrl()
            
            if (token.isNotBlank() && serviceUserId.isNotBlank()) {
                // Store the token securely in the background
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val tokenStorage = TokenStorageService.getInstance(project)
                    tokenStorage.setHiBobToken(token)
                    tokenStorage.setHiBobServiceUserId(serviceUserId)
                    tokenStorage.setHiBobBaseUrl(baseUrl)
                    
                    // Also update HiBobApiService for immediate use
                    val hibobService = HiBobApiService.getInstance(project)
                    hibobService.setApiCredentials(serviceUserId, token, baseUrl)
                    
                    // Switch to UI thread to show notification
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        NotificationService.showInfo(
                            project,
                            "HiBob API token configured successfully",
                            "Commit Tracer"
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Dialog to enter HiBob API token.
     * Uses IntelliJ UI DSL and secure password field.
     */
    private class HiBobTokenDialog(project: Project) : DialogWrapper(project) {
        private val serviceUserIdField = JBTextField()
        private val tokenField = JPasswordField()
        private val baseUrlField = JBTextField()
        
        init {
            title = "Configure HiBob API"
            
            // Pre-populate fields if available
            val tokenStorage = TokenStorageService.getInstance(project)
            baseUrlField.text = tokenStorage.getHiBobBaseUrl()
            serviceUserIdField.text = tokenStorage.getHiBobServiceUserId()
            
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val mainPanel = BorderLayoutPanel()
            mainPanel.preferredSize = Dimension(450, 150)
            
            val formPanel = panel {
                row("HiBob Service User ID:") {
                    cell(serviceUserIdField)
                        .columns(30)
                        .comment("Your HiBob service user ID")
                }
                row("HiBob API Token:") {
                    cell(tokenField)
                        .columns(30)
                        .comment("Securely stored in IntelliJ credential store")
                }
                row("API Base URL:") {
                    cell(baseUrlField)
                        .columns(30)
                        .comment("Default: https://api.hibob.com/v1")
                }
            }
            
            mainPanel.addToCenter(formPanel)
            mainPanel.border = JBUI.Borders.empty(10)
            
            return mainPanel
        }
        
        fun getServiceUserId(): String = serviceUserIdField.text
        
        fun getToken(): String = String(tokenField.password)
        
        fun getBaseUrl(): String = baseUrlField.text.ifBlank { "https://api.hibob.com/v1" }
    }
}