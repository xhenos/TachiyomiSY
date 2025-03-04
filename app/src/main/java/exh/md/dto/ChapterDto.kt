package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChapterListDto(
    override val limit: Int,
    override val offset: Int,
    override val total: Int,
    override val results: List<ChapterDto>,
) : ListCallDto<ChapterDto>

@Serializable
data class ChapterDto(
    val result: String,
    val data: ChapterDataDto,
)

@Serializable
data class ChapterDataDto(
    val id: String,
    val type: String,
    val attributes: ChapterAttributesDto,
    val relationships: List<RelationshipDto>,
)

@Serializable
data class ChapterAttributesDto(
    val title: String?,
    val volume: String?,
    val chapter: String?,
    val translatedLanguage: String,
    val hash: String,
    val data: List<String>,
    val dataSaver: List<String>,
    val externalUrl: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val publishAt: String,
)

@Serializable
data class GroupListDto(
    override val limit: Int,
    override val offset: Int,
    override val total: Int,
    override val results: List<GroupDto>,
) : ListCallDto<GroupDto>

@Serializable
data class GroupDto(
    val result: String,
    val data: GroupDataDto,
)

@Serializable
data class GroupDataDto(
    val id: String,
    val attributes: GroupAttributesDto,
)

@Serializable
data class GroupAttributesDto(
    val name: String,
)
