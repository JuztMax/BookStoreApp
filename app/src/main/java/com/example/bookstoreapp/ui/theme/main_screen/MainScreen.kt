package com.example.bookstoreapp.ui.theme.main_screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bookstoreapp.data.Book
import com.example.bookstoreapp.ui.theme.login.data.MainScreenDataObject
import com.example.bookstoreapp.ui.theme.main_screen.bottom_menu.BottomMenu
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    navData: MainScreenDataObject,
    onAdminClick: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val booksListState = remember {
        mutableStateOf(emptyList<Book>())
    }

    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        getAllBooks(db) { books ->
            booksListState.value = books
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(),
        drawerContent = {
            Column(
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                DrawerHeader(navData.email)
                DrawerBody{
                    coroutineScope.launch {
                        drawerState.close()
                    }
                    onAdminClick()
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomMenu()
            }
        ) { paddingValues ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(booksListState.value) { book ->
                    BookListItemUi(book)
                }
            }
        }
    }
}

private fun getAllBooks(
    db: FirebaseFirestore,
    onBooks: (List<Book>) -> Unit
){
    db.collection("books")
        .get()
        .addOnSuccessListener { task ->
            val booksList = task.toObjects(Book::class.java)
            onBooks(booksList)
        }
        .addOnFailureListener {

        }
}