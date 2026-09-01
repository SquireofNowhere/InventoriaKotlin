package com.inventoria.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.main.MainActivity
import com.inventoria.app.ui.theme.InventoriaTheme
import com.inventoria.app.widget.WidgetNav
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: FirebaseAuthRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A home-screen widget tap carries a destination. When an account already exists there
        // is nothing for the splash to decide, so skip its animation and go straight in; a brand
        // new install still gets the sign-in choices below, and the route rides along after.
        if (intent.hasExtra(WidgetNav.EXTRA_NAV_ROUTE) && authRepository.getCurrentUser() != null) {
            navigateToMain()
            return
        }
        setContent {
            InventoriaTheme {
                SplashScreenContent(
                    authRepository = authRepository,
                    settingsRepository = settingsRepository,
                    onNavigateToMain = {
                        navigateToMain()
                    }
                )
            }
        }
    }

    private fun navigateToMain() {
        val main = Intent(this, MainActivity::class.java).apply {
            // Both flags together: CLEAR_TOP alone finishes and recreates a standard-launch-mode
            // activity (losing the NavHost's state); with SINGLE_TOP the existing MainActivity is
            // kept and receives the intent through onNewIntent instead. Matters when a widget
            // opens the app while it is already running behind the launcher.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.getStringExtra(WidgetNav.EXTRA_NAV_ROUTE)?.let { putExtra(WidgetNav.EXTRA_NAV_ROUTE, it) }
        }
        startActivity(main)
        finish()
    }
}
