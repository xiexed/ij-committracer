package com.example.ijcommittracer.ui.util

import com.example.ijcommittracer.actions.ListCommitsAction.CommitInfo

/**
 * Data class for YouTrack ticket information
 */
data class TicketInfo(
    val ticketId: String,
    val commits: List<CommitInfo>,
    val isBlocker: Boolean = false,
    val isRegression: Boolean = false
)