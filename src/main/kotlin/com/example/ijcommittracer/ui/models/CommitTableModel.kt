package com.example.ijcommittracer.ui.models

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.table.AbstractTableModel

/**
 * Table model for displaying Git commits.
 */
class CommitTableModel(private var commits: List<CommitInfo>) : AbstractTableModel() {
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