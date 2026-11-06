package com.receiptledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.receiptledger.app.ui.ReceiptLedgerNav
import com.receiptledger.app.ui.theme.ReceiptLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ReceiptLedgerApp
        setContent {
            ReceiptLedgerTheme {
                ReceiptLedgerNav(app.container)
            }
        }
    }
}
