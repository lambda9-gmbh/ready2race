package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import java.time.LocalDateTime
import java.util.UUID

data class CompetitionMatchDto(
    val id: UUID,
    val name: String?,
    val teams: List<CompetitionMatchTeamDto>,
    val weighting: Int,
    val executionOrder: Int,
    /** Geplanter Start aus dem Zeitplan. */
    val startTime: LocalDateTime?,
    val startTimeOffset: Long?,
    val currentlyRunning: Boolean,
    /** Tatsächlicher Start - null, solange niemand gestartet hat. */
    val startedAt: LocalDateTime?,
    /** Persistiertes Ende. Gesetzt heißt ausschließlich: jemand hat den Lauf beendet. */
    val finishedAt: LocalDateTime?,
    /** Der Zeitstrahl-Slot dieses Laufs ist abgesagt. */
    val skipped: Boolean,
    /**
     * Der abgeleitete Lauf-Zustand für den Status-Chip. [currentlyRunning] bleibt daneben stehen:
     * an ihm hängt die Checkbox "Aktuell laufend" und der Rahmen der Karte, und die sind nicht
     * Teil dieser Änderung.
     */
    val status: MatchStatusDto,
    /** Wann der automatische RaceClocker-Abruf diesen Lauf zuletzt versucht hat. */
    val raceClockerPolledAt: LocalDateTime?,
    /** ErrorCode des letzten fehlgeschlagenen Abrufs, null = in Ordnung. */
    val raceClockerPollError: String?,
    /** Gesetzt, solange die Automatik diesen Lauf wegen einer Handeingabe in Ruhe lässt. */
    val raceClockerAutoPausedAt: LocalDateTime?,
)
