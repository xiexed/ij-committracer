package com.example.ijcommittracer.services

import com.example.ijcommittracer.CommitTracerBundle
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.io.HttpRequests
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Service that interacts with YouTrack API to fetch issue details.
 */
@Service(Service.Level.PROJECT)
class YouTrackService(private val project: Project) {

    private val YOUTRACK_URL = "https://youtrack.jetbrains.com"
    private val API_BASE_URL = "$YOUTRACK_URL/api"
    private val ISSUE_API_URL = "$API_BASE_URL/issues"
    private val CREDENTIAL_KEY = "commit-tracer-youtrack-token"

    /**
     * Fetches issue details from YouTrack by issue ID.
     *
     * @param issueId The ID of the issue to fetch
     * @return YouTrackIssue object with issue details or null if issue not found
     */
    fun getIssueDetails(issueId: String): YouTrackIssue? {
        // Get the token or prompt for it if not available
        val token = getOrRequestToken() ?: return null

        try {
            val url = "$ISSUE_API_URL/$issueId?fields=id,summary,tags(name,color)"
            
            val connection = HttpRequests.request(url)
                .tuner { connection ->
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Content-Type", "application/json")
                }
                .connect { request ->
                    val connection = request.connection as HttpURLConnection
                    val responseCode = connection.responseCode
                    
                    if (responseCode == 401) {
                        // Token is invalid - clear it and notify user
                        clearToken()
                        NotificationService.showError(
                            project,
                            CommitTracerBundle.message("notification.youtrack.auth.failed"),
                            "YouTrack Connection Error"
                        )
                        return@connect null
                    }
                    
                    if (responseCode == 404) {
                        NotificationService.showWarning(
                            project,
                            CommitTracerBundle.message("notification.youtrack.issue.not.found", issueId),
                            "YouTrack Issue Not Found"
                        )
                        return@connect null
                    }
                    
                    if (responseCode != 200) {
                        NotificationService.showError(
                            project,
                            CommitTracerBundle.message("notification.youtrack.api.error", responseCode),
                            "YouTrack API Error"
                        )
                        return@connect null
                    }
                    
                    val response = request.readString()
                    parseIssueResponse(response)
                }
            
            return connection
        } catch (e: IOException) {
            NotificationService.showError(
                project,
                CommitTracerBundle.message("notification.youtrack.connection.error", e.message.orEmpty()),
                "YouTrack Connection Error"
            )
            return null
        }
    }

    /**
     * Parses the JSON response from YouTrack API.
     */
    private fun parseIssueResponse(jsonString: String): YouTrackIssue {
        val json = JSONObject(jsonString)
        val id = json.getString("id")
        val summary = json.getString("summary")
        val tags = mutableListOf<YouTrackTag>()
        
        if (json.has("tags")) {
            val tagsArray = json.getJSONArray("tags")
            for (i in 0 until tagsArray.length()) {
                val tagJson = tagsArray.getJSONObject(i)
                val name = tagJson.getString("name")
                val color = if (tagJson.has("color")) tagJson.getString("color") else null
                tags.add(YouTrackTag(name, color))
            }
        }
        
        return YouTrackIssue(id, summary, tags)
    }

    /**
     * Gets the stored YouTrack API token or requests it from the user.
     */
    private fun getOrRequestToken(): String? {
        // Try to get the token from PasswordSafe
        val credentialAttributes = createCredentialAttributes()
        val credentials = PasswordSafe.instance.get(credentialAttributes)

        if (credentials != null && !credentials.getPasswordAsString().isNullOrEmpty()) {
            return credentials.getPasswordAsString()
        }
        
        // If no token found, prompt the user
        val token = Messages.showInputDialog(
            project,
            CommitTracerBundle.message("dialog.youtrack.token.prompt"),
            CommitTracerBundle.message("dialog.youtrack.auth"),
            null
        ) ?: return null
        
        if (token.isBlank()) {
            return null
        }
        
        // Save the token for future use
        PasswordSafe.instance.set(
            credentialAttributes,
            Credentials("YouTrack API Token", token)
        )
        
        return token
    }

    /**
     * Clears the stored token.
     */
    private fun clearToken() {
        PasswordSafe.instance.set(createCredentialAttributes(), null)
    }

    /**
     * Creates credential attributes for the token.
     */
    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("IJ Commit Tracer", CREDENTIAL_KEY)
        )
    }

    /**
     * Data class representing a YouTrack issue.
     */
    data class YouTrackIssue(
        val id: String,
        val summary: String,
        val tags: List<YouTrackTag>
    )

    /**
     * Data class representing a YouTrack tag.
     */
    data class YouTrackTag(
        val name: String,
        val color: String?
    )
}
