package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

/** Grenzen der Board-Konfiguration — eine Stelle für Backend-Validierung und Editor-Hinweise. */
object BoardLimits {
    const val MAX_OFFSET = 6
    const val MIN_LIST_LIMIT = 1
    const val MAX_LIST_LIMIT = 20
    // 3 statt anfangs 10: Rückmeldung der Sprecherinnen vom 10.08.2026 („max. 5 s
    // Aktualisierung", danach „bis 3 s"). Der serverseitige Zwischenspeicher
    // (BoardLogic.CACHE_TTL_SECONDS, passend darunter) deckelt die Datenbanklast
    // unabhängig vom Takt und von der Zahl der Bildschirme.
    const val MIN_REFRESH_INTERVAL_SECONDS = 3
    const val DEFAULT_REFRESH_INTERVAL_SECONDS = 15
    const val MIN_ROTATION_INTERVAL_SECONDS = 3
    const val DEFAULT_ROTATION_INTERVAL_SECONDS = 10
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 4
    const val MAX_TILES = 12
    const val MAX_ROW_SPAN = 3
}

/**
 * Die festen Layouts der ersten Board-Fassung (10.08.2026, vormittags). Seit dem Umbau
 * auf freie Spannweiten nur noch Alt-Lesart: [de.lambda9.ready2race.backend.app.eventInfo.control.toDto]
 * übersetzt eine gespeicherte Konfiguration mit `layout` beim Lesen in `columns`; nach
 * außen (API) taucht das Feld nicht mehr auf.
 */
enum class BoardLayout(val tileCount: Int, val columns: Int) {
    ONE_COLUMN(1, 1),
    TWO_COLUMNS(2, 2),
    THREE_COLUMNS(3, 3),
    SIX_TILES(6, 3),
}

enum class BoardElementType { MATCH, MATCH_LIST, CLOCK, TEXT, AWARD_CEREMONY }

/** SCHEDULE = Tagesprogramm: alle Slots des Zeitplans mit Status, für Sprecherinnen und Aushänge. */
enum class BoardListMode { UPCOMING, RESULTS, RUNNING, SCHEDULE }

/**
 * Ein Element einer Kachel. Bewusst flach statt sealed: das Schema geht 1:1 durch das
 * handgepflegte OpenAPI-YAML und den hey-api-Generator, die mit einem Discriminator
 * beide mehr Reibung als Nutzen erzeugen. Welche Felder je [type] Pflicht sind,
 * erzwingt [BoardRequest.validate].
 */
data class BoardElement(
    val type: BoardElementType,
    // MATCH: Position auf der Tages-Timeline, 0 = zuletzt gestarteter noch laufender Lauf.
    val offset: Int? = null,
    val showCrew: Boolean? = null,
    val showCountdown: Boolean? = null,
    val showTimes: Boolean? = null,
    val contrastColors: Boolean? = null,
    val autoFit: Boolean? = null,
    // Sprecherinnen-Optionen: Zusatzdaten, die der Server nur liefert, wenn irgendein
    // Element sie anfordert — die Sparsamkeitsregel der öffentlichen Anzeige bleibt.
    /** Jede Athletin einzeln mit Rolle und Heimatverein statt der einzeiligen Namensliste. */
    val showCrewDetails: Boolean? = null,
    /** Geburtsjahr je Athletin (nur zusammen mit [showCrewDetails] sichtbar). */
    val showBirthYears: Boolean? = null,
    /** „Weiter kommen N Boote → Finale" aus der Rundenkonfiguration. */
    val showAdvancement: Boolean? = null,
    /** Der meldende Verein des Bootes (Verwaltungsinfo, sonst bewusst weggelassen). */
    val showRegisteringClub: Boolean? = null,
    // MATCH_LIST
    val listMode: BoardListMode? = null,
    val limit: Int? = null,
    /** Wettkampf-Kürzel (short_name) statt des vollen Namens — für schmale Listen. */
    val useShortNames: Boolean? = null,
    // AWARD_CEREMONY: die Ehrung (Wettkampf + optionale Wertung), deren Podium die Kachel zeigt.
    val competitionId: UUID? = null,
    val ratingCategoryId: UUID? = null,
    // CLOCK
    val showEventName: Boolean? = null,
    // TEXT
    val text: String? = null,
)

data class BoardTile(
    val rotationIntervalSeconds: Int = BoardLimits.DEFAULT_ROTATION_INTERVAL_SECONDS,
    /** Breite in Rasterspalten; die Kacheln fließen in Reihenfolge ins Raster. */
    val colSpan: Int = 1,
    /** Höhe in Rasterzeilen. */
    val rowSpan: Int = 1,
    val elements: List<BoardElement>,
)

data class BoardConfig(
    /** Nur noch Alt-Lesart gespeicherter Konfigurationen — siehe [BoardLayout]. */
    val layout: BoardLayout? = null,
    /** Spaltenzahl des Rasters; die Zeilenzahl ergibt sich aus den Kacheln. */
    val columns: Int? = null,
    /** Kopfzeile mit Veranstaltungsname, Uhr und „Stand"-Zeile — wie die alte Bühne. */
    val showHeader: Boolean? = null,
    val refreshIntervalSeconds: Int = BoardLimits.DEFAULT_REFRESH_INTERVAL_SECONDS,
    val tiles: List<BoardTile>,
) {
    /** Die wirksame Spaltenzahl, egal in welcher Fassung die Konfiguration gespeichert wurde. */
    fun resolvedColumns(): Int = columns ?: layout?.columns ?: 3
}
