package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Die Validierung der Board-Konfiguration ist die einzige Stelle, die die JSONB-Struktur
 * vor dem Schreiben prüft — was hier durchrutscht, steht in der Datenbank und trifft die
 * öffentliche Anzeige.
 */
class BoardRequestValidationTest {

    private fun matchElement(offset: Int) = BoardElement(type = BoardElementType.MATCH, offset = offset)

    private fun request(config: BoardConfig) = BoardRequest(name = "Steg", config = config)

    private fun tiles(n: Int, colSpan: Int = 1, rowSpan: Int = 1) =
        List(n) { BoardTile(colSpan = colSpan, rowSpan = rowSpan, elements = listOf(matchElement(0))) }

    @Test
    fun aPlainConfigIsValid() {
        val result = request(BoardConfig(columns = 3, tiles = tiles(3))).validate()
        assertEquals(ValidationResult.Valid, result)
    }

    // Der Editor sendet immer `columns`; `layout` ist nur noch Alt-Lesart gespeicherter
    // Stände und wird beim Lesen normalisiert — als Request ist es ungültig.
    @Test
    fun columnsAreRequiredAndBounded() {
        assertNotEquals(ValidationResult.Valid, request(BoardConfig(tiles = tiles(3))).validate())
        assertNotEquals(
            ValidationResult.Valid,
            request(BoardConfig(layout = BoardLayout.THREE_COLUMNS, tiles = tiles(3))).validate(),
        )
        assertNotEquals(ValidationResult.Valid, request(BoardConfig(columns = 0, tiles = tiles(1))).validate())
        assertNotEquals(ValidationResult.Valid, request(BoardConfig(columns = 5, tiles = tiles(1))).validate())
    }

    @Test
    fun tileCountIsBounded() {
        assertNotEquals(ValidationResult.Valid, request(BoardConfig(columns = 3, tiles = emptyList())).validate())
        assertNotEquals(ValidationResult.Valid, request(BoardConfig(columns = 3, tiles = tiles(17))).validate())
        // 16 = volles 4×4-Raster aus 1×1-Kacheln — der Anlass für die Grenze.
        assertEquals(ValidationResult.Valid, request(BoardConfig(columns = 3, tiles = tiles(16))).validate())
    }

