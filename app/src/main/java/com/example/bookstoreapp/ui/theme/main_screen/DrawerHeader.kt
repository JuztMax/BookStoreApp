package com.example.bookstoreapp.ui.theme.main_screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.example.bookstoreapp.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookstoreapp.ui.theme.DarkBlue
//import com.google.apps.card.v1.Columns

@Composable
fun DrawerHeader(email: String) {
    Column (
        Modifier.fillMaxWidth()
            .height(240.dp)
            .background(DarkBlue),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        Image(
            modifier = Modifier.size(120.dp),
            painter = painterResource(id = R.drawable.books),
            contentDescription = ""
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Book Store App",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            email,
            color = Color.Gray,
            fontSize = 16.sp
        )
    }
}