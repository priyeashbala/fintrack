package com.fintrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fintrack.ui.theme.FinTrackTheme
import java.text.NumberFormat
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinTrackTheme {
                TransactionsScreen(transactions = sampleTransactions)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionsScreen(transactions: List<Transaction>) {
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
            Text(
                text = "Recent Transactions",
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
