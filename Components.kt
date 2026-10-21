package com.jarvis.assistant.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.executor.Action
import com.jarvis.assistant.executor.describe
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sin

fun has(ctx: Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val n = cm.activeNetwork ?: return false
    return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SectionTitle(t: String) = Text(t, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

// ---------- Animated core: only animates when not Idle (battery friendly) ----------
@Composable
fun JarvisCore(status: Status, levelFlow: StateFlow<Float>, modifier: Modifier = Modifier) {
    val raw by levelFlow.collectAsState()
    val level = if (status == Status.Speaking) 0.5f else raw
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(status) {
        if (status != Status.Idle) {
            var last = 0L
            val speed = if (status == Status.Thinking || status == Status.Executing) 2f else 1f
            while (true) {
                withFrameNanos { n ->
                    if (last != 0L) phase += (n - last) / 1_000_000_000f * speed
                    last = n
                }
            }
        }
    }
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.secondary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(220.dp)) {
            val p = phase
            val r = size.minDimension / 2
            val active = status == Status.Listening || status == Status.Speaking
            val pulse = if (active) 1f + 0.10f * level.coerceIn(0f, 1f) + 0.03f * sin(p * 4f) else 1f
            drawCircle(c1.copy(alpha = 0.10f), r * 0.95f * pulse, center)
            for (k in 0..2) {
                val rad = r * (0.55f + 0.15f * k) * pulse
                drawArc(
                    color = if (k % 2 == 0) c1 else c2,
                    startAngle = p * (40f + 30f * k) * (if (k % 2 == 0) 1f else -1f),
                    sweepAngle = 110f + k * 30f, useCenter = false,
                    topLeft = Offset(center.x - rad, center.y - rad), size = Size(rad * 2, rad * 2),
                    style = Stroke(width = 6f + k * 2f, cap = StrokeCap.Round)
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(c1, c1.copy(alpha = 0f)), center = center, radius = r * 0.4f * pulse),
                r * 0.4f * pulse, center
            )
        }
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val p = phase
            val n = 28
            val w = size.width / (n * 2f)
            val active = status == Status.Listening || status == Status.Speaking
            for (i in 0 until n) {
                val amp = if (active) (0.25f + 0.75f * abs(sin(p * 3f + i * 0.6f))) * (0.35f + level.coerceIn(0f, 1f)) else 0.08f
                val h = size.height * amp.coerceIn(0.05f, 1f)
                drawRoundRect(c1, Offset(w * (2 * i + 0.5f), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
            }
        }
    }
}

@Composable
fun InputBar(
    status: Status, onSend: (String) -> Unit, onMic: () -> Unit,
    onAttach: (() -> Unit)? = null, attached: Boolean = false
) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
            placeholder = { Text("Jarvis se baat karo…") }, maxLines = 3
        )
        Spacer(Modifier.width(6.dp))
        if (onAttach != null) TextButton(onClick = onAttach) { Text(if (attached) "📷✓" else "📷") }
        if (text.isNotBlank() || attached) {
            FilledIconButton(onClick = { onSend(text); text = "" }) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        } else {
            FilledIconButton(onClick = onMic) { Text(if (status == Status.Listening) "⏹" else "🎤") }
        }
    }
}

@Composable
fun PlanCard(plan: List<Action>, onApprove: () -> Unit, onCancel: () -> Unit) {
    GlassCard {
        SectionTitle("Proposed plan — your approval needed")
        plan.forEachIndexed { i, a -> Text("${i + 1}. ${a.describe()}") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApprove) { Text("Approve") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

// ---------- Rich text: markdown code blocks + clickable links ----------
fun splitBlocks(t: String): List<Pair<Boolean, String>> {
    val out = mutableListOf<Pair<Boolean, String>>()
    t.split("```").forEachIndexed { i, s ->
        if (i % 2 == 1) out += true to (if (s.contains('\n')) s.substringAfter('\n') else s).trimEnd()
        else out += false to s
    }
    return out
}

fun codeBlocks(t: String): List<String> = splitBlocks(t).filter { it.first }.map { it.second }

fun htmlBlock(t: String): String? {
    t.split("```").forEachIndexed { i, s ->
        if (i % 2 == 1 && s.substringBefore('\n').trim().lowercase() == "html") return s.substringAfter('\n').trimEnd()
    }
    return null
}

fun linkify(t: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    Regex("https?://[^\\s)]+").findAll(t).forEach { m ->
        append(t.substring(last, m.range.first))
        withLink(
            LinkAnnotation.Url(m.value, TextLinkStyles(SpanStyle(color = Color(0xFF00B8D4), textDecoration = TextDecoration.Underline)))
        ) { append(m.value) }
        last = m.range.last + 1
    }
    append(t.substring(last))
}

@Composable
fun RichText(text: String, modifier: Modifier = Modifier) {
    val parts = remember(text) { splitBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEach { (isCode, body) ->
            if (isCode) CodeBlock(body)
            else if (body.isNotBlank()) Text(linkify(body.trim()), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun CodeBlock(code: String) {
    val clip = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF020611)) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { clip.setText(AnnotatedString(code)) }) { Text("Copy") }
            }
            Text(
                highlight(code), fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false,
                color = Color(0xFFD6E4F0), modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

private val KW = "fun|val|var|class|object|interface|if|else|when|for|while|return|import|package|def|function|const|let|" +
    "public|private|static|void|int|new|try|catch|true|false|null|None|True|False|in|is|as|override|suspend|from|elif|" +
    "async|await|this|self|extends|implements|struct|enum|switch|case|break|continue"
private val TOKEN = Regex(
    "(//.*|/\\*[\\s\\S]*?\\*/|(?m)^\\s*#.*)|(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b(?:$KW)\\b)"
)

fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in TOKEN.findAll(code)) {
        append(code.substring(last, m.range.first))
        val color = when {
            m.groups[1] != null -> Color(0xFF7A8BA8)
            m.groups[2] != null -> Color(0xFFC3E88D)
            m.groups[3] != null -> Color(0xFFF78C6C)
            else -> Color(0xFFC792EA)
        }
        withStyle(SpanStyle(color = color)) { append(m.value) }
        last = m.range.last + 1
    }
    append(code.substring(last))
}
