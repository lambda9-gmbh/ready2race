package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.competitionExecution.entity.MatchTeamLapDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import java.time.LocalDateTime
import java.util.UUID

// Die Sammel-Antwort der alten Athleten-Anzeige (AthleteBoardDto) ist mit dem
// Board-Umbau vom 10.08.2026 entfallen; die Bausteine darunter tragen jetzt die
// Slots und Listen der Board-Antwort (BoardViewDto). Bewusst schlank: Jahrgang,
// Geschlecht, Teilnehmer-IDs und der externe Vereinsname fehlen, weil sie nicht
// angezeigt werden und im Mobilfunknetz kosten.
data class AthleteBoardMatch(
    val matchId: UUID,
    val competitionName: String,
    /** Wettkampf-Kürzel (short_name) für kompakte Darstellungen; null, wenn keins gepflegt ist. */
    val competitionShortName: String? = null,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    /** Geplanter Start aus dem Zeitplan. */
    val startTime: LocalDateTime?,
    /**
     * Tatsächlicher Start, nur im Block `running` gefüllt. Er trägt die Uhrzeit für „gestartet
     * 14:32"; ob der Lauf am Steg steht oder fährt, sagt dagegen [state]. Im Block `upcoming`
     * immer null.
     */
    val actualStartTime: LocalDateTime? = null,
    /**
     * Der Lauf-Zustand aus [de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic.deriveMatchState]
     * — dieselbe Ableitung wie im Schiedsrichter-Dashboard, im Zeitplan und auf der
     * Durchführungsseite. Die Anzeige leitete „in Vorbereitung" bis zum 09.08.2026 selbst aus
     * [actualStartTime] ab; damit war sie die einzige Oberfläche mit einer zweiten Ableitung.
     */
    val state: MatchState,
    val startState: AthleteBoardStartState,
    val teams: List<AthleteBoardTeam>,
    /** true für einen Platzhalter aus einem wartenden Zeitstrahl-Slot; teams ist dann immer leer. */
    val pendingRound: Boolean = false,
    /**
     * Name eines FREE-Platzhalters (Programmpunkt wie "Mittagspause") - null für echte Läufe und
     * für wartende Rund-Platzhalter ([pendingRound]). Nur gesetzt, wenn die Veranstaltung Pausen
     * auf öffentlichen Anzeigen zeigt.
     */
    val name: String? = null,
    /**
     * Der Lauf ist abgesagt ("Findet nicht statt"). Er bleibt trotzdem an seiner geplanten Stelle
     * im Block `upcoming` stehen: eine Besatzung, die am Steg auf ihren Lauf wartet, kann einen
     * spurlos verschwundenen Lauf nicht von einem Anzeigefehler unterscheiden. [teams] ist dann
     * immer leer - an einem abgesagten Lauf hängt keine Aufstellung mehr.
     */
    val cancelled: Boolean = false,
    /**
     * „Weiter kommen [advancingSeats] Boote → [nextRoundName]" — aus der Rundenkonfiguration,
     * nur befüllt, wenn ein Element showAdvancement anfordert. [advancingSeats] bleibt null,
     * wenn die Folgerunde ein Massenfeld ist (Platzzahl nicht festgelegt).
     */
    val nextRoundName: String? = null,
    val advancingSeats: Int? = null,
    /**
     * Das Freilos dieses Laufs — dieselbe Ableitung wie im Zeitplan und im
     * Schiedsrichter-Dashboard (`MatchStatusLogic.deriveBye`), befüllt in
     * `BoardService.getBoardView`. Die öffentlichen Anzeigen brauchen sie vor allem für
     * Freilose mit „muss gefahren werden": Dort erklärt eine Zweitzeile, warum das Boot allein
     * fährt und dass die Zeit außer Konkurrenz läuft.
     */
    val bye: de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto? = null,
)

data class AthleteBoardTeam(
    /**
     * Startposition im Lauf, aus `competition_match_team.start_number`. Die Anzeige nannte sie
     * bis zum 09.08.2026 „Bahn"; eine davon unabhängige Bahnnummer gibt es im Datenmodell nicht.
     *
     * Nicht nullbar - die Spalte ist seit Migration V202507040930 NOT NULL. Bis zum 09.08.2026
     * stand hier `Int?`, und die Anzeige führte für den vermeintlich fehlenden Wert eine
     * „–"-Zeile mit, die es nie zu sehen gab. Begründung bei
     * `EventInfoService.getMatchResultTeams`.
     */
    val startNumber: Int,
    /** Die n-te Mannschaft dieses Vereins im Wettkampf - nur gezeigt, wenn [teamName] fehlt. */
    val teamNumber: Int?,
    /**
     * Die Vereine der Athleten dieses Bootes als Kette, in Kurzform und voll ausgeschrieben. Bis
     * zum 09.08.2026 stand hier ein einzelner Vereinsname und für ein gemischtes Boot pauschal
     * "Renngemeinschaft" - mehrere Boote desselben Laufs waren damit nicht zu unterscheiden.
     * Welche der beiden Formen erscheint, entscheidet die Anzeige nach der Breite des Schirms.
     */
    val clubsShort: String?,
    val clubsFull: String?,
    val teamName: String?,
    val participants: List<AthleteBoardParticipant>,
    /**
     * Teilergebnis eines laufenden Laufs. Alle vier Felder sind im Block `upcoming` immer leer:
     * dort ist noch nichts gefahren. Im Block `running` füllen sie sich, sobald die Zeitnahme
     * einzelne Boote gewertet hat - der Lauf muss dafür nicht beendet sein.
     */
    val place: Int? = null,
    val timeString: String? = null,
    /** Nur ausgewiesen, nie verrechnet; [timeString] enthält die Strafe bereits. */
    val penaltySeconds: Int? = null,
    val penaltyNote: String? = null,
    val failed: Boolean = false,
    val failedReason: String? = null,
    /** Meldender Verein — nur befüllt, wenn ein Element showRegisteringClub anfordert. */
    val registeringClub: String? = null,
    /**
     * Zwischenzeiten aus RaceClocker, in Markenreihenfolge — leer, wenn das Rennen keine führt.
     * Die Anzeige zeigt sie unter der Zeile, sobald sie da sind (Rückmeldung vom 11.08.2026).
     */
    val laps: List<MatchTeamLapDto> = emptyList(),
)

