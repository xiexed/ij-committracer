package com.example.ijcommittracer.services

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Service for securely storing API tokens using IntelliJ's credential store.
 * Implements PersistentStateComponent for proper persistence across IDE restarts.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "com.example.ijcommittracer.TokenStorageService",
    storages = [Storage("commitTracerTokens.xml")]
)
class TokenStorageService(private val project: Project) : PersistentStateComponent<TokenStorageService.State> {
    
    companion object {
        private const val YOUTRACK_TOKEN_KEY = "YOUTRACK_API_TOKEN"
        private const val HIBOB_TOKEN_KEY = "HIBOB_API_TOKEN"
        private const val HIBOB_BASE_URL_KEY = "HIBOB_API_BASE_URL"
        
        @JvmStatic
        fun getInstance(project: Project): TokenStorageService = project.service()
    }
    
    private var myState = State()
    
    // State class to persist non-sensitive settings
    data class State(
        var youTrackBaseUrl: String = "https://youtrack.jetbrains.com/api",
        var hiBobBaseUrl: String = "https://api.hibob.com/v1"
    )
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    // YouTrack token methods
    fun getYouTrackToken(): String? {
        return getStoredToken(YOUTRACK_TOKEN_KEY)
    }
    
    fun setYouTrackToken(token: String) {
        storeToken(YOUTRACK_TOKEN_KEY, token)
    }
    
    fun clearYouTrackToken() {
        clearToken(YOUTRACK_TOKEN_KEY)
    }
    
    fun getYouTrackBaseUrl(): String = myState.youTrackBaseUrl
    
    fun setYouTrackBaseUrl(url: String) {
        myState.youTrackBaseUrl = url
    }
    
    // HiBob token methods
    fun getHiBobToken(): String? {
        return getStoredToken(HIBOB_TOKEN_KEY)
    }
    
    fun setHiBobToken(token: String) {
        storeToken(HIBOB_TOKEN_KEY, token)
    }
    
    fun clearHiBobToken() {
        clearToken(HIBOB_TOKEN_KEY)
    }
    
    fun getHiBobBaseUrl(): String = myState.hiBobBaseUrl
    
    fun setHiBobBaseUrl(url: String) {
        myState.hiBobBaseUrl = url
    }
    
    // Private helper methods for token storage using PasswordSafe
    private fun getStoredToken(key: String): String? {
        val credentialAttributes = createCredentialAttributes(key)
        return PasswordSafe.instance.getPassword(credentialAttributes)
    }
    
    private fun storeToken(key: String, token: String) {
        val credentialAttributes = createCredentialAttributes(key)
        val credentials = Credentials("", token)
        PasswordSafe.instance.set(credentialAttributes, credentials)
    }
    
    private fun clearToken(key: String) {
        val credentialAttributes = createCredentialAttributes(key)
        PasswordSafe.instance.set(credentialAttributes, null)
    }
    
    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("CommitTracer", "$key:${project.locationHash}"),
            "token"
        )
    }
}