package com.example.ijcommittracer.services

import com.example.ijcommittracer.CommitTracerBundle
import com.example.ijcommittracer.util.EnvChangeListener
import com.example.ijcommittracer.util.EnvFileReader
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.io.HttpRequests
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference

/**
 * Service for fetching issue information from YouTrack.
 * This service connects to YouTrack API and retrieves issue details.
 */
@Service(Service.Level.PROJECT)
class YouTrackApiService(private val project: Project) : EnvChangeListener {
    private val logger = Logger.getInstance(YouTrackApiService::class.java)
    
    // Constants for .env file properties
    private val YOUTRACK_API_TOKEN_KEY = "YOUTRACK_API_TOKEN"
    private val YOUTRACK_API_URL_KEY = "YOUTRACK_API_URL"
    
    // Use AtomicReference for thread-safe mutable values
    private val youtrackUrlRef = AtomicReference<String>()
    private val apiBaseUrlRef = AtomicReference<String>()
    private val issueApiUrlRef = AtomicReference<String>()
    
    // Cache to avoid repeated API calls for the same ticket
    private val ticketCache = mutableMapOf<String, TicketInfo>()
    private val credentialKey = CommitTracerBundle.message("youtrack.credentials.key")
    
    init {
        // Subscribe to environment changes
        project.messageBus.connect().subscribe(EnvFileReader.ENV_CHANGED_TOPIC, this)
        
        // Initialize URL values
        refreshUrls()
    }
    
    /**
     * Initialize or refresh the URL values from environment variables
     */
    private fun refreshUrls() {
        val envReader = EnvFileReader.getInstance(project)
        val youtrackUrl = envReader.getProperty(
            YOUTRACK_API_URL_KEY, 
            CommitTracerBundle.message("youtrack.api.url")
        )
        
        youtrackUrlRef.set(youtrackUrl)
        apiBaseUrlRef.set("$youtrackUrl/api")
        issueApiUrlRef.set("${apiBaseUrlRef.get()}/issues")
        
        logger.info("YouTrack URLs refreshed: base=${youtrackUrlRef.get()}")
    }
    
    /**
     * Handler for environment variable changes
     */
    override fun onEnvChanged(changedKeys: Set<String>?) {
        // If changedKeys is null, all keys are considered changed
        if (changedKeys == null || 
            changedKeys.contains(YOUTRACK_API_TOKEN_KEY) || 
            changedKeys.contains(YOUTRACK_API_URL_KEY)) {
            
            logger.info("Detected environment changes affecting YouTrack configuration")
            
            // Clear cache when tokens or URLs change
            ticketCache.clear()
            
            // Refresh URLs if they've changed
            if (changedKeys == null || changedKeys.contains(YOUTRACK_API_URL_KEY)) {
                refreshUrls()
            }
        }
    }

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
        
        // Cache for ticket info to avoid repeated API calls
        val cachedInfo = ticketCache[issueId]
        if (cachedInfo != null) {
            return cachedInfo
        }
        
        try {
            val issueApiUrl = issueApiUrlRef.get()
            val url = "$issueApiUrl/$issueId?fields=idReadable,summary,tags(name,id)"
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
                            val ticketInfo = parseTicketResponse(responseText)
                            // Cache the result
                            ticketCache[issueId] = ticketInfo
                            ticketInfo
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
     * Gets stored YouTrack token or requests a new one from the user.
     */
    fun getOrRequestToken(): String? {
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
        
        // Return the token immediately but store it in the background
        // to avoid blocking the UI thread
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            storeToken(token)
        }
        
        return token
    }
    
    /**
     * Stores the provided token in the password safe.
     * This method should be called from a background thread to avoid UI freezes.
     */
    fun storeToken(token: String) {
        // Move token storage to a background thread
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            com.intellij.util.SlowOperations.assertSlowOperationsAreAllowed()
            PasswordSafe.instance.set(
                createCredentialAttributes(),
                Credentials("YouTrack API Token", token)
            )
            logger.info("YouTrack API token stored successfully")
            
            // Clear cache when token changes
            ticketCache.clear()
        }
    }
    
    /**
     * Clears the stored token.
     * This method should be called from a background thread to avoid UI freezes.
     */
    fun clearToken() {
        // Move token clearing to a background thread
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            com.intellij.util.SlowOperations.assertSlowOperationsAreAllowed()
            PasswordSafe.instance.set(createCredentialAttributes(), null)
            logger.info("YouTrack API token cleared")
            
            // Clear cache when token is cleared
            ticketCache.clear()
        }
    }
    
    /**
     * Retrieves the YouTrack API token.
     * First tries to get the token from .env file, then falls back to password safe.
     */
    private fun getStoredToken(): String? {
        // Try to get token from .env file first
        val envToken = EnvFileReader.getInstance(project).getProperty(YOUTRACK_API_TOKEN_KEY)
        if (!envToken.isNullOrBlank()) {
            logger.info("Using YouTrack API token from .env file")
            return envToken
        }
        
        // Fall back to credential store
        val credentialAttributes = createCredentialAttributes()
        val credentials = PasswordSafe.instance.get(credentialAttributes)
        return credentials?.getPasswordAsString()
    }
    
    /**
     * Creates credential attributes for the YouTrack token.
     */
    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("IJ Commit Tracer", credentialKey)
        )
    }
    
    /**
     * Verifies if the token is valid by making a test API call.
     */
    fun validateToken(token: String): Boolean {
        try {
            // Try to fetch a user profile or a simple API endpoint to validate token
            val apiBaseUrl = apiBaseUrlRef.get()
            val url = "$apiBaseUrl/users/me?fields=id,name"
            val result = HttpRequests.request(url)
                .tuner { conn ->
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Accept", "application/json")
                }
                .connect { request ->
                    val connection = request.connection as HttpURLConnection
                    connection.responseCode == HttpURLConnection.HTTP_OK
                }
            
            return result
        } catch (e: IOException) {
            logger.warn("Failed to validate YouTrack token", e)
            return false
        }
    }
    
    /**
     * Clears the ticket cache.
     */
    fun clearCache() {
        ticketCache.clear()
        logger.info("YouTrack ticket cache cleared")
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
