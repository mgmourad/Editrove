// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import news.openwire.R
import news.openwire.domain.engine.Languages
import news.openwire.ui.home.GlassCard
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.theme.Glass

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Offered after moving to an edition that reads in another language. Written in both
 * languages, so whichever one the reader knows, they know what they're agreeing to.
 */
@Composable
fun LanguageOfferDialog(offer: Languages.Language, current: Languages.Language, onSwitch: () -> Unit, onKeep: () -> Unit) {
    GlassDialog(icon = Icons.Outlined.Translate, kicker = stringResource(R.string.language_kicker), onDismiss = onKeep) {
        // Laid out in the offered language's own direction, whatever the app's is.
        Text(offerTitle(offer), style = serif(24.sp, FontWeight.Bold, lineHeight = 30.sp).copy(textDirection = TextDirection.Content), color = Glass.textPrimary, modifier = Modifier.fillMaxWidth())
        if (offer.code != current.code) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.language_offer_title, offer.nativeName), style = sans(14.sp, lineHeight = 20.sp), color = Glass.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.language_offer_body), style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary.copy(alpha = 0.9f))
        Spacer(Modifier.height(18.dp))
        DialogButton(offerSwitch(offer), primary = true, onClick = onSwitch)
        Spacer(Modifier.height(8.dp))
        DialogButton(stringResource(R.string.language_keep, current.nativeName), primary = false, onClick = onKeep)
    }
}

/** The offer in the offered language itself. */
private fun offerTitle(l: Languages.Language) = when (l.code) {
    "ar" -> "هل تريد قراءة Editrove بالعربية؟"
    "fr" -> "Lire Editrove en français ?"
    "es" -> "¿Leer Editrove en español?"
    "de" -> "Editrove auf Deutsch lesen?"
    else -> "Read Editrove in English?"
}

private fun offerSwitch(l: Languages.Language) = when (l.code) {
    "ar" -> "التبديل إلى العربية"
    "fr" -> "Passer au français"
    "es" -> "Cambiar a español"
    "de" -> "Auf Deutsch umstellen"
    else -> "Switch to English"
}

@Composable
fun GlassDialog(icon: ImageVector, kicker: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val g = Glass
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth()
                .background(g.deviceBg.copy(alpha = 0.94f), RoundedCornerShape(news.openwire.ui.home.BentoRadius))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentPadding = PaddingValues(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = g.accentSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(kicker.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = g.accentSoft)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.Top) { content() }
        }
    }
}

@Composable
fun DialogButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val g = Glass
    Box(
        Modifier.fillMaxWidth().clip(CircleShape)
            .background(if (primary) g.accent else g.chipFill)
            .clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = sans(13.sp, FontWeight.SemiBold).copy(textDirection = TextDirection.Content), color = if (primary) Color.White else g.textPrimary, textAlign = TextAlign.Center)
    }
}
