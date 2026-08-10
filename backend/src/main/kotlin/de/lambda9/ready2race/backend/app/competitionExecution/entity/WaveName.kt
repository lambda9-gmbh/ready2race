package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Der RaceClocker-"Wellen-Name" eines Laufs - einmal gebaut, an genau zwei Stellen verwendet:
 * [de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.buildCsv]
 * schreibt ihn in die Startliste, und
 * [de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo.getForRaceClockerPull]
 * leitet denselben Namen für den Ergebnis-Pull-Fallback-Filter ab (siehe
 * `CompetitionExecutionService.assignFeedRows`: Zeilen ohne bekannte Team-ID werden über den
 * Wellen-Namen aus dem Feed gefiltert). Weichen Export und Pull hier auch nur in der Formatierung
 * voneinander ab, greift der Fallback-Filter nicht mehr - deshalb genau eine Funktion für beide.
 *
 * Die RaceClocker-Vorgabe: die geplante Startzeit gehört mit in den Namen ("10:30 | 12 JM4x | AF1"),
 * damit sie auf dem Zeitnahme-Gerät sichtbar ist, ohne extra nachzuschlagen. Rennnummer und Kürzel
 * des Wettkampfs stehen aus demselben Grund dabei: die Wellen-Liste eines RaceClocker-Rennens hält
 * alle Wettkämpfe einer Veranstaltung nebeneinander, und "AF1" allein sagt dort nicht, um welches
 * Rennen es geht. Die Startzeit bleibt vorn, damit die alphabetisch sortierte Liste chronologisch
 * bleibt.
 */
object WaveName {

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private const val BLOCK_SEPARATOR = " | "

    /**
     * `"HH:mm | <identifier> <shortName> | <matchName>"`, wobei jeder der drei Blöcke samt
     * Trennzeichen entfällt, wenn er nichts beizutragen hat - ein Wettkampf ohne Kürzel wird also zu
     * `"10:30 | 12 | AF1"` und nicht zu `"10:30 | 12  | AF1"`. Ist gar nichts bekannt, bleibt es
     * `null`; in der Praxis tritt das nicht ein, weil die Rennnummer im Schema Pflicht ist.
     */
    fun format(
        matchName: String?,
        startTime: LocalDateTime?,
        competitionIdentifier: String?,
        competitionShortName: String?,
    ): String? {
        val competition = listOfNotNull(competitionIdentifier, competitionShortName).join(" ")

        return listOfNotNull(
            startTime?.format(TIME_FORMAT),
            competition,
            matchName,
        ).join(BLOCK_SEPARATOR)
    }

    /** Leere und blanke Teile fallen weg; bleibt nichts übrig, ist das Ergebnis `null`. */
    private fun List<String>.join(separator: String): String? =
        filter { it.isNotBlank() }
            .joinToString(separator) { it.trim() }
            .takeIf { it.isNotBlank() }
}
