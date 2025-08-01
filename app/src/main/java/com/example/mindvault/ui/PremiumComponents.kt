package com.example.mindvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PremiumTab(
    title: String,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFF6C63FF)
            } else {
                Color.Transparent
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}
