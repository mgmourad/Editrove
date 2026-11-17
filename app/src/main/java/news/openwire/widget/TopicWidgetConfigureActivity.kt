// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import news.openwire.i18n.tr
import news.openwire.R
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import news.openwire.ui.home.GlassCard
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.OpenWireTheme
import kotlinx.coroutines.launch

/**
 * Shown when "Topic of Choice" is added to the home screen (and when it's reconfigured):
 * the reader picks the one topic the widget will brief.
 */
class TopicWidgetConfigureActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(news.openwire.i18n.AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget un-added.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val topics = WidgetData.topics(this)
        val current = BriefWidgets.topicFor(this, TopicWidgetReceiver::class.java, id)
        setContent {
            OpenWireTheme(darkTheme = isSystemInDarkTheme()) {
                val g = Glass
                Box(Modifier.fillMaxSize().background(g.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Text(tr(R.string.toc_kicker), style = mono(10.sp, FontWeight.SemiBold), color = g.accentSoft)
                            Spacer(Modifier.height(6.dp))
                            Text(tr(R.string.toc_title), style = serif(26.sp, lineHeight = 30.sp), color = g.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(tr(R.string.toc_body), style = sans(13.sp, lineHeight = 19.sp), color = g.textSecondary)
                            Spacer(Modifier.height(10.dp))
                        }
                        items(topics, key = { it.id }) { t ->
                            GlassCard(onClick = { pick(id, t.id) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), selected = t.id == current) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.label, style = serif(18.sp), color = g.textPrimary)
                                        if (t.keywords.isNotEmpty()) Text(t.keywords.joinToString(", "), style = sans(11.sp), color = g.textSecondary, maxLines = 1)
                                    }
                                    if (t.id == current) Text(tr(R.string.selected), style = mono(9.sp, FontWeight.SemiBold), color = g.accentSoft, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pick(id: Int, topicId: String) {
        BriefWidgets.setTopic(this, id, topicId)
        val app = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            runCatching { TopicNewsWidget().update(app, androidx.glance.appwidget.GlanceAppWidgetManager(app).getGlanceIdBy(id)) }
        }
        // Make sure the widget has stories to show.
        WidgetSync.refreshTopic(this, topicId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
