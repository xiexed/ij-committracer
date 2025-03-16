package com.example.ijcommittracer.ui.models

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.ui.util.TicketInfo
import javax.swing.table.AbstractTableModel

/**
 * Table model for displaying YouTrack tickets.
 */
class TicketsTableModel(private val tickets: List<TicketInfo>) : AbstractTableModel() {
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