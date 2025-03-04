package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.dto.ChapterDto
import exh.md.service.MangaDexService
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.md.utils.mdListCall
import exh.metadata.metadata.MangaDexSearchMetadata
import kotlinx.coroutines.async
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo

class MangaHandler(
    private val lang: String,
    private val service: MangaDexService,
    private val apiMangaParser: ApiMangaParser,
    private val followsHandler: FollowsHandler
) {
    suspend fun getMangaDetails(manga: MangaInfo, sourceId: Long, forceLatestCovers: Boolean): MangaInfo {
        val response = withIOContext { service.viewManga(MdUtil.getMangaId(manga.key)) }
        return apiMangaParser.parseToManga(manga, response, sourceId)
    }

    fun fetchMangaDetailsObservable(manga: SManga, sourceId: Long, forceLatestCovers: Boolean): Observable<SManga> {
        return runAsObservable({
            getMangaDetails(manga.toMangaInfo(), sourceId, forceLatestCovers).toSManga()
        })
    }

    fun fetchChapterListObservable(manga: SManga): Observable<List<SChapter>> = runAsObservable({
        getChapterList(manga.toMangaInfo()).map { it.toSChapter() }
    })

    suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return withIOContext {
            val results = mdListCall {
                service.viewChapters(MdUtil.getMangaId(manga.key), lang, it)
            }

            val groupMap = getGroupMap(results)

            apiMangaParser.chapterListParse(results, groupMap)
        }
    }

    private fun getGroupMap(results: List<ChapterDto>): Map<String, String> {
        return results.map { chapter -> chapter.data.relationships }
            .flatten()
            .filter { it.type == MdConstants.Types.scanlator }
            .map { it.id to it.attributes!!.name!! }
            .toMap()
    }

    suspend fun fetchRandomMangaId(): String {
        return withIOContext {
            service.randomManga().data.id
        }
    }

    suspend fun getTrackingInfo(track: Track): Pair<Track, MangaDexSearchMetadata?> {
        return withIOContext {
            /*val metadata = async {
                val mangaUrl = MdUtil.buildMangaUrl(MdUtil.getMangaId(track.tracking_url))
                val manga = MangaInfo(mangaUrl, track.title)
                val response = client.newCall(mangaRequest(manga)).await()
                val metadata = MangaDexSearchMetadata()
                apiMangaParser.parseIntoMetadata(metadata, response, emptyList())
                metadata
            }*/
            val remoteTrack = async {
                followsHandler.fetchTrackingInfo(track.tracking_url)
            }
            remoteTrack.await() to null
        }
    }

    suspend fun getMangaFromChapterId(chapterId: String): String? {
        return withIOContext {
            apiMangaParser.chapterParseForMangaId(service.viewChapter(chapterId))
        }
    }
}
