package com.example.bookstoreapp.ui.theme.add_book_screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import com.example.bookstoreapp.ui.theme.add_book_screen.data.AddScreenObject
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

// === КОНФИГУРАЦИЯ SUPABASE (ТОЛЬКО ДЛЯ КАРТИНОК) ===
private const val SUPABASE_URL = "https://xiyuxasweqjadpsmhstd.supabase.co"
private const val SUPABASE_ANON_KEY = "sb_publishable_EJXnfbQ5HddIuRXeOzHW6A_Aogof9Bf"
private const val BUCKET_NAME = "book-covers"

@Composable
fun AddBookScreen(
    navData: AddScreenObject? = null,
    onSaved: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(navData?.category ?: "") }
    val title = remember { mutableStateOf(navData?.name ?: "") }
    val description = remember { mutableStateOf(navData?.description ?: "") }
    val price = remember { mutableStateOf(navData?.price ?: "") }

    val selectedImageUri = remember { mutableStateOf<Uri?>(null) }
    // Сохраняем старую ссылку на картинку из БД, если мы в режиме редактирования
    val oldImageUrl = navData?.imageUrl ?: ""

    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val firestore = remember { Firebase.firestore }

    // Клиент Supabase только для Storage
    val supabaseStorage = remember {
        createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
            install(Storage)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri.value = uri
    }

    // Фоновое изображение: новое выбранное ИЛИ старое из БД
    val backgroundImageModel = selectedImageUri.value ?: oldImageUrl.takeIf { it.isNotEmpty() }
    Image(
        painter = rememberAsyncImagePainter(model = backgroundImageModel),
        contentDescription = "BG",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
        alpha = 0.4f
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 50.dp),
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
            text = if (navData == null) "Add new book" else "Edit book",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            fontFamily = FontFamily.Serif
        )
        Spacer(modifier = Modifier.height(15.dp))

        RoundedCornerDropDownMenu(selectedCategory) { selectedCategory = it }
        Spacer(modifier = Modifier.height(15.dp))

        RoundedCornerTextField(text = title.value, label = "Title") { title.value = it }
        Spacer(modifier = Modifier.height(15.dp))

        RoundedCornerTextField(
            singleLine = false, maxLines = 5,
            text = description.value, label = "Description"
        ) { description.value = it }
        Spacer(modifier = Modifier.height(15.dp))

        RoundedCornerTextField(text = price.value, label = "Price") { price.value = it }
        Spacer(modifier = Modifier.height(15.dp))

        LoginButton(text = "Select image") {
            imageLauncher.launch("image/*")
        }
        Spacer(modifier = Modifier.height(10.dp))

        LoginButton(text = if (isLoading) "Saving..." else "Save") {
            coroutineScope.launch {
                isLoading = true
                try {
                    // 1. Определяем итоговый URL картинки
                    val finalImageUrl = if (selectedImageUri.value != null) {
                        // Если выбрали новую - грузим в Supabase
                        uploadImageToSupabase(supabaseStorage, context, selectedImageUri.value!!)
                    } else {
                        // Иначе оставляем старую ссылку
                        oldImageUrl
                    }

                    // 2. Сохраняем данные книги в Firebase Firestore
                    saveBookToFireStore(
                        firestore = firestore,
                        existingKey = navData?.key, // Передаем ключ для обновления
                        imageUrl = finalImageUrl,
                        book = Book(
                            name = title.value,
                            description = description.value,
                            price = price.value,
                            category = selectedCategory
                        ),
                        onSaved = { onSaved() },
                        onError = { e -> println("Ошибка Firestore: ${e.message}") }
                    )
                } catch (e: Exception) {
                    println("Ошибка операции: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }
}

/**
 * Загрузка изображения ТОЛЬКО в Supabase Storage
 */
private suspend fun uploadImageToSupabase(
    supabase: io.github.jan.supabase.SupabaseClient,
    context: Context,
    uri: Uri
): String {
    val mimeType = context.contentResolver.getType(uri)
    val extension = when (mimeType) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "png"
    }

    val fileName = "${UUID.randomUUID()}.$extension"

    val bytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Не удалось прочитать файл")
    }

    supabase.storage[BUCKET_NAME].upload(fileName, bytes)
    return supabase.storage[BUCKET_NAME].publicUrl(fileName)
}

/**
 * Сохранение/Обновление книги в Firebase Firestore
 */
private fun saveBookToFireStore(
    firestore: FirebaseFirestore,
    existingKey: String?,
    imageUrl: String,
    book: Book,
    onSaved: () -> Unit,
    onError: (Exception) -> Unit
) {
    val collection = firestore.collection("books")

    // Если есть ключ - обновляем существующий документ, иначе создаем новый
    val docRef = if (existingKey != null) {
        collection.document(existingKey)
    } else {
        collection.document()
    }

    docRef.set(
        book.copy(
            key = docRef.id, // При создании ID документа должен совпадать с полем key
            imageUrl = imageUrl
        )
    )
        .addOnSuccessListener { onSaved() }
        .addOnFailureListener { onError(it) }
}