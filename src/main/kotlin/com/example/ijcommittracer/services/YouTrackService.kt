package com.example.ijcommittracer.services

import com.example.ijcommittracer.CommitTracerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that interacts with YouTrack API to fetch issue details.
 * This service uses the YouTrackApiService which handles the direct API communication.
 */
@Service(Service.Level.PROJECT)
class YouTrackService(private val project: Project) {
    private val LOG = logger<YouTrackService>()
    private val youTrackApiService = project.getService(YouTrackApiService::class.java)
    
    // Thread-safe cache for issue details
    private val cache = ConcurrentHashMap<String, CachedIssue>()
    
    /**
     * Fetches issue details from YouTrack by issue ID.
     * Uses cache if available.
     *
     * @param issueId The ID of the issue to fetch
     * @return YouTrackIssue object with issue details or null if not found
     */
    fun getIssueDetails(issueId: String): YouTrackIssue? {
        // Try to get from cache first
        cache[issueId]?.let { cachedIssue ->
            if (cachedIssue.isValid()) {
                return cachedIssue.issue
            }
        }
        
        try {
            // Fetch ticket info using the YouTrackApiService
            val ticketInfo = youTrackApiService.fetchTicketInfo(issueId) ?: return null
            
            // Convert to our model
            val ytIssue = YouTrackIssue(
                id = ticketInfo.id,
                summary = ticketInfo.summary,
                tags = ticketInfo.tags.map { tag -> YouTrackTag(tag, null) }
            )
            
            // Cache the result
            cache[issueId] = CachedIssue(ytIssue, LocalDateTime.now())
            return ytIssue
        } catch (e: Exception) {
            LOG.error("Error fetching issue from YouTrack", e)
            NotificationService.showError(
                project,
                CommitTracerBundle.message("notification.youtrack.connection.error", e.message.orEmpty()),
                "YouTrack Connection Error"
            )
            return null
        }
    }
    
    /**
     * Fetches issue details asynchronously using coroutines.
     * Should be used from UI contexts to avoid blocking.
     *
     * @param issueId The ID of the issue to fetch
     * @return YouTrackIssue object with issue details or null if not found
     */
    suspend fun getIssueDetailsAsync(issueId: String): YouTrackIssue? = withContext(Dispatchers.IO) {
        getIssueDetails(issueId)
    }

    /**
     * Gets the stored YouTrack API token or requests it from the user.
     */
    fun getOrRequestToken(): String? {
        return youTrackApiService.getOrRequestToken()
    }
    
    /**
     * Stores the provided token in the password safe.
     * This method should be called from a background thread to avoid UI freezes.
     */
    fun storeToken(token: String) {
        youTrackApiService.storeToken(token)
        
        // Clear our cache when token changes
        cache.clear()
    }

    /**
     * Clears the stored token.
     */
    fun clearToken() {
        youTrackApiService.clearToken()
        
        // Clear our cache when token is cleared
        cache.clear()
    }
    
    /**
     * Validates the YouTrack connection by trying to verify the token.
     * Returns true if connection is valid, false otherwise.
     */
    fun validateConnection(): Boolean {
        val token = youTrackApiService.getOrRequestToken() ?: return false
        return youTrackApiService.validateToken(token)
    }
    
    /**
     * Clears the cache.
     */
    fun clearCache() {
        cache.clear()
        LOG.info("YouTrack issue cache cleared")
    }
    
    companion object {
        // Cache validity duration - 1 hour
        private val CACHE_VALIDITY_DURATION = Duration.ofHours(1)
    }
    
    /**
     * Private class for cached issue with timestamp.
     */
    private data class CachedIssue(
        val issue: YouTrackIssue,
        val timestamp: LocalDateTime
    ) {
        fun isValid(): Boolean {
            val now = LocalDateTime.now()
            return Duration.between(timestamp, now) < CACHE_VALIDITY_DURATION
        }
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
