package com.example.ijcommittracer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ijcommittracer.ui.theme.IntelliJColors

/**
 * A styled OutlinedTextField that looks similar to IntelliJ IDEA's text field
 */
@Composable
fun IdeaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = IntelliJColors.Border,
            unfocusedContainerColor = IntelliJColors.TextFieldBg,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

/**
 * A styled Button that looks similar to IntelliJ IDEA's button
 */
@Composable
fun IdeaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        content = content
    )
}

/**
 * A styled TabRow that looks similar to IntelliJ IDEA's tabs
 */
@Composable
fun IdeaTabs(
    selectedTabIndex: Int,
    tabs: @Composable () -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {
            Divider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        },
        tabs = tabs
    )
}

/**
 * A styled Tab that looks similar to IntelliJ IDEA's tab
 */
@Composable
fun IdeaTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: String
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            )
        }
    )
}

/**
 * A stylized Card that resembles IntelliJ IDEA panels
 */
@Composable
fun IdeaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(4.dp),
        content = content
    )
}

/**
 * A selectable list with styling similar to IntelliJ IDEA lists
 */
@Composable
fun <T> IdeaSelectableList(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    itemContent: @Composable (T, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState
    ) {
        items(items) { item ->
            val isSelected = item == selectedItem
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .selectable(
                        selected = isSelected,
                        onClick = { onItemSelected(item) },
                        role = Role.RadioButton
                    )
            ) {
                itemContent(item, isSelected)
            }
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    }
}

/**
 * Header for tables and lists, with IntelliJ styling
 */
@Composable
fun TableHeader(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * A standard header text for tables
 */
@Composable
fun HeaderText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold
        ),
        modifier = modifier
    )
}

/**
 * Label with value styled to match IntelliJ UI
 */
@Composable
fun LabelWithValue(
    label: String,
    value: String,
    labelWidth: Int = 100
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.width(labelWidth.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Ellipsis
        )
    }
}