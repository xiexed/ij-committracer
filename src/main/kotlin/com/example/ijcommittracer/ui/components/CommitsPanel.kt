package com.example.ijcommittracer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import java.util.regex.Pattern

/**
 * Composable for displaying Git commits
 */
@Composable
fun CommitsPanel(
    commits: List<CommitInfo>,
    onCommitSelected: (CommitInfo) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var selectedCommit by remember { mutableStateOf<CommitInfo?>(null) }
    val youtrackTicketPattern = Pattern.compile("([A-Z]+-\\d+)") // Pattern for YouTrack ticket references
    
    // Initialize selected commit if not already set
    LaunchedEffect(commits) {
        if (selectedCommit == null && commits.isNotEmpty()) {
            selectedCommit = commits.first()
            onCommitSelected(commits.first())
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text(CommitTracerBundle.message("dialog.filter.search")) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        
        // Split pane - commits list and details
        Row(modifier = Modifier.fillMaxSize()) {
            // Commits list - 60% width
            Surface(
                modifier = Modifier.weight(0.6f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                val filteredCommitsList = commits.filter { 
                    searchText.isEmpty() || it.message.contains(searchText, ignoreCase = true) 
                }
                
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Message",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Date",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.width(120.dp)
                            )
                            Text(
                                "Hash",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                "Tests",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.width(50.dp),
                                maxLines = 1
                            )
                        }
                        Divider()
                    }
                    
                    items(filteredCommitsList) { commit ->
                        val isSelected = selectedCommit == commit
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface,
                            onClick = { 
                                selectedCommit = commit
                                onCommitSelected(commit)
                            }
                        ) {
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
                                val hasTests = commit.changedFiles.any { it.isTestFile }
                                Text(
                                    text = if (hasTests) "✓" else "✗",
                                    color = if (hasTests) Color.Green else Color.Red,
                                    fontWeight = if (hasTests) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.width(50.dp),
                                    maxLines = 1
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
            
            // Divider
            Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
            
            // Commit details - 40% width
            Surface(
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                selectedCommit?.let { commit ->
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            CommitTracerBundle.message("dialog.commit.details"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Repository: ${commit.repositoryName}")
                        Text("Commit: ${commit.hash}")
                        Text("Author: ${commit.author}")
                        Text("Date: ${commit.date}")
                        
                        if (commit.branches.isNotEmpty()) {
                            Text("Branch: ${commit.branches.joinToString(", ")}")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Message:", style = MaterialTheme.typography.titleSmall)
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Message with highlighted YouTrack tickets
                        Text(buildAnnotatedString {
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
                        })
                        
                        if (commit.changedFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Changed Files (${commit.changedFiles.size}):", style = MaterialTheme.typography.titleSmall)
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Changed files list
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant
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
                                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Icon based on change type
                                            val changePrefix = when (file.changeType) {
                                                com.example.ijcommittracer.actions.ListCommitsAction.ChangeType.ADDED -> "[+] "
                                                com.example.ijcommittracer.actions.ListCommitsAction.ChangeType.DELETED -> "[-] "
                                                else -> "[M] "
                                            }
                                            
                                            // Use different color for test files
                                            val textColor = if (file.isTestFile) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
                                            
                                            Text(
                                                text = "$changePrefix${file.path}",
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
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