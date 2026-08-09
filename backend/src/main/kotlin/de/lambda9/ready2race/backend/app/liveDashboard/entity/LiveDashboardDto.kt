package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
import java.time.LocalDateTime
import java.util.UUID

/**
 * [PREPARING]: An den Start gerufen, aber noch nicht unterwegs
 * (`competition_match.activated_at` gesetzt, `started_at` nicht). Bis zum 09.08.2026 hieß dieser
 * Zustand ebenfalls [RUNNING] - der Klick des Schiedsrichters stellte fest, dass der Lauf
 * drankommt, die Oberfläche behauptete aber, er fahre. Erst der automatische RaceClocker-Abruf
 * liefert einen zuverlässigen Sender für den Ist-Start und macht die Trennung belegbar.
 *
 * [SKIPPED]: Der Zeitstrahl-Slot dieses Laufs ist abgesagt. Anders als auf den öffentlichen
 * Anzeigen wird der Lauf im Schiedsrichter-Dashboard NICHT versteckt, sondern gekennzeichnet -
 * der Schiedsrichter muss die Absage sehen, um sie im Zeitplan zurücknehmen zu können (`/unskip`).
 * Ein still verschwundener Lauf wäre am Steg nicht von einem Anzeigefehler zu unterscheiden.
 *
 * [AWAITING_FINISH]: Alle Boote sind gewertet, aber niemand hat den Lauf beendet
 * ([FINISHED] heißt ausschließlich: `competition_match.finished_at` ist gesetzt). Bis zum
 * 06.08.2026 fielen beide Sachverhalte auf [FINISHED] zusammen; der Lauf verschwand damit aus dem
 * Live-Tab und bot "Lauf aktivieren" statt "Lauf beenden" an. Die Trennung setzt die Entscheidung
 * vom 04.08.2026 um (Backlog C1/A1): Beendet wird nur durch aktiven Input, weil der Beenden-Klick
 * das Signal ans Regattabüro ist, dass der Stand final ist - bis dahin kann noch eine Zeitstrafe
 * kommen.
 */
enum class LiveDashboardMatchState { PREPARING, RUNNING, FINISHED, SKIPPED, AWAITING_FINISH, UPCOMING, UNSCHEDULED }

enum class LiveDashboardInvoiceState { PAID, OPEN, NONE }

enum class TimeCheckStatus { OK, TOO_EARLY, LATE, NOT_CHECKED }

data class TimeCheckDto(
    val deltaMinutes: Long?,
    val status: TimeCheckStatus,
)

data class LiveDashboardRequirementStatusDto(
    val requirementId: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val checked: Boolean,
    val checkedAt: LocalDateTime?,
    val note: String?,
    val timeCheck: TimeCheckDto?,
    /** Fertige Ampel dieser Bedingung - siehe [LiveDashboardLogic.requirementSeverity]. */
    val severity: EffectiveSeverity,
)

data class LiveDashboardParticipantDto(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val namedRole: String?,
    val year: Int?,
    val gender: String?,
    /**
     * Der Verein, den diese Person trägt - bei Gastruderern der Freitext aus der Meldung, sonst
     * der Name ihres eigenen Vereins. Bis zum 09.08.2026 stand hier der Verein der *Meldung*,
     * derselbe für die ganze Mannschaft; bei 42 der 100 CRF-Meldungen war das schlicht falsch.
     */
    val clubName: String?,
    /** Name of the participant this one replaced, if they were substituted into this round. */
    val substitutedFor: String?,
    val substitutionReason: String?,
    val requirements: List<LiveDashboardRequirementStatusDto>,
)

/**
 * Eine Person in der Kurzfassung, die die breite Karte je Boot zeigt - bewusst nicht die volle
 * [LiveDashboardParticipantDto] mit ihren Teilnahmebedingungen: die Karte hängt am Sekunden-Poll,
 * der Detail-Dialog lädt einzeln nach.
 */
data class LiveDashboardCrewMemberDto(
    /** Der Vorname fehlt bewusst: auf dem Wasser ruft niemand ihn, und die Zeile bleibt kurz. */
    val lastName: String,
    /** Kurzform des Vereins, den diese Person trägt - dieselbe Regel wie in der Kette. */
    val clubShort: String?,
    /** Kurzform der Rolle, siehe [LiveDashboardLogic.roleAbbreviation]. */
    val role: String?,
)

