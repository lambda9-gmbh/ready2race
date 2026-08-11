package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import java.time.LocalDateTime

/**
 * Die Entscheidungen des automatischen RaceClocker-Abrufs, bewusst ohne Datenbank- und HTTP-Bezug —
 * wie [de.lambda9.ready2race.backend.app.competitionExecution.boundary.RaceClockerAssignmentLogic]
 * und aus demselben Grund: Am Renntag zählt, dass diese Regeln stimmen, und sie lassen sich nur
 * dann ohne laufende Umgebung prüfen.
 */
object RaceClockerPollLogic {

    /**
     * Kein Takt unter zwei Sekunden. Die Takte sind pro Veranstaltung einstellbar, und eine
     * versehentlich eingetragene `1` würde die Regatta in Dauerfeuer gegen raceclocker.com
     * verwandeln. Die Grenze steht hier statt nur im Formular, damit sie auch greift, wenn der Wert
     * auf anderem Weg in die Datenbank kommt.
     */
    const val MIN_INTERVAL_SECONDS = 2

    enum class PollMode { ACTIVE, UPCOMING }

    fun intervalSeconds(configured: Int): Int = configured.coerceAtLeast(MIN_INTERVAL_SECONDS)

    /**
     * Ob dieser Lauf überhaupt beobachtet wird.
     *
     * Ein aktivierter Lauf immer — er kann längst vor oder nach seinem Plan laufen, und was
     * tatsächlich passiert, schlägt den Plan. Dass er dabei erst am Start steht und noch nicht
     * unterwegs ist, ändert nichts: Gerade dann soll der Abruf den gemessenen Start entdecken. Ein
     * noch nicht aktivierter nur im Fenster um seine geplante Startzeit: ohne diese Grenze würde
     * eine Veranstaltung in drei Monaten jede Minute abgefragt.
     *
     * Beide Grenzen zählen einschließlich. Ohne geplante Startzeit gibt es kein Fenster, also auch
     * keine Beobachtung — solche Läufe aktiviert weiterhin jemand von Hand, und ab dann greift der
     * erste Zweig.
     */
    fun isWatched(
        activated: Boolean,
        startTime: LocalDateTime?,
        now: LocalDateTime,
        watchBeforeMinutes: Int,
        watchAfterMinutes: Int,
    ): Boolean = when {
        activated -> true
        startTime == null -> false
        else -> !now.isBefore(startTime.minusMinutes(watchBeforeMinutes.toLong())) &&
            !now.isAfter(startTime.plusMinutes(watchAfterMinutes.toLong()))
    }

    /**
     * Der Takt gilt für die ganze Veranstaltung, nicht je Lauf: Ein Abruf holt ohnehin das ganze
     * Rennen. Sobald ein einziger Lauf aktiv ist, lohnt sich der schnelle Takt für alle.
     */
    fun modeFor(anyRunning: Boolean): PollMode = if (anyRunning) PollMode.ACTIVE else PollMode.UPCOMING

    /** Noch nie abgerufen heißt sofort fällig — beim Start des Servers soll nicht erst gewartet werden. */
    fun isDue(lastPolledAt: LocalDateTime?, now: LocalDateTime, intervalSeconds: Int): Boolean =
        lastPolledAt == null || !now.isBefore(lastPolledAt.plusSeconds(intervalSeconds.toLong()))

    /**
     * Ob der Feed für diesen Lauf sagt, dass er losgegangen ist.
     *
     * Zwei Belege: eine gemessene Startzeit oder ein verwertbares Ergebnis. Die Startzeit ist der
     * übliche Fall; das Ergebnis fängt den ab, bei dem die Zeitnahme den Start nicht erfasst hat
     * (Nachtrag von Hand, Zeitfahren ohne Startstempel) — ein Boot mit Zeit ist unstrittig gefahren.
     *
     * Die Fortschritts-Texte von RaceClocker (`Not started`, `In race...`) belegen für sich nichts:
     * `Not started` steht direkt nach dem Startlisten-Import in jeder Zeile, und `In race...` setzt
     * RaceClocker mit dem Start — aber dann liegt auch eine Startzeit vor, die hier zählt. Der
     * Umweg über [RaceClockerFeedRow.hasResult] hält diese Unterscheidung an genau einer Stelle.
     */
    fun startDetected(rows: List<RaceClockerFeedRow>): Boolean =
        rows.any { it.start != null || it.hasResult }

