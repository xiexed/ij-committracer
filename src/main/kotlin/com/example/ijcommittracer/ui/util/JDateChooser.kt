package com.example.ijcommittracer.ui.util

import java.awt.BorderLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Date picker component (simple implementation).
 */
class JDateChooser(initialDate: Date, private val dateFormat: SimpleDateFormat) : JPanel() {
    private val textField = JTextField(dateFormat.format(initialDate), 10)
    
    var date: Date?
        get() {
            return try {
                dateFormat.parse(textField.text)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value != null) {
                textField.text = dateFormat.format(value)
            }
        }
    
    init {
        layout = BorderLayout()
        add(textField, BorderLayout.CENTER)
    }
}