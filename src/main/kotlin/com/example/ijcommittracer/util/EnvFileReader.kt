package com.example.ijcommittracer.util

import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.util.Properties

/**
 * Utility class for reading environment variables from .env file.
 * This allows developers to store API tokens in a local file rather than 
 * having to enter them in the UI each time.
 */
object EnvFileReader {
    private val LOG = logger<EnvFileReader>()
    private val envProperties = Properties()
    private var initialized = false
    
    /**
     * Loads properties from .env file in the project root directory.
     * Called once when first property is accessed.
     */
    private fun initialize() {
        if (initialized) return
        
        try {
            val envFile = File(".env")
            if (envFile.exists()) {
                envFile.inputStream().use {
                    envProperties.load(it)
                }
                LOG.info("Successfully loaded .env file")
            } else {
                LOG.info("No .env file found, will use credential store")
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
}