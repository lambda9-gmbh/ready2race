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
            ).joinToString("|")
        }.sorted().joinToString(";")
}
