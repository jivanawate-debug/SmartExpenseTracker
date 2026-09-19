@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.smartexpensetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.smartexpensetracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartExpenseApp() }
    }
}

class ExpenseVM(private val db: AppDb) : ViewModel() {
    val expenses = db.expenses().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = db.budgets().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules = db.rules().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recurring = db.recurring().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(e: Expense) = viewModelScope.launch(Dispatchers.IO) { db.expenses().insert(e) }
    fun update(e: Expense) = viewModelScope.launch(Dispatchers.IO) { db.expenses().update(e) }
    fun delete(e: Expense) = viewModelScope.launch(Dispatchers.IO) { db.expenses().delete(e) }
    fun saveBudget(c: String, n: Double) = viewModelScope.launch(Dispatchers.IO) { db.budgets().save(Budget(c, n)) }
    fun learn(m: String, c: String) = viewModelScope.launch(Dispatchers.IO) { db.rules().save(MerchantRule(m, c)) }
    fun saveRecurring(r: Recurring) = viewModelScope.launch(Dispatchers.IO) { db.recurring().save(r) }
    fun deleteRecurring(r: Recurring) = viewModelScope.launch(Dispatchers.IO) { db.recurring().delete(r) }
}

class ExpenseVMFactory(private val db: AppDb) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpenseVM(db) as T
}

private val categories = listOf("Food", "Travel", "Shopping", "Bills", "Health", "Other")
private val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

@Composable
fun SmartExpenseApp() {
    val context = LocalContext.current
    val db = remember { Room.databaseBuilder(context, AppDb::class.java, "expenses.db").build() }
    val vm: ExpenseVM = viewModel(factory = ExpenseVMFactory(db))
    val expenses by vm.expenses.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Smart Expense Tracker", fontWeight = FontWeight.Bold) }) },
            bottomBar = {
                NavigationBar {
                    listOf("Home", "Transactions", "Review", "Budget", "More").forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text((i + 1).toString()) }, label = { Text(label) })
                    }
                }
            },
            floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("+") } }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> Dashboard(expenses, budgets, recurring)
                    1 -> Transactions(expenses, vm)
                    2 -> Review(expenses.filter { it.needsReview }, vm)
                    3 -> BudgetScreen(expenses, budgets, vm)
                    4 -> MoreScreen(expenses, vm)
                }
            }
        }
    }
    if (showAdd) AddExpenseDialog(onDismiss = { showAdd = false }, onSave = { vm.add(it); showAdd = false })
}

@Composable
fun Dashboard(expenses: List<Expense>, budgets: List<Budget>, recurring: List<Recurring>) {
    val monthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val month = expenses.filter { it.dateMillis >= monthStart }
    val spent = month.filter { it.transactionType == "Debit" }.sumOf { it.amount }
    val income = month.filter { it.transactionType == "Credit" }.sumOf { it.amount }
    val byCat = month.filter { it.transactionType == "Debit" }.groupBy { it.category }.mapValues { it.value.sumOf { x -> x.amount } }.toList().sortedByDescending { it.second }
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Text("Good overview", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Spent", "₹%.0f".format(spent), Modifier.weight(1f))
            StatCard("Income", "₹%.0f".format(income), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        StatCard("Net", "₹%.0f".format(income - spent), Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Text("Spending by category", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (byCat.isEmpty()) Text("No expenses this month yet.")
        byCat.take(6).forEach { (cat, amount) ->
            val max = (byCat.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
            Text("$cat  •  ₹%.0f".format(amount))
            LinearProgressIndicator(progress = { (amount / max).toFloat() }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(14.dp))
        val upcoming = recurring.filter { it.active }.sortedBy { it.nextDueMillis }.take(3)
        Text("Recurring expenses", style = MaterialTheme.typography.titleLarge)
        if (upcoming.isEmpty()) Text("None added yet.") else upcoming.forEach { Text("${it.merchant} • ₹%.0f • ${it.frequency}".format(it.amount)) }
    }
}

@Composable fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.labelLarge); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) } }
}

@Composable
fun Transactions(expenses: List<Expense>, vm: ExpenseVM) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val filtered = expenses.filter { query.isBlank() || it.merchant.contains(query, true) || it.category.contains(query, true) || it.source.contains(query, true) }
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Text("Transactions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        LazyColumn { items(filtered, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.merchant, fontWeight = FontWeight.Bold)
                        Text("₹%.2f".format(e.amount), fontWeight = FontWeight.Bold)
                    }
                    Text("${e.category} • ${e.source} • ${dateFmt.format(Date(e.dateMillis))}")
                    if (e.needsReview) Text("Needs review", color = MaterialTheme.colorScheme.error)
                    Row { TextButton(onClick = { editing = e }) { Text("Edit") }; TextButton(onClick = { vm.delete(e) }) { Text("Delete") } }
                }
            }
        } }
    }
    editing?.let { EditExpenseDialog(it, { editing = null }, { vm.update(it); editing = null }) }
}

