package com.example.ijcommittracer.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.EventListener
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Interface for listeners that want to be notified of environment variable changes.
 */
interface EnvChangeListener : EventListener {
    /**
     * Called when the environment variables change.
     * @param changedKeys Set of keys that have changed, or null if all properties should be considered changed
     */
    fun onEnvChanged(changedKeys: Set<String>? = null)
}

/**
 * Utility class for reading environment variables from .env file.
 * This allows developers to store API tokens in a local file rather than 
 * having to enter them in the UI each time.
 * 
 * This implementation is thread-safe, with proper synchronization for
 * both instance creation and property access.
 */
class EnvFileReader private constructor(private val project: Project?, private val envFilePath: String) {
    private val LOG = logger<EnvFileReader>()
    private val envProperties = Properties()
    private var initialized = false
    private var envFileHash: String? = null
    private val lock = ReentrantReadWriteLock()
    
    companion object {
        // Use ConcurrentHashMap for thread-safe instance storage
        private val instances = ConcurrentHashMap<String, EnvFileReader>()
        
        /**
         * Topic for publishing environment change events
         */
        val ENV_CHANGED_TOPIC = Topic.create("EnvFileChanged", EnvChangeListener::class.java)
        
        /**
         * Gets an instance of EnvFileReader for the given project.
         * This method is thread-safe and will always return the same instance
         * for the same project.
         * 
         * @param project The IntelliJ project
         * @return An EnvFileReader instance for the project
         */
        fun getInstance(project: Project): EnvFileReader {
            val projectPath = project.basePath ?: ""
            val envFilePath = "$projectPath/.env"
            // computeIfAbsent is thread-safe and avoids race conditions
            return instances.computeIfAbsent(envFilePath) {
                EnvFileReader(project, envFilePath).apply { initialize() }
            }
        }
        
        /**
         * Gets an instance of EnvFileReader for the given env file path.
         * This method is thread-safe and will always return the same instance
         * for the same file path.
         * 
         * @param envFilePath The absolute path to the .env file
         * @return An EnvFileReader instance for the file path
         */
        fun getInstance(envFilePath: String): EnvFileReader {
            // computeIfAbsent is thread-safe and avoids race conditions
            return instances.computeIfAbsent(envFilePath) {
                EnvFileReader(null, envFilePath).apply { initialize() }
            }
        }
    }
    
