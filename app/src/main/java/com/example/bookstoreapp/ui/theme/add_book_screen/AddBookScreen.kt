package com.example.bookstoreapp.ui.theme.add_book_screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.bookstoreapp.R
import com.example.bookstoreapp.data.Book
import com.example.bookstoreapp.ui.theme.login.LoginButton
import com.example.bookstoreapp.ui.theme.login.RoundedCornerTextField
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// === КОНФИГУРАЦИЯ SUPABASE ===
private const val SUPABASE_URL = "https://xiyuxasweqjadpsmhstd.supabase.co"
private const val SUPABASE_ANON_KEY = "sb_publishable_EJXnfbQ5HddIuRXeOzHW6A_Aogof9Bf"
private const val BUCKET_NAME = "book-covers"

@Composable
fun AddBookScreen(
    onSaved: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf("Bestsellers") }
    val title = remember { mutableStateOf("") }
    val description = remember { mutableStateOf("") }
    val price = remember { mutableStateOf("") }
    val selectedImageUri = remember { mutableStateOf<Uri?>(null) }

    // Состояние загрузки и контекст
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val firestore = remember { Firebase.firestore }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri.value = uri
    }

    // Фоновое изображение
    Image(
        painter = rememberAsyncImagePainter(model = selectedImageUri.value),
        contentDescription = "BG",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
        alpha = 0.4f
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 50.dp, end = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.books),
            contentDescription = "Logo",
            modifier = Modifier.size(90.dp)
        )
        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = "Add new book",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            fontFamily = FontFamily.Serif
        )
        Spacer(modifier = Modifier.height(15.dp))

        // === ВОЗВРАЩАЕМ ВЫБОР КАТЕГОРИИ ===
        RoundedCornerDropDownMenu { selectedItem ->
            selectedCategory = selectedItem
        }

        Spacer(modifier = Modifier.height(15.dp))

        RoundedCornerTextField(text = title.value, label = "Title") { title.value = it }
        Spacer(modifier = Modifier.height(15.dp))
        RoundedCornerTextField(
            singleLine = false, maxLines = 5, text = description.value, label = "Description"
        ) { description.value = it }
        Spacer(modifier = Modifier.height(15.dp))
        RoundedCornerTextField(text = price.value, label = "Price") { price.value = it }
        Spacer(modifier = Modifier.height(15.dp))

        LoginButton(text = "Select image") {
            imageLauncher.launch("image/*")
        }

        Spacer(modifier = Modifier.height(10.dp))

        LoginButton(text = if (isLoading) "Saving..." else "Save") {
            selectedImageUri.value?.let { uri ->
                coroutineScope.launch {
                    isLoading = true
                    try {
                        // 1. Загружаем картинку в Supabase
                        val imageUrl = uploadImageToSupabase(context, uri)

                        // 2. Сохраняем книгу в Firestore
                        saveBookToFireStore(
                            firestore = firestore,
                            url = imageUrl,
                            book = Book(
                                name = title.value,
                                description = description.value,
                                price = price.value,
                                category = selectedCategory
                            ),
                            onSaved = {
                                onSaved()
                            },
                            onError = { e ->
                                println("Ошибка сохранения в БД: ${e.message}")
                            }
                        )
                    } catch (e: Exception) {
                        println("Ошибка загрузки картинки: ${e.message}")
                    } finally {
                        isLoading = false
                    }
                }
            } ?: run {
                println("Пожалуйста, выберите изображение!")
            }
        }
    }
}

/**
 * Загрузка изображения в Supabase Storage
 */
private suspend fun uploadImageToSupabase(context: Context, uri: Uri): String {
    val supabase = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Storage)
    }

    val mimeType = context.contentResolver.getType(uri)
    val extension = when (mimeType) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }

    val fileName = "${UUID.randomUUID()}.$extension"
    // ================================================

    val bytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Не удалось прочитать файл")
    }

    supabase.storage[BUCKET_NAME].upload(fileName, bytes)

    return supabase.storage[BUCKET_NAME].publicUrl(fileName)
}

/**
 * Сохранение данных книги в Firestore
 */
private fun saveBookToFireStore(
    firestore: FirebaseFirestore,
    url: String,
    book: Book,
    onSaved: () -> Unit,
    onError: (Exception) -> Unit
) {
    val db = firestore.collection("books")
    val key = db.document().id
    db.document(key)
        .set(
            book.copy(
                key = key,
                imageUrl = url
            )
        )
        .addOnSuccessListener { onSaved() }
        .addOnFailureListener { onError(it) }
}