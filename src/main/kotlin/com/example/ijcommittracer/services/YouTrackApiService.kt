package com.example.ijcommittracer.services

import com.example.ijcommittracer.CommitTracerBundle
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.PersistentHashMap
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Service for fetching issue information from YouTrack.
 * This service connects to YouTrack API and retrieves issue details.
 */
@Service(Service.Level.PROJECT)
class YouTrackApiService(private val project: Project) {
    private val logger = Logger.getInstance(YouTrackApiService::class.java)
    
    private val youtrackUrl = CommitTracerBundle.message("youtrack.api.url")
    private val apiBaseUrl = "$youtrackUrl/api"
    private val issueApiUrl = "$apiBaseUrl/issues"
    private val credentialKey = CommitTracerBundle.message("youtrack.credentials.key")
    
    // In-memory cache to avoid repeated API calls during a session
    private val sessionTicketCache = mutableMapOf<String, TicketInfo>()
    
    // Persistent cache for blocker and regression status
    private var blockerStatusCache: PersistentHashMap<String, Boolean>? = null
    private var regressionStatusCache: PersistentHashMap<String, Boolean>? = null
    
    init {
        try {
            initPersistentCaches()
            // Register this service with the project's disposable to ensure cleanup
            Disposer.register(project, { dispose() })
        } catch (e: Exception) {
            logger.error("Failed to initialize persistent caches", e)
            // We'll continue without persistent caching if it fails
        }
    }
    
