package com.example.ijcommittracer.playground

/**
 * Data class to store employee information for the CLI tool.
 * This is a simplified version of com.example.ijcommittracer.services.EmployeeInfo
 * that can be used without IntelliJ platform dependencies.
 */
data class EmployeeInfoSimple(
    val email: String,
    val name: String,
    val team: String,
    val title: String,
    val manager: String
)