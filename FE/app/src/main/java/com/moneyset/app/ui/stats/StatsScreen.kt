package com.moneyset.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyset.app.core.label
import com.moneyset.app.core.toVnd
import com.moneyset.app.data.repository.FinanceRepository
import com.moneyset.app.domain.model.CategorySpending
import com.moneyset.app.domain.model.MonthlyOverview
import com.moneyset.app.domain.model.Transaction
import com.moneyset.app.ui.components.GradientProgressBar
import com.moneyset.app.ui.components.ScreenTopBar
import com.moneyset.app.ui.components.SectionTitle
import com.moneyset.app.ui.components.TransactionRow
import com.moneyset.app.ui.components.WhiteCard
import com.moneyset.app.ui.components.color
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatsUiState(
    val overview: MonthlyOverview? = null,
    val spending: List<CategorySpending> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(repository: FinanceRepository) : ViewModel() {
    val state: StateFlow<StatsUiState> =
        combine(repository.overview, repository.categorySpending, repository.transactions) { o, s, t ->
            StatsUiState(o, s.filter { it.spent > 0 }.sortedByDescending { it.spent }, t)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }
}

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maxSpent = state.spending.maxOfOrNull { it.spent } ?: 1L

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTopBar(
                overline = "Thống kê",
                title = state.overview?.month?.label() ?: "",
                onBack = null,
            )
        }
        item {
            WhiteCard(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Chi theo danh mục", style = MaterialTheme.typography.titleMedium)
                state.spending.forEach { s ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.category.label, color = Ink, modifier = Modifier.weight(1f))
                            Text(s.spent.toVnd(), color = Ink, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        GradientProgressBar(
                            s.spent.toFloat() / maxSpent,
                            listOf(s.category.color.copy(alpha = 0.6f), s.category.color),
                            height = 8.dp,
                        )
                    }
                }
                if (state.spending.isEmpty()) Text("Chưa có chi tiêu tháng này", color = InkSoft)
            }
        }
        item { SectionTitle("Giao dịch gần đây", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        items(state.transactions.take(30)) { TransactionRow(it, Modifier.padding(horizontal = 20.dp)) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