data class LiveDashboardTeamDto(
    val teamId: UUID,
    val teamName: String?,
    /**
     * Der *meldende* Verein. Bleibt im Datensatz, weil er die Rechnung trägt und in der Verwaltung
     * gebraucht wird - angezeigt wird er seit dem 09.08.2026 nirgends mehr, dafür sind
     * [clubsShort]/[clubsFull] da.
     */
    val clubName: String?,
    /** Die Vereine der Crew in Bootsreihenfolge, Kurzformen - siehe [de.lambda9.ready2race.backend.app.club.boundary.ClubComposition]. */
    val clubsShort: String,
    /** Dieselbe Kette in vollen Vereinsnamen; die breite Karte und der Detail-Dialog zeigen sie. */
    val clubsFull: String,
    /**
     * Die Crew in Kurzfassung - nur gefüllt, wenn der Abruf sie mit `crew=true` angefordert hat.
     * Am Telefon bleibt die Nutzlast damit unverändert; erst die dritte Anzeigestufe braucht sie.
     */
    val crew: List<LiveDashboardCrewMemberDto>?,
    val startNumber: Int?,
    val place: Int?,
    val time: String?,
    val failed: Boolean,
    val failedReason: String?,
    /** Zeitstrafe in Sekunden; die Ergebniszeit enthält sie bereits. */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
    val invoiceState: LiveDashboardInvoiceState,
    /** Fertige Ampel der Zeile - die Bewertungsregeln liegen im Backend, siehe [LiveDashboardLogic]. */
    val severity: EffectiveSeverity,
    /**
     * Die Rechnung getrennt bewertet: der Detail-Dialog färbt seinen Rechnungs-Chip danach ein.
     * Aus [severity] ließe sich das nicht zurückrechnen - dort ist sie mit allem anderen verrechnet.
     */
    val invoiceSeverity: EffectiveSeverity,
    /** Ob dieser Wettkampf überhaupt eine An-/Abmeldung verlangt; steuert die Anzeige von [onWaterAt]. */
    val onWaterRequired: Boolean,
    /**
     * "Auf dem Wasser" getrennt bewertet: der Detail-Dialog färbt seinen Chip danach ein.
     * Aus [severity] ließe sich das nicht zurückrechnen - dort ist sie mit allem anderen verrechnet.
     */
    val onWaterSeverity: EffectiveSeverity,
    /** Ob mindestens eine Person für diese Runde umgemeldet wurde. */
    val substituted: Boolean,
    /**
     * Wann das Boot aufs Wasser gegangen ist (spätester Eincheck-Scan, wenn die gesamte Crew
     * zuletzt eingecheckt ist) - null, solange mindestens eine Person nicht eingecheckt ist
     * oder keine Crew bekannt ist. Siehe [LiveDashboardLogic.teamOnWaterAt].
     */
    val onWaterAt: LocalDateTime?,
)

/** Was der Detail-Dialog zusätzlich braucht; wird einzeln je Mannschaft geladen. */
data class LiveDashboardTeamDetailDto(
    val teamId: UUID,
    val participants: List<LiveDashboardParticipantDto>,
)

data class LiveDashboardMatchDto(
    val matchId: UUID,
    /**
     * Der abgeleitete Lauf-Zustand — die einzige Aussage der Karte darüber, wo der Lauf steht.
     * Ein eigenes `currentlyRunning` stand hier bis zum 09.08.2026 daneben; seit „am Start" und
     * „unterwegs" zwei Zustände sind ([LiveDashboardMatchState.PREPARING] und
     * [LiveDashboardMatchState.RUNNING]), wäre ein zweites Feld nur eine zweite Wahrheit.
     */
    val state: LiveDashboardMatchState,
    val competitionId: UUID,
    val competitionName: String,
    /** Rennnummer und Kurzname des Wettkampfs - das Board zeigt sie statt des ausgeschriebenen
     * Namens, wenn die Kurzform eingeschaltet ist (dieselbe Wahl wie im Zeitplan-Tab). */
    val competitionIdentifier: String?,
    val competitionShortName: String?,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val startTime: LocalDateTime?,
    val startedAt: LocalDateTime?,
    val elapsedMinutes: Long?,
    val teams: List<LiveDashboardTeamDto>,
    /**
     * Fehlercode des letzten fehlgeschlagenen automatischen Abrufs, null wenn er in Ordnung ist.
     *
     * Der Zeitpunkt des letzten Abrufs (`raceclocker_polled_at`) steht hier bewusst NICHT, obwohl
     * der Durchführungs-Tab ihn zeigt: `respondETagged` bildet den Hash über das serialisierte DTO,
     * und ein Feld, das sich für jeden beobachteten Lauf alle fünf Sekunden ändert, macht jede
     * 304-Antwort unmöglich - jedes Schiedsrichter-Telefon lüde das ganze Dashboard neu, solange der
     * Job läuft. Die Karte zeigt den Zeitpunkt ohnehin nicht an; sie unterscheidet nur „Fehler" und
     * „pausiert" von „alles in Ordnung".
     */
    val raceClockerPollError: String?,
    /** Gesetzt, solange der automatische Abruf diesen Lauf in Ruhe lässt, weil von Hand Ergebnisse eingetragen wurden. */
    val raceClockerAutoPausedAt: LocalDateTime?,
)

/**
 * Ein Platzhalter im Zeitstrahl des Live-Dashboards - entweder ein wartender Lauf-Slot (Runde noch
 * nicht erzeugt) oder ein FREE-Slot/Programmpunkt (z.B. "Mittagspause"). Bewusst ohne Team-/
 * Personendaten, die gibt es für beide Platzhalter-Arten ohnehin nicht. [name] unterscheidet die
 * Fälle: gesetzt für Programmpunkte, null für Lauf-Platzhalter.
 */
data class PendingSlotDto(
    val slotId: UUID,
    val startTime: LocalDateTime,
    /** Name des Programmpunkts - null bei einem Lauf-Platzhalter. */
    val name: String?,
    val competitionName: String?,
    /** Wie bei [LiveDashboardMatchDto]; für Programmpunkte beide null. */
    val competitionIdentifier: String?,
    val competitionShortName: String?,
    val roundName: String?,
    val matchName: String?,
)

data class LiveDashboardDto(
    val matches: List<LiveDashboardMatchDto>,
    /** Aufsteigend nach Startzeit; in beiden Scopes (ALL und LIVE) enthalten - die Liste ist klein. */
    val pendingSlots: List<PendingSlotDto>,
    /** Steuert im Frontend, ob "Lauf beenden" im Dashboard überhaupt angeboten wird (C1). */
    val chainProgressionMode: ChainProgressionMode,
)
