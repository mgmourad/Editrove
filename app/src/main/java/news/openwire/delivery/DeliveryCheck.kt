// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.delivery

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import news.openwire.R
import news.openwire.i18n.tr
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.settings.DialogButton
import news.openwire.ui.settings.GlassDialog
import news.openwire.ui.theme.Glass

/**
 * Whether alerts can actually reach the reader: notifications allowed, and Editrove free to
 * run in the background (battery optimisation, and the extra launch managers some makers add,
 * Huawei's among them, silently stop background checks). Checked after every update.
 */
object DeliveryCheck {
    data class Status(val notificationsAllowed: Boolean, val unrestricted: Boolean, val launchManager: Intent?) {
        val ok get() = notificationsAllowed && unrestricted
    }

    fun status(context: Context): Status = Status(
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        unrestricted = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
        launchManager = launchManagerIntent(context),
    )

    private const val PREFS = "delivery"

    /** Once per installed version, when alerts are on and something could stop them. */
    fun shouldPrompt(context: Context, alertsOn: Boolean): Boolean {
        if (!alertsOn) return false
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getLong("checked", -1) == versionCode(context)) return false
        val s = status(context)
        return !s.ok || s.launchManager != null
    }

    fun markChecked(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("checked", versionCode(context)).apply()

    /** Shown on demand too (Settings › Notifications, or right after turning alerts on). */
    var showing by mutableStateOf(false)

    @Suppress("DEPRECATION")
    private fun versionCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    fun notificationSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else appDetails(context)

    /** Android's own "let this app run in the background?" question. */
    @SuppressLint("BatteryLife") // a news app whose alerts are its point, distributed outside Play
    fun batteryRequest(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Makers' own background-launch managers (only the one present on this phone). */
    private val LAUNCH_MANAGERS = listOf(
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
    )

    fun launchManagerIntent(context: Context): Intent? = LAUNCH_MANAGERS.firstNotNullOfOrNull { (pkg, cls) ->
        val i = Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.takeIf { runCatching { context.packageManager.resolveActivity(it, 0) }.getOrNull()?.activityInfo?.exported == true }
    }

    fun open(context: Context, intent: Intent) {
        val ok = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok) runCatching { context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** "Make sure alerts reach you": each check, with the fix one tap away. Rechecks on return. */
@Composable
fun DeliveryDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val s = remember(tick) { DeliveryCheck.status(context) }
    GlassDialog(icon = Icons.Outlined.NotificationsActive, kicker = tr(R.string.delivery_kicker), onDismiss = onDone) {
        Text(tr(if (s.ok) R.string.delivery_title_ok else R.string.delivery_title), style = serif(24.sp, FontWeight.Bold, lineHeight = 30.sp), color = Glass.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(tr(R.string.delivery_body), style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary.copy(alpha = 0.9f))
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CheckRow(s.notificationsAllowed, tr(R.string.delivery_notify), tr(R.string.delivery_fix)) { DeliveryCheck.open(context, DeliveryCheck.notificationSettings(context)) }
            CheckRow(s.unrestricted, tr(R.string.delivery_battery), tr(R.string.delivery_allow)) {
                DeliveryCheck.open(context, DeliveryCheck.batteryRequest(context))
            }
            s.launchManager?.let { intent ->
                // Can't be read back: offered as a step, not a check.
                CheckRow(null, tr(R.string.delivery_launch), tr(R.string.delivery_open), hint = tr(R.string.delivery_launch_hint)) { DeliveryCheck.open(context, intent) }
            }
        }
        Spacer(Modifier.height(18.dp))
        DialogButton(tr(if (s.ok) R.string.delivery_done else R.string.delivery_later), primary = s.ok, onClick = onDone)
    }
}

@Composable
private fun CheckRow(ok: Boolean?, title: String, action: String, hint: String? = null, onFix: () -> Unit) {
    val g = Glass
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok == true) Icons.Filled.CheckCircle else Icons.Outlined.ErrorOutline, null,
            tint = when (ok) { true -> Color(0xFF34C759); false -> Color(0xFFFF9F0A); null -> g.accentSoft },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(14.sp, FontWeight.Medium), color = g.textPrimary)
            hint?.let { Text(it, style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary) }
        }
        if (ok != true) {
            Spacer(Modifier.width(8.dp))
            Text(
                action, style = sans(12.sp, FontWeight.SemiBold), color = Color.White,
                modifier = Modifier.clip(CircleShape).background(g.accent).clickable(onClick = onFix).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
