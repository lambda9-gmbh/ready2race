package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Das Format des Startlisten-Sammelexports am Zeitplan-Tab.
 *
 * [ZIP] packt eine CSV je Wettkampf (Dateinamen wie beim Runden-Export) - die Form, die Webscorer
 * braucht, weil dort jeder Wettkampf ein eigenes Rennen ist. [CSV] schreibt eine große Datei, nach
 * Startzeit über alles sortiert - die Form für RaceClocker, wo ein Rennen alle Wellen trägt.
 * Vorbelegt wird nach dem Zeitnahmesystem der Veranstaltung, umschaltbar bleibt beides.
 */
enum class EventStartlistFileType { ZIP, CSV }

/**
 * Ein Lauf des Sammelexports, so weit Delta-Abgleich, Sortierung und Dateiname ihn brauchen.
 */
data class BulkStartlistMatch(
    val setupMatchId: UUID,
    /** Geplante Startzeit - Sortierschlüssel der großen CSV. Null reißt den Export später ohnehin. */
    val startTime: LocalDateTime?,
    /** Rundenname für den ZIP-Dateinamen (wie beim Runden-Export). */
    val roundName: String,
    /**
     * Die Lauf-Mannschafts-Kennungen (competition_match_team.id) - je Runde eindeutig und damit
     * das einzige belastbare Kriterium für den Delta-Abgleich gegen den RaceClocker-Feed
     * (siehe RaceClockerAssignmentLogic.matchInFeed).
     */
    val matchTeamIds: List<UUID>,
)

/**
 * Ein Wettkampf des Sammelexports: seine Kennung (Dateiname, Reihenfolge der ZIP-Einträge), das
 * angewählte RaceClocker-Rennen (Delta-Abgleich; null = keines angewählt) und die zu
 * exportierenden Läufe.
 */
data class BulkStartlistCompetition(
    val competitionId: UUID,
    val identifier: String,
    val raceUrl: String?,
    val matches: List<BulkStartlistMatch>,
)
