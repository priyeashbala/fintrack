package com.fintrack

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fintrack.data.AppDatabase
import com.fintrack.data.SmsTransactionEntity
import com.fintrack.sms.SmsParser
import com.fintrack.ui.theme.FinTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Transaction(
    val title: String,
    val category: String,
    val date: String,
    val amount: Double,
    val type: TransactionType
)

enum class TransactionType { INCOME, EXPENSE }

private val sampleTransactions = listOf(
    Transaction("Salary", "Income", "Mar 01, 2026", 72000.00, TransactionType.INCOME),
    Transaction("Groceries", "Food", "Mar 02, 2026", 2450.75, TransactionType.EXPENSE),
    Transaction("Uber", "Transport", "Mar 03, 2026", 380.00, TransactionType.EXPENSE),
    Transaction("Freelance", "Income", "Mar 05, 2026", 12500.00, TransactionType.INCOME),
    Transaction("Electricity Bill", "Utilities", "Mar 08, 2026", 1890.50, TransactionType.EXPENSE),
    Transaction("Movie Night", "Entertainment", "Mar 12, 2026", 620.00, TransactionType.EXPENSE),
    Transaction("Dining Out", "Food", "Mar 14, 2026", 1100.00, TransactionType.EXPENSE)
)

class TransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).transactionDao()
    private var inboxImported = false

    val transactions: StateFlow<List<Transaction>> = dao.observeAll()
        .map { entities ->
            if (entities.isEmpty()) {
                sampleTransactions
            } else {
                entities.map { it.toUiModel() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = sampleTransactions
        )

    fun importInboxIfNeeded() {
        if (inboxImported) return
        inboxImported = true

        viewModelScope.launch(Dispatchers.IO) {
            val parsed = readAndParseInboxSms()
            if (parsed.isNotEmpty()) {
                dao.insertAll(parsed)
            }
        }
    }

    @RequiresPermission(Manifest.permission.READ_SMS)
    private fun readAndParseInboxSms(): List<SmsTransactionEntity> {
        val resolver = getApplication<Application>().contentResolver
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val parsed = mutableListOf<SmsTransactionEntity>()

        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)

            if (addressIndex == -1 || bodyIndex == -1 || dateIndex == -1) return@use

            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIndex).orEmpty()
                val body = cursor.getString(bodyIndex).orEmpty()
                val timestamp = cursor.getLong(dateIndex)
                val txn = SmsParser.parse(sender = sender, body = body, timestampMillis = timestamp)
                if (txn != null) parsed.add(txn)
            }
        }

        return parsed
    }
}

private fun SmsTransactionEntity.toUiModel(): Transaction {
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    val dateText = formatter.format(Date(occurredAt))
    val transactionType = if (type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE

    return Transaction(
        title = title,
        category = channel,
        date = dateText,
        amount = amount,
        type = transactionType
    )
}

class MainActivity : ComponentActivity() {
    private val viewModel: TransactionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinTrackTheme {
                FinTrackRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun FinTrackRoot(viewModel: TransactionsViewModel = viewModel()) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsState()

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasSmsPermission = grants[Manifest.permission.READ_SMS] == true
    }

    LaunchedEffect(Unit) {
        if (!hasSmsPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                )
            )
        }
    }

    LaunchedEffect(hasSmsPermission) {
        if (hasSmsPermission) {
            viewModel.importInboxIfNeeded()
        }
    }

    TransactionsScreen(
        transactions = transactions,
        hasSmsPermission = hasSmsPermission,
        onRequestPermission = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                )
            )
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionsScreen(
    transactions: List<Transaction>,
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("FinTrack") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (!hasSmsPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SMS permission needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Grant SMS permission to auto-fetch bank, card, and UPI transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )
                        Button(onClick = onRequestPermission) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            Text(
                text = if (hasSmsPermission) "Parsed Transactions" else "Example Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionCard(transaction = transaction)
                }
            }
        }
    }
}

@Composable
fun TransactionCard(transaction: Transaction) {
    val formattedAmount = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        .format(transaction.amount)
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) Color(0xFF1B8A3A) else Color(0xFFC43E2F)
    val prefix = if (isIncome) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${transaction.category} • ${transaction.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$prefix$formattedAmount",
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionsScreenPreview() {
    FinTrackTheme {
        TransactionsScreen(
            transactions = sampleTransactions,
            hasSmsPermission = false,
            onRequestPermission = {}
        )
    }
}
