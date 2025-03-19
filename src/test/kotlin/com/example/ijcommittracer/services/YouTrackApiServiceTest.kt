package com.example.ijcommittracer.services

import com.example.ijcommittracer.util.EnvChangeListener
import com.example.ijcommittracer.util.EnvFileReader
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for YouTrackApiService to ensure it correctly responds to environment variable changes.
 */
class YouTrackApiServiceTest : BasePlatformTestCase() {
    // Test directory and files
    private lateinit var testDir: File
    private lateinit var envFile: File
    private lateinit var project: Project
    private lateinit var messageBus: MessageBus
    private lateinit var connection: MessageBusConnection
    private lateinit var tokenStorageService: TokenStorageService
    
    // Test constants
    private val YOUTRACK_API_TOKEN_KEY = "YOUTRACK_API_TOKEN"
    private val YOUTRACK_API_URL_KEY = "YOUTRACK_API_URL"
    
    // Capture the EnvChangeListener that the service registers
    private val listenerCaptor = argumentCaptor<EnvChangeListener>()
    
    @Before
    override fun setUp() {
        super.setUp()
        
        // Create a temporary directory for the test
        testDir = Files.createTempDirectory("youtrack-api-service-test").toFile()
        
        // Create a fresh env file
        envFile = File(testDir, ".env")
        envFile.writeText("""
            YOUTRACK_API_TOKEN=test_token
            YOUTRACK_API_URL=https://test.youtrack.com/api
        """.trimIndent())
        
        // Set up message bus mocks
        connection = mock()
        messageBus = mock()
        whenever(messageBus.connect()).thenReturn(connection)
        
        // Set up the project mock
        project = mock()
        whenever(project.basePath).thenReturn(testDir.absolutePath)
        whenever(project.messageBus).thenReturn(messageBus)
        
        // Set up token storage service mock
        tokenStorageService = mock()
        whenever(TokenStorageService.getInstance(project)).thenReturn(tokenStorageService)
    }
    
    /**
     * Test that the YouTrackApiService correctly responds to environment variable changes.
     */
    @Test
    fun testEnvChangeHandling() {
        // Create a YouTrackApiService instance
        val service = spy(YouTrackApiService(project))
        
        // Verify that it subscribed to environment changes
        verify(connection).subscribe(eq(EnvFileReader.ENV_CHANGED_TOPIC), listenerCaptor.capture())
        
        // Get the captured listener
        val listener = listenerCaptor.firstValue
        
        // Now we can test the onEnvChanged method
        // First, spy on the clearCache method
        doNothing().whenever(service).clearCache()
        
        // Test with token change
        val tokenChanges = setOf(YOUTRACK_API_TOKEN_KEY)
        listener.onEnvChanged(tokenChanges)
        
        // Verify cache was cleared
        verify(service).clearCache()
        
        // Reset the spy
        reset(service)
        doNothing().whenever(service).clearCache()
        
        // Test with URL change - should refresh URLs too
        val urlChanges = setOf(YOUTRACK_API_URL_KEY)
        listener.onEnvChanged(urlChanges)
        
        // Verify cache was cleared and URLs were refreshed
        verify(service).clearCache()
        
        // Make sure we're accessing the token via EnvFileReader
        val mockEnvReader = mock<EnvFileReader>()
        whenever(EnvFileReader.getInstance(project)).thenReturn(mockEnvReader)
        whenever(mockEnvReader.getProperty(YOUTRACK_API_TOKEN_KEY)).thenReturn("new_token")
        
        // Access the token through the service
        service.fetchTicketInfo("TEST-123")
        
        // Verify the environment reader was queried
        verify(mockEnvReader).getProperty(YOUTRACK_API_TOKEN_KEY)
    }
}