    /**
     * Loads properties from .env file in the project root directory or from specified path.
     * This method is synchronized to prevent multiple threads from 
     * initializing the properties simultaneously.
     */
    private fun initialize() {
        // Fast check to avoid acquiring the lock unnecessarily
        if (initialized) return
        
        // Acquire write lock for initialization
        lock.write {
            // Double-check inside the lock
            if (initialized) return
            
            try {
                val envFile = File(envFilePath)
                LOG.info("Looking for .env file at: ${envFile.absolutePath}")
                
                if (envFile.exists()) {
                    // Calculate the file hash before loading
                    envFileHash = calculateFileHash(envFile)
                    
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
                    
                    // Only create sample file if we have a project
                    if (project != null) {
                        val projectPath = project.basePath
                        if (projectPath != null) {
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
                                        HIBOB_SERVICE_USER_ID=your_service_user_id_here
                                        HIBOB_API_TOKEN=your_hibob_token_here
                                        HIBOB_API_URL=https://api.hibob.com/v1
                                    """.trimIndent())
                                    LOG.info("Created sample .env file at ${sampleEnvFile.absolutePath}")
                                }
                            } catch (e: Exception) {
                                LOG.debug("Failed to create sample .env file", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to load .env file", e)
            } finally {
                initialized = true
            }
        }
    }
    
    /**
     * Calculates a hash of the given file's contents.
     * 
     * @param file The file to hash
     * @return A hexadecimal string representation of the file's hash
     */
    private fun calculateFileHash(file: File): String {
        val bytes = Files.readAllBytes(Paths.get(file.toURI()))
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Gets a property from the .env file.
     * Thread-safe implementation that ensures proper initialization.
     * 
     * @param key The property key to look up
     * @return The property value or null if not found
     */
    fun getProperty(key: String): String? {
        // Initialize if needed (will acquire write lock internally)
        if (!initialized) {
            initialize()
        } else {
            // Check if the file has changed
            checkAndReloadIfChanged()
        }
        
        // Use read lock for property access
        return lock.read {
            envProperties.getProperty(key)
        }
    }
    
    /**
     * Gets a property from the .env file with a default value.
     * Thread-safe implementation.
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
     * Thread-safe implementation that ensures proper initialization.
     * 
     * @param key The property key to check
     * @return true if the property exists, false otherwise
     */
    fun hasProperty(key: String): Boolean {
        // Initialize if needed (will acquire write lock internally)
        if (!initialized) {
            initialize()
        } else {
            // Check if the file has changed
            checkAndReloadIfChanged()
        }
        
        // Use read lock for property access
        return lock.read {
            envProperties.containsKey(key)
        }
    }
    
    /**
     * Checks if the .env file has changed and reloads it if necessary.
     */
    private fun checkAndReloadIfChanged(): Boolean {
        // Fast check - if we don't have a hash, we don't have a file
        if (envFileHash == null) return false
        
        val envFile = File(envFilePath)
        
        // If file doesn't exist anymore, clear properties
        if (!envFile.exists()) {
            lock.write {
                val oldKeys = envProperties.keys.map { it.toString() }.toSet()
                envProperties.clear()
                envFileHash = null
                LOG.info(".env file no longer exists, cleared properties")
                
                // Notify listeners that all properties are gone
                if (oldKeys.isNotEmpty() && project != null) {
                    notifyListeners(oldKeys)
                }
            }
            return true
        }
        
        // Calculate current hash
        val currentHash = calculateFileHash(envFile)
        
        // If hash has changed, reload properties
        if (currentHash != envFileHash) {
            lock.write {
                try {
                    // Save old properties to detect changes
                    val oldProperties = Properties()
                    oldProperties.putAll(envProperties)
                    
                    envProperties.clear()
                    envFile.inputStream().use {
                        envProperties.load(it)
                    }
                    envFileHash = currentHash
                    LOG.info(".env file has changed, reloaded properties")
                    
                    // Find changed keys
                    val changedKeys = findChangedKeys(oldProperties, envProperties)
                    
                    // Notify listeners if there are changes
                    if (changedKeys.isNotEmpty()) {
                        notifyListeners(changedKeys)
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to reload .env file after change", e)
                }
            }
            return true
        }
        
        return false
    }
    
    /**
     * Find keys that have been added, removed, or changed between old and new properties.
     */
    private fun findChangedKeys(oldProps: Properties, newProps: Properties): Set<String> {
        val changedKeys = mutableSetOf<String>()
        
        // Find added or changed keys
        for (key in newProps.keys) {
            val strKey = key.toString()
            if (!oldProps.containsKey(key) || oldProps.getProperty(strKey) != newProps.getProperty(strKey)) {
                changedKeys.add(strKey)
            }
        }
        
        // Find removed keys
        for (key in oldProps.keys) {
            val strKey = key.toString()
            if (!newProps.containsKey(key)) {
                changedKeys.add(strKey)
            }
        }
        
        return changedKeys
    }
    
    /**
     * Notify listeners of changed keys.
     */
    private fun notifyListeners(changedKeys: Set<String>) {
        try {
            if (project != null) {
                LOG.info("Notifying listeners about changed environment variables: ${changedKeys.joinToString(", ")}")
                project.messageBus.syncPublisher(ENV_CHANGED_TOPIC).onEnvChanged(changedKeys)
            }
        } catch (e: Exception) {
            LOG.warn("Failed to notify listeners about env changes", e)
        }
    }
    
    /**
     * Gets a File object representing a path relative to the project root.
     * This method is thread-safe as it only reads the project path.
     * 
     * @param relativePath The path relative to the project root
     * @return A File object representing the absolute path, or null if project path is unavailable
     */
    fun getFileInProjectRoot(relativePath: String): File? {
        if (project == null) return null
        val projectPath = project.basePath ?: return null
        return File(projectPath, relativePath)
    }
    
    /**
     * Forces a reload of the .env file.
     * This can be called manually when you want to ensure the latest values are loaded.
     * 
     * @return true if the file was reloaded, false otherwise
     */
    fun forceReload(): Boolean {
        val envFile = File(envFilePath)
        
        lock.write {
            try {
                // Save old properties to detect changes
                val oldProperties = Properties()
                oldProperties.putAll(envProperties)
                
                envProperties.clear()
                if (envFile.exists()) {
                    envFile.inputStream().use {
                        envProperties.load(it)
                    }
                    envFileHash = calculateFileHash(envFile)
                    LOG.info("Forced reload of .env file, properties: ${envProperties.size}")
                    
                    // Find changed keys
                    val changedKeys = findChangedKeys(oldProperties, envProperties)
                    
                    // Notify listeners if there are changes
                    if (changedKeys.isNotEmpty() && project != null) {
                        notifyListeners(changedKeys)
                        return true
                    }
                } else {
                    // Only notify if we previously had properties
                    val hadProperties = !oldProperties.isEmpty
                    
                    envFileHash = null
                    LOG.info("Forced reload attempted but .env file does not exist")
                    
                    if (hadProperties && project != null) {
                        notifyListeners(oldProperties.keys.map { it.toString() }.toSet())
                        return true
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to force reload .env file", e)
            }
        }
        return false
    }
}