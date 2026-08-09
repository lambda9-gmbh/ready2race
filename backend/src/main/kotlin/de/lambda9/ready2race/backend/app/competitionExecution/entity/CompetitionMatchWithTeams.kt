package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.time.LocalDateTime
import java.util.UUID

data class CompetitionMatchWithTeams(
    val competitionSetupMatch: UUID,
    /** Geplanter Start aus dem Zeitplan. */
    val startTime: LocalDateTime?,
    val currentlyRunning: Boolean,
    /** Tatsächlicher Start - null, solange niemand gestartet hat. */
    val startedAt: LocalDateTime?,
    /** Persistiertes Ende. Gesetzt heißt ausschließlich: jemand hat den Lauf beendet. */
    val finishedAt: LocalDateTime?,
    /** Der Zeitstrahl-Slot dieses Laufs ist abgesagt (`event_schedule_slot.skipped_at`). */
    val skipped: Boolean,
    /** Wann der automatische RaceClocker-Abruf diesen Lauf zuletzt versucht hat. */
    val raceClockerPolledAt: LocalDateTime?,
    /** ErrorCode des letzten fehlgeschlagenen Abrufs, null = in Ordnung. */
    val raceClockerPollError: String?,
    /** Gesetzt, solange die Automatik diesen Lauf wegen einer Handeingabe in Ruhe lässt. */
    val raceClockerAutoPausedAt: LocalDateTime?,
    val teams: List<CompetitionMatchTeamWithRegistration>
)