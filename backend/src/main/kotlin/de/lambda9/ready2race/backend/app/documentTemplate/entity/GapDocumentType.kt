package de.lambda9.ready2race.backend.app.documentTemplate.entity

enum class GapDocumentType(val allowedPlaceholders: Set<GapDocumentPlaceholderType>) {
    CERTIFICATE_OF_PARTICIPATION(
        setOf(
            GapDocumentPlaceholderType.FIRST_NAME,
            GapDocumentPlaceholderType.LAST_NAME,
            GapDocumentPlaceholderType.FULL_NAME,
            GapDocumentPlaceholderType.RESULT,
            GapDocumentPlaceholderType.EVENT_NAME,
        )
    ),
    AWARD_CERTIFICATE(
        setOf(
            GapDocumentPlaceholderType.FIRST_NAME,
            GapDocumentPlaceholderType.LAST_NAME,
            GapDocumentPlaceholderType.FULL_NAME,
            GapDocumentPlaceholderType.RESULT,
            GapDocumentPlaceholderType.EVENT_NAME,
            GapDocumentPlaceholderType.PLACE,
            GapDocumentPlaceholderType.COMPETITION_NAME,
            GapDocumentPlaceholderType.COMPETITION_SHORT_NAME,
            GapDocumentPlaceholderType.CLUB_NAME,
            GapDocumentPlaceholderType.TEAM_NAME,
            GapDocumentPlaceholderType.EVENT_DATE,
            GapDocumentPlaceholderType.EVENT_LOCATION,
            GapDocumentPlaceholderType.FREE_TEXT,
        )
    ),
}