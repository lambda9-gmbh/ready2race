package de.lambda9.ready2race.backend.app.matchStatus.entity

import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardMatchState
import java.time.LocalDateTime

/**
 * Der richtige Name für die Aufzählung, die heute noch [LiveDashboardMatchState] heißt. Der Name
 * ist schief, seit Durchführungsseite und Zeitplan denselben Zustand führen - umbenannt wird er
 * trotzdem nicht: das zöge `documentation.yaml`, `types.gen.ts`, vier Frontend-Module, den
 * i18n-Pfad `event.liveDashboard.state.*` und `LiveDashboardLogicTest` hinter sich her, also
 * Namenskosmetik auf getestetem Code. Der Alias gibt neuem Code den richtigen Namen, ohne die
 * Leitung anzufassen.
 */
typealias MatchState = LiveDashboardMatchState

/**
 * Der Zustand eines Laufs, wie ihn alle Oberflächen lesen - eine Ableitung, drei Aufrufer
 * (Durchführungsseite, Zeitplan, Schiedsrichter-Dashboard).
 *
 * "Überfällig" und "Teilweise gewertet" sind bewusst KEINE eigenen Zustände, sondern Ablesungen
 * aus diesen Feldern:
 * - Teilweise gewertet: `state != RUNNING && 0 < teamsScored < teamsTotal`
 * - Überfällig: `state == UPCOMING && startTime + 5 min < jetzt`
 *
 * Neue Werte in [MatchState] würden still durch jedes `when`/`switch` in den `else`-Zweig fallen,
 * das heute über die Aufzählung verzweigt (`selectForScope`, `matchControls`,
 * `dashboardMatchState`) - und damit genau die getestete Kette verschieben.
 *
 * Die verstrichenen Minuten und die Überfälligkeit rechnet das Frontend gegen die Browseruhr,
 * damit der Chip zwischen zwei Polls weiterzählt statt zu stehen.
 */
data class MatchStatusDto(
    val state: MatchState,
    /** Tatsächlicher Start (`competition_match.started_at`) - null, solange niemand gestartet hat. */
    val startedAt: LocalDateTime?,
    val teamsTotal: Int,
    val teamsScored: Int,
    /** null = in dieser Ansicht nicht erhoben (Zeitplan, öffentliche Anzeigen). */
    val teamsOnWater: Int? = null,
)

/**
 * Die drei Angaben je Mannschaft, aus denen sich "gewertet" ergibt - dieselbe Regel wie im
 * Dashboard (siehe `LiveDashboardLogic.teamHasResult`). Bewusst ein eigener, minimaler Typ statt
 * eines der großen Team-DTOs: die Ableitung soll ohne Datenbank und ohne Ansichtskontext prüfbar
 * bleiben.
 */
data class MatchStatusTeam(
    val place: Int?,
    val failed: Boolean,
    val deregistered: Boolean,
)

/**
 * Die Zahlen der Zählerleiste über einer Runde ("1 läuft · 1 offen · 3 beendet · 1 abgesagt").
 *
 * [open] ist alles, was noch eine Handlung verlangt: anstehend, ungeplant und - der wichtigste
 * Fall - vollständig gewertet, aber nicht beendet ([MatchState.AWAITING_FINISH]). Ein solcher Lauf
 * darf nicht unter "beendet" verschwinden, denn auf seinen Beenden-Klick wartet die Kette.
 */
data class RoundCountersDto(
    val total: Int,
    val running: Int,
    val open: Int,
    val finished: Int,
    val skipped: Int,
)
