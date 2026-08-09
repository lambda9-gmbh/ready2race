package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.*
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AwardCeremonyLogic {

    /** Geehrt wird bis Rang drei - die Zahl der Medaillensätze, nicht die Zahl der Boote. */
    const val MAX_RANK = 3

    /**
     * Ab wie vielen Personenzeilen die Seite eine Schriftstufe zurückgeht. Drei Vierer mit
     * Steuermann sind 15 Zeilen und passen bequem; drei Achter mit Steuermann sind 27 und
     * passen nicht.
     */
    const val COMPACT_THRESHOLD = 18

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

        // Volle Namen, keine Kurzformen: das Blatt wird vorgelesen, und "RC Nürtingen" spricht
        // sich schlechter als der ausgeschriebene Name.
        val chain = ClubComposition.of(worn, ClubShortNameSettings.none).full
        val clubLine = chain.ifEmpty { candidate.registeringClubName }

        return AwardCeremonyTeam(
            clubLine = clubLine,
            // Sagt die Titelzeile schon alles, wäre "Meldender Verein: dasselbe" reine
            // Wiederholung - dann entfällt die Zeile.
            registeringClub = candidate.registeringClubName.takeIf { it != clubLine },
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
                    club = club?.takeIf { it.isNotBlank() && it != clubLine },
                )
            },
        )
    }

    fun formatBoatLine(teamName: String?, startNumber: Int): String = listOfNotNull(
        teamName?.takeIf { it.isNotBlank() }?.let { "Boot „$it\"" },
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
     * („Finale"); fehlt er, tritt die Runde an seine Stelle. Fehlt beides und die Uhrzeit, gibt
     * es keine Zeile - eine leere Klammer wäre schlechter als gar nichts.
     */
    fun formatRaceLine(roundName: String?, matchName: String?, at: LocalDateTime?): String? =
        listOfNotNull(
            (matchName ?: roundName)?.takeIf { it.isNotBlank() },
            at?.format(raceTimeFormat),
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    fun densityFor(personRows: Int): AwardCeremonyDensity =
        if (personRows > COMPACT_THRESHOLD) AwardCeremonyDensity.COMPACT else AwardCeremonyDensity.NORMAL

    fun sheet(
        eventName: String,
        eventDate: String,
        eventLocation: String?,
        competitionIdentifier: String,
        competitionShortName: String?,
        competitionName: String,
        ratingCategoryName: String?,
        candidates: List<AwardCeremonyCandidate>,
    ): AwardCeremonySheet {
        val ranks = rank(candidates)

        return AwardCeremonySheet(
            eventName = eventName,
            eventDate = eventDate,
            eventLocation = eventLocation,
            competitionIdentifier = competitionIdentifier,
            competitionShortName = competitionShortName,
            competitionName = competitionName,
            ratingCategoryName = ratingCategoryName,
            // Siehe AwardCeremonySheet.ceremonyTime: der Zeitplan gibt den Termin (noch) nicht her.
            ceremonyTime = null,
            ranks = ranks,
            density = densityFor(ranks.sumOf { it.team.athletes.size }),
        )
    }
}
