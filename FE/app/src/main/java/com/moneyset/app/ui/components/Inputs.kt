package com.moneyset.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.InkSoft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Hiển thị "1234567" thành "1.234.567" khi gõ, giá trị thật vẫn là chuỗi số. */
object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val hasSeparatorAfter = { i: Int -> val rest = digits.length - i - 1; rest > 0 && rest % 3 == 0 }
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                append(c)
                if (hasSeparatorAfter(i)) append('.')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, digits.length)
                var separators = 0
                for (i in 0 until o - 1) if (hasSeparatorAfter(i)) separators++
                return o + separators
            }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset.coerceIn(0, formatted.length)).count { it != '.' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@Composable
fun MoneyTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        suffix = { Text("đ") },
        visualTransformation = ThousandsSeparatorTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Hộp thoại nhập một số tiền (ngân sách tháng, hạn mức...). */
@Composable
fun AmountDialog(
    title: String,
    label: String,
    initial: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    description: String? = null,
) {
    var value by remember { mutableStateOf(if (initial > 0) initial.toString() else "") }
    val amount = value.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                description?.let { Text(it, color = InkSoft) }
                MoneyTextField(value, { value = it }, label = label)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(amount) }, enabled = amount > 0) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Chọn") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    ) {
        DatePicker(state = state)
    }
}

/** Trả về hàm mở nhận dạng giọng nói tiếng Việt của hệ thống. */
@Composable
fun rememberSpeechInput(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { currentOnResult(it) }
    }
    return remember(launcher) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói khoản chi của bạn, ví dụ: ăn trưa 45 nghìn")
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Thiết bị không hỗ trợ nhập bằng giọng nói", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Nút camera: chụp ảnh hóa đơn hoặc chọn từ thư viện. */
@Composable
fun ReceiptCaptureButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBitmap: (Bitmap) -> Unit,
    onUri: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalContext.current
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let(onBitmap)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onUri)
    }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier) {
        Box(
            Modifier
                .size(size)
                .shadow(8.dp, shape, spotColor = AppGradients.Camera.last())
                .clip(shape)
                .background(Brush.linearGradient(AppGradients.Camera))
                .clickable { onExpandedChange(true) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PhotoCamera, "Quét hóa đơn", tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Chụp ảnh hóa đơn") },
                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                onClick = {
                    onExpandedChange(false)
                    try {
                        takePicture.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Không tìm thấy ứng dụng camera", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Chọn từ thư viện") },
                leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                onClick = {
                    onExpandedChange(false)
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

/** Hiện snackbar khi ViewModel phát ra thông báo, rồi báo lại là đã hiển thị. */
@Composable
fun MessageEffect(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message)
            currentOnShown()
        }
    }
}
