package eu.kanade.tachiyomi.extension.api

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.source.BlacklistedSources
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import uy.kohesive.injekt.injectLazy
import java.util.Date

internal class ExtensionGithubApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            networkService.client
                .newCall(GET("${REPO_URL_PREFIX}index.min.json"))
                .await()
                .parseAs<JsonArray>()
                .let { parseResponse(it) }
        } /* SY --> */ + preferences.extensionRepos().get().flatMap { repoPath ->
            val url = "$BASE_URL$repoPath/repo/"
            networkService.client
                .newCall(GET("${url}index.min.json"))
                .await()
                .parseAs<JsonArray>()
                .let {
                    parseResponse(it, url)
                }
        }
        // SY <--
    }

    suspend fun checkForUpdates(context: Context): List<Extension.Installed> {
        val extensions = findExtensions()

        preferences.lastExtCheck().set(Date().time)

        // SY -->
        val blacklistEnabled = preferences.enableSourceBlacklist().get()
        // SY <--

        val installedExtensions = ExtensionLoader.loadExtensions(context)
            .filterIsInstance<LoadResult.Success>()
            .map { it.extension }
            // SY -->
            .filterNot { it.isBlacklisted(blacklistEnabled) }
        // SY <--

        val extensionsWithUpdate = mutableListOf<Extension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdate = availableExt.versionCode > installedExt.versionCode
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        return extensionsWithUpdate
    }

    private fun parseResponse(json: JsonArray /* SY --> */, repoUrl: String = REPO_URL_PREFIX /* SY <-- */): List<Extension.Available> {
        return json
            .filter { element ->
                val versionName = element.jsonObject["version"]!!.jsonPrimitive.content
                val libVersion = versionName.substringBeforeLast('.').toDouble()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map { element ->
                val name = element.jsonObject["name"]!!.jsonPrimitive.content.substringAfter("Tachiyomi: ")
                val pkgName = element.jsonObject["pkg"]!!.jsonPrimitive.content
                val apkName = element.jsonObject["apk"]!!.jsonPrimitive.content
                val versionName = element.jsonObject["version"]!!.jsonPrimitive.content
                val versionCode = element.jsonObject["code"]!!.jsonPrimitive.long
                val lang = element.jsonObject["lang"]!!.jsonPrimitive.content
                val nsfw = element.jsonObject["nsfw"]!!.jsonPrimitive.int == 1
                // SY -->
                val icon = "$repoUrl/icon/${apkName.replace(".apk", ".png")}"
                // SY <--

                Extension.Available(name, pkgName, versionName, versionCode, lang, nsfw, apkName, icon /* SY --> */, repoUrl /* SY <-- */)
            }
    }

    fun getApkUrl(extension: Extension.Available): String {
        return /* SY --> */ "${extension.repoUrl}/apk/${extension.apkName}" /* SY <-- */
    }

    // SY -->
    private fun Extension.isBlacklisted(
        blacklistEnabled: Boolean = preferences.enableSourceBlacklist().get()
    ): Boolean {
        return pkgName in BlacklistedSources.BLACKLISTED_EXTENSIONS && blacklistEnabled
    }
    // SY <--

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/"
        const val REPO_URL_PREFIX = "${BASE_URL}tachiyomiorg/tachiyomi-extensions/repo/"
    }
}
