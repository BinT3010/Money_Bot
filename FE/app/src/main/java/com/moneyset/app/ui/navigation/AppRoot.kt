package com.moneyset.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyset.app.ui.auth.AuthNavHost
import com.moneyset.app.ui.auth.SessionState
import com.moneyset.app.ui.auth.SessionViewModel
import com.moneyset.app.ui.components.Mascot
import com.moneyset.app.ui.theme.AppBackground

/**
 * Cổng phiên đăng nhập: chưa đăng nhập -> màn Login, đã đăng nhập -> app chính.
 * Đăng xuất (hoặc token hết hạn) sẽ tự quay về Login, back stack của app chính bị bỏ.
 */
@Composable
fun AppRoot(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val state by sessionViewModel.state.collectAsStateWithLifecycle()
    when (state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            Mascot(Modifier.size(120.dp))
        }
        SessionState.LoggedOut -> AuthNavHost()
        is SessionState.LoggedIn -> MoneySetNavApp()
    }
}
