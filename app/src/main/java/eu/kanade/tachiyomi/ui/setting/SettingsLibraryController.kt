package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.CHARGING
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.UNMETERED_NETWORK
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.PrefLibraryColumnsBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.category.genre.SortTagController
import eu.kanade.tachiyomi.ui.library.LibrarySettingsSheet
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
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import eu.kanade.tachiyomi.widget.materialdialogs.setQuadStateMultiChoiceItems
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsLibraryController : SettingsController() {

    private val db: DatabaseHelper = Injekt.get()
    private val trackManager: TrackManager by injectLazy()

    /**
     * Sheet containing filter/sort/display items.
     */
    private var settingsSheet: LibrarySettingsSheet? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_library

        val dbCategories = db.getCategories().executeAsBlocking()
        val categories = listOf(Category.createDefault(context)) + dbCategories

        preferenceCategory {
            titleRes = R.string.pref_category_display

            preference {
                key = "pref_library_columns"
                titleRes = R.string.pref_library_columns
                onClick {
                    LibraryColumnsDialog().showDialog(router)
                }

                fun getColumnValue(value: Int): String {
                    return if (value == 0) {
                        context.getString(R.string.label_default)
                    } else {
                        value.toString()
                    }
                }

                preferences.portraitColumns().asFlow().combine(preferences.landscapeColumns().asFlow()) { portraitCols, landscapeCols -> Pair(portraitCols, landscapeCols) }
                    .onEach { (portraitCols, landscapeCols) ->
                        val portrait = getColumnValue(portraitCols)
                        val landscape = getColumnValue(landscapeCols)
                        summary = "${context.getString(R.string.portrait)}: $portrait, " +
                            "${context.getString(R.string.landscape)}: $landscape"
                    }
                    .launchIn(viewScope)
            }
            if (!context.isTablet()) {
                switchPreference {
                    key = Keys.jumpToChapters
                    titleRes = R.string.pref_jump_to_chapters
                    defaultValue = false
                }
            }
            // SY -->
            preference {
                key = "pref_library_settings_sheet"
                titleRes = R.string.library_settings_sheet

                summaryRes = R.string.library_settings_sheet_summary

                onClick {
                    if (settingsSheet == null) {
                        settingsSheet = LibrarySettingsSheet(router) {}
                    }
                    settingsSheet?.show()
                }
            }
            // Sy <--
        }

        preferenceCategory {
            titleRes = R.string.categories

            preference {
                key = "pref_action_edit_categories"
                titleRes = R.string.action_edit_categories

                val catCount = dbCategories.size
                summary = context.resources.getQuantityString(R.plurals.num_categories, catCount, catCount)

                onClick {
                    router.pushController(CategoryController().withFadeTransaction())
                }
            }

            intListPreference {
                key = Keys.defaultCategory
                titleRes = R.string.default_category

                entries = arrayOf(context.getString(R.string.default_category_summary)) +
                    categories.map { it.name }.toTypedArray()
                entryValues = arrayOf("-1") + categories.map { it.id.toString() }.toTypedArray()
                defaultValue = "-1"

                val selectedCategory = categories.find { it.id == preferences.defaultCategory() }
                summary = selectedCategory?.name
                    ?: context.getString(R.string.default_category_summary)
                onChange { newValue ->
                    summary = categories.find {
                        it.id == (newValue as String).toInt()
                    }?.name ?: context.getString(R.string.default_category_summary)
                    true
                }
            }

            switchPreference {
                key = Keys.categorizedDisplay
                titleRes = R.string.categorized_display_settings
                defaultValue = false
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_library_update

            intListPreference {
                key = Keys.libraryUpdateInterval
                titleRes = R.string.pref_library_update_interval
                entriesRes = arrayOf(
                    R.string.update_never,
                    R.string.update_3hour,
                    R.string.update_4hour,
                    R.string.update_6hour,
                    R.string.update_8hour,
                    R.string.update_12hour,
                    R.string.update_24hour,
                    R.string.update_48hour,
                    R.string.update_weekly
                )
                entryValues = arrayOf("0", "3", "4", "6", "8", "12", "24", "48", "168")
                defaultValue = "24"
                summary = "%s"

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    LibraryUpdateJob.setupTask(context, interval)
                    true
                }
            }
            multiSelectListPreference {
                key = Keys.libraryUpdateRestriction
                titleRes = R.string.pref_library_update_restriction
                entriesRes = arrayOf(R.string.network_unmetered, R.string.charging)
                entryValues = arrayOf(UNMETERED_NETWORK, CHARGING)
                defaultValue = setOf(UNMETERED_NETWORK)

                preferences.libraryUpdateInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(viewScope)

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    ContextCompat.getMainExecutor(context).execute { LibraryUpdateJob.setupTask(context) }
                    true
                }

                fun updateSummary() {
                    val restrictions = preferences.libraryUpdateRestriction().get()
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

                preferences.libraryUpdateRestriction().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            switchPreference {
                key = Keys.updateOnlyNonCompleted
                titleRes = R.string.pref_update_only_non_completed
                defaultValue = false
            }
            preference {
                key = Keys.libraryUpdateCategories
                titleRes = R.string.categories
                onClick {
                    LibraryGlobalUpdateCategoriesDialog().showDialog(router)
                }

                fun updateSummary() {
                    val selectedCategories = preferences.libraryUpdateCategories().get()
                        .mapNotNull { id -> categories.find { it.id == id.toInt() } }
                        .sortedBy { it.order }
                    val includedItemsText = if (selectedCategories.isEmpty()) {
                        context.getString(R.string.all)
                    } else {
                        selectedCategories.joinToString { it.name }
                    }

                    val excludedCategories = preferences.libraryUpdateCategoriesExclude().get()
                        .mapNotNull { id -> categories.find { it.id == id.toInt() } }
                        .sortedBy { it.order }
                    val excludedItemsText = if (excludedCategories.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        excludedCategories.joinToString { it.name }
                    }

                    summary = buildSpannedString {
                        append(context.getString(R.string.include, includedItemsText))
                        appendLine()
                        append(context.getString(R.string.exclude, excludedItemsText))
                    }
                }

                preferences.libraryUpdateCategories().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
                preferences.libraryUpdateCategoriesExclude().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            // SY -->
            listPreference {
                key = Keys.groupLibraryUpdateType
                titleRes = R.string.library_group_updates
                entriesRes = arrayOf(
                    R.string.library_group_updates_global,
                    R.string.library_group_updates_all_but_ungrouped,
                    R.string.library_group_updates_all
                )
                entryValues = arrayOf(
                    PreferenceValues.GroupLibraryMode.GLOBAL.name,
                    PreferenceValues.GroupLibraryMode.ALL_BUT_UNGROUPED.name,
                    PreferenceValues.GroupLibraryMode.ALL.name
                )
                defaultValue = PreferenceValues.GroupLibraryMode.GLOBAL.name
                summary = "%s"
            }
            // SY <--
            intListPreference {
                key = Keys.libraryUpdatePrioritization
                titleRes = R.string.pref_library_update_prioritization

                // The following array lines up with the list rankingScheme in:
                // ../../data/library/LibraryUpdateRanker.kt
                val priorities = arrayOf(
                    Pair("0", R.string.action_sort_alpha),
                    Pair("1", R.string.action_sort_last_checked),
                    Pair("2", R.string.action_sort_next_updated)
                )
                val defaultPriority = priorities[0]

                entriesRes = priorities.map { it.second }.toTypedArray()
                entryValues = priorities.map { it.first }.toTypedArray()
                defaultValue = defaultPriority.first

                val selectedPriority = priorities.find { it.first.toInt() == preferences.libraryUpdatePrioritization().get() }
                summaryRes = selectedPriority?.second ?: defaultPriority.second
                onChange { newValue ->
                    summaryRes = priorities.find {
                        it.first == (newValue as String)
                    }?.second ?: defaultPriority.second
                    true
                }
            }
            switchPreference {
                key = Keys.autoUpdateMetadata
                titleRes = R.string.pref_library_update_refresh_metadata
                summaryRes = R.string.pref_library_update_refresh_metadata_summary
                defaultValue = false
            }
            if (trackManager.hasLoggedServices()) {
                switchPreference {
                    key = Keys.autoUpdateTrackers
                    titleRes = R.string.pref_library_update_refresh_trackers
                    summaryRes = R.string.pref_library_update_refresh_trackers_summary
                    defaultValue = false
                }
            }
            switchPreference {
                key = Keys.showLibraryUpdateErrors
                titleRes = R.string.pref_library_update_error_notification
                defaultValue = true
            }
        }

        // SY -->
        preferenceCategory {
            titleRes = R.string.pref_sorting_settings
            preference {
                key = "pref_tag_sorting"
                titleRes = R.string.pref_tag_sorting
                val count = preferences.sortTagsForLibrary().get().size
                summary = context.resources.getQuantityString(R.plurals.pref_tag_sorting_desc, count, count)
                onClick {
                    router.pushController(SortTagController().withFadeTransaction())
                }
            }
        }

        if (preferences.skipPreMigration().get() || preferences.migrationSources().get()
            .isNotEmpty()
        ) {
            preferenceCategory {
                titleRes = R.string.migration

                switchPreference {
                    key = Keys.skipPreMigration
                    titleRes = R.string.skip_pre_migration
                    summaryRes = R.string.pref_skip_pre_migration_summary
                    defaultValue = false
                }
            }
        }
        // SY <--
    }

    class LibraryColumnsDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()

        private var portrait = preferences.portraitColumns().get()
        private var landscape = preferences.landscapeColumns().get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val binding = PrefLibraryColumnsBinding.inflate(LayoutInflater.from(activity!!))
            onViewCreated(binding)
            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.pref_library_columns)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    preferences.portraitColumns().set(portrait)
                    preferences.landscapeColumns().set(landscape)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }

        fun onViewCreated(binding: PrefLibraryColumnsBinding) {
            with(binding.portraitColumns) {
                displayedValues = arrayOf(context.getString(R.string.label_default)) +
                    IntRange(1, 10).map(Int::toString)
                value = portrait

                setOnValueChangedListener { _, _, newValue ->
                    portrait = newValue
                }
            }
            with(binding.landscapeColumns) {
                displayedValues = arrayOf(context.getString(R.string.label_default)) +
                    IntRange(1, 10).map(Int::toString)
                value = landscape

                setOnValueChangedListener { _, _, newValue ->
                    landscape = newValue
                }
            }
        }
    }

    class LibraryGlobalUpdateCategoriesDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()
        private val db: DatabaseHelper = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val dbCategories = db.getCategories().executeAsBlocking()
            val categories = listOf(Category.createDefault(activity!!)) + dbCategories

            val items = categories.map { it.name }
            var selected = categories
                .map {
                    when (it.id.toString()) {
                        in preferences.libraryUpdateCategories().get() -> QuadStateTextView.State.CHECKED.ordinal
                        in preferences.libraryUpdateCategoriesExclude().get() -> QuadStateTextView.State.INVERSED.ordinal
                        else -> QuadStateTextView.State.UNCHECKED.ordinal
                    }
                }
                .toIntArray()

            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.categories)
                .setQuadStateMultiChoiceItems(
                    message = R.string.pref_library_update_categories_details,
                    items = items,
                    initialSelected = selected
                ) { selections ->
                    selected = selections
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val included = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.CHECKED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()
                    val excluded = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.INVERSED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()

                    preferences.libraryUpdateCategories().set(included)
                    preferences.libraryUpdateCategoriesExclude().set(excluded)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }
}
