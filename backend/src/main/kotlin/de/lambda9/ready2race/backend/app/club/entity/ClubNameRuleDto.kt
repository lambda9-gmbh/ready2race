package de.lambda9.ready2race.backend.app.club.entity

import java.util.UUID

data class ClubNameRuleDto(
    val id: UUID,
    val kind: ClubNameRuleKind,
    val term: String?,
    val replacement: String?,
    val sortOrder: Int,
)
