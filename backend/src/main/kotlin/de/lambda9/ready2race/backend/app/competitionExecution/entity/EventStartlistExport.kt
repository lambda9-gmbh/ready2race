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
 * Ein Lauf des Sammelexports, so weit Delta-Abgleich, Sortierung, Dateiname und Vorschau ihn
 * brauchen.
 */
data class BulkStartlistMatch(
    val setupMatchId: UUID,
    /**
     * Geplante Startzeit - Sortierschlüssel der großen CSV. Null blockiert den Export
     * ([CompetitionExecutionError.StartlistMatchesWithoutStartTime]); die Vorschau weist solche
     * Läufe als nicht exportierbar aus.
     */
    val startTime: LocalDateTime?,
    /** Rundenname für den ZIP-Dateinamen (wie beim Runden-Export) und die Vorschau. */
    val roundName: String,
    /** Laufname (competition_setup_match.name) - nur für die Vorschau und Fehlermeldungen. */
    val matchName: String?,
    /**
     * Die Lauf-Mannschafts-Kennungen (competition_match_team.id) - je Runde eindeutig und damit
     * das einzige belastbare Kriterium für den Delta-Abgleich gegen den RaceClocker-Feed
     * (siehe RaceClockerAssignmentLogic.matchInFeed).
     */
    val matchTeamIds: List<UUID>,
)

/**
 * Eine Zeile der Wettkampf-Abfrage des Sammelexports (getForBulkStartlistExport): Kennung,
 * Adresse und Kennung des angewählten RaceClocker-Rennens (beides null = keines angewählt).
 * Die Rennen-Kennung trägt den optionalen Filter „nur Wettkämpfe dieses Rennens" - seit dem
 * Ein-Rennen-Modell ist die Anwahl je Wettkampf eindeutig.
 */
data class BulkStartlistCompetitionRow(
    val competitionId: UUID,
    val identifier: String,
    val raceUrl: String?,
    val raceId: UUID?,
    /** Kürzel und Name - nur für Vorschau und Fehlermeldungen, der Export selbst braucht sie nicht. */
    val shortName: String?,
    val name: String?,
)

/**
 * Ein Wettkampf des Sammelexports: seine Kennung (Dateiname, Reihenfolge der ZIP-Einträge), das
 * angewählte RaceClocker-Rennen (Delta-Abgleich; null = keines angewählt) und die zu
 * exportierenden Läufe. Kürzel und Name tragen Vorschau und Fehlermeldungen.
 */
data class BulkStartlistCompetition(
    val competitionId: UUID,
    val identifier: String,
    val shortName: String?,
    val name: String?,
    val raceUrl: String?,
    val matches: List<BulkStartlistMatch>,
)

/**
 * Eine Zeile der Export-Vorschau (GET /event/{eventId}/schedule/startlists/preview): genau die
 * Läufe, die der Export mit denselben Parametern exportieren würde - dieselbe Plan-Logik, keine
 * zweite Wahrheit. [startTime] null heißt: der Lauf würde den Export blockieren; die Oberfläche
 * nimmt ihn aus der Auswahl.
 */
data class EventStartlistPreviewMatchDto(
    val matchId: UUID,
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionShortName: String?,
    val competitionName: String?,
    val roundName: String,
    val matchName: String?,
    val startTime: LocalDateTime?,
    /**
     * Fehlt der Lauf im Feed des angewählten RaceClocker-Rennens? Im Delta-Modus trivialerweise
     * überall true (die anderen sind schon herausgefiltert); ohne Delta trägt das Flag die
     * Information. null = kein Rennen angewählt, es gibt keine Vergleichsbasis.
     */
    val missingInRaceClocker: Boolean?,
)
