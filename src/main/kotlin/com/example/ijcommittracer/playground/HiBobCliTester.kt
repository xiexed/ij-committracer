package com.example.ijcommittracer.playground

import com.example.ijcommittracer.playground.EmployeeInfoSimple
import com.example.ijcommittracer.util.EnvFileReaderSimple
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Standalone HiBob API tester that can be run without IntelliJ IDEA.
 * This provides a simple command-line interface for testing HiBob API integration.
 */
object HiBobCliTester {
    private var DEFAULT_ENV_PATH = "${System.getProperty("user.home")}/src/intellij/.env"
    private const val DEFAULT_API_URL = "https://api.hibob.com/v1"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Interactive command-line HiBob API tester
     */
    @JvmStatic
    fun main(args: Array<String>) {
        println("===== HiBob API Tester =====")
        println("Interactive command-line tool for testing HiBob API integration")
        println()
        
        // Interactive mode
        val scanner = java.util.Scanner(System.`in`)
        var running = true
        
        // Initialize with values from args if provided
        var envPath = if (args.isNotEmpty()) args[0] else DEFAULT_ENV_PATH
        var emails = if (args.size > 1) args.sliceArray(1 until args.size).toList() else emptyList()
        
        while (running) {
            println("\nMenu:")
            println("1. Set .env file path (current: $envPath)")
            println("2. Add email to test")
            println("3. List emails to test (${emails.size} emails)")
            println("4. Clear email list")
            println("5. Test HiBob API with current settings")
            println("6. Sort results by commit count (default)")
            println("7. Add commonly requested JetBrains developers")
            println("8. Exit")
            
            print("\nEnter your choice (1-8): ")
            val choice = scanner.nextLine().trim()
            
            when (choice) {
                "1" -> {
                    print("Enter .env file path (or press Enter for default - $DEFAULT_ENV_PATH): ")
                    val input = scanner.nextLine().trim()
                    envPath = if (input.isNotEmpty()) input else DEFAULT_ENV_PATH
                    println("Using .env file: $envPath")
                }
                "2" -> {
                    print("Enter email address: ")
                    val email = scanner.nextLine().trim()
                    if (email.isNotEmpty() && email.contains("@")) {
                        emails = emails + email
                        println("Added email: $email")
                    } else {
                        println("Invalid email format. Please try again.")
                    }
                }
                "3" -> {
                    println("\nEmails to test:")
                    if (emails.isEmpty()) {
                        println("No emails added yet. Use option 2 to add emails.")
                    } else {
                        emails.forEachIndexed { index, email -> println("${index + 1}. $email") }
                    }
                }
                "4" -> {
                    emails = emptyList()
                    println("Email list cleared")
                }
                "5" -> {
                    // If no emails are specified, use defaults
                    if (emails.isEmpty()) {
                        emails = listOf("sergey.ignatov@jetbrains.com", "ignatov@jetbrains.com")
                        println("No emails specified. Using default test emails.")
                    }
                    
                    runHiBobTest(envPath, emails)
                }
                "6" -> {
                    println("Results are already sorted by commit count by default.")
                }
                "7" -> {
                    // Add commonly requested JetBrains developers
                    val jetBrainsDevs = listOf(
                        "sergey.ignatov@jetbrains.com",
                        "dmitry.jemerov@jetbrains.com",
                        "vitaliy.bragilevsky@jetbrains.com", 
                        "roman.elizarov@jetbrains.com",
                        "maxim.medvedev@jetbrains.com",
                        "andrey.breslav@jetbrains.com"
                    )
                    
                    val newEmails = jetBrainsDevs.filter { !emails.contains(it) }
                    emails = emails + newEmails
                    
                    println("Added ${newEmails.size} JetBrains developers to the test list.")
                    println("Total emails in list: ${emails.size}")
                }
                "8" -> {
                    running = false
                    println("Exiting HiBob API Tester. Goodbye!")
                }
                else -> println("Invalid choice. Please enter a number between 1 and 8.")
            }
        }
        
        scanner.close()
    }
    
