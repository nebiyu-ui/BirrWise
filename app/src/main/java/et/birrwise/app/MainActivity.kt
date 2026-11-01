package et.birrwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private enum class TxType { INCOME, EXPENSE }

private data class Transaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val type: TxType,
    val category: String,
    val date: LocalDate = LocalDate.now()
)

private data class Budget(val category: String, val limit: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BirrWiseApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirrWiseApp() {
    val transactions = remember {
        mutableStateListOf(
            Transaction(1, "Salary", 15000.0, TxType.INCOME, "Income"),
            Transaction(2, "Groceries", 850.0, TxType.EXPENSE, "Food"),
            Transaction(3, "Taxi", 180.0, TxType.EXPENSE, "Transport"),
            Transaction(4, "Mobile data", 250.0, TxType.EXPENSE, "Phone & internet")
        )
    }
    val budgets = remember {
        listOf(
            Budget("Food", 2500.0),
            Budget("Transport", 1200.0),
            Budget("Phone & internet", 600.0),
            Budget("Family support", 2000.0)
        )
    }
    var showAdd by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val currentMonth = YearMonth.now()
    val monthTx = transactions.filter { YearMonth.from(it.date) == currentMonth }
    val income = monthTx.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expenses = monthTx.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val money = remember { NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF9FE3C0),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF062A1B),
            secondary = androidx.compose.ui.graphics.Color(0xFFB7C7FF),
            background = androidx.compose.ui.graphics.Color(0xFF101512),
            surface = androidx.compose.ui.graphics.Color(0xFF18201B)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("BirrWise", fontWeight = FontWeight.Bold)
                            Text("Your money, made clear.", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add transaction")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add transaction") }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Wallet, contentDescription = null) },
                        label = { Text("Overview") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                        label = { Text("Budgets") }
                    )
                }
            }
        ) { padding ->
            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text("THIS MONTH", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(8.dp))
                                Text("${money.format(income - expenses)} ETB", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text("Income minus expenses · ${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SummaryPill("Income", income, true, Modifier.weight(1f))
                                    SummaryPill("Expenses", expenses, false, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    item { Text("Recent transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (transactions.isEmpty()) {
                        item { Text("No transactions yet. Add one to get started.") }
                    } else {
                        items(transactions.sortedByDescending { it.date }.take(20), key = { it.id }) { tx ->
                            TransactionRow(tx, money.format(tx.amount), onDelete = { transactions.remove(tx) })
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Monthly budgets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Starter limits. Customize them in a later build.", style = MaterialTheme.typography.bodyMedium)
                    }
                    items(budgets) { budget ->
                        val spent = monthTx.filter { it.type == TxType.EXPENSE && it.category == budget.category }.sumOf { it.amount }
                        val progress = (spent / budget.limit).toFloat().coerceIn(0f, 1f)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(budget.category, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    Text("${money.format(spent)} / ${money.format(budget.limit)} ETB", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(4.dp))
                                Text("${money.format((budget.limit - spent).coerceAtLeast(0.0))} ETB remaining", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            onDismiss = { showAdd = false },
            onSave = { title, amount, type, category ->
                transactions.add(0, Transaction(
                    id = (transactions.maxOfOrNull { it.id } ?: 0L) + 1L,
                    title = title,
                    amount = amount,
                    type = type,
                    category = category
                ))
                showAdd = false
            }
        )
    }
}

@Composable
private fun SummaryPill(label: String, amount: Double, positive: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (positive) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text("${"%,.2f".format(amount)} ETB", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, formattedAmount: String, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background) {
                Icon(
                    if (tx.type == TxType.INCOME) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = if (tx.type == TxType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.title, fontWeight = FontWeight.SemiBold)
                Text("${tx.category} · ${tx.date}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (tx.type == TxType.INCOME) "+" else "−"}$formattedAmount",
                    fontWeight = FontWeight.Bold,
                    color = if (tx.type == TxType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete transaction", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, TxType, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var category by remember { mutableStateOf("Food") }
    var categoryExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Food", "Transport", "Phone & internet", "Household", "Family support", "Shopping", "Entertainment", "Income", "Other")
    val amount = amountText.toDoubleOrNull()
    val valid = title.isNotBlank() && amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (ETB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == TxType.EXPENSE, onClick = { type = TxType.EXPENSE }, label = { Text("Expense") })
                    FilterChip(selected = type == TxType.INCOME, onClick = { type = TxType.INCOME; category = "Income" }, label = { Text("Income") })
                }
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        categories.filter { type != TxType.INCOME || it == "Income" }.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { category = option; categoryExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onSave(title.trim(), amount!!, type, category) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
