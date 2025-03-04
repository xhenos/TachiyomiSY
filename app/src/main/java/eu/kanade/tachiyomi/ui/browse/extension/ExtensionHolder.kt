package eu.kanade.tachiyomi.ui.browse.extension

import android.annotation.SuppressLint
import android.view.View
import coil.clear
import coil.load
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ExtensionCardItemBinding
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionHolder(view: View, val adapter: ExtensionAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = ExtensionCardItemBinding.bind(view)

    private val shouldLabelNsfw by lazy {
        Injekt.get<PreferencesHelper>().labelNsfwExtension()
    }

    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(bindingAdapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension

        binding.extTitle.text = extension.name
        binding.version.text = extension.versionName
        binding.lang.text = LocaleHelper.getSourceDisplayName(extension.lang, itemView.context)
        binding.warning.text = when {
            extension is Extension.Untrusted -> itemView.context.getString(R.string.ext_untrusted)
            extension is Extension.Installed && extension.isUnofficial -> itemView.context.getString(R.string.ext_unofficial)
            extension is Extension.Installed && extension.isObsolete -> itemView.context.getString(R.string.ext_obsolete)
            // SY -->
            extension is Extension.Installed && extension.isRedundant -> itemView.context.getString(R.string.ext_redundant)
            extension.isNsfw && shouldLabelNsfw -> itemView.context.getString(R.string.ext_nsfw_short).plusRepo(extension)
            else -> "".plusRepo(extension)
            // SY <--
        }.uppercase()

        binding.image.clear()
        if (extension is Extension.Available) {
            binding.image.load(extension.iconUrl)
        } else {
            extension.getApplicationIcon(itemView.context)?.let { binding.image.setImageDrawable(it) }
        }
        bindButton(item)
    }

    // SY -->
    private fun String.plusRepo(extension: Extension): String {
        return if (extension is Extension.Available) {
            when (extension.repoUrl) {
                ExtensionGithubApi.REPO_URL_PREFIX -> this
                else -> {
                    this + if (this.isEmpty()) {
                        ""
                    } else {
                        " • "
                    } + itemView.context.getString(R.string.repo_source)
                }
            }
        } else this
    }

    // SY <--

    @Suppress("ResourceType")
    fun bindButton(item: ExtensionItem) = with(binding.extButton) {
        isEnabled = true
        isClickable = true

        val extension = item.extension

        val installStep = item.installStep
        if (installStep != null) {
            setText(
                when (installStep) {
                    InstallStep.Pending -> R.string.ext_pending
                    InstallStep.Downloading -> R.string.ext_downloading
                    InstallStep.Installing -> R.string.ext_installing
                    InstallStep.Installed -> R.string.ext_installed
                    InstallStep.Error -> R.string.action_retry
                }
            )
            if (installStep != InstallStep.Error) {
                isEnabled = false
                isClickable = false
            }
        } else if (extension is Extension.Installed) {
            when {
                extension.hasUpdate -> {
                    setText(R.string.ext_update)
                }
                else -> {
                    // SY -->
                    if (extension.sources.any { it is ConfigurableSource }) {
                        @SuppressLint("SetTextI18n")
                        text = context.getString(R.string.action_settings) + "+"
                    } else {
                        setText(R.string.action_settings)
                    }
                    // SY <--
                }
            }
        } else if (extension is Extension.Untrusted) {
            setText(R.string.ext_trust)
        } else {
            setText(R.string.ext_install)
        }
    }
}
