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
        assertNotEquals(ValidationResult.Valid, request(BoardConfig(columns = 3, tiles = tiles(13))).validate())
        assertEquals(ValidationResult.Valid, request(BoardConfig(columns = 3, tiles = tiles(12))).validate())
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
        val fastRefresh = BoardConfig(columns = 1, refreshIntervalSeconds = 5, tiles = tiles(1))
        assertNotEquals(ValidationResult.Valid, request(fastRefresh).validate())

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
