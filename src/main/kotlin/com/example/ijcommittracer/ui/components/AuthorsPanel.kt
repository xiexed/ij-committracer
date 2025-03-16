package com.example.ijcommittracer.ui.components

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.AuthorStats
import com.example.ijcommittracer.actions.ListCommitsAction.ChangedFileInfo
import com.example.ijcommittracer.actions.ListCommitsAction.ChangeType
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import com.example.ijcommittracer.ui.models.AuthorTableModel
import com.example.ijcommittracer.ui.models.CommitTableModel
import com.example.ijcommittracer.ui.models.TicketsTableModel
import com.example.ijcommittracer.ui.util.TicketInfo
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * Panel for displaying author statistics.
 */
class AuthorsPanel(
    private val authorStats: Map<String, AuthorStats>,
    private val commits: List<CommitInfo>
) : JPanel(BorderLayout()) {
    
    private lateinit var authorsTable: JBTable
    private lateinit var authorCommitsTable: JBTable
    private lateinit var ticketsTable: JBTable
    private var authorCommitsSelectionListener: ListSelectionListener? = null
    private var ticketCommitsSelectionListener: ListSelectionListener? = null
    private val filteredCommits: List<CommitInfo> = commits
    
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
        
        // Create table with author statistics
        val tableModel = AuthorTableModel(authorStats.values.toList())
        authorsTable = JBTable(tableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            columnModel.getColumn(0).preferredWidth = 200 // Author
            columnModel.getColumn(1).preferredWidth = 80  // Commit Count
            columnModel.getColumn(2).preferredWidth = 80  // Tickets Count
            columnModel.getColumn(3).preferredWidth = 80  // Blockers Count
            columnModel.getColumn(4).preferredWidth = 80  // Regressions Count
            columnModel.getColumn(5).preferredWidth = 80  // Test Commits Count
            columnModel.getColumn(6).preferredWidth = 80  // Test Coverage %
            columnModel.getColumn(7).preferredWidth = 150 // First Commit
            columnModel.getColumn(8).preferredWidth = 150 // Last Commit
            columnModel.getColumn(9).preferredWidth = 80  // Active Days
            columnModel.getColumn(10).preferredWidth = 120 // Commits/Day
            
            // Center-align numeric columns
            val centerRenderer = DefaultTableCellRenderer()
            centerRenderer.horizontalAlignment = SwingConstants.CENTER
            columnModel.getColumn(1).cellRenderer = centerRenderer // Commit Count
            columnModel.getColumn(2).cellRenderer = centerRenderer // Tickets Count
            columnModel.getColumn(3).cellRenderer = centerRenderer // Blockers Count
            columnModel.getColumn(4).cellRenderer = centerRenderer // Regressions Count
            columnModel.getColumn(5).cellRenderer = centerRenderer // Test Commits Count
            
            // Create a custom renderer for the Test Coverage % column with color-coding
            val testCoverageRenderer = object : DefaultTableCellRenderer() {
                // Use a local formatter for percentage values
                private val percentageFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
                
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                    label.horizontalAlignment = SwingConstants.CENTER
                    
                    if (value is Number) {
                        val percentage = value.toDouble()
                        label.text = percentageFormat.format(percentage) + "%"
                        
                        // Color code based on test coverage percentage
                        when {
                            percentage >= 75.0 -> label.foreground = JBColor.GREEN.darker()
                            percentage >= 50.0 -> label.foreground = JBColor.ORANGE
                            percentage >= 25.0 -> label.foreground = JBColor.YELLOW.darker()
                            else -> label.foreground = JBColor.RED
                        }
                    }
                    
                    return label
                }
            }
            columnModel.getColumn(6).cellRenderer = testCoverageRenderer // Test Coverage %
            
            columnModel.getColumn(9).cellRenderer = centerRenderer // Active Days
            
            // Create special renderer for commits/day with 2 decimal places
            val commitsPerDayRenderer = DefaultTableCellRenderer().apply {
                horizontalAlignment = SwingConstants.CENTER
            }
            columnModel.getColumn(10).cellRenderer = commitsPerDayRenderer // Commits/Day
            
            // Add date renderer for date columns to ensure consistent display
            val dateRenderer = DefaultTableCellRenderer()
            dateRenderer.horizontalAlignment = SwingConstants.CENTER
            columnModel.getColumn(7).cellRenderer = dateRenderer // First Commit
            columnModel.getColumn(8).cellRenderer = dateRenderer // Last Commit
            
            // Add row sorter for sorting with appropriate comparators
            val sorter = TableRowSorter(tableModel)
            
            // Make sure numeric columns are sorted as numbers
            sorter.setComparator(1, Comparator.comparingInt<Any> { (it as Number).toInt() }) // Commits
            sorter.setComparator(2, Comparator.comparingInt<Any> { (it as Number).toInt() }) // Tickets Count
            sorter.setComparator(3, Comparator.comparingInt<Any> { (it as Number).toInt() }) // Blockers Count
            sorter.setComparator(4, Comparator.comparingInt<Any> { (it as Number).toInt() }) // Regressions Count
            sorter.setComparator(5, Comparator.comparingInt<Any> { (it as Number).toInt() }) // Test Commits Count
            // Test coverage % - use numeric value for sorting
            sorter.setComparator(6, Comparator.comparingDouble<Any> {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            })
            sorter.setComparator(9, Comparator.comparingLong<Any> { (it as Number).toLong() }) // Active Days
            // Commits/Day - still use the numeric value for sorting (the formatted value is still a Double)
            sorter.setComparator(10, Comparator.comparingDouble<Any> {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            })
            
            // Sort by commit count (descending) by default
            sorter.toggleSortOrder(1)
            sorter.toggleSortOrder(1) // Toggle twice to get descending order
            
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
                            // Create case-insensitive regex filter for author column (0)
                            sorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 0)
                        } catch (ex: java.util.regex.PatternSyntaxException) {
                            // If the regex pattern is invalid, just show all rows
                            sorter.rowFilter = null
                        }
                    }
                }
            })
        }
        
        // Create tabbed panel for details
        val authorDetailsTabbedPane = JBTabbedPane()
        
        // Create author details panel
        val authorCommitsPanel = JPanel(BorderLayout())
        authorCommitsPanel.border = JBUI.Borders.empty(5)
        
        // Panel for label and search field
        val authorCommitsHeaderPanel = JPanel(BorderLayout())
        val authorCommitsLabel = JBLabel(CommitTracerBundle.message("dialog.author.commits", "", "0"))
        authorCommitsHeaderPanel.add(authorCommitsLabel, BorderLayout.WEST)
        
        // Add search field for filtering author's commits
        val authorCommitsSearchPanel = JPanel(BorderLayout())
        val authorCommitsSearchLabel = JBLabel(CommitTracerBundle.message("dialog.filter.search"))
        authorCommitsSearchLabel.border = JBUI.Borders.empty(0, 5)
        authorCommitsSearchPanel.add(authorCommitsSearchLabel, BorderLayout.WEST)
        
        val authorCommitsSearchField = JTextField().apply {
            preferredSize = Dimension(150, preferredSize.height)
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                        text = ""
                    }
                }
            })
        }
        authorCommitsSearchPanel.add(authorCommitsSearchField, BorderLayout.CENTER)
        authorCommitsHeaderPanel.add(authorCommitsSearchPanel, BorderLayout.EAST)
        
        authorCommitsPanel.add(authorCommitsHeaderPanel, BorderLayout.NORTH)
        
        // Create author commits table
        authorCommitsTable = JBTable()
        val authorCommitsScrollPane = JBScrollPane(authorCommitsTable)
        
        // Create changed files panel
        val changedFilesPanel = JPanel(BorderLayout())
        changedFilesPanel.border = JBUI.Borders.empty(5, 0, 0, 0)
        
        val changedFilesLabel = JBLabel(CommitTracerBundle.message("dialog.changed.files"))
        changedFilesLabel.border = JBUI.Borders.empty(0, 0, 5, 0)
        changedFilesPanel.add(changedFilesLabel, BorderLayout.NORTH)
        
        // Create a list model and JList for the changed files
        val changedFilesListModel = DefaultListModel<ChangedFileInfo>()
        val changedFilesList = JBList<ChangedFileInfo>(changedFilesListModel).apply {
            setCellRenderer(object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ChangedFileInfo && component is JLabel) {
                        // Add appropriate icons and formatting based on change type
                        when (value.changeType) {
                            ChangeType.ADDED -> {
                                component.text = "[+] ${value.path}"
                                component.icon = AllIcons.Actions.AddFile
                            }
                            ChangeType.DELETED -> {
                                component.text = "[-] ${value.path}"
                                component.icon = AllIcons.Actions.DeleteTag
                            }
                            ChangeType.MODIFIED -> {
                                component.text = "[M] ${value.path}"
                                component.icon = AllIcons.Actions.Edit
                            }
                        }
                        
                        // Set text color to green for test files
                        if (value.isTestFile) {
                            component.foreground = JBColor.GREEN.darker()
                            // Override the change type icon with the test icon for test files
                            component.icon = AllIcons.Nodes.JunitTestMark
                        }
                    }
                    return component
                }
            })
        }
        
        changedFilesPanel.add(JBScrollPane(changedFilesList), BorderLayout.CENTER)
        
        // Split the author commits panel vertically
        val splitPane = OnePixelSplitter(true, 0.6f)
        splitPane.firstComponent = authorCommitsScrollPane
        splitPane.secondComponent = changedFilesPanel
        authorCommitsPanel.add(splitPane, BorderLayout.CENTER)
        
        // Create YouTrack tickets panel
        val ticketsPanel = JPanel(BorderLayout())
        ticketsPanel.border = JBUI.Borders.empty(5)
        
        // Panel for label and search field
        val ticketsHeaderPanel = JPanel(BorderLayout())
        val ticketsLabel = JBLabel(CommitTracerBundle.message("dialog.youtrack.tickets", "0"))
        ticketsHeaderPanel.add(ticketsLabel, BorderLayout.WEST)
        
        // Add search field for filtering tickets
        val ticketsSearchPanel = JPanel(BorderLayout())
        val ticketsSearchLabel = JBLabel(CommitTracerBundle.message("dialog.filter.search"))
        ticketsSearchLabel.border = JBUI.Borders.empty(0, 5)
        ticketsSearchPanel.add(ticketsSearchLabel, BorderLayout.WEST)
        
        val ticketsSearchField = JTextField().apply {
            preferredSize = Dimension(150, preferredSize.height)
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                        text = ""
                    }
                }
            })
        }
        ticketsSearchPanel.add(ticketsSearchField, BorderLayout.CENTER)
        ticketsHeaderPanel.add(ticketsSearchPanel, BorderLayout.EAST)
        
        ticketsPanel.add(ticketsHeaderPanel, BorderLayout.NORTH)
        
        ticketsTable = JBTable()
        val ticketsScrollPane = JBScrollPane(ticketsTable)
        ticketsPanel.add(ticketsScrollPane, BorderLayout.CENTER)
        
        // Add tabs to the tabbed pane
        authorDetailsTabbedPane.addTab(CommitTracerBundle.message("dialog.tab.all.commits"), authorCommitsPanel)
        authorDetailsTabbedPane.addTab("YouTrack Tickets", ticketsPanel)
        
        // Add a change listener to clear the changed files panel when switching tabs
        authorDetailsTabbedPane.addChangeListener { _: ChangeEvent -> 
            // Clear the changed files panel when changing tabs
            changedFilesListModel.clear()
            changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
        }
        
        // Add selection listener to show author details
        authorsTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = authorsTable.selectedRow
                if (selectedRow >= 0) {
                    val modelRow = authorsTable.convertRowIndexToModel(selectedRow)
                    val author = authorStats.values.toList()[modelRow]
                    
                    // Filter commits for this author
                    val authorCommits = filteredCommits.filter { it.author == author.author }
                    
                    // Update author commits table
                    val authorCommitsModel = CommitTableModel(authorCommits)
                    authorCommitsTable.model = authorCommitsModel
                    
                    // Clear the changed files panel when author selection changes
                    changedFilesListModel.clear()
                    changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                    
                    // Select the first commit by default if available
                    if (authorCommits.isNotEmpty()) {
                        SwingUtilities.invokeLater {
                            if (authorCommitsTable.rowCount > 0) {
                                try {
                                    authorCommitsTable.setRowSelectionInterval(0, 0)
                                    authorCommitsTable.scrollRectToVisible(authorCommitsTable.getCellRect(0, 0, true))
                                    
                                    // Manually update the changed files list for the first commit
                                    val selectedCommit = authorCommits[0]
                                    changedFilesListModel.clear()
                                    if (selectedCommit.changedFiles.isNotEmpty()) {
                                        // Sort files by path, with test files first
                                        val sortedFiles = selectedCommit.changedFiles.sortedWith(
                                            compareByDescending<ChangedFileInfo> { it.isTestFile }
                                            .thenBy { it.path.lowercase() }
                                        )
                                        sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                        changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${selectedCommit.changedFiles.size})"
                                    } else {
                                        changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                    }
                                } catch (e: Exception) {
                                    // Log any error but continue
                                    println("Error selecting first commit: ${e.message}")
                                }
                            }
                        }
                    }
                    
                    // Configure columns
                    authorCommitsTable.columnModel.getColumn(0).preferredWidth = 450 // Message
                    authorCommitsTable.columnModel.getColumn(1).preferredWidth = 120 // Date
                    authorCommitsTable.columnModel.getColumn(2).preferredWidth = 80  // Hash
                    authorCommitsTable.columnModel.getColumn(3).preferredWidth = 50  // Tests
                    
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
                    authorCommitsTable.columnModel.getColumn(3).cellRenderer = testsRenderer
                    
                    // Add row sorter for author commits table
                    val sorter = TableRowSorter(authorCommitsModel)
                    authorCommitsTable.rowSorter = sorter
                    
                    // Remove previous selection listener if exists
                    authorCommitsSelectionListener?.let {
                        authorCommitsTable.selectionModel.removeListSelectionListener(it)
                    }
                    
                    // Create and add new selection listener to update changed files panel
                    authorCommitsSelectionListener = ListSelectionListener { e ->
                        if (!e.valueIsAdjusting) {
                            val selectedRow = authorCommitsTable.selectedRow
                            if (selectedRow >= 0) {
                                val modelRow = authorCommitsTable.convertRowIndexToModel(selectedRow)
                                val commit = authorCommits[modelRow]
                                
                                // Update the changed files list
                                changedFilesListModel.clear()
                                if (commit.changedFiles.isNotEmpty()) {
                                    // Sort files by path, with test files first
                                    val sortedFiles = commit.changedFiles.sortedWith(
                                        compareByDescending<ChangedFileInfo> { it.isTestFile }
                                        .thenBy { it.path.lowercase() }
                                    )
                                    sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                    changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${commit.changedFiles.size})"
                                } else {
                                    changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                }
                            }
                        }
                    }.also {
                        authorCommitsTable.selectionModel.addListSelectionListener(it)
                    }
                    
                    // Set up filtering on the search field
                    authorCommitsSearchField.document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        
                        private fun filterTable() {
                            val text = authorCommitsSearchField.text
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
                    
                    authorCommitsLabel.text = CommitTracerBundle.message("dialog.author.commits", author.author, authorCommits.size.toString())
                    
                    // Update tickets table
                    val tickets = author.youTrackTickets.entries
                        .map { (ticket, commits) -> 
                            // Check if this ticket is a blocker or regression
                            val isBlocker = author.blockerTickets.containsKey(ticket)
                            val isRegression = author.regressionTickets.containsKey(ticket)
                            TicketInfo(ticket, commits, isBlocker, isRegression)
                        }
                        .toList()
                    
                    val ticketsModel = TicketsTableModel(tickets)
                    ticketsTable.model = ticketsModel
                    
                    // Configure ticket table columns
                    ticketsTable.columnModel.getColumn(0).preferredWidth = 120  // Ticket ID
                    ticketsTable.columnModel.getColumn(1).preferredWidth = 80   // Commit Count
                    ticketsTable.columnModel.getColumn(2).preferredWidth = 80   // Blocker
                    ticketsTable.columnModel.getColumn(3).preferredWidth = 80   // Regression
                    
                    // Center-align numeric columns
                    val centerRenderer = DefaultTableCellRenderer()
                    centerRenderer.horizontalAlignment = SwingConstants.CENTER
                    ticketsTable.columnModel.getColumn(1).cellRenderer = centerRenderer // Commit Count
                    
                    // Boolean renderer for blocker and regression columns
                    ticketsTable.columnModel.getColumn(2).cellRenderer = DefaultTableCellRenderer().apply {
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    ticketsTable.columnModel.getColumn(3).cellRenderer = DefaultTableCellRenderer().apply {
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    
                    // Add row sorter for tickets table
                    val ticketsSorter = TableRowSorter(ticketsModel)
                    ticketsSorter.setComparator(1, Comparator.comparingInt<Any> { (it as Number).toInt() })
                    ticketsSorter.setComparator(2, Comparator.comparing<Any, Boolean> { it as Boolean })
                    ticketsSorter.setComparator(3, Comparator.comparing<Any, Boolean> { it as Boolean })
                    
                    // Sort by commit count (descending) by default
                    ticketsSorter.toggleSortOrder(1)
                    ticketsSorter.toggleSortOrder(1) // Toggle twice to get descending order
                    
                    ticketsTable.rowSorter = ticketsSorter
                    
                    // Set up filtering on the search field
                    ticketsSearchField.document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        
                        private fun filterTable() {
                            val text = ticketsSearchField.text
                            if (text.isNullOrBlank()) {
                                ticketsSorter.rowFilter = null
                            } else {
                                try {
                                    // Create case-insensitive regex filter for ticket ID column (0)
                                    ticketsSorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 0)
                                } catch (ex: java.util.regex.PatternSyntaxException) {
                                    // If the regex pattern is invalid, just show all rows
                                    ticketsSorter.rowFilter = null
                                }
                            }
                        }
                    })
                    
                    // Add selection listener to tickets table
                    ticketsTable.selectionModel.addListSelectionListener { ticketEvent ->
                        if (!ticketEvent.valueIsAdjusting) {
                            val selectedTicketRow = ticketsTable.selectedRow
                            if (selectedTicketRow >= 0) {
                                val ticketModelRow = ticketsTable.convertRowIndexToModel(selectedTicketRow)
                                val ticketInfo = tickets[ticketModelRow]
                                
                                // Update author commits table to show only commits related to this ticket
                                val ticketCommitsModel = CommitTableModel(ticketInfo.commits)
                                authorCommitsTable.model = ticketCommitsModel
                                
                                // Clear the changed files panel when ticket selection changes
                                changedFilesListModel.clear()
                                changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                
                                // Select the first commit by default if available
                                if (ticketInfo.commits.isNotEmpty()) {
                                    SwingUtilities.invokeLater {
                                        if (authorCommitsTable.rowCount > 0) {
                                            try {
                                                authorCommitsTable.setRowSelectionInterval(0, 0)
                                                authorCommitsTable.scrollRectToVisible(authorCommitsTable.getCellRect(0, 0, true))
                                                
                                                // Manually update the changed files list for the first commit
                                                val selectedCommit = ticketInfo.commits[0]
                                                changedFilesListModel.clear()
                                                if (selectedCommit.changedFiles.isNotEmpty()) {
                                                    // Sort files by path, with test files first
                                                    val sortedFiles = selectedCommit.changedFiles.sortedWith(
                                                        compareByDescending<ChangedFileInfo> { it.isTestFile }
                                                        .thenBy { it.path.lowercase() }
                                                    )
                                                    sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                                    changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${selectedCommit.changedFiles.size})"
                                                } else {
                                                    changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                                }
                                            } catch (e: Exception) {
                                                // Log any error but continue
                                                println("Error selecting first ticket commit: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                
                                // Configure columns
                                authorCommitsTable.columnModel.getColumn(3).preferredWidth = 50  // Tests
                                
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
                                authorCommitsTable.columnModel.getColumn(3).cellRenderer = testsRenderer
                                
                                val ticketCommitsSorter = TableRowSorter(ticketCommitsModel)
                                authorCommitsTable.rowSorter = ticketCommitsSorter
                                
                                // Reset and clear changed files panel
                                changedFilesListModel.clear()
                                changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                
                                // Remove previous selection listener if exists
                                ticketCommitsSelectionListener?.let {
                                    authorCommitsTable.selectionModel.removeListSelectionListener(it)
                                }
                                
                                // Create and add selection listener for the ticket-specific commits
                                ticketCommitsSelectionListener = ListSelectionListener { e ->
                                    if (!e.valueIsAdjusting) {
                                        val selectedRow = authorCommitsTable.selectedRow
                                        if (selectedRow >= 0) {
                                            val modelRow = authorCommitsTable.convertRowIndexToModel(selectedRow)
                                            val commit = ticketInfo.commits[modelRow]
                                            
                                            // Update the changed files list
                                            changedFilesListModel.clear()
                                            if (commit.changedFiles.isNotEmpty()) {
                                                // Sort files by path, with test files first
                                                val sortedFiles = commit.changedFiles.sortedWith(
                                                    compareByDescending<ChangedFileInfo> { it.isTestFile }
                                                    .thenBy { it.path.lowercase() }
                                                )
                                                sortedFiles.forEach { changedFilesListModel.addElement(it) }
                                                changedFilesLabel.text = "${CommitTracerBundle.message("dialog.changed.files")} (${commit.changedFiles.size})"
                                            } else {
                                                changedFilesLabel.text = CommitTracerBundle.message("dialog.changed.files")
                                            }
                                        }
                                    }
                                }.also {
                                    authorCommitsTable.selectionModel.addListSelectionListener(it)
                                }
                                
                                // Set up filtering on the author commits search field for ticket commits
                                authorCommitsSearchField.document.addDocumentListener(object : DocumentListener {
                                    override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                                    override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                                    override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                                    
                                    private fun filterTable() {
                                        val text = authorCommitsSearchField.text
                                        if (text.isNullOrBlank()) {
                                            ticketCommitsSorter.rowFilter = null
                                        } else {
                                            try {
                                                // Create case-insensitive regex filter for message column (0)
                                                ticketCommitsSorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 0)
                                            } catch (ex: java.util.regex.PatternSyntaxException) {
                                                // If the regex pattern is invalid, just show all rows
                                                ticketCommitsSorter.rowFilter = null
                                            }
                                        }
                                    }
                                })

                                // Update label
                                authorCommitsLabel.text = CommitTracerBundle.message("dialog.ticket.commits", ticketInfo.ticketId, ticketInfo.commits.size.toString())
                                
                                // Switch to the commits tab
                                authorDetailsTabbedPane.selectedIndex = 0
                            }
                        }
                    }
                    
                    ticketsLabel.text = CommitTracerBundle.message("dialog.youtrack.tickets", tickets.size.toString())
                    ticketsLabel.text = CommitTracerBundle.message("dialog.tickets.for.author", author.author)
                }
            }
        }
        
        // Split view: table on top, details below
        val splitPane2 = OnePixelSplitter(true, 0.5f)
        splitPane2.firstComponent = JBScrollPane(authorsTable)
        splitPane2.secondComponent = authorDetailsTabbedPane
        
        add(splitPane2, BorderLayout.CENTER)
        
        // Select first row by default if there are authors
        if (authorStats.isNotEmpty()) {
            authorsTable.selectionModel.setSelectionInterval(0, 0)
        }
    }
    
    /**
     * Update the table with new data
     */
    fun updateData(newAuthors: List<AuthorStats>) {
        (authorsTable.model as AuthorTableModel).updateData(newAuthors)
    }
}