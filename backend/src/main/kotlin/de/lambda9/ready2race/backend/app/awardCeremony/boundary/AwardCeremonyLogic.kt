package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.*
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RankedEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AwardCeremonyLogic {

    /** Geehrt wird bis Rang drei - die Zahl der Medaillensätze, nicht die Zahl der Boote. */
    const val MAX_RANK = 3

    // Ohne Wochentag: dessen Abkürzung hängt an der CLDR-Fassung des JDK ("Sa" vs. "Sa.") und
    // machte den Test von der Java-Version abhängig. Der Tag steht ohnehin im Datum.
    private val raceTimeFormat = DateTimeFormatter.ofPattern("dd.MM., HH:mm", Locale.GERMANY)

    /**
     * Die geehrten Boote einer Wertungskategorie, aus deren fertiger Rangliste.
     *
     * Gezählt und sortiert hat `RatingCategoryRanking.groupAndRank` - dieselbe Rechnung wie auf der
     * öffentlichen Ergebnisseite und im Ergebnis-PDF. Hier kommt nur dazu, was allein die
     * Siegerehrung angeht: der Schnitt bei [MAX_RANK] und die Kennzeichen [AwardCeremonyRank.shared]
     * und [AwardCeremonyRank.first].
     *
     * Bei einem Gleichstand auf zwei gibt es keine Bronze - das ist fachlich richtig und kein Fehler
     * in der Ausgabe. Beginnt eine Gruppe Gleichplatzierter dagegen noch innerhalb der ersten drei
     * Ränge, kommen *alle* ihre Boote auf das Blatt, auch wenn dadurch mehr als drei Blöcke
     * entstehen: geehrt wird, wer den Rang hat.
     *
     * Boote ohne Platz - abgemeldet, ausgeschieden, disqualifiziert oder noch nicht gewertet - haben
     * auf einem Siegerehrungsbogen nichts zu suchen; in der Ergebnisliste stehen sie weiterhin.
     */
    fun ranks(entries: List<RankedEntry<AwardCeremonyCandidate>>): List<AwardCeremonyRank> = entries
        .mapNotNull { entry -> entry.categoryPlace?.let { it to entry.item } }
        .filter { (place, _) -> place <= MAX_RANK }
        // groupBy hält die Reihenfolge der zuerst gesehenen Schlüssel, und die Einträge kommen
        // bereits nach Platz und Startnummer sortiert an - die Blockfolge bleibt damit die des Blatts.
        .groupBy { (place, _) -> place }
        .flatMap { (place, tied) ->
            tied.mapIndexed { position, (_, candidate) ->
                AwardCeremonyRank(
                    rank = place,
                    shared = tied.size > 1,
                    first = position == 0,
                    team = team(candidate),
                )
            }
        }

    internal fun team(candidate: AwardCeremonyCandidate): AwardCeremonyTeam {
        // Der Verein, den eine Person trägt - dieselbe Regel wie im Schiedsrichter-Board. Der
        // meldende Verein steht bewusst NICHT in dieser Kette: wer gemeldet hat, ist Verwaltung.
        val worn = candidate.participants.map {
            ClubComposition.clubWorn(it.external, it.externalClubName, it.ownClubName)
        }

        // Dieselbe Regel wie auf der Urkunde, deshalb an einer Stelle: volle Namen, keine
        // Kurzformen, ersatzweise der meldende Verein. Begründung bei ClubComposition.printedLine.
        val clubLine = ClubComposition.printedLine(worn, candidate.registeringClubName)

        // Vergleich über den Namensschlüssel, nicht über die Zeichenkette: zwei Schreibvarianten
        // desselben Vereins ("Rostocker Ruderclub" / "...von 1885 e.V.") fasst ClubComposition zu
        // einem Kettenglied zusammen, behält dabei aber die zuerst gesehene Schreibweise - ein
        // roher Stringvergleich sähe dann fälschlich einen Unterschied. Bei mehreren Vereinen
        // bleibt clubLine die zusammengesetzte Kette "A / B"; kein einzelner Vereinsname ist je
        // gleich dieser Kette, und genau das muss für die gemischte Crew so bleiben.
        //
        // Die Gleichheit der ganzen Zeichenkette steht bewusst vorn: die Kettenglieder werden
        // hier aus der fertigen Zeile zurückgewonnen, und ein Verein, der das Trennzeichen im
        // Namen trägt ("Ruder- / Kanuverein X"), zerfiele dabei in zwei Glieder. Der direkte
        // Vergleich fängt genau diesen Fall.
        val clubLineKeys = clubLine.split(ClubComposition.SEPARATOR).map(ClubNameKey::of).toSet()
        fun sameAsClubLine(name: String) =
            name == clubLine || (clubLineKeys.size == 1 && ClubNameKey.of(name) in clubLineKeys)

        return AwardCeremonyTeam(
            clubLine = clubLine,
            // Sagt die Titelzeile schon alles, wäre "Meldender Verein: dasselbe" reine
            // Wiederholung - dann entfällt die Zeile.
            registeringClub = candidate.registeringClubName.takeIf { it.isNotBlank() && !sameAsClubLine(it) },
            boatLine = formatBoatLine(candidate.teamName, candidate.startNumber),
            time = candidate.time,
            penalty = formatPenalty(candidate.penaltySeconds, candidate.penaltyNote),
            raceLine = formatRaceLine(candidate.roundName, candidate.matchName, candidate.matchTime),
            athletes = candidate.participants.map { participant ->
                val club = ClubComposition.clubWorn(
                    participant.external,
                    participant.externalClubName,
                    participant.ownClubName,
                )
                AwardCeremonyAthlete(
                    name = "${participant.firstName} ${participant.lastName}",
                    year = participant.year,
                    role = participant.role,
                    club = club?.takeIf { it.isNotBlank() && !sameAsClubLine(it) },
                )
            },
        )
    }

    fun formatBoatLine(teamName: String?, startNumber: Int): String = listOfNotNull(
        teamName?.takeIf { it.isNotBlank() }?.let { "Boot „$it“" },
        "Startnummer $startNumber",
    ).joinToString(" · ")

    /**
     * Die Strafe hängt an den Sekunden, nicht an der Notiz: eine Notiz ohne Sekunden ist keine
     * Zeitstrafe und darf auf dem Blatt nicht wie eine aussehen.
     */
    fun formatPenalty(seconds: Int?, note: String?): String? = seconds?.let {
        val text = "Zeitstrafe +$it s"
        val reason = note?.takeIf { n -> n.isNotBlank() }
        if (reason == null) text else "$text ($reason)"
    }

    /**
     * Der Laufname ist die genauere Angabe („Finale A") und verdrängt deshalb den Rundennamen
     * („Finale"); fehlt er - auch als leerer Text -, tritt die Runde an seine Stelle. Fehlt
     * beides und die Uhrzeit, gibt es keine Zeile - eine leere Klammer wäre schlechter als gar
     * nichts.
     */
    fun formatRaceLine(roundName: String?, matchName: String?, at: LocalDateTime?): String? =
        listOfNotNull(
            listOfNotNull(matchName, roundName).firstOrNull { it.isNotBlank() },
            at?.format(raceTimeFormat),
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    fun sheet(
        eventName: String,
        eventDate: String,
        eventLocation: String?,
        competitionIdentifier: String,
        competitionShortName: String?,
        competitionName: String,
        ratingCategoryName: String?,
        entries: List<RankedEntry<AwardCeremonyCandidate>>,
    ): AwardCeremonySheet =
        AwardCeremonySheet(
            eventName = eventName,
            eventDate = eventDate,
            eventLocation = eventLocation,
            competitionIdentifier = competitionIdentifier,
            competitionShortName = competitionShortName,
            competitionName = competitionName,
            ratingCategoryName = ratingCategoryName,
            // Siehe AwardCeremonySheet.ceremonyTime: der Zeitplan gibt den Termin (noch) nicht her.
            ceremonyTime = null,
            ranks = ranks(entries),
        )
}
