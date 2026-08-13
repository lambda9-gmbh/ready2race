package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.competitionExecution.entity.MatchTeamLapDto
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class LatestMatchResultInfo(
    val matchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    /** Wettkampf-Kürzel (short_name) für kompakte Anzeigen; null, wenn keins gepflegt ist. */
    val competitionShortName: String? = null,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val matchNumber: Int?,
    val updatedAt: LocalDateTime,
    val startTime: LocalDateTime?,
    /** Tatsächlicher Start aus `competition_match.started_at`, falls gestempelt. */
    val startedAt: LocalDateTime?,
    val teams: List<MatchResultTeamInfo>
)

data class MatchResultTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    val teamNumber: Int?,
    val clubName: String?,
    /**
     * Die Vereine, die die Athleten dieses Bootes tragen, als Kette in Bootsreihenfolge - kurz
     * und lang, damit die Anzeige nach Platz entscheiden kann. Fehlt jede Angabe zur Crew, bleiben
     * beide leer und der meldende [clubName] tritt an ihre Stelle (siehe eventInfo/control).
     */
    val clubsShort: String?,
    val clubsFull: String?,
    val startNumber: Int,
    val place: Int?,
    /**
     * Die Wertungskategorie des Bootes, `null` für ein Boot ohne Kategorie. Die Anzeige gruppiert
     * danach; die Reihenfolge der Abschnitte steckt in [RatingCategoryRef.sortOrder].
     */
    val ratingCategory: RatingCategoryRef?,
    /**
     * Der Platz **innerhalb** der Wertungskategorie, gezählt ab 1 — das ist die Zahl, die die
     * Ergebnisliste zeigt. [place] bleibt daneben stehen: es ist der Platz im Lauf und damit die
     * Grundlage, aus der dieser hier abgeleitet wird. `null` heißt ungewertet.
     */
    val categoryPlace: Int?,
    val timeString: String?,
    val failed: Boolean,
    val failedReason: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
    val participants: List<ParticipantInfo>,
    /** Zwischenzeiten dieses Boots, nachträglich gefüllt (EventInfoService.attachLaps). */
    val laps: List<MatchTeamLapDto> = emptyList(),
)