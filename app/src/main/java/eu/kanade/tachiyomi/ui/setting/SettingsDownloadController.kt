package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.buildSpannedString
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import eu.kanade.tachiyomi.widget.materialdialogs.setQuadStateMultiChoiceItems
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsDownloadController : SettingsController() {

    private val db: DatabaseHelper by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_downloads

        preference {
            key = Keys.downloadsDirectory
            titleRes = R.string.pref_download_directory
            onClick {
                val ctrl = DownloadDirectoriesDialog()
                ctrl.targetController = this@SettingsDownloadController
                ctrl.showDialog(router)
            }

            preferences.downloadsDirectory().asFlow()
                .onEach { path ->
                    val dir = UniFile.fromUri(context, path.toUri())
                    summary = dir.filePath ?: path
                }
                .launchIn(viewScope)
        }
        switchPreference {
            key = Keys.downloadOnlyOverWifi
            titleRes = R.string.pref_download_only_over_wifi
            defaultValue = true
        }

        // SY -->
        switchPreference {
            key = Keys.saveChaptersAsCBZ
            titleRes = R.string.save_chapter_as_cbz
            defaultValue = false
        }

        intListPreference {
            titleRes = R.string.save_chapter_as_cbz_level
            key = Keys.saveChaptersAsCBZLevel
            entries = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
            entryValues = entries
            defaultValue = "0"

            preferences.saveChaptersAsCBZ().asImmediateFlow { isVisible = it }
                .launchIn(viewScope)
        }
        // SY <--

        preferenceCategory {
            titleRes = R.string.pref_category_delete_chapters

            switchPreference {
                key = Keys.removeAfterMarkedAsRead
                titleRes = R.string.pref_remove_after_marked_as_read
                defaultValue = false
            }
            intListPreference {
                key = Keys.removeAfterReadSlots
                titleRes = R.string.pref_remove_after_read
                entriesRes = arrayOf(
                    R.string.disabled,
                    R.string.last_read_chapter,
                    R.string.second_to_last,
                    R.string.third_to_last,
                    R.string.fourth_to_last,
                    R.string.fifth_to_last
                )
                entryValues = arrayOf("-1", "0", "1", "2", "3", "4")
                defaultValue = "-1"
                summary = "%s"
            }
            switchPreference {
                key = Keys.removeBookmarkedChapters
                titleRes = R.string.pref_remove_bookmarked_chapters
                defaultValue = false
            }
            // SY -->
            preference {
                val dbCategories = db.getCategories().executeAsBlocking()
                val categories = listOf(Category.createDefault(context)) + dbCategories

                key = Keys.dontDeleteFromCategories
                titleRes = R.string.pref_dont_delete_from_categories

                onClick {
                    val ctrl = DontDeleteFromCategoriesDialog()
                    ctrl.targetController = this@SettingsDownloadController
                    ctrl.showDialog(router)
                }

                preferences.dontDeleteFromCategories().asFlow()
                    .onEach { mutableSet ->
                        val selectedCategories = mutableSet
                            .mapNotNull { id -> categories.find { it.id == id.toInt() } }
                            .sortedBy { it.order }

                        summary = context.getString(
                            R.string.pref_dont_delete_from_categories_summary,
                            if (selectedCategories.isEmpty()) {
                                context.getString(R.string.tapping_inverted_none)
                            } else {
                                selectedCategories.joinToString { it.name }
                            }
                        )
                    }
                    .launchIn(viewScope)
            }
            // SY <--
        }

        val dbCategories = db.getCategories().executeAsBlocking()
        val categories = listOf(Category.createDefault(context)) + dbCategories

        preferenceCategory {
            titleRes = R.string.pref_category_auto_download

            switchPreference {
                key = Keys.downloadNew
                titleRes = R.string.pref_download_new
                defaultValue = false
            }
            preference {
                key = Keys.downloadNewCategories
                titleRes = R.string.categories
                onClick {
                    DownloadCategoriesDialog().showDialog(router)
                }

                preferences.downloadNew().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)

                fun updateSummary() {
                    val selectedCategories = preferences.downloadNewCategories().get()
                        .mapNotNull { id -> categories.find { it.id == id.toInt() } }
                        .sortedBy { it.order }
                    val includedItemsText = if (selectedCategories.isEmpty()) {
                        context.getString(R.string.all)
                    } else {
                        selectedCategories.joinToString { it.name }
                    }

                    val excludedCategories = preferences.downloadNewCategoriesExclude().get()
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

                preferences.downloadNewCategories().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
                preferences.downloadNewCategoriesExclude().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DOWNLOAD_DIR -> if (data != null && resultCode == Activity.RESULT_OK) {
                val context = applicationContext ?: return
                val uri = data.data
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                if (uri != null) {
                    @Suppress("NewApi")
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                }

                val file = UniFile.fromUri(context, uri)
                preferences.downloadsDirectory().set(file.uri.toString())
            }
        }
    }

    fun predefinedDirectorySelected(selectedDir: String) {
        val path = File(selectedDir).toUri()
        preferences.downloadsDirectory().set(path.toString())
    }

    fun customDirectorySelected() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            startActivityForResult(intent, DOWNLOAD_DIR)
        } catch (e: ActivityNotFoundException) {
            activity?.toast(R.string.file_picker_error)
        }
    }

    class DownloadDirectoriesDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val currentDir = preferences.downloadsDirectory().get()
            val externalDirs = (getExternalDirs() + File(activity.getString(R.string.custom_dir))).map(File::toString)
            var selectedIndex = externalDirs.indexOfFirst { it in currentDir }

            return MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.pref_download_directory)
                .setSingleChoiceItems(externalDirs.toTypedArray(), selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val target = targetController as? SettingsDownloadController
                    if (selectedIndex == externalDirs.lastIndex) {
                        target?.customDirectorySelected()
                    } else {
                        target?.predefinedDirectorySelected(externalDirs[selectedIndex])
                    }
                }
                .create()
        }

        private fun getExternalDirs(): List<File> {
            val defaultDir = Environment.getExternalStorageDirectory().absolutePath +
                File.separator + resources?.getString(R.string.app_name) +
                File.separator + "downloads"

            return mutableListOf(File(defaultDir)) +
                ContextCompat.getExternalFilesDirs(activity!!, "").filterNotNull()
        }
    }

    class DownloadCategoriesDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()
        private val db: DatabaseHelper = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val dbCategories = db.getCategories().executeAsBlocking()
            val categories = listOf(Category.createDefault(activity!!)) + dbCategories

            val items = categories.map { it.name }
            var selected = categories
                .map {
                    when (it.id.toString()) {
                        in preferences.downloadNewCategories().get() -> QuadStateTextView.State.CHECKED.ordinal
                        in preferences.downloadNewCategoriesExclude().get() -> QuadStateTextView.State.INVERSED.ordinal
                        else -> QuadStateTextView.State.UNCHECKED.ordinal
                    }
                }
                .toIntArray()

            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.categories)
                .setQuadStateMultiChoiceItems(
                    message = R.string.pref_download_new_categories_details,
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

                    preferences.downloadNewCategories().set(included)
                    preferences.downloadNewCategoriesExclude().set(excluded)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }

    class DontDeleteFromCategoriesDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()
        private val db: DatabaseHelper = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val dbCategories = db.getCategories().executeAsBlocking()
            val categories = listOf(Category.createDefault(activity!!)) + dbCategories

            val items = categories.map { it.name }.toTypedArray()
            val selection = categories
                .mapNotNull { category ->
                    category.id in preferences.dontDeleteFromCategories().get().map { it.toInt() }
                }
                .toBooleanArray()

            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.categories)
                .setMultiChoiceItems(
                    items,
                    selection
                ) { _, which, selected ->
                    selection[which] = selected
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val included = selection
                        .mapIndexed { index, selected ->
                            if (selected) {
                                categories[index].id.toString()
                            } else null
                        }
                        .filterNotNull()
                        .toSet()

                    preferences.dontDeleteFromCategories().set(included)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }

    private companion object {
        const val DOWNLOAD_DIR = 104
    }
}