@Composable
fun Review(expenses: List<Expense>, vm: ExpenseVM) {
    var editing by remember { mutableStateOf<Expense?>(null) }
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Text("Review Inbox", style = MaterialTheme.typography.headlineSmall)
        Text("Automatic transactions with lower confidence appear here.")
        Spacer(Modifier.height(10.dp))
        if (expenses.isEmpty()) Text("Everything is reviewed. 🎉")
        LazyColumn { items(expenses, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("₹%.2f • ${e.merchant}".format(e.amount), fontWeight = FontWeight.Bold)
                    Text("${e.category} • ${e.source}")
                    Row { Button(onClick = { vm.update(e.copy(needsReview = false)) }) { Text("Approve") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { editing = e }) { Text("Edit") } }
                }
            }
        } }
    }
    editing?.let { EditExpenseDialog(it, { editing = null }, { vm.update(it.copy(needsReview = false)); editing = null }) }
}

@Composable
fun BudgetScreen(expenses: List<Expense>, budgets: List<Budget>, vm: ExpenseVM) {
    val monthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val spent = expenses.filter { it.dateMillis >= monthStart && it.transactionType == "Debit" }.groupBy { it.category }.mapValues { it.value.sumOf { x -> x.amount } }
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Text("Monthly budgets", style = MaterialTheme.typography.headlineSmall)
        Text("Set a limit for each category.")
        Spacer(Modifier.height(10.dp))
        LazyColumn { items(categories) { c ->
            var value by remember(c, budgets) { mutableStateOf(budgets.firstOrNull { it.category == c }?.monthlyLimit?.toString() ?: "") }
            val limit = value.toDoubleOrNull() ?: 0.0
            val used = spent[c] ?: 0.0
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("$c  •  ₹%.0f / ₹%.0f".format(used, limit), fontWeight = FontWeight.SemiBold)
                    if (limit > 0) LinearProgressIndicator(progress = { (used / limit).coerceIn(0.0, 1.0).toFloat() }, Modifier.fillMaxWidth())
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(value, { value = it }, label = { Text("Monthly limit") }, modifier = Modifier.weight(1f), singleLine = true)
                        Spacer(Modifier.width(8.dp)); Button(onClick = { value.toDoubleOrNull()?.let { vm.saveBudget(c, it) } }) { Text("Save") }
                    }
                }
            }
        } }
    }
}

@Composable
fun MoreScreen(expenses: List<Expense>, vm: ExpenseVM) {
    val context = LocalContext.current
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Text("More", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }, Modifier.fillMaxWidth()) { Text("Enable notification access") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val csv = buildString { appendLine("Date,Merchant,Category,Amount,Type,Source"); expenses.forEach { appendLine("\"${dateFmt.format(Date(it.dateMillis))}\",\"${it.merchant.replace("\"", "\"\"")}\",${it.category},${it.amount},${it.transactionType},${it.source}") } }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_TEXT, csv) }, "Export transactions"))
        }, Modifier.fillMaxWidth()) { Text("Export transactions") }
        Spacer(Modifier.height(12.dp))
        Text("Privacy", style = MaterialTheme.typography.titleLarge)
        Text("Your transaction database is stored locally on the device. The app does not require an internet connection.")
        Spacer(Modifier.height(12.dp))
        Text("Automation", style = MaterialTheme.typography.titleLarge)
        Text("The app reads supported payment notifications locally, extracts transaction details on-device, and sends uncertain matches to Review Inbox.")
    }
}

@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var amount by remember { mutableStateOf("") }; var merchant by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Other") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add expense") }, text = {
        Column { OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true); OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }, singleLine = true); CategoryMenu(category) { category = it }; OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true) }
    }, confirmButton = { Button(onClick = { amount.toDoubleOrNull()?.let { onSave(Expense(amount = it, category = category, merchant = merchant.ifBlank { "Cash expense" }, note = note, dateMillis = System.currentTimeMillis())) } }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun EditExpenseDialog(expense: Expense, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var amount by remember { mutableStateOf(expense.amount.toString()) }; var merchant by remember { mutableStateOf(expense.merchant) }; var category by remember { mutableStateOf(expense.category) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit transaction") }, text = { Column { OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }); OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }); CategoryMenu(category) { category = it } } }, confirmButton = { Button(onClick = { amount.toDoubleOrNull()?.let { onSave(expense.copy(amount = it, merchant = merchant, category = category)) } }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun CategoryMenu(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text("Category: $value") }; DropdownMenu(open, { open = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onChange(c); open = false }) } } }
}