    /**
     * Initialize persistent caches for blocker and regression status
     */
    private fun initPersistentCaches() {
        val cacheDirPath = Paths.get(PathManager.getSystemPath(), "committracer-cache")
        Files.createDirectories(cacheDirPath)
        
        try {
            blockerStatusCache = PersistentHashMap(
                cacheDirPath.resolve("blocker-status.dat"),
                EnumeratorStringDescriptor,
                BooleanDataExternalizer
            )
            
            regressionStatusCache = PersistentHashMap(
                cacheDirPath.resolve("regression-status.dat"),
                EnumeratorStringDescriptor,
                BooleanDataExternalizer
            )
        } catch (e: IOException) {
            // If cache files are corrupted, delete them and try again
            logger.warn("Cache files may be corrupted, recreating them", e)
            try {
                // Close any open caches first
                blockerStatusCache?.close()
                regressionStatusCache?.close()
                
                // Delete the corrupt files
                Files.deleteIfExists(cacheDirPath.resolve("blocker-status.dat"))
                Files.deleteIfExists(cacheDirPath.resolve("regression-status.dat"))
                
                // Recreate the caches
                blockerStatusCache = PersistentHashMap(
                    cacheDirPath.resolve("blocker-status.dat"),
                    EnumeratorStringDescriptor,
                    BooleanDataExternalizer
                )
                
                regressionStatusCache = PersistentHashMap(
                    cacheDirPath.resolve("regression-status.dat"),
                    EnumeratorStringDescriptor,
                    BooleanDataExternalizer
                )
            } catch (ex: IOException) {
                logger.error("Failed to recreate persistent caches", ex)
                // Disable persistent caching
                blockerStatusCache = null
                regressionStatusCache = null
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
        
        // Check in-memory cache first
        val cachedInfo = sessionTicketCache[issueId]
        if (cachedInfo != null) {
            return cachedInfo
        }
        
        try {
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
                            
                            // Cache the result in memory
                            sessionTicketCache[issueId] = ticketInfo
                            
                            // Cache blocker and regression status persistently
                            try {
                                val isBlocker = ticketInfo.tags.any { tag -> tag.startsWith("blocking-") }
                                val isRegression = ticketInfo.tags.any { tag -> 
                                    tag.lowercase().contains("regression") 
                                } || ticketInfo.summary.lowercase().contains("regression")
                                
                                // Update persistent caches safely
                                updateBlockerCache(issueId, isBlocker)
                                updateRegressionCache(issueId, isRegression)
                            } catch (e: Exception) {
                                logger.warn("Failed to update persistent cache", e)
                                // Continue without updating the cache
                            }
                            
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
     * Checks if a ticket is a blocker based on cached data or API call
     * 
     * @param issueId The ID of the issue to check
     * @return true if the issue is a blocker, false otherwise
     */
    fun isBlockerTicket(issueId: String): Boolean {
        // Check persistent cache first
        return try {
            if (blockerStatusCache?.containsMapping(issueId) == true) {
                blockerStatusCache?.get(issueId) ?: false
            } else {
                // Fetch from API if not in cache
                val ticketInfo = fetchTicketInfo(issueId)
                val isBlocker = ticketInfo?.tags?.any { tag -> tag.startsWith("blocking-") } ?: false
                
                // Update cache
                updateBlockerCache(issueId, isBlocker)
                
                isBlocker
            }
        } catch (e: Exception) {
            logger.warn("Error checking blocker status for $issueId", e)
            // Fallback to API call
            val ticketInfo = fetchTicketInfo(issueId)
            ticketInfo?.tags?.any { tag -> tag.startsWith("blocking-") } ?: false
        }
    }
    
    /**
     * Checks if a ticket is a regression based on cached data or API call
     * 
     * @param issueId The ID of the issue to check
     * @return true if the issue is a regression, false otherwise
     */
    fun isRegressionTicket(issueId: String): Boolean {
        // Check persistent cache first
        return try {
            if (regressionStatusCache?.containsMapping(issueId) == true) {
                regressionStatusCache?.get(issueId) ?: false
            } else {
                // Fetch from API if not in cache
                val ticketInfo = fetchTicketInfo(issueId)
                val isRegression = ticketInfo?.tags?.any { tag -> 
                    tag.lowercase().contains("regression") 
                } ?: false || ticketInfo?.summary?.lowercase()?.contains("regression") ?: false
                
                // Update cache
                updateRegressionCache(issueId, isRegression)
                
                isRegression
            }
        } catch (e: Exception) {
            logger.warn("Error checking regression status for $issueId", e)
            // Fallback to API call
            val ticketInfo = fetchTicketInfo(issueId)
            (ticketInfo?.tags?.any { tag -> tag.lowercase().contains("regression") } ?: false) || 
            (ticketInfo?.summary?.lowercase()?.contains("regression") ?: false)
        }
    }
    
    /**
     * Safely update the blocker cache
     */
    private fun updateBlockerCache(issueId: String, isBlocker: Boolean) {
        try {
            blockerStatusCache?.let { cache ->
                if (isBlocker) {
                    cache.put(issueId, true)
                } else if (!cache.containsMapping(issueId)) {
                    cache.put(issueId, false)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to update blocker cache for $issueId", e)
        }
    }
    
    /**
     * Safely update the regression cache
     */
    private fun updateRegressionCache(issueId: String, isRegression: Boolean) {
        try {
            regressionStatusCache?.let { cache ->
                if (isRegression) {
                    cache.put(issueId, true)
                } else if (!cache.containsMapping(issueId)) {
                    cache.put(issueId, false)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to update regression cache for $issueId", e)
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
        
        // Save the token for future use
        storeToken(token)
        
        return token
    }
    
    /**
     * Stores the provided token in the password safe.
     */
    fun storeToken(token: String) {
        PasswordSafe.instance.set(
            createCredentialAttributes(),
            Credentials("YouTrack API Token", token)
        )
        logger.info("YouTrack API token stored successfully")
    }
    
    /**
     * Clears the stored token.
     */
    fun clearToken() {
        PasswordSafe.instance.set(createCredentialAttributes(), null)
        logger.info("YouTrack API token cleared")
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
            generateServiceName("IJ Commit Tracer", credentialKey)
        )
    }
    
    /**
     * Verifies if the token is valid by making a test API call.
     */
    fun validateToken(token: String): Boolean {
        try {
            // Try to fetch a user profile or a simple API endpoint to validate token
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
     * Data class representing ticket information.
     */
    data class TicketInfo(
        val id: String,
        val summary: String,
        val tags: List<String>
    )
    
    /**
     * This method must be called when the plugin is being unloaded
     * to ensure proper cleanup of resources.
     */
    fun dispose() {
        try {
            blockerStatusCache?.apply {
                force()
                close()
            }
            regressionStatusCache?.apply {
                force()
                close()
            }
            logger.info("YouTrack cache resources released")
        } catch (e: Exception) {
            logger.warn("Error closing YouTrack caches", e)
        }
    }
    
    /**
     * Force flush the cache to disk
     */
    fun flushCaches() {
        try {
            blockerStatusCache?.force()
            regressionStatusCache?.force()
        } catch (e: Exception) {
            logger.warn("Error flushing caches to disk", e)
        }
    }
    
    /**
     * Helper class for serializing boolean values in PersistentHashMap
     */
    private object BooleanDataExternalizer : com.intellij.util.io.DataExternalizer<Boolean> {
        override fun save(out: java.io.DataOutput, value: Boolean) {
            out.writeBoolean(value)
        }
        
        override fun read(input: java.io.DataInput): Boolean {
            return input.readBoolean()
        }
    }
    
    /**
     * Helper class for serializing string keys in PersistentHashMap
     */
    private object EnumeratorStringDescriptor : com.intellij.util.io.KeyDescriptor<String> {
        override fun getHashCode(value: String): Int = value.hashCode()
        
        override fun isEqual(val1: String, val2: String): Boolean = val1 == val2
        
        override fun save(out: java.io.DataOutput, value: String) {
            out.writeUTF(value)
        }
        
        override fun read(input: java.io.DataInput): String {
            return input.readUTF()
        }
    }
}