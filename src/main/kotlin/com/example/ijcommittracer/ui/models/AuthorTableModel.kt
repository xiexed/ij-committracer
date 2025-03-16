package com.example.ijcommittracer.ui.models

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.actions.ListCommitsAction.AuthorStats
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.table.AbstractTableModel

/**
 * Table model for displaying author statistics.
 */
class AuthorTableModel(private var authors: List<AuthorStats>) : AbstractTableModel() {
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