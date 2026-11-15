package news.openwire.ui.home

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import news.openwire.data.remote.ArticleContent
import news.openwire.data.remote.ArticleTextExtractor
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article
import news.openwire.ui.theme.NewsreaderFamily
import news.openwire.ui.theme.Rose

@Composable
fun ReaderContent(
    article: Article,
    router: ArticleRouter,
    extractor: ArticleTextExtractor,
    scope: CoroutineScope,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val url = article.targetUrl ?: article.originalUrl ?: ""
    val content by produceState<ArticleContent?>(initialValue = null, article.id) {
        value = if (url.isNotEmpty()) extractor.extract(url) else null
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (article.source ?: article.domain ?: "").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = NewsProcessor.formatRelativeTime(article.publishedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(article.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (article.imageUrl != null) {
            GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        AudioPlayerCard(article = article, content = content, context = context)
        Spacer(modifier = Modifier.height(20.dp))

        if (content == null) {
            Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ArticleBody(content!!.text)
            Spacer(modifier = Modifier.height(20.dp))
            GlassCard(onClick = { router.open(scope, context, article) }) {
                Text("Continue on publisher site →", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ArticleBody(text: String) {
    val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
    paragraphs.forEachIndexed { index, para ->
        if (index == 0) {
            val first = para.firstOrNull()?.toString() ?: ""
            val rest = para.drop(1)
            val annotated = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontFamily = NewsreaderFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 46.sp,
                        color = Rose,
                    ),
                ) { append(first) }
                append(rest)
            }
            Text(annotated, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                text = para,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun AudioPlayerCard(article: Article, content: ArticleContent?, context: Context) {
    var playing by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value = engine
        }
        onDispose { engine.stop(); engine.shutdown() }
    }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Rose)
                    .clickable {
                        val engine = tts.value
                        if (playing) {
                            engine?.stop()
                            playing = false
                        } else if (engine != null) {
                            engine.language = Locale.US
                            engine.setSpeechRate(1.0f)
                            val text = article.title + ". " + (content?.text ?: "")
                            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openwire-tts")
                            playing = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Listen to narrated briefing", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Waveform(playing)
            }
        }
    }
}

@Composable
private fun Waveform(playing: Boolean) {
    val heights = listOf(35, 65, 90, 50, 80, 100, 60, 40, 85, 70, 95, 45, 75, 60, 85, 50, 70, 90, 40, 65)
    val transition = rememberInfiniteTransition(label = "wave")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "waveProgress",
    )
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        heights.forEachIndexed { index, h ->
            val animated = if (playing) {
                val wave = sin((progress * 2 * PI + index * 0.5).toFloat()).toFloat()
                h * (0.35f + 0.65f * (wave + 1f) / 2f)
            } else h.toFloat()
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((animated * 0.14f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun ReaderOverlay(
    article: Article,
    router: ArticleRouter,
    extractor: ArticleTextExtractor,
    scope: CoroutineScope,
    context: Context,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("OpenWire", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            ReaderContent(
                article = article,
                router = router,
                extractor = extractor,
                scope = scope,
                context = context,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
