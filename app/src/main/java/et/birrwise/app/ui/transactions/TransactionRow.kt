package et.birrwise.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import et.birrwise.app.R
import et.birrwise.app.domain.MoneyFormatter
import et.birrwise.app.domain.TransactionListItem
import et.birrwise.app.domain.TransactionStatus
import et.birrwise.app.domain.TransactionType
import et.birrwise.app.ui.theme.LocalFinanceColors
import java.time.ZoneId

/**
 * One transaction in a list. Income and expense are told apart by an icon, a "+" or "−" sign, and
 * a text label, never by color alone.
 */
@Composable
fun TransactionRow(
    item: TransactionListItem,
    showAccount: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transaction = item.transaction
    val type = transaction.type
    val date = transaction.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate()

    val details = buildList {
        add(stringResource(type.labelRes()))
        add(date.toString())
        if (showAccount && item.accountName.isNotEmpty()) add(item.accountName)
        item.categoryName?.let { add(it) }
    }.joinToString(" \u00B7 ")

    val amountColor = when (type) {
        TransactionType.INCOME, TransactionType.REFUND, TransactionType.TRANSFER_IN ->
            LocalFinanceColors.current.income
        TransactionType.EXPENSE, TransactionType.FEE, TransactionType.TRANSFER_OUT ->
            LocalFinanceColors.current.expense
        TransactionType.REVERSAL -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Icon(
                    imageVector = type.icon(),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = transaction.description.ifEmpty { stringResource(type.labelRes()) },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (transaction.status == TransactionStatus.PENDING_REVIEW) {
                    Text(
                        text = stringResource(R.string.needs_review),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = type.signSymbol() + MoneyFormatter.format(transaction.amount),
                style = MaterialTheme.typography.titleSmall,
                color = amountColor,
                textAlign = TextAlign.End
            )
        }
    }
}
