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
    /** Wann der Lauf an den Start gerufen wurde - null, solange ihn niemand aktiviert hat. */
    val activatedAt: LocalDateTime?,
    /** Tatsächlicher Start - null, solange niemand gestartet hat. */
    val startedAt: LocalDateTime?,
    /** Persistiertes Ende. Gesetzt heißt ausschließlich: jemand hat den Lauf beendet. */
    val finishedAt: LocalDateTime?,
    /** Der Zeitstrahl-Slot dieses Laufs ist abgesagt. */
    val skipped: Boolean,
    /**
     * Der abgeleitete Lauf-Zustand für den Status-Chip — dieselbe Ableitung wie im
     * Schiedsrichter-Dashboard und im Zeitplan. [activatedAt] bleibt daneben stehen, weil an ihm
     * der Schalter "Am Start" hängt: er fragt, ob der Lauf aufgerufen ist, nicht in welchem
     * Zustand er sich befindet.
     */
    val status: MatchStatusDto,
    /** Wann der automatische RaceClocker-Abruf diesen Lauf zuletzt versucht hat. */
    val raceClockerPolledAt: LocalDateTime?,
    /** ErrorCode des letzten fehlgeschlagenen Abrufs, null = in Ordnung. */
    val raceClockerPollError: String?,
    /** Gesetzt, solange die Automatik diesen Lauf wegen einer Handeingabe in Ruhe lässt. */
    val raceClockerAutoPausedAt: LocalDateTime?,
)
