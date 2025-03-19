package com.example.ijcommittracer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.AuthorStats
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import com.example.ijcommittracer.services.NotificationService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.VerticalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import java.awt.BorderLayout
import java.awt.Dimension
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.swing.JComponent
import javax.swing.JPanel

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

    private var filteredCommits: List<CommitInfo> by mutableStateOf(commits)
    private var selectedTabIndex by mutableStateOf(0)
    private var selectedCommit by mutableStateOf<CommitInfo?>(null)
    
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
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(1000, 600)
        
        val composePanel = ComposePanel()
        composePanel.setContent {
            // Use custom IntelliJ-style theme
            com.example.ijcommittracer.ui.theme.IntelliJTheme {
                // Use Surface as the root component
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        // Repository name and date filter controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Repository name
                            val repoName = if (commits.isNotEmpty()) commits.first().repositoryName else repository.root.name
                            Text(
                                text = CommitTracerBundle.message("dialog.repository.label", repoName),
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            // Date filter controls
                            DateRangeSelector(
                                fromDate = fromDate,
                                toDate = toDate,
                                onDateRangeChanged = { newFromDate, newToDate ->
                                    applyDateFilter(newFromDate, newToDate)
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Use our custom IntelliJ-style tabs
                        com.example.ijcommittracer.ui.components.IdeaTabs(
                            selectedTabIndex = selectedTabIndex
                        ) {
                            com.example.ijcommittracer.ui.components.IdeaTab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = CommitTracerBundle.message("dialog.tab.by.author")
                            )
                            com.example.ijcommittracer.ui.components.IdeaTab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = CommitTracerBundle.message("dialog.tab.all.commits")
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        when (selectedTabIndex) {
                            0 -> AuthorsTab(
                                authorStats = authorStats.values.toList(),
                                filteredCommits = filteredCommits
                            )
                            1 -> CommitsTab(
                                commits = filteredCommits,
                                onCommitSelected = { commit -> selectedCommit = commit }
                            )
                        }
                    }
                }
            }
        }
        
        panel.add(composePanel, BorderLayout.CENTER)
        return panel
    }
    
    @Composable
    private fun DateRangeSelector(
        fromDate: Date,
        toDate: Date,
        onDateRangeChanged: (Date, Date) -> Unit
    ) {
        var fromDateText by remember { mutableStateOf(displayDateFormat.format(fromDate)) }
        var toDateText by remember { mutableStateOf(displayDateFormat.format(toDate)) }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(CommitTracerBundle.message("dialog.filter.from"))
            
            // Simple text field for date input - direct text editing
            com.example.ijcommittracer.ui.components.IdeaTextField(
                value = fromDateText,
                onValueChange = { fromDateText = it },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
            
            Text(CommitTracerBundle.message("dialog.filter.to"))
            
            com.example.ijcommittracer.ui.components.IdeaTextField(
                value = toDateText,
                onValueChange = { toDateText = it },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
            
            // Apply button applies date filter when clicked
            com.example.ijcommittracer.ui.components.IdeaButton(
                onClick = {
                    try {
                        // Parse the dates from the text fields
                        val parsedFromDate = displayDateFormat.parse(fromDateText)
                        val parsedToDate = displayDateFormat.parse(toDateText)
                        
                        // Apply date filter if both dates are valid
                        if (parsedFromDate != null && parsedToDate != null) {
                            onDateRangeChanged(parsedFromDate, parsedToDate)
                        } else {
                            // Show error if parsing fails
                            NotificationService.showWarning(
                                project,
                                "Invalid date format. Please use format yyyy-MM-dd",
                                "Commit Tracer"
                            )
                        }
                    } catch (e: Exception) {
                        // Show error if parsing fails
                        NotificationService.showWarning(
                            project,
                            "Invalid date format. Please use format yyyy-MM-dd",
                            "Commit Tracer"
                        )
                    }
                }
            ) {
                Text(CommitTracerBundle.message("dialog.filter.apply"))
            }
        }
    }
    
    @OptIn(ExperimentalSplitPaneApi::class)
    @Composable
    private fun AuthorsTab(
        authorStats: List<AuthorStats>,
        filteredCommits: List<CommitInfo>
    ) {
        var searchText by remember { mutableStateOf("") }
        var selectedAuthor by remember { mutableStateOf<AuthorStats?>(null) }
        var authorCommits by remember { mutableStateOf<List<CommitInfo>>(emptyList()) }
        var selectedAuthorCommit by remember { mutableStateOf<CommitInfo?>(null) }
        
        // Update author commits when the filtered commits list changes
        LaunchedEffect(filteredCommits, selectedAuthor) {
            if (selectedAuthor != null) {
                val newAuthorCommits = filteredCommits.filter { it.author == selectedAuthor?.author }
                authorCommits = newAuthorCommits
                
                // Update selected commit when the list changes
                if (selectedAuthorCommit == null || !newAuthorCommits.contains(selectedAuthorCommit)) {
                    selectedAuthorCommit = newAuthorCommits.firstOrNull()
                }
            }
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Search field for authors using our custom IntelliJ-style TextField
            com.example.ijcommittracer.ui.components.IdeaTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(CommitTracerBundle.message("dialog.filter.search")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            
            // Split pane with authors list and author details
            val splitPaneState = rememberSplitPaneState(0.4f)
            VerticalSplitPane(
                splitPaneState = splitPaneState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Authors list (top panel)
                first {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column {
                            // Use our custom TableHeader component
                            com.example.ijcommittracer.ui.components.TableHeader {
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Author",
                                    modifier = Modifier.weight(1f)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Commits",
                                    modifier = Modifier.width(100.dp)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "First Commit",
                                    modifier = Modifier.width(120.dp)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Last Commit",
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                            
                            // Authors list with our custom SelectableList
                            val filteredAuthors = authorStats.filter { 
                                searchText.isEmpty() || it.author.contains(searchText, ignoreCase = true)
                            }
                            
                            // Initialize the selected author if not already set
                            LaunchedEffect(filteredAuthors) {
                                if (selectedAuthor == null && filteredAuthors.isNotEmpty()) {
                                    selectedAuthor = filteredAuthors.first()
                                    // Initially select all commits by this author
                                    authorCommits = filteredCommits.filter { it.author == filteredAuthors.first().author }
                                    selectedAuthorCommit = authorCommits.firstOrNull()
                                }
                            }
                            
                            // Using our custom IdeaSelectableList for IntelliJ styling with keyboard navigation
                            com.example.ijcommittracer.ui.components.IdeaSelectableList(
                                items = filteredAuthors,
                                selectedItem = selectedAuthor,
                                onItemSelected = { newSelectedAuthor ->
                                    selectedAuthor = newSelectedAuthor
                                    // Update commits for the selected author
                                    val newAuthorCommits = filteredCommits.filter { it.author == newSelectedAuthor.author }
                                    authorCommits = newAuthorCommits
                                    // Update selected commit
                                    selectedAuthorCommit = newAuthorCommits.firstOrNull()
                                },
                                itemContent = { item, selected ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            item.author,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            item.commitCount.toString(),
                                            modifier = Modifier.width(100.dp)
                                        )
                                        Text(
                                            displayDateFormat.format(item.firstCommitDate),
                                            modifier = Modifier.width(120.dp)
                                        )
                                        Text(
                                            displayDateFormat.format(item.lastCommitDate),
                                            modifier = Modifier.width(120.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Author details (bottom panel) - show commits by author with details
                second {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (selectedAuthor != null) {
                            // Use a specialized CommitsListWithSelection to ensure proper commit selection
                            CommitsListWithSelection(
                                commits = authorCommits,
                                selectedCommit = selectedAuthorCommit,
                                onCommitSelected = { selectedAuthorCommit = it },
                                title = "Commits by ${selectedAuthor?.author} (${authorCommits.size})"
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Select an author to view their commits")
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun CommitsTab(
        commits: List<CommitInfo>,
        onCommitSelected: (CommitInfo) -> Unit
    ) {
        var selectedTabCommit by remember { mutableStateOf<CommitInfo?>(commits.firstOrNull()) }
        
        // Initialize the selected commit if needed
        LaunchedEffect(commits) {
            if (selectedTabCommit == null && commits.isNotEmpty()) {
                selectedTabCommit = commits.first()
                onCommitSelected(commits.first())
            } else if (commits.isNotEmpty() && (selectedTabCommit == null || !commits.contains(selectedTabCommit))) {
                // If the currently selected commit is no longer in the list
                selectedTabCommit = commits.first()
                onCommitSelected(commits.first())
            }
        }
        
        CommitsListWithSelection(
            commits = commits,
            selectedCommit = selectedTabCommit,
            onCommitSelected = { 
                selectedTabCommit = it
                onCommitSelected(it)
            },
            title = "All Commits (${commits.size})"
        )
    }
    
    /**
     * A version of CommitsListWithDetails that takes a selectedCommit and updates it
     */
    @OptIn(ExperimentalSplitPaneApi::class)
    @Composable
    private fun CommitsListWithSelection(
        commits: List<CommitInfo>,
        selectedCommit: CommitInfo?,
        onCommitSelected: (CommitInfo) -> Unit,
        title: String
    ) {
        var searchText by remember { mutableStateOf("") }
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Optional title
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Search field using our custom IntelliJ-style TextField
            com.example.ijcommittracer.ui.components.IdeaTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(CommitTracerBundle.message("dialog.filter.search")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            
            // Split pane - commits list and details
            val splitPaneState = rememberSplitPaneState(0.6f)
            HorizontalSplitPane(
                splitPaneState = splitPaneState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Commits list - left side
                first {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val filteredCommitsList = commits.filter { 
                            searchText.isEmpty() || it.message.contains(searchText, ignoreCase = true) 
                        }
                        
                        // Ensure we select a commit from the filtered list if needed
                        LaunchedEffect(filteredCommitsList, selectedCommit) {
                            if ((selectedCommit == null || !filteredCommitsList.contains(selectedCommit)) && filteredCommitsList.isNotEmpty()) {
                                onCommitSelected(filteredCommitsList.first())
                            }
                        }
                        
                        Column {
                            // Use our custom TableHeader component
                            com.example.ijcommittracer.ui.components.TableHeader {
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Message",
                                    modifier = Modifier.weight(1f)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Date",
                                    modifier = Modifier.width(120.dp)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Hash",
                                    modifier = Modifier.width(80.dp)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Tests",
                                    modifier = Modifier.width(50.dp)
                                )
                            }
                            
                            // Using our custom IdeaSelectableList for better IntelliJ styling
                            com.example.ijcommittracer.ui.components.IdeaSelectableList(
                                items = filteredCommitsList,
                                selectedItem = selectedCommit,
                                onItemSelected = { newSelectedCommit ->
                                    onCommitSelected(newSelectedCommit)
                                },
                                itemContent = { commit, selected ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            commit.message.lines().first(),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            commit.date,
                                            modifier = Modifier.width(120.dp)
                                        )
                                        Text(
                                            commit.hash.take(7),
                                            modifier = Modifier.width(80.dp)
                                        )
                                        
                                        // Test indicator
                                        // Check if any file paths contain test-related keywords
                                        val hasTests = commit.changedFiles.any { file -> 
                                            file.path.contains("test", ignoreCase = true) || 
                                            file.path.contains("spec", ignoreCase = true)
                                        }
                                        val testIconColor = if (hasTests) 
                                            com.example.ijcommittracer.ui.theme.IntelliJColors.Green 
                                        else 
                                            com.example.ijcommittracer.ui.theme.IntelliJColors.Red
                                        
                                        Text(
                                            text = if (hasTests) "✓" else "✗",
                                            color = testIconColor,
                                            fontWeight = if (hasTests) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.width(50.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Commit details - right side
                second {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        selectedCommit?.let { commit ->
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Text(
                                    CommitTracerBundle.message("dialog.commit.details"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Use our custom IdeaCard component for sectioned information
                                com.example.ijcommittracer.ui.components.IdeaCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // Use our custom LabelWithValue component
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Repository", commit.repositoryName)
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Commit", commit.hash)
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Author", commit.author)
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Date", commit.date)
                                        
                                        if (commit.branches.isNotEmpty()) {
                                            com.example.ijcommittracer.ui.components.LabelWithValue("Branch", commit.branches.joinToString(", "))
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text("Message:", style = MaterialTheme.typography.titleSmall)
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Message with highlighted YouTrack tickets
                                com.example.ijcommittracer.ui.components.IdeaCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        buildAnnotatedString {
                                            val matcher = youtrackTicketPattern.matcher(commit.message)
                                            var lastEnd = 0
                                            
                                            while (matcher.find()) {
                                                // Add text before the match
                                                append(commit.message.substring(lastEnd, matcher.start()))
                                                
                                                // Add the ticket with bold style
                                                pushStyle(SpanStyle(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                ))
                                                append(matcher.group(1))
                                                pop()
                                                
                                                lastEnd = matcher.end()
                                            }
                                            
                                            // Add remaining text
                                            if (lastEnd < commit.message.length) {
                                                append(commit.message.substring(lastEnd))
                                            }
                                        },
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                
                                if (commit.changedFiles.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Changed Files (${commit.changedFiles.size}):", style = MaterialTheme.typography.titleSmall)
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Changed files list
                                    com.example.ijcommittracer.ui.components.IdeaCard(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier.heightIn(max = 200.dp)
                                        ) {
                                            val sortedFiles = commit.changedFiles.sortedWith(
                                                compareByDescending<com.example.ijcommittracer.actions.ListCommitsAction.ChangedFileInfo> { it.isTestFile }
                                                .thenBy { it.path.lowercase() }
                                            )
                                            
                                            items(sortedFiles) { file ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Icon based on change type
                                                    val changePrefix = when (file.changeType) {
                                                        com.example.ijcommittracer.actions.ListCommitsAction.ChangeType.ADDED -> "[+] "
                                                        com.example.ijcommittracer.actions.ListCommitsAction.ChangeType.DELETED -> "[-] "
                                                        else -> "[M] "
                                                    }
                                                    
                                                    // Use different color for test files
                                                    val isTestFile = file.path.contains("test", ignoreCase = true) || 
                                                                    file.path.contains("spec", ignoreCase = true)
                                                    val textColor = if (isTestFile) 
                                                        com.example.ijcommittracer.ui.theme.IntelliJColors.Green 
                                                    else 
                                                        LocalContentColor.current
                                                    
                                                    Text(
                                                        text = "$changePrefix${file.path}",
                                                        color = textColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                
                                                if (sortedFiles.indexOf(file) < sortedFiles.size - 1) {
                                                    Divider(modifier = Modifier.padding(horizontal = 8.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } ?: run {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Select a commit to view details")
                            }
                        }
                    }
                }
            }
        }
    }
    
    @OptIn(ExperimentalSplitPaneApi::class)
    @Composable
    private fun CommitsListWithDetails(
        commits: List<CommitInfo>,
        title: String
    ) {
        var searchText by remember { mutableStateOf("") }
        var selectedCommit by remember { mutableStateOf<CommitInfo?>(commits.firstOrNull()) }
        
        // Initialize selected commit if not already set
        LaunchedEffect(commits) {
            if (selectedCommit == null && commits.isNotEmpty()) {
                selectedCommit = commits.first()
            }
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Optional title
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Search field using our custom IntelliJ-style TextField
            com.example.ijcommittracer.ui.components.IdeaTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(CommitTracerBundle.message("dialog.filter.search")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            
            // Split pane - commits list and details
            val splitPaneState = rememberSplitPaneState(0.6f)
            HorizontalSplitPane(
                splitPaneState = splitPaneState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Commits list - left side
                first {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val filteredCommitsList = commits.filter { 
                            searchText.isEmpty() || it.message.contains(searchText, ignoreCase = true) 
                        }
                        
                        Column {
                            // Use our custom TableHeader component
                            com.example.ijcommittracer.ui.components.TableHeader {
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Message",
                                    modifier = Modifier.weight(1f)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Date",
                                    modifier = Modifier.width(120.dp)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Hash",
                                    modifier = Modifier.width(80.dp)
                                )
                                com.example.ijcommittracer.ui.components.HeaderText(
                                    "Tests",
                                    modifier = Modifier.width(50.dp)
                                )
                            }
                            
                            // Using our custom IdeaSelectableList for better IntelliJ styling
                            com.example.ijcommittracer.ui.components.IdeaSelectableList(
                                items = filteredCommitsList,
                                selectedItem = selectedCommit,
                                onItemSelected = { newSelectedCommit ->
                                    selectedCommit = newSelectedCommit
                                },
                                itemContent = { commit, selected ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            commit.message.lines().first(),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            commit.date,
                                            modifier = Modifier.width(120.dp)
                                        )
                                        Text(
                                            commit.hash.take(7),
                                            modifier = Modifier.width(80.dp)
                                        )
                                        
                                        // Test indicator
                                        // Check if any file paths contain test-related keywords
                                        val hasTests = commit.changedFiles.any { file -> 
                                            file.path.contains("test", ignoreCase = true) || 
                                            file.path.contains("spec", ignoreCase = true)
                                        }
                                        val testIconColor = if (hasTests) 
                                            com.example.ijcommittracer.ui.theme.IntelliJColors.Green 
                                        else 
                                            com.example.ijcommittracer.ui.theme.IntelliJColors.Red
                                        
                                        Text(
                                            text = if (hasTests) "✓" else "✗",
                                            color = testIconColor,
                                            fontWeight = if (hasTests) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.width(50.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Commit details - right side
                second {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        selectedCommit?.let { commit ->
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Text(
                                    CommitTracerBundle.message("dialog.commit.details"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Use our custom IdeaCard component for sectioned information
                                com.example.ijcommittracer.ui.components.IdeaCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // Use our custom LabelWithValue component
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Repository", commit.repositoryName)
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Commit", commit.hash)
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Author", commit.author)
                                        com.example.ijcommittracer.ui.components.LabelWithValue("Date", commit.date)
                                        
                                        if (commit.branches.isNotEmpty()) {
                                            com.example.ijcommittracer.ui.components.LabelWithValue("Branch", commit.branches.joinToString(", "))
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text("Message:", style = MaterialTheme.typography.titleSmall)
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Message with highlighted YouTrack tickets
                                com.example.ijcommittracer.ui.components.IdeaCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        buildAnnotatedString {
                                            val matcher = youtrackTicketPattern.matcher(commit.message)
                                            var lastEnd = 0
                                            
                                            while (matcher.find()) {
                                                // Add text before the match
                                                append(commit.message.substring(lastEnd, matcher.start()))
                                                
                                                // Add the ticket with bold style
                                                pushStyle(SpanStyle(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                ))
                                                append(matcher.group(1))
                                                pop()
                                                
                                                lastEnd = matcher.end()
                                            }
                                            
                                            // Add remaining text
                                            if (lastEnd < commit.message.length) {
                                                append(commit.message.substring(lastEnd))
                                            }
                                        },
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                
                                if (commit.changedFiles.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Changed Files (${commit.changedFiles.size}):", style = MaterialTheme.typography.titleSmall)
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Changed files list
                                    com.example.ijcommittracer.ui.components.IdeaCard(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier.heightIn(max = 200.dp)
                                        ) {
                                            val sortedFiles = commit.changedFiles.sortedWith(
                                                compareByDescending<com.example.ijcommittracer.actions.ListCommitsAction.ChangedFileInfo> { it.isTestFile }
                                                .thenBy { it.path.lowercase() }
                                            )
                                            
                                            items(sortedFiles) { file ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Icon based on change type
                                                    val changePrefix = when (file.changeType) {
                                                        com.example.ijcommittracer.actions.ListCommitsAction.ChangeType.ADDED -> "[+] "
                                                        com.example.ijcommittracer.actions.ListCommitsAction.ChangeType.DELETED -> "[-] "
                                                        else -> "[M] "
                                                    }
                                                    
                                                    // Use different color for test files
                                                    val isTestFile = file.path.contains("test", ignoreCase = true) || 
                                                                    file.path.contains("spec", ignoreCase = true)
                                                    val textColor = if (isTestFile) 
                                                        com.example.ijcommittracer.ui.theme.IntelliJColors.Green 
                                                    else 
                                                        LocalContentColor.current
                                                    
                                                    Text(
                                                        text = "$changePrefix${file.path}",
                                                        color = textColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                
                                                if (sortedFiles.indexOf(file) < sortedFiles.size - 1) {
                                                    Divider(modifier = Modifier.padding(horizontal = 8.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } ?: run {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Select a commit to view details")
                            }
                        }
                    }
                }
            }
        }
    }
    
    
    private fun applyDateFilter(newFromDate: Date, newToDate: Date) {
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
        
        // Actually call the refresh method to update the commits
        refreshCommitsWithDateFilter()
    }
    
    private fun refreshCommitsWithDateFilter() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, 
            CommitTracerBundle.message("task.filtering.commits"), 
            true
        ) {
            private var newCommits: List<CommitInfo> = emptyList()
            private var newAuthorStats: List<AuthorStats> = emptyList()
            
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
                        // Changed files will have default empty list
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
                
                // Convert author map to list for UI
                newAuthorStats = authorMap.values.toList()
            }
            
            override fun onSuccess() {
                // Update both the commits list and author stats
                filteredCommits = newCommits
                selectedCommit = newCommits.firstOrNull()
                
                // Also update the author stats data
                val authorStats = this@CommitListDialog.authorStats.toMutableMap()
                authorStats.clear()
                newAuthorStats.forEach { stats ->
                    authorStats[stats.author] = stats
                }
                
                // Notify UI of date filter applied
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
}