data class AthleteBoardParticipant(
    val name: String,
    val role: String?,
    /**
     * Geburtsjahr und getragener Verein — nur befüllt, wenn ein Board-Element die
     * Sprecherinnen-Details anfordert (BoardElement.showCrewDetails/showBirthYears);
     * sonst gilt die Sparsamkeitsregel der öffentlichen Anzeige.
     */
    val year: Int? = null,
    val clubName: String? = null,
    /**
     * Bedingungen dieser Person (Sprecher-Kachel MATCH_DETAIL). Nur befüllt, wenn das Board
     * eine solche Kachel hat, und serverseitig doppelt gefiltert: ausschließlich Bedingungen,
     * die für die Rolle der Person überhaupt gelten UND `publicly_visible = true` tragen —
     * dieselbe Regel wie das persönliche Dashboard (MyEventService). Interne Bedingungen und
     * Freitext-Notizen verlassen den Server nie (siehe [AthleteBoardRequirement]).
     */
    val requirements: List<AthleteBoardRequirement> = emptyList(),
)

/**
 * Eine Bedingung auf der öffentlichen Sprecher-Kachel: bewusst nur Name und Erfüllt-Status.
 * Keine Kennung, keine Beschreibung und vor allem keine Notiz — die `note`-Spalte der
 * Erfüllungstabelle ist für interne Augen und wird für Boards gar nicht erst geladen
 * (gleiche Vorsicht wie `MyEventRepo.findFulfilledRequirementIds`).
 */
data class AthleteBoardRequirement(
    val name: String,
    val fulfilled: Boolean,
)

data class AthleteBoardResult(
    val matchId: UUID,
    val competitionName: String,
    /** Wie bei [AthleteBoardMatch.competitionShortName]. */
    val competitionShortName: String? = null,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    /** Geplanter Start aus dem Zeitplan. */
    val startTime: LocalDateTime?,
    /** Tatsächlicher Start, falls gestempelt - erklärt eine Abweichung vom Zeitplan. */
    val actualStartTime: LocalDateTime?,
    val teams: List<AthleteBoardResultTeam>,
)

data class AthleteBoardResultTeam(
    val place: Int?,
    /**
     * Die Wertungskategorie des Bootes, `null` ohne Zuordnung. Die Anzeige trennt danach in
     * Abschnitte, in der Reihenfolge aus [RatingCategoryRef.sortOrder].
     */
    val ratingCategory: RatingCategoryRef?,
    /** Der Platz innerhalb der Wertungskategorie, ab 1 — das ist die angezeigte Zahl. */
    val categoryPlace: Int?,
    /** Wie bei [AthleteBoardTeam.startNumber]: die Startposition im Lauf, nicht nullbar. */
    val startNumber: Int,
    /** Die n-te Mannschaft dieses Vereins im Wettkampf - nur gezeigt, wenn [teamName] fehlt. */
    val teamNumber: Int?,
    /** Wie bei [AthleteBoardTeam.clubsShort]: die Vereinskette des Bootes, kurz und lang. */
    val clubsShort: String?,
    val clubsFull: String?,
    val teamName: String?,
    val timeString: String?,
    /** Nur ausgewiesen, nie verrechnet; [timeString] enthält die Strafe bereits. */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val failed: Boolean,
    val failedReason: String?,
    /**
     * Abgemeldet für diese Runde. Solche Mannschaften haben weder Platz noch Zeit und werden
     * deshalb ausdrücklich als abgemeldet gekennzeichnet, statt still zu verschwinden - sonst
     * sucht die Besatzung im Ergebnis nach einem Boot, das gar nicht gefahren ist.
     */
    val deregistered: Boolean,
    val deregisteredReason: String?,
    /** Zwischenzeiten wie bei [AthleteBoardTeam.laps]; leer, wenn das Rennen keine führt. */
    val laps: List<MatchTeamLapDto> = emptyList(),
    /**
     * Aufstellung des Bootes — nur befüllt, wenn ein Board-Element Crew-Details anfordert
     * (bis zur Sprecher-Kachel zeigten Ergebnis-Karten nie eine Crew; die Ergebnis-Quelle
     * trägt die Personen längst, siehe `MatchResultTeamInfo.participants`).
     */
    val participants: List<AthleteBoardParticipant> = emptyList(),
)
