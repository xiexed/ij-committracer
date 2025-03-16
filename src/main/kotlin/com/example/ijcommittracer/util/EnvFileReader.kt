package com.example.ijcommittracer.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Properties

/**
 * Utility class for reading environment variables from .env file.
 * This allows developers to store API tokens in a local file rather than 
 * having to enter them in the UI each time.
 */
class EnvFileReader(private val project: Project) {
    private val LOG = logger<EnvFileReader>()
    private val envProperties = Properties()
    private var initialized = false
    
    companion object {
        private val instances = mutableMapOf<String, EnvFileReader>()
        
        /**
         * Gets an instance of EnvFileReader for the given project.
         * 
         * @param project The IntelliJ project
         * @return An EnvFileReader instance for the project
         */
        fun getInstance(project: Project): EnvFileReader {
            val projectPath = project.basePath ?: ""
            return instances.getOrPut(projectPath) {
                EnvFileReader(project).apply { initialize() }
            }
        }
    }
    
    /**
     * Loads properties from .env file in the project root directory.
     */
    private fun initialize() {
        if (initialized) return
        
        try {
            val projectPath = project.basePath
            if (projectPath == null) {
                LOG.warn("Project base path is null, cannot load .env file")
                return
            }
            
            val envFile = File(projectPath, ".env")
            LOG.info("Looking for .env file at: ${envFile.absolutePath}")
            
            if (envFile.exists()) {
                envFile.inputStream().use {
                    envProperties.load(it)
                }
                LOG.info("Successfully loaded .env file with ${envProperties.size} properties")
                
                // Log the keys (but not the values) for debugging
                if (envProperties.isNotEmpty()) {
                    LOG.info("Found properties: ${envProperties.keys.joinToString(", ")}")
                }
            } else {
                LOG.info("No .env file found at ${envFile.absolutePath}, will use credential store")
                // Try to create a sample .env file for the user
                try {
                    val sampleEnvFile = File(projectPath, ".env.sample")
                    if (!sampleEnvFile.exists()) {
                        sampleEnvFile.writeText("""
                            # Sample .env file for Commit Tracer
                            # Copy this file to .env and fill in your API tokens
                            
                            # YouTrack API Configuration
                            YOUTRACK_API_TOKEN=your_youtrack_token_here
                            YOUTRACK_API_URL=https://youtrack.jetbrains.com/api
                            
                            # HiBob API Configuration
                            HIBOB_API_TOKEN=your_hibob_token_here
                            HIBOB_API_URL=https://api.hibob.com/v1
                        """.trimIndent())
                        LOG.info("Created sample .env file at ${sampleEnvFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    LOG.debug("Failed to create sample .env file", e)
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load .env file", e)
        } finally {
            initialized = true
        }
    }
    
    /**
     * Gets a property from the .env file.
     * 
     * @param key The property key to look up
     * @return The property value or null if not found
     */
    fun getProperty(key: String): String? {
        if (!initialized) {
            initialize()
        }
        
        return envProperties.getProperty(key)
    }
    
    /**
     * Gets a property from the .env file with a default value.
     * 
     * @param key The property key to look up
     * @param defaultValue The default value to return if the property is not found
     * @return The property value or the default value if not found
     */
    fun getProperty(key: String, defaultValue: String): String {
        return getProperty(key) ?: defaultValue
    }
    
    /**
     * Checks if a property exists in the .env file.
     * 
     * @param key The property key to check
     * @return true if the property exists, false otherwise
     */
    fun hasProperty(key: String): Boolean {
        if (!initialized) {
            initialize()
        }
        
        return envProperties.containsKey(key)
    }
    
    /**
     * Gets a File object representing a path relative to the project root.
     * 
     * @param relativePath The path relative to the project root
     * @return A File object representing the absolute path, or null if project path is unavailable
     */
    fun getFileInProjectRoot(relativePath: String): File? {
        val projectPath = project.basePath ?: return null
        return File(projectPath, relativePath)
    }
}