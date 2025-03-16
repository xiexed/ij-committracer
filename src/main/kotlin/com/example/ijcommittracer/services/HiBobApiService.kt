package com.example.ijcommittracer.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with HiBob API to fetch employee information.
 */
@Service(Service.Level.PROJECT)
class HiBobApiService(private val project: Project) {
    
    private val cache = ConcurrentHashMap<String, EmployeeInfo>()
    private var lastCacheRefresh: LocalDate = LocalDate.now().minusDays(1)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // API base URL and token should be configurable
    private var apiToken: String = ""
    private var apiBaseUrl: String = "https://api.hibob.com/v1"
    
    /**
     * Set the API credentials.
     */
    fun setApiCredentials(token: String, baseUrl: String = "https://api.hibob.com/v1") {
        apiToken = token
        apiBaseUrl = baseUrl
    }
    
    /**
     * Get employee information by email.
     * Uses cache if available and not expired.
     */
    fun getEmployeeByEmail(email: String): EmployeeInfo? {
        // Check if we need to refresh the cache (once per day)
        val today = LocalDate.now()
        if (today.isAfter(lastCacheRefresh)) {
            clearCache()
            lastCacheRefresh = today
        }
        
        // Return from cache if available
        cache[email]?.let { return it }
        
        // If not in cache, fetch from API
        if (apiToken.isBlank()) {
            return null
        }
        
        return try {
            fetchEmployeeFromApi(email)?.also {
                // Store in cache
                cache[email] = it
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Clear the cache.
     */
    fun clearCache() {
        cache.clear()
    }
    
    /**
     * Fetch employee information from HiBob API.
     */
    private fun fetchEmployeeFromApi(email: String): EmployeeInfo? {
        val request = Request.Builder()
            .url("$apiBaseUrl/people?email=$email")
            .header("Authorization", "Bearer $apiToken")
            .header("Accept", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            return null
        }
        
        val responseBody = response.body?.string() ?: return null
        val jsonObject = JSONObject(responseBody)
        
        // Parse the response - HiBob API might return an array or a single object
        // Adjust parsing based on actual API response structure
        val employeesArray = jsonObject.optJSONArray("employees") ?: return null
        
        if (employeesArray.length() == 0) {
            return null
        }
        
        val employee = employeesArray.getJSONObject(0)
        
        return EmployeeInfo(
            email = email,
            name = employee.optString("displayName", ""),
            team = employee.optString("department", ""),
            title = employee.optString("title", ""),
            manager = employee.optString("managerEmail", "")
        )
    }
    
    companion object {
        @JvmStatic
        fun getInstance(project: Project): HiBobApiService = project.service()
    }
}

/**
 * Data class to store employee information.
 */
data class EmployeeInfo(
    val email: String,
    val name: String,
    val team: String,
    val title: String,
    val manager: String
)