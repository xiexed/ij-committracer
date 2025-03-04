package com.example.ijcommittracer

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

/**
 * Bundle for internationalization of the plugin.
 */
class CommitTracerBundle : DynamicBundle(BUNDLE) {
    companion object {
        @NonNls
        private const val BUNDLE = "messages.CommitTracerBundle"
        private val INSTANCE = CommitTracerBundle()

        fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
            return INSTANCE.getMessage(key, *params)
        }
    }
}
