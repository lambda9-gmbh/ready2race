package de.lambda9.ready2race.backend.app.awardCeremony.entity

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
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
    /**
     * Die Wertungskategorie der Mannschaft, `null` ohne Zuordnung. Der ganze Verweis statt nur des
     * Namens, weil `RatingCategoryRanking.groupAndRank` über die Id gruppiert und über die
     * Sortierstelle anordnet - dieselbe Rechnung wie in jeder anderen Ergebnisanzeige.
     */
    val ratingCategory: RatingCategoryRef?,
    /** Der meldende Verein der Mannschaft - reine Verwaltung, nicht der Verein der Personen. */
    val registeringClubName: String,
    val teamName: String?,
    val time: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val roundName: String?,
    val matchName: String?,
    val matchTime: LocalDateTime?,
    /**
     * Die Partie, in der der Platz VERGEBEN wurde - nur bei Wertung je Partie gesetzt (siehe
     * `TeamPlacement`). Nicht dasselbe wie [matchName]: der beschreibt das zuletzt gefahrene
     * Rennen fürs Blatt, diese beiden hier steuern Rechnung und Blockbildung - jede Partie zählt
     * ihre Ränge für sich, und nur Boote derselben Partie können sich einen teilen.
     */
    val placementMatchName: String? = null,
    val placementMatchWeighting: Int? = null,
    val participants: List<AwardCeremonyCandidateParticipant>,
)

data class AwardCeremonyCandidateParticipant(
    val firstName: String,
    val lastName: String,
    /** Jahrgang - die Sprecherin liest ihn bei der Ehrung mit vor (Wunsch von Lea, 10.08.2026). */
    val year: Int,
    val role: String,
    val external: Boolean?,
    val externalClubName: String?,
    /** Der eigene Verein der Person; bei Gastruderern leer, dort trägt [externalClubName]. */
    val ownClubName: String?,
)

data class AwardCeremonyChoiceDto(
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionShortName: String?,
    val competitionName: String,
    /**
     * Der Schlüssel der Wertung - `null` heißt „der Wettkampf als Ganzes". Die Id statt des Namens,
     * weil zwei gleichnamige Kategorien sonst dieselbe Ehrung bezeichneten.
     */
    val ratingCategoryId: UUID?,
    /** Reiner Anzeigewert; ausgewählt wird über [ratingCategoryId]. */
    val ratingCategoryName: String?,
    /**
     * Die Zahl der Boote, die auf dem Blatt stehen: Ränge bis drei, Gleichstände eingeschlossen.
     *
     * Bewusst nicht die Zahl aller platzierten Boote der Wertung. Die Zahl steht in der Auswahl
     * neben der Ehrung und beantwortet dort die Frage „was bekomme ich, wenn ich das ankreuze" -
     * „8 Boote" neben einem Blatt mit drei Booten wäre genau die falsche Antwort.
     */
    val awardedTeams: Int,
)

/**
 * Die Einheit einer Seite. `null` als Kategorie ist ein gültiger Wert, kein „unbekannt".
 *
 * Der Name der Wertung steht bewusst nicht mehr darin: er würde hier nichts auswählen, aber eine
 * Auswahl, die nur ihn schickte, träfe still die Ehrung ohne Wertung.
 */
data class AwardCeremonyKeyRequest(
    val competitionId: UUID,
    val ratingCategoryId: UUID?,
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
                        ratingCategoryId = UUID.randomUUID(),
                    )
                )
            )
    }
}

/**
 * Eine Ehrung, in aller Regel ein A4-Blatt. Wie eng sie gesetzt wird - und ob sie ausnahmsweise
 * auf mehrere Blätter läuft - steht bewusst nicht hier: das misst AwardCeremonyPdf am fertig
 * gesetzten Blatt, weil der Zeilenumbruch an den Textlängen hängt.
 */
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
)

data class AwardCeremonyRank(
    val rank: Int,
    /** Mehrere Boote teilen sich diesen Rang. */
    val shared: Boolean,
    /**
     * Das erste Boot des Rangs - innerhalb eines Blatts trägt nur dieses die große Rangzahl.
     * Eröffnet ein weiteres Boot desselben Rangs eine Fortsetzungsseite, druckt AwardCeremonyPdf
     * die Zahl dort zusätzlich; sonst stünde der Rang auf jenem Blatt nur im kleinen Vermerk.
     */
    val first: Boolean,
    val team: AwardCeremonyTeam,
)

data class AwardCeremonyTeam(
    /** Titelzeile: der Verein, oder bei gemischter Crew die Vereinskette in Bootsreihenfolge. */
    val clubLine: String,
    /** Der meldende Verein - nur gesetzt, wenn er von [clubLine] abweicht. */
    val registeringClub: String?,
    /** `Boot „RCN I“ · Startnummer 3` - reduziert auf die vorhandenen Teile. */
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
    /** Jahrgang, auf dem Blatt hinter dem Namen. */
    val year: Int,
    val role: String,
    /** Heimatverein - nur gesetzt, wenn er von der Titelzeile des Bootes abweicht. */
    val club: String?,
)
