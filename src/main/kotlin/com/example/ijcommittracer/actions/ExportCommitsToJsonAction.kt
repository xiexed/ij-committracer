package com.example.ijcommittracer.actions

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.services.NotificationService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogDataKeys
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Action for exporting selected commits from the Git log to JSON format.
 * This action is available in the Git commits tree view.
 */
class ExportCommitsToJsonAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcsLog = e.getData(VcsLogDataKeys.VCS_LOG) ?: return
        val selectedCommits = vcsLog.selectedDetails

        if (selectedCommits.isEmpty()) {
            NotificationService.showWarning(
                project,
                CommitTracerBundle.message("notification.warning.no.commits.selected"),
                "Commit Tracer"
            )
            return
        }

        // Create a file chooser dialog to select where to save the JSON file
        val descriptor = object : FileChooserDescriptor(false, true, false, false, false, false) {
            override fun getTitle(): String = CommitTracerBundle.message("dialog.export.json.title")
            override fun getDescription(): String = CommitTracerBundle.message("dialog.export.json.description")
        }

        FileChooser.chooseFile(descriptor, project, null) { file ->
            // Create JSON array to hold all commits
            val jsonArray = JSONArray()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            // Convert each commit to a JSON object
            for (commit in selectedCommits) {
                val jsonObject = JSONObject()
                jsonObject.put("hash", commit.id.asString())
                jsonObject.put("author", commit.author.name)
                jsonObject.put("authorEmail", commit.author.email)
                jsonObject.put("date", dateFormat.format(Date(commit.authorTime)))
                jsonObject.put("message", commit.fullMessage)

                // Extract referenced issues from commit message
                val issues = extractYouTrackTickets(commit.fullMessage)
                if (issues.isNotEmpty()) {
                    val issuesArray = JSONArray()
                    issues.forEach { issuesArray.put(it) }
                    jsonObject.put("referencedIssues", issuesArray)
                }

                jsonArray.put(jsonObject)
            }

            // Write JSON to file
            try {
                // Create a file in the selected directory with a timestamp-based name
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "commits_export_${timestamp}.json"
                val outputFile = File(file.path, fileName)
                outputFile.writeText(jsonArray.toString(2)) // Pretty print with 2-space indentation

                NotificationService.showInfo(
                    project,
                    CommitTracerBundle.message(
                        "notification.info.commits.exported",
                        selectedCommits.size,
                        outputFile.absolutePath
                    ),
                    "Commit Tracer"
                )
            } catch (ex: Exception) {
                NotificationService.showError(
                    project,
                    CommitTracerBundle.message("notification.error.export.failed", ex.message ?: ""),
                    "Commit Tracer"
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only when we're in the Git log view and commits are selected
        val project = e.project
        val vcsLog = e.getData(VcsLogDataKeys.VCS_LOG)

        e.presentation.isEnabledAndVisible = project != null && vcsLog != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * Extracts YouTrack ticket IDs from a commit message.
     * Looks for patterns like PROJECT-123, ABC-456, etc.
     */
    private fun extractYouTrackTickets(message: String): Set<String> {
        val pattern = "[A-Z]+-\\d+".toRegex()
        return pattern.findAll(message).map { it.value }.toSet()
    }
}
