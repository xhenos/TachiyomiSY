package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.CHARGING
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.UNMETERED_NETWORK
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.databinding.DialogStubTextinputBinding
import eu.kanade.tachiyomi.ui.setting.eh.FrontPageCategoriesDialog
import eu.kanade.tachiyomi.ui.setting.eh.LanguagesDialog
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.multiSelectListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.toast
import exh.eh.EHentaiUpdateWorker
import exh.eh.EHentaiUpdateWorkerConstants
import exh.eh.EHentaiUpdaterStats
import exh.favorites.FavoritesIntroDialog
import exh.favorites.LocalFavoritesStorage
import exh.log.xLogD
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.source.isEhBasedManga
import exh.uconfig.WarnConfigureDialogController
import exh.ui.login.EhLoginActivity
import exh.util.days
import exh.util.executeOnIO
import exh.util.hours
import exh.util.milliseconds
import exh.util.minutes
import exh.util.nullIfBlank
import exh.util.seconds
import exh.util.trans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration

/**
 * EH Settings fragment
 */

class SettingsEhController : SettingsController() {
    private val db: DatabaseHelper by injectLazy()

    fun Preference<*>.reconfigure(): Boolean {
        // Listen for change commit
        asFlow()
            .take(1) // Only listen for first commit
            .onEach {
                // Only listen for first change commit
                WarnConfigureDialogController.uploadSettings(router)
            }
            .launchIn(viewScope)

        // Always return true to save changes
        return true
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_eh

        preferenceCategory {
            titleRes = R.string.ehentai_prefs_account_settings

            switchPreference {
                titleRes = R.string.enable_exhentai
                summaryOff = context.getString(R.string.requires_login)
                key = PreferenceKeys.eh_enableExHentai
                isPersistent = false
                defaultValue = false
                preferences.enableExhentai()
                    .asFlow()
                    .onEach {
                        isChecked = it
                    }
                    .launchIn(viewScope)

                onChange { newVal ->
                    newVal as Boolean
                    if (!newVal) {
                        preferences.enableExhentai().set(false)
                        true
                    } else {
                        startActivityForResult(EhLoginActivity.newIntent(activity!!), LOGIN_RESULT)
                        false
                    }
                }
            }

            intListPreference {
                titleRes = R.string.use_hentai_at_home

                key = PreferenceKeys.eh_enable_hah
                summaryRes = R.string.use_hentai_at_home_summary
                entriesRes = arrayOf(
                    R.string.use_hentai_at_home_option_1,
                    R.string.use_hentai_at_home_option_2
                )
                entryValues = arrayOf("0", "1")

                onChange { preferences.useHentaiAtHome().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            switchPreference {
                titleRes = R.string.show_japanese_titles
                summaryOn = context.getString(R.string.show_japanese_titles_option_1)
                summaryOff = context.getString(R.string.show_japanese_titles_option_2)
                key = "use_jp_title"
                defaultValue = false

                onChange { preferences.useJapaneseTitle().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            switchPreference {
                titleRes = R.string.use_original_images
                summaryOn = context.getString(R.string.use_original_images_on)
                summaryOff = context.getString(R.string.use_original_images_off)
                key = PreferenceKeys.eh_useOrigImages
                defaultValue = false

                onChange { preferences.exhUseOriginalImages().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            preference {
                key = "pref_watched_tags"
                titleRes = R.string.watched_tags
                summaryRes = R.string.watched_tags_summary
                onClick {
                    val intent = if (preferences.enableExhentai().get()) {
                        WebViewActivity.newIntent(activity!!, url = "https://exhentai.org/mytags", title = context.getString(R.string.watched_tags_exh))
                    } else {
                        WebViewActivity.newIntent(activity!!, url = "https://e-hentai.org/mytags", title = context.getString(R.string.watched_tags_eh))
                    }
                    startActivity(intent)
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            preference {
                titleRes = R.string.tag_filtering_threshold
                key = PreferenceKeys.eh_tag_filtering_value
                defaultValue = 0

                summary = context.getString(R.string.tag_filtering_threshhold_summary, preferences.ehTagFilterValue().get())

                onClick {
                    var value: Int? = preferences.ehTagFilterValue().get()
                    MaterialAlertDialogBuilder(activity!!)
                        .setTitle(R.string.tag_filtering_threshold)
                        .let { builder ->
                            val binding = DialogStubTextinputBinding.inflate(LayoutInflater.from(builder.context))
                            binding.textField.editText?.apply {
                                inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                                setText(value.toString(), TextView.BufferType.EDITABLE)
                                doAfterTextChanged {
                                    value = it?.toString()?.toIntOrNull()
                                    this@SettingsEhController.xLogD(value)
                                    error = if (value in -9999..0 || it.toString() == "-") {
                                        null
                                    } else {
                                        context.getString(R.string.tag_filtering_threshhold_error)
                                    }
                                }
                                post {
                                    requestFocusFromTouch()
                                    context.getSystemService<InputMethodManager>()?.showSoftInput(this, 0)
                                }
                            }
                            builder.setView(binding.root)
                        }
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            preferences.ehTagFilterValue().set(value ?: return@setPositiveButton)
                            summary = context.getString(R.string.tag_filtering_threshhold_summary, preferences.ehTagFilterValue().get())
                            preferences.ehTagFilterValue().reconfigure()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            preference {
                titleRes = R.string.tag_watching_threshhold
                key = PreferenceKeys.eh_tag_watching_value
                defaultValue = 0

                summary = context.getString(R.string.tag_watching_threshhold_summary, preferences.ehTagWatchingValue().get())

                onClick {
                    var value: Int? = preferences.ehTagWatchingValue().get()
                    MaterialAlertDialogBuilder(activity!!)
                        .setTitle(R.string.tag_watching_threshhold)
                        .let { builder ->
                            val binding = DialogStubTextinputBinding.inflate(LayoutInflater.from(builder.context))
                            binding.textField.editText?.apply {
                                inputType = InputType.TYPE_NUMBER_FLAG_SIGNED

                                setText(value.toString(), TextView.BufferType.EDITABLE)
                                doAfterTextChanged {
                                    value = it?.toString()?.toIntOrNull()

                                    error = if (value in 0..9999) {
                                        null
                                    } else {
                                        context.getString(R.string.tag_watching_threshhold_error)
                                    }
                                }
                                post {
                                    requestFocusFromTouch()
                                    context.getSystemService<InputMethodManager>()?.showSoftInput(this, 0)
                                }
                            }
                            builder.setView(binding.root)
                        }
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            preferences.ehTagWatchingValue().set(value ?: return@setPositiveButton)
                            summary = context.getString(R.string.tag_watching_threshhold_summary, preferences.ehTagWatchingValue().get())
                            preferences.ehTagWatchingValue().reconfigure()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            preference {
                key = PreferenceKeys.eh_settings_languages
                titleRes = R.string.language_filtering
                summaryRes = R.string.language_filtering_summary

                onClick {
                    val dialog = LanguagesDialog()
                    dialog.targetController = this@SettingsEhController
                    dialog.showDialog(router)
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            preference {
                key = PreferenceKeys.eh_enabled_categories
                titleRes = R.string.frong_page_categories
                summaryRes = R.string.fromt_page_categories_summary

                onClick {
                    val dialog = FrontPageCategoriesDialog()
                    dialog.targetController = this@SettingsEhController
                    dialog.showDialog(router)
                }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            switchPreference {
                defaultValue = false
                key = PreferenceKeys.eh_watched_list_default_state
                titleRes = R.string.watched_list_default
                summaryRes = R.string.watched_list_state_summary

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            listPreference {
                defaultValue = "auto"
                key = PreferenceKeys.eh_ehentai_quality
                summaryRes = R.string.eh_image_quality_summary
                titleRes = R.string.eh_image_quality
                entriesRes = arrayOf(
                    R.string.eh_image_quality_auto,
                    R.string.eh_image_quality_2400,
                    R.string.eh_image_quality_1600,
                    R.string.eh_image_quality_1280,
                    R.string.eh_image_quality_980,
                    R.string.eh_image_quality_780
                )
                entryValues = arrayOf(
                    "auto",
                    "ovrs_2400",
                    "ovrs_1600",
                    "high",
                    "med",
                    "low"
                )

                onChange { preferences.imageQuality().reconfigure() }

                preferences.enableExhentai().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }

            switchPreference {
                titleRes = R.string.pref_enhanced_e_hentai_view
                summaryRes = R.string.pref_enhanced_e_hentai_view_summary
                key = PreferenceKeys.enhancedEHentaiView
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = R.string.favorites_sync

            switchPreference {
                titleRes = R.string.disable_favorites_uploading
                summaryRes = R.string.disable_favorites_uploading_summary
                key = PreferenceKeys.eh_readOnlySync
                defaultValue = false
            }

            preference {
                key = "pref_show_sync_favorite_notes"
                titleRes = R.string.show_favorite_sync_notes
                summaryRes = R.string.show_favorite_sync_notes_summary

                onClick {
                    activity?.let {
                        FavoritesIntroDialog().show(it)
                    }
                }
            }

            switchPreference {
                titleRes = R.string.ignore_sync_errors
                summaryRes = R.string.ignore_sync_errors_summary
                key = PreferenceKeys.eh_lenientSync
                defaultValue = false
            }

            preference {
                key = "pref_force_sync_reset"
                titleRes = R.string.force_sync_state_reset
                summaryRes = R.string.force_sync_state_reset_summary

                onClick {
                    activity?.let { activity ->
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.favorites_sync_reset)
                            .setMessage(R.string.favorites_sync_reset_message)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                LocalFavoritesStorage().apply {
                                    getRealm().use {
                                        it.trans {
                                            clearSnapshots(it)
                                        }
                                    }
                                }
                                activity.toast(context.getString(R.string.sync_state_reset), Toast.LENGTH_LONG)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.gallery_update_checker

            intListPreference {
                key = PreferenceKeys.eh_autoUpdateFrequency
                titleRes = R.string.time_between_batches
                entriesRes = arrayOf(
                    R.string.time_between_batches_never,
                    R.string.time_between_batches_1_hour,
                    R.string.time_between_batches_2_hours,
                    R.string.time_between_batches_3_hours,
                    R.string.time_between_batches_6_hours,
                    R.string.time_between_batches_12_hours,
                    R.string.time_between_batches_24_hours,
                    R.string.time_between_batches_48_hours
                )
                entryValues = arrayOf("0", "1", "2", "3", "6", "12", "24", "48")
                defaultValue = "0"

                preferences.exhAutoUpdateFrequency().asFlow()
                    .onEach { newVal ->
                        summary = if (newVal == 0) {
                            context.getString(R.string.time_between_batches_summary_1, context.getString(R.string.app_name))
                        } else {
                            context.getString(R.string.time_between_batches_summary_2, context.getString(R.string.app_name), newVal, EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION)
                        }
                    }
                    .launchIn(viewScope)

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    EHentaiUpdateWorker.scheduleBackground(context, interval)
                    true
                }
            }

            multiSelectListPreference {
                key = PreferenceKeys.eh_autoUpdateRestrictions
                titleRes = R.string.auto_update_restrictions
                entriesRes = arrayOf(R.string.network_unmetered, R.string.charging)
                entryValues = arrayOf(UNMETERED_NETWORK, CHARGING)

                fun updateSummary() {
                    val restrictions = preferences.exhAutoUpdateRequirements().get()
                        .sorted()
                        .map {
                            when (it) {
                                UNMETERED_NETWORK -> context.getString(R.string.network_unmetered)
                                CHARGING -> context.getString(R.string.charging)
                                else -> it
                            }
                        }
                    val restrictionsText = if (restrictions.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        restrictions.joinToString()
                    }

                    summary = context.getString(R.string.restrictions, restrictionsText)
                }

                preferences.exhAutoUpdateFrequency().asFlow()
                    .onEach { isVisible = it > 0 }
                    .launchIn(viewScope)

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    ContextCompat.getMainExecutor(context).execute { EHentaiUpdateWorker.scheduleBackground(context) }
                    true
                }

                preferences.exhAutoUpdateRequirements().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }

            preference {
                key = "pref_show_updater_statistics"
                titleRes = R.string.show_updater_statistics

                onClick {
                    val progress = MaterialAlertDialogBuilder(context)
                        .setMessage(R.string.gallery_updater_statistics_collection)
                        .setCancelable(false)
                        .create()
                    progress.show()

                    viewScope.launch(Dispatchers.IO) {
                        val updateInfo = try {
                            val stats =
                                preferences.exhAutoUpdateStats().get().nullIfBlank()?.let {
                                    Json.decodeFromString<EHentaiUpdaterStats>(it)
                                }

                            val statsText = if (stats != null) {
                                context.getString(R.string.gallery_updater_stats_text, getRelativeTimeString(getRelativeTimeFromNow(stats.startTime.milliseconds), context), stats.updateCount, stats.possibleUpdates)
                            } else context.getString(R.string.gallery_updater_not_ran_yet)

                            val allMeta = db.getFavoriteMangaWithMetadata().executeOnIO()
                                .filter(Manga::isEhBasedManga)
                                .mapNotNull {
                                    db.getFlatMetadataForManga(it.id!!).executeOnIO()
                                        ?.raise<EHentaiSearchMetadata>()
                                }.toList()

                            fun metaInRelativeDuration(duration: Duration): Int {
                                val durationMs = duration.inWholeMilliseconds
                                return allMeta.asSequence().filter {
                                    System.currentTimeMillis() - it.lastUpdateCheck < durationMs
                                }.count()
                            }

                            statsText + "\n\n" + context.getString(
                                R.string.gallery_updater_stats_time,
                                metaInRelativeDuration(1.hours),
                                metaInRelativeDuration(6.hours),
                                metaInRelativeDuration(12.hours),
                                metaInRelativeDuration(1.days),
                                metaInRelativeDuration(2.days),
                                metaInRelativeDuration(7.days),
                                metaInRelativeDuration(30.days),
                                metaInRelativeDuration(365.days)
                            )
                        } finally {
                            progress.dismiss()
                        }

                        withContext(Dispatchers.Main) {
                            MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.gallery_updater_statistics)
                                .setMessage(updateInfo)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                }
            }
        }
    }

    private fun getRelativeTimeFromNow(then: Duration): RelativeTime {
        val now = System.currentTimeMillis().milliseconds
        var period: Duration = now - then
        val relativeTime = RelativeTime()
        while (period > 0.milliseconds) {
            when {
                period >= 365.days -> {
                    (period.inWholeDays / 365).let {
                        relativeTime.years = it
                        period -= (it * 365).days
                    }
                    continue
                }
                period >= 30.days -> {
                    (period.inWholeDays / 30).let {
                        relativeTime.months = it
                        period -= (it * 30).days
                    }
                }
                period >= 7.days -> {
                    (period.inWholeDays / 7).let {
                        relativeTime.weeks = it
                        period -= (it * 7).days
                    }
                }
                period >= 1.days -> {
                    period.inWholeDays.let {
                        relativeTime.days = it
                        period -= it.days
                    }
                }
                period >= 1.hours -> {
                    period.inWholeHours.let {
                        relativeTime.hours = it
                        period -= it.hours
                    }
                }
                period >= 1.minutes -> {
                    period.inWholeMinutes.let {
                        relativeTime.minutes = it
                        period -= it.minutes
                    }
                }
                period >= 1.seconds -> {
                    period.inWholeSeconds.let {
                        relativeTime.seconds = it
                        period -= it.seconds
                    }
                }
                period >= 1.milliseconds -> {
                    period.inWholeMilliseconds.let {
                        relativeTime.milliseconds = it
                    }
                    period = 0.milliseconds
                }
            }
        }
        return relativeTime
    }

    private fun getRelativeTimeString(relativeTime: RelativeTime, context: Context): String {
        return relativeTime.years?.let { context.resources.getQuantityString(R.plurals.humanize_year, it.toInt(), it) }
            ?: relativeTime.months?.let { context.resources.getQuantityString(R.plurals.humanize_month, it.toInt(), it) }
            ?: relativeTime.weeks?.let { context.resources.getQuantityString(R.plurals.humanize_week, it.toInt(), it) }
            ?: relativeTime.days?.let { context.resources.getQuantityString(R.plurals.humanize_day, it.toInt(), it) }
            ?: relativeTime.hours?.let { context.resources.getQuantityString(R.plurals.humanize_hour, it.toInt(), it) }
            ?: relativeTime.minutes?.let { context.resources.getQuantityString(R.plurals.humanize_minute, it.toInt(), it) }
            ?: relativeTime.seconds?.let { context.resources.getQuantityString(R.plurals.humanize_second, it.toInt(), it) }
            ?: context.getString(R.string.humanize_fallback)
    }

    data class RelativeTime(
        var years: Long? = null,
        var months: Long? = null,
        var weeks: Long? = null,
        var days: Long? = null,
        var hours: Long? = null,
        var minutes: Long? = null,
        var seconds: Long? = null,
        var milliseconds: Long? = null
    )

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == LOGIN_RESULT) {
                // Upload settings
                WarnConfigureDialogController.uploadSettings(router)
            }
        }
    }

    companion object {
        const val LOGIN_RESULT = 500
    }
}
