package com.example.ijcommittracer.services

import com.example.ijcommittracer.CommitTracerBundle
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Service for fetching issue information from YouTrack.
 * This service connects to YouTrack API and retrieves issue details.
 */
@Service(Service.Level.PROJECT)
class YouTrackApiService(private val project: Project) {
    private val logger = Logger.getInstance(YouTrackApiService::class.java)
    
    private val YOUTRACK_URL = CommitTracerBundle.message("youtrack.api.url")
    private val API_BASE_URL = "$YOUTRACK_URL/api"
    private val ISSUE_API_URL = "$API_BASE_URL/issues"
    private val CREDENTIAL_KEY = CommitTracerBundle.message("youtrack.credentials.key")

    /**
     * Fetches ticket information for a specific issue ID.
     * 
     * @param issueId The ID of the issue to fetch (e.g., "IDEA-12345")
     * @return A TicketInfo object containing ticket name and tags, or null if not found
     */
    fun fetchTicketInfo(issueId: String): TicketInfo? {
        // Get token from password safe
        val token = getStoredToken()
        if (token.isNullOrEmpty()) {
            logger.warn(CommitTracerBundle.message("notification.youtrack.no.token"))
            return null
        }
        
        try {
            val url = "$ISSUE_API_URL/$issueId?fields=idReadable,summary,tags(name,id)"
            val connection = HttpRequests.request(url)
                .tuner { conn ->
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Accept", "application/json")
                }
                .connect { request ->
                    val connection = request.connection as HttpURLConnection
                    val responseCode = connection.responseCode
                    
                    when (responseCode) {
                        HttpURLConnection.HTTP_OK -> {
                            val responseText = request.readString()
                            parseTicketResponse(responseText)
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> {
                            logger.warn(CommitTracerBundle.message("notification.youtrack.auth.error", responseCode))
                            null
                        }
                        HttpURLConnection.HTTP_NOT_FOUND -> {
                            logger.info(CommitTracerBundle.message("youtrack.error.not.found", issueId))
                            null
                        }
                        else -> {
                            logger.warn(CommitTracerBundle.message("youtrack.error.fetch", responseCode))
                            null
                        }
                    }
                }
            
            return connection
        } catch (e: IOException) {
            logger.warn(CommitTracerBundle.message("youtrack.error.connection"), e)
            return null
        }
    }
    
    /**
     * Parses the JSON response from YouTrack API.
     */
    private fun parseTicketResponse(jsonText: String): TicketInfo {
        val jsonObject = JSONObject(jsonText)
        val ticketId = jsonObject.getString("idReadable")
        val summary = jsonObject.getString("summary")
        val tags = mutableListOf<String>()
        
        if (jsonObject.has("tags")) {
            val tagsArray = jsonObject.getJSONArray("tags")
            for (i in 0 until tagsArray.length()) {
                val tag = tagsArray.getJSONObject(i)
                if (tag.has("name")) {
                    tags.add(tag.getString("name"))
                }
            }
        }
        
        return TicketInfo(ticketId, summary, tags)
    }
    
    /**
     * Retrieves the YouTrack API token from password safe.
     */
    private fun getStoredToken(): String? {
        val credentialAttributes = createCredentialAttributes()
        val credentials = PasswordSafe.instance.get(credentialAttributes)
        return credentials?.getPasswordAsString()
    }
    
    /**
     * Creates credential attributes for the YouTrack token.
     */
    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("IJ Commit Tracer", CREDENTIAL_KEY)
        )
    }
    
    /**
     * Data class representing ticket information.
     */
    data class TicketInfo(
        val id: String,
        val summary: String,
        val tags: List<String>
    )
}