    /**
     * Ob der Feed den Ist-Start dieses Laufs zurückgenommen hat — die Gegenrichtung zu
     * [startDetected], mit denselben Belegen, nur negiert: Nach einem Fehlstart zieht der
     * Zeitnehmer in RaceClocker alle Zeiten zurück, jede Zeile steht wieder ohne gemessene
     * Startzeit und ohne verwertbares Ergebnis da — der Feed behauptet, dieser Lauf sei nie
     * losgegangen, und ready2race übernimmt diese Aussage, statt auf „Läuft" stehen zu bleiben.
     *
     * Die Randfälle sind Absicht, nicht Zufall:
     * - **Leere [rows]**: kein Rückzug. Zugeordnete Zeilen gibt es dann nicht — der Feed KENNT
     *   diesen Lauf schlicht (noch) nicht, und ein vom Schiedsrichter von Hand markierter Start
     *   (markMatchStarted) darf einen leeren Feed überleben. Nur ein Feed, der den Lauf kennt und
     *   ihn wieder auf ungestartet zeigt, zählt. (Der `NotInFeed`-Fall kehrt im Abruf schon vorher
     *   um und erreicht diese Frage nie — die Leer-Bedingung hält die Regel trotzdem für sich
     *   allein vollständig.)
     * - **[anyStoredResult]**: kein Rückzug. Stehen in ready2race Zeiten, Plätze, Ausscheidungen
     *   oder Strafzeiten, während der Feed nichts davon kennt, sind das Handstände — die nimmt der
     *   Abruf grundsätzlich nicht zurück. Kamen die Stände dagegen aus dem Feed, hat sich mit dem
     *   Rückzug der Fingerabdruck geändert, und der bestehende Reset-Pfad
     *   (`resetRaceClockerResults` in `applyRaceClockerRows`) räumt Ergebnisse UND Ist-Start in
     *   einem Zug ab — dieser Zweig hier deckt allein den ergebnislosen Fehlstart ab, bei dem der
     *   Fingerabdruck unverändert bleiben kann, und baut die Reset-Logik nicht nach.
     * - **[existingStartedAt] == null**: nichts zurückzunehmen.
     *
     * `activated_at` geht diesen Zweig nichts an: Der Lauf bleibt an den Start gerufen und steht
     * nach dem Rückzug wieder „In Vorbereitung".
     */
    fun startRetracted(
        rows: List<RaceClockerFeedRow>,
        existingStartedAt: LocalDateTime?,
        anyStoredResult: Boolean,
    ): Boolean =
        existingStartedAt != null &&
            rows.isNotEmpty() &&
            !startDetected(rows) &&
            !anyStoredResult

    /**
     * Der Ist-Start, den der Feed für einen bereits aktivierten Lauf hergibt — null, wenn er schon
     * einen hat oder der Feed keinen kennt.
     *
     * Der Unterschied zu [startDetected] ist Absicht: Dort genügt ein verwertbares Ergebnis als
     * Beleg, dass der Lauf gefahren ist; hier zählt nur eine gemessene Startzeit, denn nur sie sagt,
     * WANN er losging. Ein Ergebnis ohne Startstempel lässt `started_at` deshalb leer, statt eine
     * Uhrzeit zu erfinden.
     *
     * [existingStartedAt] gewinnt immer: Ein einmal gesetzter Ist-Start wird nicht verschoben, auch
     * wenn RaceClocker später eine andere Zeit meldet — sonst rückte der Zeitpunkt unter der
     * laufenden Anzeige weg.
     *
     * Der Renntag steckt bewusst in dieser Regel und nicht im Abruf: Der Feed liefert nur die
     * Uhrzeit. Den Tag dazu liefert [now] — es gewinnt das Datum (gestern, heute oder morgen), das
     * den Stempel am nächsten an [now] heranrückt. Der Start ist gerade eben passiert, der Abruf
     * läuft im Sekundentakt; näher als einen halben Tag liegt die Wahrheit immer. Der geplante
     * Renntag taugt dafür ausdrücklich NICHT: Läuft ein Rennen an einem anderen Tag als geplant
     * (Testbetrieb, verschobener Zeitplan), stünde der Ist-Start Tage daneben — in der Zukunft
     * liegend wurde daraus „Läuft · 0 min", weil die Anzeige negative Laufzeiten auf 0 klemmt
     * (beobachtet am 10.08.2026). Die Mitternachtsfälle deckt die Nächstliegend-Regel in beide
     * Richtungen ab.
     */
    fun measuredStartFor(
        rows: List<RaceClockerFeedRow>,
        existingStartedAt: LocalDateTime?,
        now: LocalDateTime,
    ): LocalDateTime? = when {
        existingStartedAt != null -> null
        else -> RaceClockerFeedRow.earliestStart(rows)?.let { stampOnNearestDay(it, now) }
    }

    /**
     * Hängt an eine Feed-Uhrzeit das Datum (gestern, heute oder morgen), das sie am nächsten an
     * [now] heranrückt — die eine Datumsregel für alle Stellen, die aus dem Feed einen Ist-Start
     * ableiten ([measuredStartFor] und das Überschreiben in `applyRaceClockerRows`).
     */
    fun stampOnNearestDay(time: java.time.LocalTime, now: LocalDateTime): LocalDateTime =
        listOf(-1L, 0L, 1L)
            .map { time.atDate(now.toLocalDate().plusDays(it)) }
            .minBy { java.time.Duration.between(it, now).abs() }

    /**
     * Ein Kurzwert über alles, was aus diesen Zeilen in die Datenbank wandert. Ist er seit dem
     * letzten Abruf unverändert, schreibt der Job nichts — sonst sähe jeder aktive Lauf alle fünf
     * Sekunden „bearbeitet" aus, obwohl sich nichts getan hat.
     *
     * [RaceClockerFeedRow.name] steht bewusst nicht drin: Er wird nirgends übernommen. [rank] dagegen
     * schon, denn aus der Reihenfolge der Zeilen entstehen die Bahnen
     * ([RaceClockerFeedRow.lanesByRow]). Sortiert, weil die Reihenfolge innerhalb der Antwort keine
     * Aussage trägt — [rank] trägt sie.
     */
    fun fingerprint(rows: List<RaceClockerFeedRow>): String =
        rows.map { row ->
            listOf(
                row.ids.map { it.toString() }.sorted().joinToString("/"),
                row.rank?.toString() ?: "",
                row.result?.trim() ?: "",
                row.start?.toString() ?: "",
                row.penaltySeconds?.toString() ?: "",
                row.penaltyNote ?: "",
                // Auch eine neue oder korrigierte Zwischenzeit ist eine Änderung, die geschrieben
                // werden muss - ohne sie hier bliebe der Takt in der Abkürzung hängen.
                row.laps.joinToString("/") { "${it.name}=${it.time}" },
            ).joinToString("|")
        }.sorted().joinToString(";")
}
