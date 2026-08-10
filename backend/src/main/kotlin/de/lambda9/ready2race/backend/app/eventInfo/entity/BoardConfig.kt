package de.lambda9.ready2race.backend.app.eventInfo.entity

/** Grenzen der Board-Konfiguration — eine Stelle für Backend-Validierung und Editor-Hinweise. */
object BoardLimits {
    const val MAX_OFFSET = 6
    const val MIN_LIST_LIMIT = 1
    const val MAX_LIST_LIMIT = 20
    const val MIN_REFRESH_INTERVAL_SECONDS = 10
    const val DEFAULT_REFRESH_INTERVAL_SECONDS = 15
    const val MIN_ROTATION_INTERVAL_SECONDS = 3
    const val DEFAULT_ROTATION_INTERVAL_SECONDS = 10
}

enum class BoardLayout(val tileCount: Int, val columns: Int) {
    ONE_COLUMN(1, 1),
    TWO_COLUMNS(2, 2),
    THREE_COLUMNS(3, 3),
    SIX_TILES(6, 3),
}

enum class BoardElementType { MATCH, MATCH_LIST, CLOCK, TEXT }

enum class BoardListMode { UPCOMING, RESULTS, RUNNING }

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
    // MATCH_LIST
    val listMode: BoardListMode? = null,
    val limit: Int? = null,
    // CLOCK
    val showEventName: Boolean? = null,
    // TEXT
    val text: String? = null,
)

data class BoardTile(
    val rotationIntervalSeconds: Int = BoardLimits.DEFAULT_ROTATION_INTERVAL_SECONDS,
    val elements: List<BoardElement>,
)

data class BoardConfig(
    val layout: BoardLayout,
    val refreshIntervalSeconds: Int = BoardLimits.DEFAULT_REFRESH_INTERVAL_SECONDS,
    val tiles: List<BoardTile>,
)
