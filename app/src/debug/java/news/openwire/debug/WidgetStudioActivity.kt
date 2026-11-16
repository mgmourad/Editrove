// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Debug-only "widget studio": hosts the real widgets at the four grid sizes
 * (needs `adb shell appwidget grantbind --package news.editrove`).
 * Extras: `widget` = audio|top|topic|all, `size` = 2x2|4x2|4x4|6x3|all.
 */
class WidgetStudioActivity : Activity() {
    private lateinit var host: AppWidgetHost
    private val ids = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, 4242)
        host.deleteHost()
        val mgr = AppWidgetManager.getInstance(this)
        val which = intent.getStringExtra("widget") ?: "all"
        val sizeArg = intent.getStringExtra("size") ?: "all"
        val providers = listOf(
            "audio" to "news.openwire.widget.AudioBriefWidgetReceiver",
            "top" to "news.openwire.widget.TopStoriesWidgetReceiver",
            "topic" to "news.openwire.widget.TopicWidgetReceiver",
        ).filter { which == "all" || it.first == which }
        val sizes = listOf("2x2" to (190f to 190f), "4x2" to (380f to 190f), "4x4" to (380f to 380f), "6x3" to (600f to 300f))
            .filter { sizeArg == "all" || it.first == sizeArg }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val d = resources.displayMetrics.density
        for ((name, cls) in providers) {
            val cn = ComponentName(packageName, cls)
            val info = mgr.installedProviders.firstOrNull { it.provider == cn } ?: continue
            for ((label, wh) in sizes) {
                val id = host.allocateAppWidgetId(); ids += id
                val opts = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wh.first.toInt()); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wh.first.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, wh.second.toInt()); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, wh.second.toInt())
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(wh.first, wh.second)))
                }
                if (!mgr.bindAppWidgetIdIfAllowed(id, cn, opts)) { col.addView(TextView(this).apply { text = "Bind not allowed: run appwidget grantbind"; setTextColor(Color.RED) }); continue }
                col.addView(TextView(this).apply { text = "$name · $label"; setTextColor(Color.GRAY); setPadding(0, 24, 0, 8) })
                val v = host.createView(this, id, info)
                if (android.os.Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(opts, listOf(SizeF(wh.first, wh.second)))
                col.addView(v, LinearLayout.LayoutParams((wh.first * d).toInt(), (wh.second * d).toInt()))
            }
        }
        setContentView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) })
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { super.onStop(); host.stopListening() }
    override fun onDestroy() { ids.forEach { host.deleteAppWidgetId(it) }; super.onDestroy() }
}
