package com.example.ijcommittracer.ui

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.AuthorStats
import com.example.ijcommittracer.actions.ListCommitsAction.ChangedFileInfo
import com.example.ijcommittracer.actions.ListCommitsAction.ChangeType
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import com.intellij.icons.AllIcons
import com.example.ijcommittracer.services.NotificationService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Font
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * Dialog for displaying a list of Git commits with date filtering and author aggregation.
 */
class CommitListDialog(
    private val project: Project,
    private val commits: List<CommitInfo>,
    private val authorStats: Map<String, AuthorStats>,
    private var fromDate: Date,
    private var toDate: Date,
    private val repository: GitRepository
) : DialogWrapper(project) {

    private lateinit var commitsTable: JBTable
    private lateinit var authorsTable: JBTable
    private lateinit var fromDatePicker: JDateChooser
    private lateinit var toDatePicker: JDateChooser
    private lateinit var filterButton: JButton
    private var filteredCommits: List<CommitInfo> = commits
    
    // Use a fixed format with Locale.US for Git command date parameters
    private val gitDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    // Other date formatters with consistent Locale.US
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateTimeFormat = SimpleDateFormat("dd/MM/yy, HH:mm", Locale.US)
    
    // Pattern for YouTrack ticket references
    // Matches project code in capital letters, followed by a hyphen, followed by numbers (e.g. IDEA-12345)
    private val youtrackTicketPattern = Pattern.compile("([A-Z]+-\\d+)")

    init {
        title = CommitTracerBundle.message("dialog.commits.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = BorderLayoutPanel()
        panel.preferredSize = Dimension(1000, 600)
        
        // Add repository name label
        val repoName = if (commits.isNotEmpty()) commits.first().repositoryName else repository.root.name
        val repoLabel = JBLabel(CommitTracerBundle.message("dialog.repository.label", repoName))
        repoLabel.border = JBUI.Borders.empty(0, 5, 5, 0)
        
        // Create date filter panel
        val filterPanel = createDateFilterPanel()
        
        // Header panel with repository info and filter controls
        val headerPanel = JPanel(BorderLayout())
        headerPanel.add(repoLabel, BorderLayout.WEST)
        headerPanel.add(filterPanel, BorderLayout.EAST)
        panel.add(headerPanel, BorderLayout.NORTH)

        // Create tabbed pane for different views
        val tabbedPane = JBTabbedPane()
        
        // Add authors tab first
        val authorsPanel = createAuthorsPanel()
        tabbedPane.addTab(CommitTracerBundle.message("dialog.tab.by.author"), authorsPanel)
        
        // Add commits tab second
        val commitsPanel = createCommitsPanel()
        tabbedPane.addTab(CommitTracerBundle.message("dialog.tab.all.commits"), commitsPanel)
        
        panel.add(tabbedPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createDateFilterPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT))
        
        panel.add(JBLabel(CommitTracerBundle.message("dialog.filter.from")))
        fromDatePicker = JDateChooser(fromDate, displayDateFormat)
        fromDatePicker.preferredSize = Dimension(120, 30)
        panel.add(fromDatePicker)
        
        panel.add(JBLabel(CommitTracerBundle.message("dialog.filter.to")))
        toDatePicker = JDateChooser(toDate, displayDateFormat)
        toDatePicker.preferredSize = Dimension(120, 30)
        panel.add(toDatePicker)
        
        filterButton = JButton(CommitTracerBundle.message("dialog.filter.apply"))
        filterButton.addActionListener { applyDateFilter() }
        panel.add(filterButton)
        
        return panel
    }
    
    private fun applyDateFilter() {
        val newFromDate = fromDatePicker.date
        val newToDate = toDatePicker.date
        
        if (newFromDate != null && newToDate != null) {
            if (newFromDate.after(newToDate)) {
                NotificationService.showWarning(
                    project, 
                    CommitTracerBundle.message("notification.invalid.dates"),
                    "Commit Tracer"
                )
                return
            }
            
            // Update date range and refresh commits
            fromDate = newFromDate
            toDate = newToDate
            
            refreshCommitsWithDateFilter()
        } else {
            NotificationService.showWarning(
                project, 
                CommitTracerBundle.message("notification.invalid.dates"),
                "Commit Tracer"
            )
        }
    }
    
    private fun refreshCommitsWithDateFilter() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, 
            CommitTracerBundle.message("task.filtering.commits"), 
            true
        ) {
            private var newCommits: List<CommitInfo> = emptyList()
            private var newAuthorStats: Map<String, AuthorStats> = emptyMap()
            
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.isIndeterminate = true
                
                // Format dates for Git command
                val afterParam = "--after=${gitDateFormat.format(fromDate)}"
                val beforeParam = "--before=${gitDateFormat.format(toDate)} 23:59:59"
                
                // Get commit history with date range filtering
                val gitCommits = GitHistoryUtils.history(
                    project,
                    repository.root,
                    afterParam,
                    beforeParam,
                )
                
                val currentBranch = repository.currentBranch?.name ?: "HEAD"
                
                // Convert to CommitInfo objects
                newCommits = gitCommits.map { gitCommit ->
                    val commitDate = Date(gitCommit.authorTime)
                    CommitInfo(
                        hash = gitCommit.id.toString(),
                        author = gitCommit.author.email,
                        date = displayDateTimeFormat.format(commitDate),
                        dateObj = commitDate,
                        message = gitCommit.fullMessage.trim(),
                        repositoryName = repository.root.name,
                        branches = listOfNotNull(currentBranch).takeIf { true } ?: emptyList()
                    )
                }
                
                // Aggregate by author
                val authorMap = mutableMapOf<String, AuthorStats>()
                
                newCommits.forEach { commit ->
                    val author = commit.author
                    
                    // Extract YouTrack tickets from the commit message
                    val tickets = extractYouTrackTickets(commit.message)
                    
                    val stats = authorMap.getOrPut(author) { 
                        AuthorStats(
                            author = author,
                            commitCount = 0,
                            firstCommitDate = commit.dateObj,
                            lastCommitDate = commit.dateObj,
                            youTrackTickets = mutableMapOf()
                        )
                    }
                    
                    // Update YouTrack tickets map
                    val updatedTickets = stats.youTrackTickets.toMutableMap()
                    tickets.forEach { ticket ->
                        val ticketCommits = updatedTickets.getOrPut(ticket) { mutableListOf() }
                        ticketCommits.add(commit)
                    }
                    
                    val updatedStats = stats.copy(
                        commitCount = stats.commitCount + 1,
                        firstCommitDate = if (commit.dateObj.before(stats.firstCommitDate)) commit.dateObj else stats.firstCommitDate,
                        lastCommitDate = if (commit.dateObj.after(stats.lastCommitDate)) commit.dateObj else stats.lastCommitDate,
                        youTrackTickets = updatedTickets
                    )
                    
                    authorMap[author] = updatedStats
                }
                
                newAuthorStats = authorMap
            }
            
            override fun onSuccess() {
                filteredCommits = newCommits
                
                // Update tables
                (commitsTable.model as CommitTableModel).updateData(filteredCommits)
                (authorsTable.model as AuthorTableModel).updateData(newAuthorStats.values.toList())
                
                NotificationService.showInfo(
                    project, 
                    CommitTracerBundle.message("notification.filter.applied", filteredCommits.size),
                    "Commit Tracer"
                )
            }
        })
    }
    
    /**
     * Extract YouTrack ticket IDs from commit message
     */
    private fun extractYouTrackTickets(message: String): Set<String> {
        val matcher = youtrackTicketPattern.matcher(message)
        val tickets = mutableSetOf<String>()
        
        while (matcher.find()) {
            tickets.add(matcher.group(1))
        }
        
        return tickets
    }
    
    private fun createCommitsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
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
        panel.add(filterPanel, BorderLayout.NORTH)
        
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
            searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
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
        val splitPane = com.intellij.ui.OnePixelSplitter(true, 0.6f)
        splitPane.firstComponent = JBScrollPane(commitsTable)
        splitPane.secondComponent = detailsPanel
        
        panel.add(splitPane, BorderLayout.CENTER)
        
        // Select first row by default if there are commits
        if (filteredCommits.isNotEmpty()) {
            commitsTable.selectionModel.setSelectionInterval(0, 0)
        }
        
        return panel
    }
    
    /**
     * Highlight YouTrack tickets in commit message
     */
    private fun highlightYouTrackTickets(message: String): String {
        val matcher = youtrackTicketPattern.matcher(message)
        return matcher.replaceAll("**$1**") // Bold the ticket references
    }
    
    private fun createAuthorsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
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
        panel.add(filterPanel, BorderLayout.NORTH)
        
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
            searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
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
        val authorCommitsTable = JBTable()
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
        val splitPane = com.intellij.ui.OnePixelSplitter(true, 0.6f)
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
        
        val ticketsTable = JBTable()
        val ticketsScrollPane = JBScrollPane(ticketsTable)
        ticketsPanel.add(ticketsScrollPane, BorderLayout.CENTER)
        
        // Add tabs to the tabbed pane
        authorDetailsTabbedPane.addTab(CommitTracerBundle.message("dialog.tab.all.commits"), authorCommitsPanel)
        authorDetailsTabbedPane.addTab("YouTrack Tickets", ticketsPanel)
        
        // Add a change listener to clear the changed files panel when switching tabs
        authorDetailsTabbedPane.addChangeListener { 
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
                    
                    // Add selection listener to update changed files panel
                    authorCommitsTable.selectionModel.addListSelectionListener { e ->
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
                    }
                    
                    // Set up filtering on the search field
                    authorCommitsSearchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterTable()
                        
                        private fun filterTable() {
                            val text = authorCommitsSearchField.text
                            if (text.isNullOrBlank()) {
                                sorter.rowFilter = null
                            } else {
                                try {
                                    // Create case-insensitive regex filter for message column (3)
                                    sorter.rowFilter = RowFilter.regexFilter("(?i)" + text, 3)
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
                    ticketsSearchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
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
                                
                                // Add selection listener for the ticket-specific commits
                                authorCommitsTable.selectionModel.addListSelectionListener { e ->
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
                                }
                                
                                // Set up filtering on the author commits search field for ticket commits
                                authorCommitsSearchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
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
        val splitPane2 = com.intellij.ui.OnePixelSplitter(true, 0.5f)
        splitPane2.firstComponent = JBScrollPane(authorsTable)
        splitPane2.secondComponent = authorDetailsTabbedPane
        
        panel.add(splitPane2, BorderLayout.CENTER)
        
        // Select first row by default if there are authors
        if (authorStats.isNotEmpty()) {
            authorsTable.selectionModel.setSelectionInterval(0, 0)
        }
        
        return panel
    }
    
    /**
     * Data class for YouTrack ticket information
     */
    private data class TicketInfo(
        val ticketId: String,
        val commits: List<CommitInfo>,
        val isBlocker: Boolean = false,
        val isRegression: Boolean = false
    )
    
    /**
     * Table model for displaying Git commits.
     */
    private class CommitTableModel(private var commits: List<CommitInfo>) : AbstractTableModel() {
        private val columns = arrayOf(
            CommitTracerBundle.message("dialog.column.message"),
            CommitTracerBundle.message("dialog.column.date"),
            CommitTracerBundle.message("dialog.column.hash"),
            CommitTracerBundle.message("dialog.column.tests")
        )

        fun updateData(newCommits: List<CommitInfo>) {
            commits = newCommits
            fireTableDataChanged()
        }
        
        override fun getRowCount(): Int = commits.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]
        
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                3 -> Boolean::class.java // Tests column is boolean
                else -> String::class.java
            }
        }

        private val dateTimeFormat = SimpleDateFormat("dd/MM/yy, HH:mm", Locale.US)
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val commit = commits[rowIndex]
            return when (columnIndex) {
                0 -> {
                    // Get first line of commit message or truncate if necessary
                    val message = commit.message.lines().firstOrNull() ?: ""
                    if (message.length > 100) message.substring(0, 97) + "..." else message
                }
                1 -> {
                    // Format the date according to the new format
                    try {
                        // Try to parse the original date using the format stored in commits
                        val originalFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        val date = originalFormat.parse(commit.date)
                        dateTimeFormat.format(date)
                    } catch (e: Exception) {
                        // If parsing fails, return the original date string
                        commit.date
                    }
                }
                2 -> {
                    val shortHash = commit.hash.substring(0, 7)
                    if (commit.branches.isNotEmpty()) {
                        "$shortHash *" // Add an indicator for commits with branches
                    } else {
                        shortHash
                    }
                }
                3 -> commit.testsTouched // Whether tests were touched
                else -> ""
            }
        }
    }
    
    /**
     * Table model for displaying author statistics.
     */
    private class AuthorTableModel(private var authors: List<AuthorStats>) : AbstractTableModel() {
        private val columns = arrayOf(
            CommitTracerBundle.message("dialog.column.author"),
            CommitTracerBundle.message("dialog.column.author.commits"),
            CommitTracerBundle.message("dialog.column.author.tickets"),
            "Blockers",
            "Regressions",
            "Test Commits",
            "Test %",
            CommitTracerBundle.message("dialog.column.author.first"),
            CommitTracerBundle.message("dialog.column.author.last"),
            CommitTracerBundle.message("dialog.column.author.days"),
            CommitTracerBundle.message("dialog.column.author.avg")
        )
        
        // Use Locale.US for consistent date formatting
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val commitsDayFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
        
        fun updateData(newAuthors: List<AuthorStats>) {
            authors = newAuthors
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = authors.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]
        
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                1, 2, 3, 4, 5 -> Integer::class.java  // Commits, Tickets, Blockers, Regressions, Test Commits Count
                6, 10 -> Double::class.java  // Test % and Commits/Day
                9 -> Long::class.java     // Active Days
                else -> String::class.java
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val author = authors[rowIndex]
            return when (columnIndex) {
                0 -> author.author
                1 -> author.commitCount
                2 -> author.youTrackTickets.size
                3 -> author.getBlockerCount()
                4 -> author.getRegressionCount()
                5 -> author.testTouchedCount
                6 -> {
                    val testPercentage = author.getTestCoveragePercentage()
                    commitsDayFormat.format(testPercentage).toDouble()
                }
                7 -> dateFormat.format(author.firstCommitDate)
                8 -> dateFormat.format(author.lastCommitDate)
                9 -> author.getActiveDays()
                10 -> {
                    val commitsPerDay = author.getCommitsPerDay()
                    // Format for display while maintaining the Double type for sorting
                    commitsDayFormat.format(commitsPerDay).toDouble()
                }
                else -> ""
            }
        }
    }
    
    /**
     * Table model for displaying YouTrack tickets.
     */
    private class TicketsTableModel(private val tickets: List<TicketInfo>) : AbstractTableModel() {
        private val columns = arrayOf(
            CommitTracerBundle.message("dialog.column.ticket.id"),
            CommitTracerBundle.message("dialog.column.ticket.commits"),
            "Blocker",
            "Regression"
        )
        
        override fun getRowCount(): Int = tickets.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]
        
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                1 -> Integer::class.java  // Commits count
                2, 3 -> Boolean::class.java  // Blocker and Regression status
                else -> String::class.java
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val ticket = tickets[rowIndex]
            return when (columnIndex) {
                0 -> ticket.ticketId
                1 -> ticket.commits.size
                2 -> ticket.isBlocker
                3 -> ticket.isRegression
                else -> ""
            }
        }
    }
    
    /**
     * Date picker component (simple implementation).
     */
    private class JDateChooser(initialDate: Date, private val dateFormat: SimpleDateFormat) : JPanel() {
        private val textField = JTextField(dateFormat.format(initialDate), 10)
        
        var date: Date?
            get() {
                return try {
                    dateFormat.parse(textField.text)
                } catch (e: Exception) {
                    null
                }
            }
            set(value) {
                if (value != null) {
                    textField.text = dateFormat.format(value)
                }
            }
        
        init {
            layout = BorderLayout()
            add(textField, BorderLayout.CENTER)
        }
    }
}
