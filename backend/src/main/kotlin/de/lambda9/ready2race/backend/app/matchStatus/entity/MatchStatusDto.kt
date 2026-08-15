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
 * "Überfällig", "Teilweise gewertet" und "n abgemeldet" sind bewusst KEINE eigenen Zustände,
 * sondern Ablesungen aus diesen Feldern:
 * - Teilweise gewertet: `state != RUNNING && 0 < teamsRaced < teamsTotal - teamsDeregistered`
 * - Überfällig: `state == UPCOMING && startTime + 5 min < jetzt`
 * - n abgemeldet: `teamsDeregistered > 0`
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
    /**
     * Davon erledigt (`LiveDashboardLogic.teamIsSettled`): Platz ODER ausgeschieden ODER
     * abgemeldet. Die Zahl beantwortet "wartet hier noch jemand auf ein Ergebnis" und trägt
     * deshalb [MatchState.AWAITING_FINISH] und die Aktivierungskette - NICHT die Ablesung
     * "Teilweise gewertet".
     */
    val teamsScored: Int,
    /**
     * Davon tatsächlich gefahren (`LiveDashboardLogic.teamHasRaced`): Platz ODER ausgeschieden,
     * ohne die Abgemeldeten. Nur diese Zahl darf "Teilweise gewertet" auslösen. Vor dem 14.08.2026
     * gab es sie nicht, und ein Lauf mit einer Abmeldung und vier offenen Booten stand auf dem
     * Schiedsrichter-Board als "Teilweise gewertet 1/5" - obwohl niemand gefahren war.
     */
    val teamsRaced: Int = 0,
    /**
     * Davon für diese Runde abgemeldet. Reiner Ausweis: er wird als zweiter, leiser Chip neben dem
     * Zustands-Chip angezeigt (dieselbe Kategorie wie "Arena 0/5") und entscheidet über keinen
     * Zustand. `teamsTotal - teamsDeregistered` ist die Zahl der Boote, von denen überhaupt ein
     * Ergebnis erwartet wird.
     */
    val teamsDeregistered: Int = 0,
    /** null = in dieser Ansicht nicht erhoben (Zeitplan, öffentliche Anzeigen). */
    val teamsInArena: Int? = null,
    /**
     * Gesetzt, wenn dieser Lauf ein Freilos ist - siehe `MatchStatusLogic.deriveBye`. Wie
     * "Überfällig" und "Teilweise gewertet" ist das eine Ablesung und ausdrücklich KEIN eigener
     * [MatchState]: ein neuer Wert in der Aufzählung fiele still in jedes `when`/`switch`, das
     * heute über sie verzweigt.
     */
    val bye: MatchByeDto? = null,
)

/**
 * Der letzte Steg-Scan je bekanntem Crew-Mitglied einer Mannschaft: Scan-Art zu Zeitpunkt, oder
 * null für jede Person, die nie gescannt wurde. Genau die Eingabe, die
 * [de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic.teamInArenaAt]
 * erwartet - der Alias gibt ihr nur einen Namen, damit die Signaturen hier lesbar bleiben.
 */
typealias CrewLastScans = List<Pair<String, LocalDateTime>?>

/**
 * Die drei Angaben je Mannschaft, aus denen sich "erledigt" (`LiveDashboardLogic.teamIsSettled`)
 * und "gefahren" (`LiveDashboardLogic.teamHasRaced`) ergeben. Bewusst ein eigener, minimaler Typ
 * statt eines der großen Team-DTOs: die Ableitung soll ohne Datenbank und ohne Ansichtskontext
 * prüfbar bleiben.
 */
data class MatchStatusTeam(
    val place: Int?,
    val failed: Boolean,
    val deregistered: Boolean,
    /**
     * Vergebenes Freilos (V202608151900). Zählt wie eine Abmeldung als erledigt - auf dieses Boot
     * wartet kein Ergebnis mehr -, aber ausdrücklich NICHT als gefahren: Es war nicht auf dem
     * Wasser. Ohne den ersten Teil erreichte ein Lauf mit Freilos nie "alle gewertet", und die
     * Aktivierungskette bliebe an ihm hängen.
     */
    val bye: Boolean = false,
)

/**
 * Die Zahlen der Zählerleiste über einer Runde ("1 läuft · 1 offen · 3 beendet · 1 abgesagt").
 *
 * [open] ist alles, was noch eine Handlung verlangt: anstehend, ungeplant und - der wichtigste
 * Fall - vollständig gewertet, aber nicht beendet ([MatchState.AWAITING_FINISH]). Ein solcher Lauf
 * darf nicht unter "beendet" verschwinden, denn auf seinen Beenden-Klick wartet die Kette.
 *
 * Jeder Lauf zählt weiterhin in genau einen Topf; [preparing] nimmt seine Läufe [running] weg und
 * nicht [open]. Ein Lauf am Start ist keine offene Handlung des Regattabüros, sondern der Lauf, der
 * gerade dran ist - deshalb steht er neben "läuft" und nicht darunter.
 */
data class RoundCountersDto(
    val total: Int,
    /** Am Start gerufen, aber noch nicht unterwegs — zählt weder als „läuft" noch als „offen". */
    val preparing: Int,
    val running: Int,
    val open: Int,
    val finished: Int,
    val skipped: Int,
)
