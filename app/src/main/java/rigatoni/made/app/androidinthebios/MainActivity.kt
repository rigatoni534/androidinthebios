package rigatoni.made.app.androidinthebios

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import rigatoni.made.app.androidinthebios.R
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    secondary = Color(0xFFCCC2DC),
                    tertiary = Color(0xFFEFB8C8)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

data class ScatteredImage(
    val id: Int,
    val resId: Int,
    val x: Float, // 0..1
    val y: Float, // 0..1
    val size: Dp,
    val rotation: Float,
    val initialAlpha: Float,
    var isTapped: Boolean = false
)

@OptIn(UnstableApi::class)
@Composable
fun AppContent() {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    var gameState by remember { mutableStateOf("images") } // "images", "video", "show_credits_button", "credits"
    val images = remember {
        val list = mutableStateListOf<ScatteredImage>()
        repeat(5) { i ->
            list.add(createRandomImage(i, R.drawable.app_asset_img1))
            list.add(createRandomImage(i + 5, R.drawable.app_asset_img2))
        }
        list
    }

    val musicPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.app_asset_music}")
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
    }

    DisposableEffect(Unit) {
        musicPlayer.play()
        onDispose {
            musicPlayer.release()
        }
    }

    when (gameState) {
        "images" -> {
            Box(modifier = Modifier.fillMaxSize()) {
                images.forEach { scatteredImage ->
                    val alpha by animateFloatAsState(
                        targetValue = if (scatteredImage.isTapped) 0f else scatteredImage.initialAlpha,
                        animationSpec = tween(durationMillis = 500),
                        label = "fade"
                    )

                    if (alpha > 0.01f) {
                        Image(
                            painter = painterResource(id = scatteredImage.resId),
                            contentDescription = null,
                            modifier = Modifier
                                .offset(
                                    x = (scatteredImage.x * screenWidth.value).dp,
                                    y = (scatteredImage.y * screenHeight.value).dp
                                )
                                .size(scatteredImage.size)
                                .rotate(scatteredImage.rotation)
                                .alpha(alpha)
                                .clickable {
                                    val index = images.indexOf(scatteredImage)
                                    if (index != -1) {
                                        images[index] = scatteredImage.copy(isTapped = true)
                                    }
                                    if (images.all { it.isTapped }) {
                                        musicPlayer.stop()
                                        gameState = "video"
                                    }
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
        "video" -> {
            VideoPlayer(onVideoFinished = {
                gameState = "show_credits_button"
            })
        }
        "show_credits_button" -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Button(
                    onClick = { gameState = "credits" },
                    modifier = Modifier.padding(bottom = 64.dp).fillMaxWidth(0.6f)
                ) {
                    Text("Credits")
                }
            }
        }
        "credits" -> {
            CreditsMenu()
        }
    }
}

fun createRandomImage(id: Int, resId: Int): ScatteredImage {
    return ScatteredImage(
        id = id,
        resId = resId,
        x = Random.nextFloat() * 0.7f, // Avoid edge
        y = Random.nextFloat() * 0.7f,
        size = Random.nextInt(80, 200).dp,
        rotation = Random.nextFloat() * 360f,
        initialAlpha = Random.nextFloat() * 0.5f + 0.5f // 0.5 to 1.0
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(onVideoFinished: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.app_asset_watch}")
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onVideoFinished()
                    }
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CreditsMenu() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Credits",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        CreditItem("App Creator", "rigatoni534 on GitHub")
        CreditItem("Meme/Idea Origin", "Bringus Studios on YouTube")
        CreditItem("Video Source", "@w4.sted on TikTok")
        CreditItem("Music Source", "@lukethesoup0 on TikTok")
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { /* Could restart or something */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Nice")
        }
    }
}

@Composable
fun CreditItem(role: String, source: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = role, style = MaterialTheme.typography.labelSmall)
            Text(text = source, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
