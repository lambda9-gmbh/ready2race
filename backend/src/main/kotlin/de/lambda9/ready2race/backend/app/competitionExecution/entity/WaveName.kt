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
 * Die RaceClocker-Vorgabe: die geplante Startzeit gehört mit in den Namen ("10:30 AF1"), damit sie
 * auf dem Zeitnahme-Gerät sichtbar ist, ohne extra nachzuschlagen.
 */
object WaveName {

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * `"HH:mm <matchName>"`, wenn [startTime] gesetzt ist - sonst bleibt [matchName] unverändert
     * (auch wenn er selbst `null` ist, denn ohne geplante Startzeit gibt es nichts hinzuzufügen).
     */
    fun format(matchName: String?, startTime: LocalDateTime?): String? =
        if (startTime != null) {
            "${startTime.format(TIME_FORMAT)} ${matchName ?: ""}".trim()
        } else {
            matchName
        }
}
