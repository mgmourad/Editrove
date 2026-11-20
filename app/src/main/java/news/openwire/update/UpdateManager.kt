// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.update

import news.openwire.i18n.tr
import news.openwire.R
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import news.openwire.BuildConfig
import news.openwire.OpenWireApp
import news.openwire.data.local.SettingsRepository
import news.openwire.data.remote.ReleaseSource
import news.openwire.domain.engine.AppVersion
import news.openwire.domain.engine.ReleaseInfo
import news.openwire.domain.engine.Releases

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    /** Android must let Editrove install apps before an update can be installed. */
    data class NeedsPermission(val release: ReleaseInfo) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * Self-updates from GitHub Releases: checks on every launch/return to the app
 * (throttled), surfaces an in-app banner, and downloads → verifies → installs via
 * PackageInstaller. With "Install automatically" on (Android 12+), updates after
 * the first one Editrove installs itself need no confirmation.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val source: ReleaseSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("editrove_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    val currentVersion: AppVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(listOf(0))

    /** Installing over this build needs a release-signed APK; debug builds only check. */
    val canInstall: Boolean get() = !BuildConfig.DEBUG && BuildConfig.SELF_UPDATE

    val lastChecked: Long get() = prefs.getLong(KEY_LAST_CHECK, 0L)

    /** The version whose banner the user dismissed. */
    fun isDismissed(release: ReleaseInfo) = prefs.getString(KEY_DISMISSED, null) == release.tag
    fun dismiss(release: ReleaseInfo) = prefs.edit().putString(KEY_DISMISSED, release.tag).apply()

    /** Called whenever the app comes to the foreground. */
    fun onForeground() {
        // Back from Android's "install unknown apps" screen with permission granted: carry on.
        (_state.value as? UpdateState.NeedsPermission)?.let { if (canRequestInstalls()) { install(it.release); return } }
        if (!BuildConfig.SELF_UPDATE || !settings.settings.value.autoCheckUpdates) return
        if (System.currentTimeMillis() - lastChecked < THROTTLE_MS && _state.value !is UpdateState.Failed) return
        check()
    }

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val newest = Releases.newest(currentVersion, source.releases())
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (newest == null) UpdateState.UpToDate(System.currentTimeMillis()) else UpdateState.Available(newest)
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: tr(R.string.upd_unreachable))
            }
            (_state.value as? UpdateState.Available)?.let { if (settings.settings.value.autoInstallUpdates && canInstall) install(it.release) }
        }
    }

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(release: ReleaseInfo) {
        if (!canInstall) { _state.value = UpdateState.Failed(tr(R.string.upd_debug), release); return }
        if (!canRequestInstalls()) { _state.value = UpdateState.NeedsPermission(release); return }
        job?.cancel()
        job = scope.launch {
            try {
                _state.value = UpdateState.Downloading(release, 0f)
                val file = File(context.cacheDir, "updates/Editrove-${release.version}.apk")
                source.download(release, file) { p -> _state.value = UpdateState.Downloading(release, p) }
                _state.value = UpdateState.Installing(release)
                withContext(Dispatchers.IO) { commit(file, release) }
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.message ?: tr(R.string.upd_failed), release)
            }
        }
    }

    private fun commit(file: File, release: ReleaseInfo) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31 && settings.settings.value.autoInstallUpdates) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).putExtra(EXTRA_TAG, release.tag)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate(System.currentTimeMillis())
            PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> UpdateState.Failed(message ?: tr(R.string.upd_failed), release)
        }
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed_tag"
        private const val THROTTLE_MS = 10 * 60_000L
        const val EXTRA_TAG = "news.editrove.update.TAG"
    }
}

/** Receives PackageInstaller results; asks the user to confirm when Android requires it. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            return
        }
        (context.applicationContext as? OpenWireApp)?.container?.updateManager?.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}

/** Bundled changelog (fastlane `changelogs/<versionCode>.txt`) and the "what's new" bookkeeping. */
class Changelog(private val context: Context) {
    data class Entry(val versionCode: Int, val title: String, val items: List<String>)

    val entries: List<Entry> by lazy {
        (context.assets.list("")?.toList().orEmpty())
            .mapNotNull { name -> name.removeSuffix(".txt").takeIf { name.endsWith(".txt") }?.toIntOrNull()?.let { it to name } }
            .sortedByDescending { it.first }
            .map { (code, name) ->
                val lines = context.assets.open(name).bufferedReader().readLines().filter { it.isNotBlank() }
                Entry(code, lines.firstOrNull().orEmpty().trim(), lines.drop(1).map { it.trim().removePrefix("•").trim() })
            }
    }

    private val prefs = context.getSharedPreferences("editrove_updates", Context.MODE_PRIVATE)

    /**
     * The entry to show once on the first run of each version: after an update and on a fresh
     * install alike. A version without its own notes shows the newest notes before it.
     */
    fun pendingWhatsNew(): Entry? {
        val current = BuildConfig.VERSION_CODE
        if (prefs.getInt(KEY_SEEN, -1) >= current) return null
        return entries.firstOrNull { it.versionCode <= current }
    }

    fun markSeen() = prefs.edit().putInt(KEY_SEEN, BuildConfig.VERSION_CODE).apply()

    private companion object { const val KEY_SEEN = "whats_new_seen_code" }
}
