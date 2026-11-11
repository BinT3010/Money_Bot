package com.moneyset.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyset.app.core.dayMonth
import com.moneyset.app.core.toVnd
import com.moneyset.app.domain.model.Transaction
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.RoseRed

@Composable
fun TransactionRow(transaction: Transaction, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val c = transaction.category
        IconTile(c.icon, listOf(c.color.copy(alpha = 0.75f), c.color), size = 40.dp, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transaction.note,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${transaction.date.dayMonth()} · ${c.shortLabel}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Text("-${transaction.amount.toVnd()}", color = RoseRed, fontWeight = FontWeight.Bold)
    }
}