    @Test
    fun spansMustFitTheGrid() {
        // Breiter als das Raster geht nicht …
        assertNotEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 2, tiles = tiles(1, colSpan = 3))).validate(),
        )
        // … volle Breite schon.
        assertEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 2, tiles = tiles(1, colSpan = 2))).validate(),
        )
        assertNotEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 2, tiles = tiles(1, rowSpan = 0))).validate(),
        )
        assertNotEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 2, tiles = tiles(1, rowSpan = 4))).validate(),
        )
    }

    @Test
    fun aTileWithoutElementsIsInvalid() {
        val config = BoardConfig(columns = 1, tiles = listOf(BoardTile(elements = emptyList())))
        assertNotEquals(ValidationResult.Valid, request(config).validate())
    }

    @Test
    fun aMatchElementNeedsAnOffsetInRange() {
        val missing = BoardConfig(
            columns = 1,
            tiles = listOf(BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH)))),
        )
        assertNotEquals(ValidationResult.Valid, request(missing).validate())

        val outOfRange =
            BoardConfig(columns = 1, tiles = listOf(BoardTile(elements = listOf(matchElement(7)))))
        assertNotEquals(ValidationResult.Valid, request(outOfRange).validate())

        val edge =
            BoardConfig(columns = 1, tiles = listOf(BoardTile(elements = listOf(matchElement(-6)))))
        assertEquals(ValidationResult.Valid, request(edge).validate())
    }

    @Test
    fun aListElementNeedsModeAndLimitInRange() {
        fun listElement(mode: BoardListMode?, limit: Int?) = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(type = BoardElementType.MATCH_LIST, listMode = mode, limit = limit)
                    )
                )
            ),
        )
        assertEquals(ValidationResult.Valid, request(listElement(BoardListMode.UPCOMING, 5)).validate())
        assertNotEquals(ValidationResult.Valid, request(listElement(null, 5)).validate())
        assertNotEquals(ValidationResult.Valid, request(listElement(BoardListMode.UPCOMING, 0)).validate())
        assertNotEquals(ValidationResult.Valid, request(listElement(BoardListMode.UPCOMING, 21)).validate())
    }

    // scheduleMode wählt den Tagesprogramm-Zuschnitt (FOLLOW/FULL) — nur dort erlaubt;
    // fehlend bleibt gültig, damit Alt-Konfigurationen unverändert weiterlaufen.
    @Test
    fun scheduleModeIsOnlyValidOnScheduleLists() {
        fun listElement(mode: BoardListMode, scheduleMode: BoardScheduleMode?) = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(
                            type = BoardElementType.MATCH_LIST,
                            listMode = mode,
                            limit = 5,
                            scheduleMode = scheduleMode,
                        )
                    )
                )
            ),
        )
        assertEquals(ValidationResult.Valid, request(listElement(BoardListMode.SCHEDULE, null)).validate())
        assertEquals(ValidationResult.Valid, request(listElement(BoardListMode.SCHEDULE, BoardScheduleMode.FOLLOW)).validate())
        assertEquals(ValidationResult.Valid, request(listElement(BoardListMode.SCHEDULE, BoardScheduleMode.FULL)).validate())
        assertNotEquals(ValidationResult.Valid, request(listElement(BoardListMode.UPCOMING, BoardScheduleMode.FULL)).validate())
    }

    // Die Sprecher-Kachel: Slot-Wahl wie MATCH, aber nur als einzige Kachel des Boards —
    // sie ist als Vollbild für den zweiten Bildschirm gedacht, nicht als Raster-Baustein.
    @Test
    fun aMatchDetailElementMustBeTheOnlyTile() {
        fun detailTile(offset: Int? = 0) = BoardTile(
            elements = listOf(BoardElement(type = BoardElementType.MATCH_DETAIL, offset = offset))
        )
        assertEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 1, tiles = listOf(detailTile()))).validate(),
        )
        // Spannweiten sind egal — nur Nachbarkacheln sind verboten.
        assertEquals(
            ValidationResult.Valid,
            request(
                BoardConfig(
                    columns = 3,
                    tiles = listOf(BoardTile(colSpan = 3, rowSpan = 2, elements = detailTile().elements)),
                )
            ).validate(),
        )
        assertNotEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 2, tiles = listOf(detailTile()) + tiles(1))).validate(),
        )
        // Und wie MATCH: ohne Offset kein Slot.
        assertNotEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 1, tiles = listOf(detailTile(offset = null)))).validate(),
        )
    }

    // Kachelfarbe (Hex) und Deckkraft sind für jeden Elementtyp erlaubt; fehlende Felder
    // bleiben gültig, damit Alt-Konfigurationen unverändert deserialisieren.
    @Test
    fun backgroundColorMustBeHex() {
        fun colored(color: String?) = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(type = BoardElementType.MATCH, offset = 0, backgroundColor = color)
                    )
                )
            ),
        )
        // Beide Hex-Formen gelten, Groß-/Kleinschreibung egal.
        assertEquals(ValidationResult.Valid, request(colored("#f00")).validate())
        assertEquals(ValidationResult.Valid, request(colored("#C62828")).validate())
        // Fehlend = bisheriges Aussehen.
        assertEquals(ValidationResult.Valid, request(colored(null)).validate())
        assertNotEquals(ValidationResult.Valid, request(colored("rot")).validate())
        assertNotEquals(ValidationResult.Valid, request(colored("C62828")).validate())
        assertNotEquals(ValidationResult.Valid, request(colored("#C6282")).validate())
        assertNotEquals(ValidationResult.Valid, request(colored("#GGHHII")).validate())
    }

    @Test
    fun backgroundOpacityMustBeInUnitInterval() {
        fun withOpacity(opacity: Double?, type: BoardElementType = BoardElementType.CLOCK) = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(type = type, backgroundColor = "#0a0", backgroundOpacity = opacity)
                    )
                )
            ),
        )
        // Die Grenzen selbst sind gültig — 0.0 (unsichtbar) bis 1.0 (deckend).
        assertEquals(ValidationResult.Valid, request(withOpacity(0.0)).validate())
        assertEquals(ValidationResult.Valid, request(withOpacity(0.3)).validate())
        assertEquals(ValidationResult.Valid, request(withOpacity(1.0)).validate())
        assertEquals(ValidationResult.Valid, request(withOpacity(null)).validate())
        assertNotEquals(ValidationResult.Valid, request(withOpacity(-0.1)).validate())
        assertNotEquals(ValidationResult.Valid, request(withOpacity(1.1)).validate())
        // Und auch auf anderen Elementtypen erlaubt — z. B. der Verspätungs-Kachel.
        assertEquals(
            ValidationResult.Valid,
            request(withOpacity(0.5, type = BoardElementType.DELAY)).validate(),
        )
    }

    @Test
    fun aTextElementNeedsText() {
        val config = BoardConfig(
            columns = 1,
            tiles = listOf(BoardTile(elements = listOf(BoardElement(type = BoardElementType.TEXT, text = " ")))),
        )
        assertNotEquals(ValidationResult.Valid, request(config).validate())
    }

    @Test
    fun intervalsBelowTheFloorAreInvalid() {
        // Untergrenze 3 s (Sprecherinnen-Wunsch vom 10.08.2026) — 3 ist gültig, 2 nicht.
        val fastRefresh = BoardConfig(columns = 1, refreshIntervalSeconds = 2, tiles = tiles(1))
        assertNotEquals(ValidationResult.Valid, request(fastRefresh).validate())
        assertEquals(
            ValidationResult.Valid,
            request(BoardConfig(columns = 1, refreshIntervalSeconds = 3, tiles = tiles(1))).validate(),
        )

        val fastRotation = BoardConfig(
            columns = 1,
            tiles = listOf(BoardTile(rotationIntervalSeconds = 1, elements = listOf(matchElement(0)))),
        )
        assertNotEquals(ValidationResult.Valid, request(fastRotation).validate())

        val blankName =
            BoardRequest(name = " ", config = BoardConfig(columns = 1, tiles = tiles(1)))
        assertNotEquals(ValidationResult.Valid, blankName.validate())
    }
}
