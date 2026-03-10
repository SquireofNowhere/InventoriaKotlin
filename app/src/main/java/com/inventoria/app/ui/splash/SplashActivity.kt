package com.inventoria.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.main.MainActivity
import com.inventoria.app.ui.theme.InventoriaTheme
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
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
