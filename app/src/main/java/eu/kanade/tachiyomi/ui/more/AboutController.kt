package eu.kanade.tachiyomi.ui.more

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.ui.base.controller.NoAppBarElevationController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.main.WhatsNewDialogController
import eu.kanade.tachiyomi.ui.more.licenses.LicensesController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.preference.add
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import exh.syDebugVersion
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AboutController : SettingsController(), NoAppBarElevationController {

    private val updateChecker by lazy { AppUpdateChecker() }

    private val dateFormat: DateFormat = preferences.dateFormat()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_about

        add(MoreHeaderPreference(context))

        preference {
            key = "pref_about_version"
            titleRes = R.string.version
            summary = if (BuildConfig.DEBUG /* SY --> */ || syDebugVersion != "0" /* SY --> */) {
                "Preview r$syDebugVersion (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
            } else {
                "Stable ${BuildConfig.VERSION_NAME} (${getFormattedBuildTime()})"
            }

            onClick {
                activity?.let {
                    val deviceInfo = CrashLogUtil(it).getDebugInfo()
                    it.copyToClipboard("Debug information", deviceInfo)
                }
            }
        }
        if (BuildConfig.INCLUDE_UPDATER) {
            preference {
                key = "pref_about_check_for_updates"
                titleRes = R.string.check_for_updates

                onClick { checkVersion() }
            }
        }
        preference {
            key = "pref_about_whats_new"
            titleRes = R.string.whats_new

            onClick {
                // SY -->
                WhatsNewDialogController().showDialog(router)
                // SY <--
            }
        }
        preference {
            key = "pref_about_licenses"
            titleRes = R.string.licenses
            onClick {
                router.pushController(LicensesController().withFadeTransaction())
            }
        }

        add(AboutLinksPreference(context))
    }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private fun checkVersion() {
        if (activity == null) return

        activity?.toast(R.string.update_check_look_for_updates)

        launchNow {
            try {
                when (val result = updateChecker.checkForUpdate()) {
                    is AppUpdateResult.NewUpdate -> {
                        NewUpdateDialogController(result).showDialog(router)
                    }
                    is AppUpdateResult.NoNewUpdate -> {
                        activity?.toast(R.string.update_check_no_new_updates)
                    }
                }
            } catch (error: Exception) {
                activity?.toast(error.message)
                Timber.e(error)
            }
        }
    }

    private fun getFormattedBuildTime(): String {
        return try {
            val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
            inputDf.timeZone = TimeZone.getTimeZone("UTC")
            val buildTime = inputDf.parse(BuildConfig.BUILD_TIME)

            val outputDf = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            )
            outputDf.timeZone = TimeZone.getDefault()

            buildTime!!.toDateTimestampString(dateFormat)
        } catch (e: Exception) {
            BuildConfig.BUILD_TIME
        }
    }
}
