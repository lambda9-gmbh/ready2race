package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.data.Timecode
import java.time.LocalDateTime
import java.util.UUID

data class CompetitionMatchTeamDto(
    val registrationId: UUID,
    val teamNumber: Int,
    val clubId: UUID,
    val clubName: String,
    val actualClubName: String?,
    val namedParticipants: List<CompetitionTeamNamedParticipantDto>,
    val name: String?,
    val startNumber: Int,
    val place: Int?,
    val timeString: String?,
    val placesCalculated: Boolean,
    val deregistered: Boolean,
    val deregistrationReason: String?,
    val failed: Boolean,
    val failedReason: String?,
    /** Zeitstrafe in Sekunden; die Ergebniszeit enthält sie bereits. */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    /**
     * Zwischenzeiten aus RaceClocker, in der Reihenfolge der Marken auf der Strecke. Leer, wenn
     * das Rennen keine Split-Spalten führt.
     */
    val laps: List<MatchTeamLapDto> = emptyList(),
)

/**
 * Eine Zwischenzeit: Spaltenname aus RaceClocker und kumulierte Fahrzeit als Anzeige-Text.
 * [recordedAt] ist der Erfassungszeitpunkt (`created_at`) — additiv und nullable, weil nur
 * der Stream-Overlay-Modus LAPS ihn braucht (Eintreffzeit je Marke); alle anderen Anzeigen
 * lassen ihn weg (NON_NULL-Serialisierung).
 */
data class MatchTeamLapDto(
    val name: String,
    val timeString: String,
    val recordedAt: LocalDateTime? = null,
    /**
     * Die gefahrene Zeit als Zahl - reine Sortierhilfe des Rundenbands, angezeigt wird
     * [timeString]. Trifft im selben Abruf mehreres ein, tragen die Marken denselben
     * [recordedAt]; dann ist die höhere Zeit die jüngere Nachricht, weil dieses Boot später an
     * der Marke war.
     */
    val lapMillis: Long? = null,
)

/**
 * Kumulierte Lap-Millisekunden in denselben Anzeige-Text falten, den die Durchführungsseite zeigt:
 * über einer Stunde mit Stundenstelle, darunter m:ss, eine Nachkommastelle. Zentral, weil jetzt
 * mehrere Anzeigen (Durchführung, Schiedsrichter-Dashboard, Boards) dieselben Zwischenzeiten zeigen.
 * [recordedAt] bleibt für Aufrufer ohne Erfassungszeitpunkt weg (Default null, Quellkompatibilität).
 */
fun matchTeamLapDto(name: String, lapMillis: Long, recordedAt: LocalDateTime? = null) = MatchTeamLapDto(
    name = name,
    timeString = Timecode(
        millis = lapMillis,
        baseUnit = if (lapMillis >= 3_600_000) Timecode.BaseUnit.HOURS else Timecode.BaseUnit.MINUTES,
        millisecondPrecision = Timecode.MillisecondPrecision.ONE,
    ).toString(),
    recordedAt = recordedAt,
    lapMillis = lapMillis,
)