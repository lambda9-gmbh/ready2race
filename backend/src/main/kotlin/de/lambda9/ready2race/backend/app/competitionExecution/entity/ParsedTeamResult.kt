package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

data class ParsedTeamResult(
    val registrationId: UUID,
    val startNumber: Int?,
    val place: Int?,
    val time: String?,
    val noResultReason: String?,
    /**
     * Time penalty in seconds, for display next to the time. Only the RaceClocker pull fills this;
     * the spreadsheet upload deliberately does not carry penalties.
     */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
)
