// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import news.openwire.R
import news.openwire.domain.opinion.SocialPost
import news.openwire.i18n.tr
import news.openwire.ui.theme.Glass

/**
 * Posts a story embeds (from X), drawn in the reader's own style: a quiet glass card with
 * the writer, the words and the date. Nothing from X runs in the app.
 */
@Composable
fun EmbeddedPosts(urls: List<String>, load: suspend (String) -> SocialPost?) {
    if (urls.isEmpty()) return
    val g = Glass
    Column {
        Text(tr(R.string.reader_posts).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = g.accentSoft)
        Spacer(Modifier.height(10.dp))
        urls.forEach { url ->
            val post by produceState<SocialPost?>(null, url) { value = load(url) }
            post?.let { PostCardGlass(it); Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
fun PostCardGlass(p: SocialPost) {
    val g = Glass
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(g.chipFill)
            .border(1.dp, g.hairline, RoundedCornerShape(18.dp))
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url))) } }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(g.textPrimary), contentAlignment = Alignment.Center) {
                Text(if (p.network == "X") "𝕏" else p.authorName.take(1).uppercase(), style = sans(15.sp, FontWeight.Bold), color = g.deviceBg)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.authorName, style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("\u2066@${p.authorHandle}\u2069  ·  ${p.network}", style = sans(11.sp), color = g.textSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(p.text, style = serif(17.sp, lineHeight = 25.sp), color = g.textPrimary)
        // For X posts the oEmbed date ("March 21, 2006") rides in linkTitle.
        (if (p.network == "X") p.linkTitle else null)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = mono(10.sp, tracking = 0.04.em), color = g.textSecondary)
        }
    }
}
