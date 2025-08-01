package com.example.mindvault.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mindvault.data.AuthManager

@Composable
fun SignOutSection() {
    val context = LocalContext.current
    Button(
        onClick = {
            AuthManager.signOut()
            Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
            (context as? ComponentActivity)?.finish()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
    }
}
