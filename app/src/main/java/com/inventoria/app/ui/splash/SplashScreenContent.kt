package com.inventoria.app.ui.splash

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.inventoria.app.R
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreenContent(
    authRepository: FirebaseAuthRepository,
    settingsRepository: SettingsRepository,
    onNavigateToMain: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var startAnimation by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    
    val currentUser = authRepository.getCurrentUser()
    val isAuthenticated = currentUser != null && !currentUser.isAnonymous
    val customUsername by settingsRepository.customUsername.collectAsState(initial = null)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                scope.launch {
                    val user = authRepository.signInWithGoogle(idToken)
                    if (user != null) {
                        onNavigateToMain()
                    } else {
                        isSigningIn = false
                        Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                isSigningIn = false
                Log.e("SplashActivity", "ID Token is NULL")
            }
        } catch (e: ApiException) {
            isSigningIn = false
            Log.e("SplashActivity", "Google Sign In Failed", e)
            Toast.makeText(context, "Sign In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000),
        label = "alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1500)
        if (isAuthenticated) {
            onNavigateToMain()
        } else {
            showActions = true
        }
    }

    val brush = Brush.linearGradient(
        colors = listOf(GradientStart, GradientMiddle, GradientEnd)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isAuthenticated && !isSigningIn) {
                    onNavigateToMain()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha)
                .scale(scale)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_inventoria_logo),
                    contentDescription = "Inventoria Logo",
                    modifier = Modifier.size(120.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Inventoria",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = showActions,
                enter = fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isSigningIn) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = {
                                isSigningIn = true
                                launcher.launch(authRepository.getGoogleSignInIntent())
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = GradientStart
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_google),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google", fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = onNavigateToMain,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Use Local Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
