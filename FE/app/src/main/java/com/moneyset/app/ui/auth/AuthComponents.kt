package com.moneyset.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyset.app.core.toUserMessage
import com.moneyset.app.domain.model.AuthException
import com.moneyset.app.domain.model.AuthValidator
import com.moneyset.app.domain.model.PasswordStrength
import com.moneyset.app.ui.components.Mascot
import com.moneyset.app.ui.components.WhiteCard
import com.moneyset.app.ui.theme.AmberOrange
import com.moneyset.app.ui.theme.AppBackground
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.IndigoSoft
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.MintGreen
import com.moneyset.app.ui.theme.MintSoft
import com.moneyset.app.ui.theme.RoseRed
import com.moneyset.app.ui.theme.RoseSoft
import com.moneyset.app.ui.theme.TrackColor

/** Thông điệp lỗi hiển thị cho người dùng. */
fun Throwable.authMessage(): String = if (this is AuthException) message.orEmpty() else toUserMessage()

/** Khung chung cho các màn tài khoản: nền gradient, linh vật, thẻ trắng chứa form. */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    mascotSize: Dp = 110.dp,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Brush.linearGradient(AppGradients.Primary)),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = 0.12f), radius = size.width * 0.35f, center = Offset(size.width * 0.05f, size.height * 0.9f))
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * 0.25f, center = Offset(size.width * 0.95f, size.height * 0.1f))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                if (onBack != null) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", tint = Color.White) }
                }
            }
            Mascot(Modifier.size(mascotSize))
            Text("Money Set", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(20.dp))
            WhiteCard(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(22.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(subtitle, color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                content()
            }
            Spacer(Modifier.height(16.dp))
            footer()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    error: String?,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Thanh 3 nấc báo độ mạnh mật khẩu + gợi ý quy tắc. */
@Composable
fun PasswordStrengthMeter(password: String) {
    val strength = AuthValidator.strength(password)
    if (strength == PasswordStrength.EMPTY) {
        Text(
            "Tối thiểu ${AuthValidator.MIN_PASSWORD_LENGTH} ký tự, gồm cả chữ và số",
            color = InkSoft,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    val color = when (strength) {
        PasswordStrength.WEAK -> RoseRed
        PasswordStrength.MEDIUM -> AmberOrange
        else -> MintGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (i < strength.level) color else TrackColor),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text("Độ mạnh: ${strength.label}", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Ô nhập mã OTP dạng các ô vuông riêng lẻ. */
@Composable
fun OtpField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    length: Int = AuthValidator.OTP_LENGTH,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(length)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(length) { i ->
                        val char = value.getOrNull(i)
                        val focused = enabled && i == value.length
                        val borderColor = when {
                            isError -> RoseRed
                            focused -> Indigo
                            char != null -> Indigo.copy(alpha = 0.4f)
                            else -> Color(0xFFE5E7EB)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (char != null) IndigoSoft else Color.White)
                                .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(char?.toString().orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                    }
                }
                // Ô nhập thật được ẩn, các ô vuông phía trên chỉ để hiển thị
                Box(Modifier.alpha(0f).size(1.dp)) { inner() }
            }
        },
    )
}

@Composable
fun AuthBanner(message: String, kind: BannerKind) {
    val (icon, fg, bg) = when (kind) {
        BannerKind.ERROR -> Triple(Icons.Rounded.ErrorOutline, RoseRed, RoseSoft)
        BannerKind.SUCCESS -> Triple(Icons.Rounded.CheckCircle, MintGreen, MintSoft)
        BannerKind.INFO -> Triple(Icons.Rounded.Info, Indigo, IndigoSoft)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

enum class BannerKind { ERROR, SUCCESS, INFO }

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        text,
        color = if (enabled) Indigo else InkSoft,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

/** Dòng "Chưa có tài khoản? Đăng ký" ở cuối màn. */
@Composable
fun AuthFooterLink(question: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(question, color = InkSoft, style = MaterialTheme.typography.bodyMedium)
        TextLink(action, onClick)
    }
}
