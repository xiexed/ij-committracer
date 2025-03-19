package com.example.ijcommittracer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ijcommittracer.CommitTracerBundle
import java.text.SimpleDateFormat
import java.util.*

/**
 * Composable for date range selection
 */
@Composable
fun DateFilterPanel(
    fromDate: Date,
    toDate: Date,
    onFilterApplied: (Date, Date) -> Unit
) {
    var localFromDate by remember { mutableStateOf(fromDate) }
    var localToDate by remember { mutableStateOf(toDate) }
    val displayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(CommitTracerBundle.message("dialog.filter.from"))
        
        // Using a text field for date input (in a real app, we'd use a proper date picker)
        OutlinedTextField(
            value = displayDateFormat.format(localFromDate),
            onValueChange = { 
                try {
                    val parsedDate = displayDateFormat.parse(it)
                    if (parsedDate != null) {
                        localFromDate = parsedDate
                    }
                } catch (e: Exception) {
                    // Keep existing date if parsing fails
                }
            },
            modifier = Modifier.width(120.dp),
            singleLine = true
        )
        
        Text(CommitTracerBundle.message("dialog.filter.to"))
        
        OutlinedTextField(
            value = displayDateFormat.format(localToDate),
            onValueChange = { 
                try {
                    val parsedDate = displayDateFormat.parse(it)
                    if (parsedDate != null) {
                        localToDate = parsedDate
                    }
                } catch (e: Exception) {
                    // Keep existing date if parsing fails
                }
            },
            modifier = Modifier.width(120.dp),
            singleLine = true
        )
        
        Button(
            onClick = { onFilterApplied(localFromDate, localToDate) }
        ) {
            Text(CommitTracerBundle.message("dialog.filter.apply"))
        }
    }
}