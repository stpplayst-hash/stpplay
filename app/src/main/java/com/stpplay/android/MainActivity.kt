package com.stpplay.android

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.stpplay.android.ui.PlayerApp
import com.stpplay.android.ui.PlayerViewModel
import com.stpplay.android.ui.theme.IPTVPlayerTheme
import com.stpplay.android.ui.findActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var playerViewModel: PlayerViewModel? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permissão concedida
        } else {
            // Permissão negada ou desativada
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Ativar Modo Imersivo: Ocultar barra de notificações
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Solicitar permissão de notificação no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val viewModel: PlayerViewModel = hiltViewModel()
            playerViewModel = viewModel
            val themeColorLong by viewModel.themeColor.collectAsState()
            val currentProfile by viewModel.currentProfile.collectAsState()
            val languageCode by viewModel.language.collectAsState()

            val isKids = currentProfile?.isKids == true
            val finalColor = if (isKids) androidx.compose.ui.graphics.Color(0xFF2196F3) 
                            else androidx.compose.ui.graphics.Color(themeColorLong)
            
            val context = LocalContext.current
            val localizedContext = remember(languageCode) {
                val locale = Locale.forLanguageTag(languageCode)
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                Locale.setDefault(locale)
                val configurationContext = context.createConfigurationContext(config)
                
                // Wrapper que preserva a referência da Activity para o Hilt, 
                // mas fornece os recursos traduzidos.
                object : android.content.ContextWrapper(context) {
                    override fun getResources() = configurationContext.resources
                }
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                IPTVPlayerTheme(primaryColor = finalColor) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        PlayerApp()
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Só entra em PiP se houver um canal selecionado E estiver em reprodução ativa
        if (playerViewModel?.selectedChannel?.value != null && playerViewModel?.isPlaybackActive?.value == true) {
            enterPipMode()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerViewModel?.setPipMode(isInPictureInPictureMode)
    }
}
