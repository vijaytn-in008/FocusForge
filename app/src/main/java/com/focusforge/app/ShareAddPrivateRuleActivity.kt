package com.focusforge.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.focusforge.app.data.repository.PrivateListStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Discreet Share-to-FocusForge receiver for adding a shared URL/domain to the Private List.
 * Sets FLAG_SECURE, noHistory, excludeFromRecents, shows a neutral confirmation,
 * and stores only the hash (and vault entry if non-hash-only mode).
 */
@AndroidEntryPoint
class ShareAddPrivateRuleActivity : ComponentActivity() {

    @Inject
    lateinit var privateListStore: PrivateListStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (!sharedText.isNullOrBlank()) {
            val hash = privateListStore.addPrivateDomain(sharedText)
            if (hash != null) {
                Toast.makeText(this, "Rule added to focus rules", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not parse domain", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
