package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsGeneralController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_general

        intListPreference {
            key = Keys.startScreen
            titleRes = R.string.pref_start_screen
            entriesRes = arrayOf(
                R.string.label_library,
                R.string.label_recent_updates,
                R.string.label_recent_manga,
                R.string.browse
            )
            entryValues = arrayOf("1", "3", "2", "4")
            defaultValue = "1"
            summary = "%s"
        }
        switchPreference {
            key = Keys.confirmExit
            titleRes = R.string.pref_confirm_exit
            defaultValue = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            preference {
                key = "pref_manage_notifications"
                titleRes = R.string.pref_manage_notifications
                onClick {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    startActivity(intent)
                }
            }
        }
        // --> EXH
        preferenceCategory {
            titleRes = R.string.pref_category_fork

            switchPreference {
                key = Keys.eh_expandFilters
                titleRes = R.string.toggle_expand_search_filters
                defaultValue = false
            }

            switchPreference {
                key = Keys.eh_autoSolveCaptchas
                titleRes = R.string.auto_solve_captchas
                summaryRes = R.string.auto_solve_captchas_summary
                defaultValue = false
            }

            switchPreference {
                key = Keys.recommendsInOverflow
                titleRes = R.string.put_recommends_in_overflow
                summaryRes = R.string.put_recommends_in_overflow_summary
                defaultValue = false
            }
        }
        // <-- EXH
    }
}
