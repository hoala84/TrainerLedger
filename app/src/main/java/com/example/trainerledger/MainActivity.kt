package com.example.trainerledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.trainerledger.ui.TrainerLedgerApp
import com.example.trainerledger.ui.theme.TrainerLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrainerLedgerTheme {
                TrainerLedgerApp()
            }
        }
    }
}
