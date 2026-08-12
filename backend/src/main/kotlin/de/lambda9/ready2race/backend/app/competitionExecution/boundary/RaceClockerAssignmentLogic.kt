package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import java.util.UUID

/**
 * Reine Logik der RaceClocker-Zuordnung, bewusst ohne Datenbank- und Ktor-Bezug,
 * damit sie ohne laufende Umgebung geprüft werden kann.
 */
object RaceClockerAssignmentLogic {

    /**
     * Assigns feed rows to the teams of a match, keyed by registration id - what [ParsedTeamResult]
     * expects downstream.
     *
     * The primary key is the competition match team id: unique per team *and* round, so a row can be
     * assigned without knowing anything about waves. That matters because waves are renamed and merged
     * in RaceClocker on race day ("AF4 & AF2" for a joint start), which is invisible here.
     *
     * Start lists exported before that column existed carry only the registration id, which is unique
     * per team but repeats across the rounds that share one RaceClocker race. Those rows are narrowed
     * down by the wave name - but only as long as the exported name still occurs in the feed. Once it
     * was renamed there, matching by registration alone is the better of the two guesses.
     */
    fun assignFeedRows(
        rows: List<RaceClockerFeedRow>,
        teams: List<CompetitionMatchTeamWithRegistration>,
        waveName: String?,
    ): Map<UUID, List<RaceClockerFeedRow>> {

        val byMatchTeam = teams.associate { team ->
            team.competitionRegistration to rows.filter { team.id in it.ids }
        }.filterValues { it.isNotEmpty() }

        if (byMatchTeam.isNotEmpty()) return byMatchTeam

        val candidates = if (waveName != null && rows.any { it.wave == waveName }) {
            rows.filter { it.wave == waveName }
        } else {
            rows
        }

        return teams.associate { team ->
            team.competitionRegistration to candidates.filter { team.competitionRegistration in it.ids }
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Ob dieser Lauf im geholten Rennen bereits vorhanden ist - die Delta-Frage des
     * Startlisten-Sammelexports ("nur in RaceClocker fehlende Läufe exportieren").
     *
     * Geprüft wird ausschließlich die Lauf-Mannschafts-Kennung (competition_match_team.id) in
     * RaceClockers "Extra info" - dieselbe Kennung, über die [assignFeedRows] primär zuordnet, und
     * die einzige, die je Runde eindeutig ist. Daraus folgen zwei bewusste Entscheidungen:
     *
     * - Zeilen ohne Kennungen können einen Lauf nie als vorhanden belegen. Fehlt das UUID-Mapping
     *   im Feed (Startliste ohne Lauf-Spalte importiert oder Spalte beim Import nicht auf
     *   "Extra info" gelegt), gilt der Lauf als fehlend und wird exportiert - lieber eine Welle
     *   doppelt angeboten (das fällt beim Import sofort auf) als still ausgelassen. Der Wellenname
     *   wäre das Zweitkriterium für diese Fälle, ist aber bewusst NICHT gebaut: Wellen werden am
     *   Renntag in RaceClocker umbenannt und zusammengelegt ("AF4 & AF2"), ein Abgleich darüber
     *   riete also genau dann, wenn es darauf ankommt.
     * - Die Meldungs-Kennung (registration id) scheidet ebenfalls aus: Sie wiederholt sich über
     *   die Runden desselben Rennens und meldete eine Folgerunde als vorhanden, sobald die
     *   Vorrunde importiert war.
     */
    fun matchInFeed(rows: List<RaceClockerFeedRow>, matchTeamIds: List<UUID>): Boolean =
        rows.any { row -> matchTeamIds.any { it in row.ids } }
}
