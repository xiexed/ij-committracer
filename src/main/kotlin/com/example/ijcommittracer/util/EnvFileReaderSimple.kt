package com.example.ijcommittracer.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Simple utility for reading environment variables from .env file without IntelliJ dependencies.
 * This is intended for command-line use outside of the IntelliJ platform.
 */
class EnvFileReaderSimple private constructor(private val envFilePath: String) {
    private val envProperties = Properties()
    private var initialized = false
    private var envFileHash: String? = null
    private val lock = ReentrantReadWriteLock()

    companion object {
        // Use ConcurrentHashMap for thread-safe instance storage
        private val instances = ConcurrentHashMap<String, EnvFileReaderSimple>()

        /**
         * Gets an instance of EnvFileReaderSimple for the given env file path.
         * This method is thread-safe and will always return the same instance
         * for the same file path.
         * 
         * @param envFilePath The absolute path to the .env file
         * @return An EnvFileReaderSimple instance for the file path
         */
        fun getInstance(envFilePath: String): EnvFileReaderSimple {
            // computeIfAbsent is thread-safe and avoids race conditions
            return instances.computeIfAbsent(envFilePath) {
                EnvFileReaderSimple(envFilePath).apply { initialize() }
            }
        }
    }

    /**
     * Loads properties from the specified .env file.
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
                println("Looking for .env file at: ${envFile.absolutePath}")
                
                if (envFile.exists()) {
                    // Calculate the file hash before loading
                    envFileHash = calculateFileHash(envFile)
                    
                    envFile.inputStream().use {
                        envProperties.load(it)
                    }
                    println("Successfully loaded .env file with ${envProperties.size} properties")
                    
                    // Log the keys (but not the values) for debugging
                    if (envProperties.isNotEmpty()) {
                        println("Found properties: ${envProperties.keys.joinToString(", ")}")
                    }
                } else {
                    println("No .env file found at ${envFile.absolutePath}")
                }
            } catch (e: Exception) {
                println("Failed to load .env file: ${e.message}")
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
                envProperties.clear()
                envFileHash = null
                println(".env file no longer exists, cleared properties")
            }
            return true
        }
        
        // Calculate current hash
        val currentHash = calculateFileHash(envFile)
        
        // If hash has changed, reload properties
        if (currentHash != envFileHash) {
            lock.write {
                try {
                    envProperties.clear()
                    envFile.inputStream().use {
                        envProperties.load(it)
                    }
                    envFileHash = currentHash
                    println(".env file has changed, reloaded properties")
                    
                } catch (e: Exception) {
                    println("Failed to reload .env file after change: ${e.message}")
                }
            }
            return true
        }
        
        return false
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
                envProperties.clear()
                if (envFile.exists()) {
                    envFile.inputStream().use {
                        envProperties.load(it)
                    }
                    envFileHash = calculateFileHash(envFile)
                    println("Forced reload of .env file, properties: ${envProperties.size}")
                    return true
                } else {
                    envFileHash = null
                    println("Forced reload attempted but .env file does not exist")
                }
            } catch (e: Exception) {
                println("Failed to force reload .env file: ${e.message}")
            }
        }
        return false
    }
}