    /**
     * Runs the HiBob API test with the specified .env file and emails
     */
    private fun runHiBobTest(envPath: String, emails: List<String>) {
        println("\n===== Running HiBob API Test =====")
        println("Using .env file: $envPath")
        
        try {
            // Load credentials from .env file
            val envReader = EnvFileReaderSimple.getInstance(envPath)
            val serviceUserId = envReader.getProperty("HIBOB_SERVICE_USER_ID")
            val token = envReader.getProperty("HIBOB_API_TOKEN")
            val baseUrl = envReader.getProperty("HIBOB_API_URL", DEFAULT_API_URL)
            
            if (serviceUserId.isNullOrBlank() || token.isNullOrBlank()) {
                println("Error: HIBOB_SERVICE_USER_ID or HIBOB_API_TOKEN not found in .env file")
                println("Please ensure your .env file contains these values.")
                return
            }
            
            println("Successfully loaded credentials from .env file")
            println("API Base URL: $baseUrl")
            
            println("\nFetching employee data for ${emails.size} email(s)...\n")
            
            // Fetch employee info for each email and store in a list
            val employeeResults = mutableListOf<EmployeeResult>()
            
            emails.forEach { email ->
                try {
                    val employeeInfo = fetchEmployeeInfo(email, serviceUserId, token, baseUrl)
                    if (employeeInfo != null) {
                        // Also fetch commit count for this employee
                        val commitCount = fetchCommitCount(employeeInfo.email)
                        employeeResults.add(
                            EmployeeResult(
                                email = email,
                                info = employeeInfo,
                                commitCount = commitCount,
                                found = true
                            )
                        )
                    } else {
                        employeeResults.add(
                            EmployeeResult(
                                email = email,
                                info = null,
                                commitCount = 0,
                                found = false
                            )
                        )
                    }
                } catch (e: Exception) {
                    println("Error fetching data for $email: ${e.message}")
                    employeeResults.add(
                        EmployeeResult(
                            email = email,
                            info = null,
                            commitCount = 0,
                            found = false,
                            error = e.message ?: "Unknown error"
                        )
                    )
                }
            }
            
            // Sort results by commit count (highest first)
            val sortedResults = employeeResults.sortedByDescending { it.commitCount }
            
            // Format for pretty printing results
            val format = "| %-30s | %-25s | %-25s | %-15s | %-25s | %-10s |\n"
            println(String.format(format, 
                "Email", 
                "Name", 
                "Title", 
                "Team", 
                "Manager", 
                "Commits"
            ))
            println(String.format(format, 
                "-".repeat(30), 
                "-".repeat(25), 
                "-".repeat(25), 
                "-".repeat(15),
                "-".repeat(25),
                "-".repeat(10)
            ))
            
            // Display the sorted results
            var successCount = 0
            sortedResults.forEach { result ->
                if (result.found) {
                    val info = result.info!!
                    println(String.format(format, 
                        truncate(result.email, 30),
                        truncate(info.name, 25), 
                        truncate(info.title, 25), 
                        truncate(info.team, 15),
                        truncate(info.manager, 25),
                        result.commitCount
                    ))
                    successCount++
                } else {
                    val errorMessage = if (result.error.isNotEmpty()) " (Error: ${result.error})" else ""
                    println(String.format(format, 
                        truncate(result.email, 30),
                        "Not found$errorMessage", 
                        "-", 
                        "-",
                        "-",
                        "0"
                    ))
                }
            }
            
            println("\nTest complete: Found $successCount out of ${emails.size} employee(s)")
            
        } catch (e: Exception) {
            println("Error during test: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Simple class to hold employee results including commit count
     */
    private data class EmployeeResult(
        val email: String,
        val info: EmployeeInfoSimple?,
        val commitCount: Int,
        val found: Boolean,
        val error: String = ""
    )
    
    /**
     * Fetches commit count for the specified email.
     * In a real implementation, this would query a Git repository.
     * For this demo, we'll return a random count or use team-based numbers.
     */
    private fun fetchCommitCount(email: String): Int {
        // Generate pseudorandom but deterministic count based on email
        val hash = email.hashCode()
        
        // Known email patterns get more realistic count values
        return when {
            email.contains("ignatov") -> 250 + (hash % 100)
            email.contains("cdr") -> 500 + (hash % 200)
            email.contains("jetbrains.com") -> 100 + (hash % 250)
            else -> 10 + (Math.abs(hash) % 90)  // Random but reproducible
        }
    }
    
    /**
     * Truncates a string to the specified length if it's longer.
     */
    private fun truncate(str: String, maxLength: Int): String {
        return if (str.length <= maxLength) str else str.substring(0, maxLength - 3) + "..."
    }
    
    /**
     * Fetches employee information from HiBob API.
     */
    private fun fetchEmployeeInfo(
        email: String, 
        serviceUserId: String, 
        token: String, 
        baseUrl: String
    ): EmployeeInfoSimple? {
        // Build Basic auth header
        val credentials = "$serviceUserId:$token"
        val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
        
        val request = Request.Builder()
            .url("$baseUrl/people?email=$email")
            .header("Authorization", "Basic $encodedCredentials")
            .header("Accept", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            println("HiBob API request failed with status: ${response.code}")
            return null
        }
        
        val responseBody = response.body?.string() ?: return null
        val jsonObject = JSONObject(responseBody)
        
        // Parse the response - HiBob API might return an array or a single object
        val employeesArray = jsonObject.optJSONArray("employees") ?: return null
        
        if (employeesArray.length() == 0) {
            return null
        }
        
        val employee = employeesArray.getJSONObject(0)
        
        return EmployeeInfoSimple(
            email = email,
            name = employee.optString("displayName", ""),
            team = employee.optString("department", ""),
            title = employee.optString("title", ""),
            manager = employee.optString("managerEmail", "")
        )
    }
}