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

    private fun tiles(n: Int) = List(n) { BoardTile(elements = listOf(matchElement(0))) }

    @Test
    fun aMatchingTileCountIsValid() {
        val result = request(BoardConfig(layout = BoardLayout.THREE_COLUMNS, tiles = tiles(3))).validate()
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun aWrongTileCountIsInvalid() {
        val result = request(BoardConfig(layout = BoardLayout.SIX_TILES, tiles = tiles(3))).validate()
        assertNotEquals(ValidationResult.Valid, result)
    }

    @Test
    fun aTileWithoutElementsIsInvalid() {
        val config = BoardConfig(layout = BoardLayout.ONE_COLUMN, tiles = listOf(BoardTile(elements = emptyList())))
        assertNotEquals(ValidationResult.Valid, request(config).validate())
    }

    @Test
    fun aMatchElementNeedsAnOffsetInRange() {
        val missing = BoardConfig(
            layout = BoardLayout.ONE_COLUMN,
            tiles = listOf(BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH)))),
        )
        assertNotEquals(ValidationResult.Valid, request(missing).validate())

        val outOfRange =
            BoardConfig(layout = BoardLayout.ONE_COLUMN, tiles = listOf(BoardTile(elements = listOf(matchElement(7)))))
        assertNotEquals(ValidationResult.Valid, request(outOfRange).validate())

        val edge =
            BoardConfig(layout = BoardLayout.ONE_COLUMN, tiles = listOf(BoardTile(elements = listOf(matchElement(-6)))))
        assertEquals(ValidationResult.Valid, request(edge).validate())
    }

    @Test
    fun aListElementNeedsModeAndLimitInRange() {
        fun listElement(mode: BoardListMode?, limit: Int?) = BoardConfig(
            layout = BoardLayout.ONE_COLUMN,
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
            layout = BoardLayout.ONE_COLUMN,
            tiles = listOf(BoardTile(elements = listOf(BoardElement(type = BoardElementType.TEXT, text = " ")))),
        )
        assertNotEquals(ValidationResult.Valid, request(config).validate())
    }

    @Test
    fun intervalsBelowTheFloorAreInvalid() {
        val fastRefresh = BoardConfig(layout = BoardLayout.ONE_COLUMN, refreshIntervalSeconds = 5, tiles = tiles(1))
        assertNotEquals(ValidationResult.Valid, request(fastRefresh).validate())

        val fastRotation = BoardConfig(
            layout = BoardLayout.ONE_COLUMN,
            tiles = listOf(BoardTile(rotationIntervalSeconds = 1, elements = listOf(matchElement(0)))),
        )
        assertNotEquals(ValidationResult.Valid, request(fastRotation).validate())

        val blankName =
            BoardRequest(name = " ", config = BoardConfig(layout = BoardLayout.ONE_COLUMN, tiles = tiles(1)))
        assertNotEquals(ValidationResult.Valid, blankName.validate())
    }
}
