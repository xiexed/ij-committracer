package com.example.ijcommittracer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.AuthorStats
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import java.text.SimpleDateFormat
import java.util.*

/**
 * Composable for displaying author statistics
 */
@Composable
fun AuthorsPanel(
    authorStats: List<AuthorStats>,
    filteredCommits: List<CommitInfo>
) {
    val displayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    var searchText by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text(CommitTracerBundle.message("dialog.filter.search")) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        
        // Authors table
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            val filteredAuthors = authorStats.filter { 
                searchText.isEmpty() || it.author.contains(searchText, ignoreCase = true)
            }
            
            LazyColumn {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Author",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Commits",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            "First Commit",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.width(120.dp)
                        )
                        Text(
                            "Last Commit",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    Divider()
                }
                
                items(filteredAuthors) { authorStat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            authorStat.author,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            authorStat.commitCount.toString(),
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            displayDateFormat.format(authorStat.firstCommitDate),
                            modifier = Modifier.width(120.dp)
                        )
                        Text(
                            displayDateFormat.format(authorStat.lastCommitDate),
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    Divider()
                }
            }
        }
    }
}