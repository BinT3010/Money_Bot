package com.moneyset.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyset.app.BuildConfig
import com.moneyset.app.data.auth.AuthRepository
import com.moneyset.app.domain.model.UserSession
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.components.Mascot
import com.moneyset.app.ui.components.WhiteCard
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.RoseRed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val session: StateFlow<UserSession?> =
        authRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sau khi xóa phiên, AppRoot tự chuyển về màn đăng nhập. */
    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}

@Composable
fun ProfileScreen(onChangePassword: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Mascot(Modifier.size(130.dp))
        Text(session?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text(session?.email.orEmpty(), color = InkSoft)
        WhiteCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("Phiên bản", BuildConfig.VERSION_NAME)
            InfoRow("Nguồn dữ liệu", if (BuildConfig.USE_MOCK_API) "Dữ liệu mẫu (mock)" else "API")
            if (!BuildConfig.USE_MOCK_API) InfoRow("Máy chủ", BuildConfig.BASE_URL)
        }
        WhiteCard(contentPadding = PaddingValues(0.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onChangePassword).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Key, null, tint = Indigo)
                Spacer(Modifier.width(12.dp))
                Text("Đổi mật khẩu", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = InkSoft)
            }
        }
        GradientButton(
            text = "Đăng xuất",
            onClick = { confirmLogout = true },
            colors = AppGradients.Rose,
            icon = Icons.AutoMirrored.Rounded.Logout,
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = Color.White,
            title = { Text("Đăng xuất?") },
            text = { Text("Bạn sẽ cần đăng nhập lại để tiếp tục dùng Money Set.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                }) { Text("Đăng xuất", color = RoseRed) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, color = InkSoft, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}
