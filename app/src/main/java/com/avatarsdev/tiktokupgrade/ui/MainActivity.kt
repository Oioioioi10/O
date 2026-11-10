package com.avatarsdev.tiktokupgrade.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.avatarsdev.tiktokupgrade.TikTokUpgradeApp
import com.avatarsdev.tiktokupgrade.data.db.InboxClipEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Route to open next (set when a clip is shared into the app). */
    val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent { AppRoot(pendingRoute) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        ).joinToString("\n").trim()
        if (text.isEmpty()) return
        val url = Regex("https?://\\S+").find(text)?.value
        val db = (application as TikTokUpgradeApp).container.db
        lifecycleScope.launch {
            db.inboxDao().insert(InboxClipEntity(text = text.take(2000), url = url, addedAt = System.currentTimeMillis()))
            pendingRoute.value = Routes.FEED
        }
    }
}
