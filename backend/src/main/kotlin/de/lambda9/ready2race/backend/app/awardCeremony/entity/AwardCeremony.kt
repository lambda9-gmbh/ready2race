package de.lambda9.ready2race.backend.app.awardCeremony.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Boot, wie es aus der Platzberechnung in die Fachlogik geht. Bewusst frei von JOOQ-Records
 * und von KIO: alles, was an der Siegerehrung fachlich schiefgehen kann, soll sich ohne Datenbank
 * testen lassen.
 */
data class AwardCeremonyCandidate(
    /** Der von CompetitionExecutionService.computeCompetitionPlaces berechnete Platz im Gesamtfeld. */
    val competitionPlace: Int,
    val startNumber: Int,
    val ratingCategoryName: String?,
    /** Der meldende Verein der Mannschaft - reine Verwaltung, nicht der Verein der Personen. */
    val registeringClubName: String,
    val teamName: String?,
    val time: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val roundName: String?,
    val matchName: String?,
    val matchTime: LocalDateTime?,
    val participants: List<AwardCeremonyCandidateParticipant>,
)

data class AwardCeremonyCandidateParticipant(
    val firstName: String,
    val lastName: String,
    val role: String,
    val external: Boolean?,
    val externalClubName: String?,
    /** Der eigene Verein der Person; bei Gastruderern leer, dort trägt [externalClubName]. */
    val ownClubName: String?,
)

/** Die Einheit einer Seite. `null` als Kategorie ist ein gültiger Wert, kein „unbekannt". */
data class AwardCeremonyKey(
    val competitionId: UUID,
    val ratingCategoryName: String?,
)

data class AwardCeremonyChoiceDto(
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionShortName: String?,
    val competitionName: String,
    val ratingCategoryName: String?,
    val placedTeams: Int,
)

data class AwardCeremonyKeyRequest(
    val competitionId: UUID,
    val ratingCategoryName: String?,
)

/**
 * Leere oder fehlende Auswahl bedeutet „alle Ehrungen". Deshalb gibt es hier nichts zu
 * validieren - eine leere Liste ist kein Fehler, sondern der Normalfall „alles drucken".
 */
data class AwardCeremonySelectionRequest(
    val selection: List<AwardCeremonyKeyRequest>?,
) : Validatable {

    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = AwardCeremonySelectionRequest(
                selection = listOf(
                    AwardCeremonyKeyRequest(
                        competitionId = UUID.randomUUID(),
                        ratingCategoryName = "Masters A",
                    )
                )
            )
    }
}

/**
 * Schriftgrößenstufe einer Seite. Drei Achter mit Steuermann ergeben 27 Personenzeilen und
 * sprengen A4; über AwardCeremonyLogic.COMPACT_THRESHOLD Zeilen (die Schwelle selbst bleibt noch
 * normal) rückt die Seite eine Stufe zusammen, statt unkontrolliert umzubrechen.
 */
enum class AwardCeremonyDensity {
    NORMAL,
    COMPACT,
}

/** Genau eine A4-Seite. */
data class AwardCeremonySheet(
    val eventName: String,
    val eventDate: String,
    val eventLocation: String?,
    val competitionIdentifier: String,
    val competitionShortName: String?,
    val competitionName: String,
    val ratingCategoryName: String?,
    /**
     * Geplanter Zeitpunkt der Ehrung. Bleibt vorerst immer `null`: der Zeitplan kennt nur freie
     * Slots mit Freitext-Namen, ohne Bezug zu einem Wettkampf. Ist der Wert `null`, entfällt die
     * Zeile ersatzlos - ein leerer Platzhalter wäre auf dem Pult schlimmer als gar nichts.
     */
    val ceremonyTime: LocalDateTime?,
    val ranks: List<AwardCeremonyRank>,
    val density: AwardCeremonyDensity,
)

data class AwardCeremonyRank(
    val rank: Int,
    /** Mehrere Boote teilen sich diesen Rang. */
    val shared: Boolean,
    /** Das erste Boot des Rangs - nur dort wird die Rangzahl gedruckt. */
    val first: Boolean,
    val team: AwardCeremonyTeam,
)

data class AwardCeremonyTeam(
    /** Titelzeile: der Verein, oder bei gemischter Crew die Vereinskette in Bootsreihenfolge. */
    val clubLine: String,
    /** Der meldende Verein - nur gesetzt, wenn er von [clubLine] abweicht. */
    val registeringClub: String?,
    /** „Boot „RCN I" · Startnummer 3" - reduziert auf die vorhandenen Teile. */
    val boatLine: String,
    val time: String?,
    /** „Zeitstrafe +10 s (Frühstart)" - null ohne Strafe. */
    val penalty: String?,
    /** „Finale A · 15.08., 14:35" - null, wenn nichts davon vorliegt. */
    val raceLine: String?,
    val athletes: List<AwardCeremonyAthlete>,
)

data class AwardCeremonyAthlete(
    val name: String,
    val role: String,
    /** Heimatverein - nur gesetzt, wenn er von der Titelzeile des Bootes abweicht. */
    val club: String?,
)
