package com.example.ijcommittracer.ui

import com.example.ijcommittracer.services.YouTrackService.YouTrackIssue
import com.example.ijcommittracer.services.YouTrackService.YouTrackTag
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * UI component for displaying YouTrack issue information.
 */
class YouTrackIssuePanel(private val issue: YouTrackIssue) : JBPanel<YouTrackIssuePanel>(BorderLayout()) {
    
    init {
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(8)
        )
        
        // Issue ID and summary
        val titlePanel = JPanel(BorderLayout()).apply { 
            isOpaque = false
            border = JBUI.Borders.emptyBottom(5)
        }
        
        val idLabel = JBLabel(issue.id).apply {
            font = font.deriveFont(Font.BOLD)
            border = JBUI.Borders.emptyRight(8)
        }
        
        val summaryLabel = JBLabel(issue.summary).apply {
            font = font.deriveFont(font.size + 1f)
        }
        
        titlePanel.add(idLabel, BorderLayout.WEST)
        titlePanel.add(summaryLabel, BorderLayout.CENTER)
        
        add(titlePanel, BorderLayout.NORTH)
        
        // Tags
        if (issue.tags.isNotEmpty()) {
            val tagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(5)
            }
            
            issue.tags.forEach { tag ->
                tagsPanel.add(createTagLabel(tag))
            }
            
            add(tagsPanel, BorderLayout.CENTER)
        }
    }
    
    /**
     * Creates a styled label for a YouTrack tag.
     */
    private fun createTagLabel(tag: YouTrackTag): JBLabel {
        val backgroundColor = if (tag.color != null) {
            try {
                Color.decode(tag.color)
            } catch (e: NumberFormatException) {
                JBColor.LIGHT_GRAY
            }
        } else {
            JBColor.LIGHT_GRAY
        }
        
        // Determine text color (white for dark backgrounds, black for light backgrounds)
        val textColor = if (isDarkColor(backgroundColor)) JBColor.WHITE else JBColor.BLACK
        
        return JBLabel(tag.name).apply {
            background = backgroundColor
            foreground = textColor
            isOpaque = true
            horizontalAlignment = SwingConstants.CENTER
            border = JBUI.Borders.empty(2, 8)
        }
    }
    
    /**
     * Determines if a color is dark based on its luminance.
     */
    private fun isDarkColor(color: Color): Boolean {
        val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue) / 255
        return luminance < 0.5
    }
}
