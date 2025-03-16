package com.example.ijcommittracer.actions

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.services.HiBobApiService
import com.example.ijcommittracer.services.NotificationService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Action to configure HiBob API token.
 */
class ConfigureHiBobTokenAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val dialog = HiBobTokenDialog(project)
        if (dialog.showAndGet()) {
            val token = dialog.getToken()
            val baseUrl = dialog.getBaseUrl()
            
            if (token.isNotBlank()) {
                // Store the token in the service
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
     */
    private class HiBobTokenDialog(project: Project) : DialogWrapper(project) {
        private val tokenField = JBTextField()
        private val baseUrlField = JBTextField("https://api.hibob.com/v1")
        
        init {
            title = "Configure HiBob API"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            panel.preferredSize = Dimension(400, 150)
            
            val fieldsPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(10)
            }
            
            // Token field
            val tokenLabel = JBLabel("HiBob API Token:")
            tokenLabel.border = JBUI.Borders.emptyBottom(5)
            fieldsPanel.add(tokenLabel, BorderLayout.NORTH)
            tokenField.preferredSize = Dimension(tokenField.preferredSize.width, 35)
            fieldsPanel.add(tokenField, BorderLayout.CENTER)
            
            // Base URL field
            val baseUrlPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyTop(10)
            }
            val baseUrlLabel = JBLabel("API Base URL (optional):")
            baseUrlLabel.border = JBUI.Borders.emptyBottom(5)
            baseUrlPanel.add(baseUrlLabel, BorderLayout.NORTH)
            baseUrlField.preferredSize = Dimension(baseUrlField.preferredSize.width, 35)
            baseUrlPanel.add(baseUrlField, BorderLayout.CENTER)
            
            fieldsPanel.add(baseUrlPanel, BorderLayout.SOUTH)
            
            panel.add(fieldsPanel, BorderLayout.CENTER)
            
            return panel
        }
        
        fun getToken(): String = tokenField.text
        
        fun getBaseUrl(): String = baseUrlField.text
    }
}