package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholder
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
import de.lambda9.ready2race.backend.pdf.AdditionalText

object GapPlaceholderLogic {

    fun fill(
        placeholders: List<GapPlaceholder>,
        values: GapPlaceholderValues,
    ): List<AdditionalText> = placeholders.map { placeholder ->
        AdditionalText(
            content = content(placeholder, values),
            page = placeholder.page,
            relLeft = placeholder.relLeft,
            relTop = placeholder.relTop,
            relWidth = placeholder.relWidth,
            relHeight = placeholder.relHeight,
            textAlign = placeholder.textAlign,
            fontSize = placeholder.fontSize?.toFloat(),
            bold = placeholder.bold,
            italic = placeholder.italic,
        )
    }

    private fun content(
        placeholder: GapPlaceholder,
        values: GapPlaceholderValues,
    ): String = when (placeholder.type) {
        GapDocumentPlaceholderType.FIRST_NAME -> values.firstName
        GapDocumentPlaceholderType.LAST_NAME -> values.lastName
        GapDocumentPlaceholderType.FULL_NAME -> values.fullName
        GapDocumentPlaceholderType.RESULT -> values.result
        GapDocumentPlaceholderType.EVENT_NAME -> values.eventName
        GapDocumentPlaceholderType.PLACE -> values.place
        GapDocumentPlaceholderType.COMPETITION_NAME -> values.competitionName
        GapDocumentPlaceholderType.COMPETITION_SHORT_NAME -> values.competitionShortName ?: values.competitionName
        GapDocumentPlaceholderType.CLUB_NAME -> values.clubName
        GapDocumentPlaceholderType.TEAM_NAME -> values.teamName
        GapDocumentPlaceholderType.RATING_CATEGORY -> values.ratingCategory
        GapDocumentPlaceholderType.EVENT_DATE -> values.eventDate
        GapDocumentPlaceholderType.EVENT_LOCATION -> values.eventLocation
        GapDocumentPlaceholderType.FREE_TEXT -> placeholder.staticText
    } ?: ""
}
