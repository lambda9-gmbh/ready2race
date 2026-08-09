package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime
import java.util.UUID

data class ParticipantTrackingDto(
    val id: UUID,
    val eventId: UUID,
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val year: Int,
    val gender: Gender,
    val clubId: UUID,
    val clubName: String,
    val external: Boolean,
    val externalClubName: String?,
    val scanType: ParticipantScanType?,
    val scannedAt: LocalDateTime?,
    val lastScanBy: AppUserNameDto?,
    /** Wie der Eintrag entstand - siehe [ParticipantTrackingSource]. */
    val source: ParticipantTrackingSource,
    /**
     * Wie oft er seither von Hand berichtigt wurde; 0 heißt "unangetastet". Zusammen mit [source]
     * ergibt das die Kennzeichnung im Protokoll.
     *
     * Wer und wann berichtigt hat und aus welchem Grund, steht hier bewusst *nicht*: dieser
     * Endpunkt hängt an READ EVENT und ist mit Scope OWN auch für Vereinsvertreter erreichbar.
     * Die vollständige Spur liefert ausschließlich der Verlauf hinter
     * [ParticipantTrackingService.history], und der ist Admin und Schiedsrichtern vorbehalten.
     */
    val editCount: Int,
)