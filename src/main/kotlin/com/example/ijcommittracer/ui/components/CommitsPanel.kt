package com.example.ijcommittracer.ui.components

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import com.example.ijcommittracer.ui.models.CommitTableModel
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * Panel for displaying Git commits.
 */
class CommitsPanel(private val commits: List<CommitInfo>) : JPanel(BorderLayout()) {
    
    private lateinit var commitsTable: JBTable
    private val filteredCommits: List<CommitInfo> = commits
    private val youtrackTicketPattern = Pattern.compile("([A-Z]+-\\d+)") // Pattern for YouTrack ticket references
    
    init {
        initialize()
    }
    
    private fun initialize() {
        // Create filter panel at the top
        val filterPanel = JPanel(BorderLayout())
        filterPanel.border = JBUI.Borders.emptyBottom(5)
        
        val searchLabel = JBLabel(CommitTracerBundle.message("dialog.filter.search"))
        searchLabel.border = JBUI.Borders.empty(0, 5)
        filterPanel.add(searchLabel, BorderLayout.WEST)
        
        val searchField = JTextField().apply {
            // Add clear button (X) with escape key handler to clear the field
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                        text = ""
                    }
                }
            })
        }
        filterPanel.add(searchField, BorderLayout.CENTER)
        add(filterPanel, BorderLayout.NORTH)
        
        // Create table with commits
        val tableModel = CommitTableModel(filteredCommits)
        commitsTable = JBTable(tableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            columnModel.getColumn(0).preferredWidth = 450 // Message
            columnModel.getColumn(1).preferredWidth = 120 // Date (dd/MM/yy, HH:mm)
            columnModel.getColumn(2).preferredWidth = 80  // Hash (7 chars + potential *)
            columnModel.getColumn(3).preferredWidth = 50  // Tests
            
            // Create a custom renderer for the Tests column with green/red icons
            val testsRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val label = super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column) as JLabel
                    label.horizontalAlignment = SwingConstants.CENTER
                    
                    if (value == true) {
                        // Green checkmark for test touches
                        label.text = "✓"
                        label.foreground = JBColor.GREEN
                        label.font = label.font.deriveFont(Font.BOLD)
                    } else {
                        // Red X for no test touches
                        label.text = "✗"
                        label.foreground = JBColor.RED
                    }
                    
                    return label
                }
            }
            columnModel.getColumn(3).cellRenderer = testsRenderer
            
            // Add row sorter for sorting
            val sorter = TableRowSorter(tableModel)
            
            // Set comparator for the date column to sort by date
            sorter.setComparator(1, Comparator<String> { date1, date2 ->
                try {
                    // Create a format parser for the displayed date format
                    val dateFormat = SimpleDateFormat("dd/MM/yy, HH:mm", Locale.US)
                    val d1 = dateFormat.parse(date1)
                    val d2 = dateFormat.parse(date2)
                    d1.compareTo(d2)
                } catch (e: Exception) {
                    date1.compareTo(date2)
                }
            })
            
            rowSorter = sorter
            
            // Add document listener to filter table when text changes
            searchField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                
                private fun filterTable() {
                    val text = searchField.text
                    if (text.isNullOrBlank()) {
                        sorter.rowFilter = null
                    } else {
                        try {
                            // Create case-insensitive regex filter for message column (0)
                            sorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 0)
                        } catch (ex: java.util.regex.PatternSyntaxException) {
                            // If the regex pattern is invalid, just show all rows
                            sorter.rowFilter = null
                        }
                    }
                }
            })
        }
        
        // Create detail panel for selected commit
        val detailsPanel = JPanel(BorderLayout())
        val detailsHeaderLabel = JBLabel(CommitTracerBundle.message("dialog.commit.details"))
        detailsHeaderLabel.border = JBUI.Borders.empty(5, 5, 0, 5)
        detailsPanel.add(detailsHeaderLabel, BorderLayout.NORTH)
        
        val detailsArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(5)
        }
        detailsPanel.add(JBScrollPane(detailsArea), BorderLayout.CENTER)
        
        // Add selection listener to show details
        commitsTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = commitsTable.selectedRow
                if (selectedRow >= 0) {
                    val modelRow = commitsTable.convertRowIndexToModel(selectedRow)
                    val commit = filteredCommits[modelRow]
                    
                    // Highlight YouTrack tickets in the commit message
                    val message = highlightYouTrackTickets(commit.message)
                    
                    detailsArea.text = buildString {
                        append(CommitTracerBundle.message("dialog.repository.label", commit.repositoryName))
                        append("\n")
                        append("Commit: ${commit.hash}\n")
                        append("Author: ${commit.author}\n")
                        append("Date: ${commit.date}\n")
                        
                        // Add branches if available
                        if (commit.branches.isNotEmpty()) {
                            append(CommitTracerBundle.message("dialog.branch.label", commit.branches.joinToString(", ")))
                            append("\n")
                        }
                        
                        append("\nMessage:\n$message")
                    }
                    detailsArea.caretPosition = 0
                }
            }
        }
        
        // Split view: table on top, details below
        val splitPane = OnePixelSplitter(true, 0.6f)
        splitPane.firstComponent = JBScrollPane(commitsTable)
        splitPane.secondComponent = detailsPanel
        
        add(splitPane, BorderLayout.CENTER)
        
        // Select first row by default if there are commits
        if (filteredCommits.isNotEmpty()) {
            commitsTable.selectionModel.setSelectionInterval(0, 0)
        }
    }
    
    /**
     * Update the table with new data
     */
    fun updateData(newCommits: List<CommitInfo>) {
        (commitsTable.model as CommitTableModel).updateData(newCommits)
    }
    
    /**
     * Highlight YouTrack tickets in commit message
     */
    private fun highlightYouTrackTickets(message: String): String {
        val matcher = youtrackTicketPattern.matcher(message)
        return matcher.replaceAll("**$1**") // Bold the ticket references
    }
}