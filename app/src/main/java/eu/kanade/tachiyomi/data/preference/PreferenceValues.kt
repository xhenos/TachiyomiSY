package eu.kanade.tachiyomi.data.preference

import eu.kanade.tachiyomi.R

const val UNMETERED_NETWORK = "wifi"
const val CHARGING = "ac"

/**
 * This class stores the values for the preferences in the application.
 */
object PreferenceValues {

    /* ktlint-disable experimental:enum-entry-name-case */

    // Keys are lowercase to match legacy string values
    enum class ThemeMode {
        light,
        dark,
        system,
    }

    /* ktlint-enable experimental:enum-entry-name-case */

    enum class AppTheme(val titleResId: Int?) {
        DEFAULT(R.string.label_default),
        MONET(R.string.theme_monet),
        BLUE(R.string.theme_blue),
        GREEN_APPLE(R.string.theme_greenapple),
        MIDNIGHT_DUSK(R.string.theme_midnightdusk),
        STRAWBERRY_DAIQUIRI(R.string.theme_strawberrydaiquiri),
        TAKO(R.string.theme_tako),
        TEALTURQUOISE(R.string.theme_tealturquoise),
        YINYANG(R.string.theme_yinyang),
        YOTSUBA(R.string.theme_yotsuba),

        // SY -->
        PURE_RED(R.string.theme_pure_red),
        // SY <--

        // Deprecated
        DARK_BLUE(null),
        HOT_PINK(null),
    }

    enum class TappingInvertMode(val shouldInvertHorizontal: Boolean = false, val shouldInvertVertical: Boolean = false) {
        NONE,
        HORIZONTAL(shouldInvertHorizontal = true),
        VERTICAL(shouldInvertVertical = true),
        BOTH(shouldInvertHorizontal = true, shouldInvertVertical = true),
    }

    enum class ReaderHideThreshold(val threshold: Int) {
        HIGHEST(5),
        HIGH(13),
        LOW(31),
        LOWEST(47)
    }

    // SY -->
    enum class GroupLibraryMode {
        GLOBAL,
        ALL_BUT_UNGROUPED,
        ALL
    }
    // SY <--
}
