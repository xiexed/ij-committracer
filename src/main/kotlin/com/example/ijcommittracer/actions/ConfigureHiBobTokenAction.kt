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
            val token = dialog.getToken()
            val baseUrl = dialog.getBaseUrl()
            
            if (token.isNotBlank()) {
                // Store the token securely in the TokenStorageService
                val tokenStorage = TokenStorageService.getInstance(project)
                tokenStorage.setHiBobToken(token)
                tokenStorage.setHiBobBaseUrl(baseUrl)
                
                // Also update HiBobApiService for immediate use
                val hibobService = HiBobApiService.getInstance(project)
                hibobService.setApiCredentials(token, baseUrl)
                
                NotificationService.showInfo(
                    project,
                    "HiBob API token configured successfully",
                    "Commit Tracer"
                )
            }
        }
    }
    
    /**
     * Dialog to enter HiBob API token.
     * Uses IntelliJ UI DSL and secure password field.
     */
    private class HiBobTokenDialog(project: Project) : DialogWrapper(project) {
        private val tokenField = JPasswordField()
        private val baseUrlField = JBTextField()
        
        init {
            title = "Configure HiBob API"
            
            // Pre-populate the base URL if available
            val tokenStorage = TokenStorageService.getInstance(project)
            baseUrlField.text = tokenStorage.getHiBobBaseUrl()
            
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val mainPanel = BorderLayoutPanel()
            mainPanel.preferredSize = Dimension(450, 150)
            
            val formPanel = panel {
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
        
        fun getToken(): String = String(tokenField.password)
        
        fun getBaseUrl(): String = baseUrlField.text.ifBlank { "https://api.hibob.com/v1" }
    }
}