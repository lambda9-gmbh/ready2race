package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.text.TextAlign

/**
 * Platzhalterbeschreibung ohne Bezug zu einem generierten Datenbank-Record, damit das Befüllen
 * rein und ohne Datenbank testbar bleibt.
 */
data class GapPlaceholder(
    val type: GapDocumentPlaceholderType,
    val page: Int,
    val relLeft: Double,
    val relTop: Double,
    val relWidth: Double,
    val relHeight: Double,
    val textAlign: TextAlign,
    val fontSize: Int?,
    val bold: Boolean,
    val italic: Boolean,
    val staticText: String?,
)

data class GapPlaceholderValues(
    val firstName: String? = null,
    val lastName: String? = null,
    val fullName: String? = null,
    val result: String? = null,
    val eventName: String? = null,
    val place: String? = null,
    val competitionName: String? = null,
    val competitionShortName: String? = null,
    val clubName: String? = null,
    val teamName: String? = null,
    val eventDate: String? = null,
    val eventLocation: String? = null,
)
