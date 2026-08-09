package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.*
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AwardCeremonyLogic {

    /** Geehrt wird bis Rang drei - die Zahl der Medaillensätze, nicht die Zahl der Boote. */
    const val MAX_RANK = 3

    // Ohne Wochentag: dessen Abkürzung hängt an der CLDR-Fassung des JDK ("Sa" vs. "Sa.") und
    // machte den Test von der Java-Version abhängig. Der Tag steht ohnehin im Datum.
    private val raceTimeFormat = DateTimeFormatter.ofPattern("dd.MM., HH:mm", Locale.GERMANY)

    fun groupByRatingCategory(
        candidates: List<AwardCeremonyCandidate>,
    ): List<Pair<String?, List<AwardCeremonyCandidate>>> = candidates
        .groupBy { it.ratingCategoryName }
        .toList()
        // Kategorien alphabetisch, die Gruppe ohne Kategorie zuletzt: sie ist bei gemischten
        // Wettkämpfen der Rest, nicht der Anfang.
        .sortedWith(compareBy(nullsLast<String>()) { it.first })

    /**
     * Die Ränge einer Wertungskategorie, neu ab 1 gezählt.
     *
     * Standard-Wettkampfranking: gleicher Platz im Gesamtfeld ⇒ gleicher Rang in der Kategorie,
     * der nächste Rang überspringt entsprechend viele Stellen (1, 2, 2, 4). Bei einem Gleichstand
     * auf zwei gibt es damit keine Bronze - das ist fachlich richtig und kein Fehler in der
     * Ausgabe.
     *
     * Beginnt eine Gruppe von Gleichplatzierten noch innerhalb der ersten drei Ränge, kommen
     * *alle* ihre Boote auf das Blatt, auch wenn dadurch mehr als drei Blöcke entstehen: geehrt
     * wird, wer den Rang hat.
     */
    fun rank(candidates: List<AwardCeremonyCandidate>): List<AwardCeremonyRank> {
        val sorted = candidates.sortedWith(compareBy({ it.competitionPlace }, { it.startNumber }))
        val ranks = mutableListOf<AwardCeremonyRank>()

        var index = 0
        while (index < sorted.size) {
            val rank = index + 1
            if (rank > MAX_RANK) break

            val place = sorted[index].competitionPlace
            val tied = sorted.drop(index).takeWhile { it.competitionPlace == place }

            tied.forEachIndexed { position, candidate ->
                ranks.add(
                    AwardCeremonyRank(
                        rank = rank,
                        shared = tied.size > 1,
                        first = position == 0,
                        team = team(candidate),
                    )
                )
            }

            index += tied.size
        }

        return ranks
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
        candidates: List<AwardCeremonyCandidate>,
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
            ranks = rank(candidates),
        )
}
