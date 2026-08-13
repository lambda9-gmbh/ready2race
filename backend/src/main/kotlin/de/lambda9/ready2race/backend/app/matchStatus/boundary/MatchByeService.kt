package de.lambda9.ready2race.backend.app.matchStatus.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.matchStatus.control.MatchByeRepo
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeTeam
import de.lambda9.tailwind.core.extensions.kio.orDie
import org.jooq.Record
import java.util.UUID

object MatchByeService {

    /**
     * Die Freilose einer Veranstaltung, nach Setup-Lauf. Läufe ohne Freilos fehlen in der Karte -
     * die Aufrufer fragen mit `byeByMatch[matchId]` und bekommen null.
     */
    fun byeByMatch(eventId: UUID, competitionId: UUID? = null): App<Nothing, Map<UUID, MatchByeDto>> =
        MatchByeRepo.getByeInputs(eventId, competitionId).orDie().map { rows -> group(rows) }

    /**
     * Der Anzeigename einer Mannschaft: Verein, dahinter der Meldungsname, falls es einen gibt -
     * dieselbe Zusammensetzung, die das Panel "Teams mit Freilos" auf der Durchführungsseite zeigt.
     */
    private fun teamName(record: Record): String = listOfNotNull(
        record.get("club_name", String::class.java),
        record.get("team_name", String::class.java),
    ).joinToString(" ")

    private fun group(rows: List<Record>): Map<UUID, MatchByeDto> =
        rows.groupBy { it.get("setup_match_id", UUID::class.java)!! }
            .mapNotNull { (matchId, matchRows) ->
                val bye = MatchStatusLogic.deriveBye(
                    roundRequired = matchRows.first().get("round_required", Boolean::class.java) == true,
                    mustRace = matchRows.first().get("bye_must_race", Boolean::class.java) == true,
                    teams = matchRows.map { row ->
                        MatchByeTeam(
                            racing = row.get("team_out", Boolean::class.java) != true,
                            name = teamName(row),
                            deregistered = row.get("deregistered", Boolean::class.java) == true,
                            deregistrationReason = row.get("deregistration_reason", String::class.java),
                            seed = row.get("team_seed", Int::class.javaObjectType),
                        )
                    },
                )
                bye?.let { matchId to it }
            }
            .toMap()
}
