package de.lambda9.ready2race.backend.app.eventInfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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

    // Kachel- und Randfarbe (Hex) sind für jeden Elementtyp erlaubt; fehlende Felder
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
    fun borderColorMustBeHexAndIndependentOfBackground() {
        fun colored(
            border: String?,
            background: String? = null,
            type: BoardElementType = BoardElementType.CLOCK,
        ) = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(type = type, backgroundColor = background, borderColor = border)
                    )
                )
            ),
        )
        // Beide Hex-Formen gelten — und der Rand braucht keine Fläche (nur Rand ist gültig).
        assertEquals(ValidationResult.Valid, request(colored("#f00")).validate())
        assertEquals(ValidationResult.Valid, request(colored("#2E7D32")).validate())
        // Beides zusammen und beides fehlend sind ebenso gültig.
        assertEquals(ValidationResult.Valid, request(colored("#f00", background = "#0a0")).validate())
        assertEquals(ValidationResult.Valid, request(colored(null)).validate())
        assertNotEquals(ValidationResult.Valid, request(colored("rot")).validate())
        assertNotEquals(ValidationResult.Valid, request(colored("2E7D32")).validate())
        assertNotEquals(ValidationResult.Valid, request(colored("#2E7D3")).validate())
        // Ein gültiger Hintergrund heilt keinen ungültigen Rand — die Felder sind unabhängig.
        assertNotEquals(ValidationResult.Valid, request(colored("rot", background = "#0a0")).validate())
        // Und auch auf anderen Elementtypen erlaubt — z. B. der Lauf-Kachel.
        assertEquals(
            ValidationResult.Valid,
            request(
                BoardConfig(
                    columns = 1,
                    tiles = listOf(
                        BoardTile(
                            elements = listOf(
                                BoardElement(type = BoardElementType.MATCH, offset = 0, borderColor = "#c62828")
                            )
                        )
                    ),
                )
            ).validate(),
        )
    }

    // Die Konfiguration liegt als JSON in der Datenbank — ein gespeicherter Stand mit dem
    // am 12.08.2026 entfernten Feld `backgroundOpacity` muss weiterhin fehlerfrei laden.
    // Der Mapper ist wie der produktive in den Conversions gebaut (nacktes Kotlin-Modul,
    // FAIL_ON_UNKNOWN_PROPERTIES standardmäßig an) — die Toleranz kommt aus der
    // @JsonIgnoreProperties-Annotation am BoardElement, nicht aus der Mapper-Konfiguration.
    @Test
    fun aStoredConfigWithTheRemovedOpacityFieldStillDeserializes() {
        val mapper = ObjectMapper().registerKotlinModule()
        val stored = """
            {"columns": 1, "refreshIntervalSeconds": 15, "tiles": [
              {"colSpan": 1, "rowSpan": 1, "elements": [
                {"type": "MATCH", "offset": 0, "backgroundColor": "#c62828", "backgroundOpacity": 0.3}
              ]}
            ]}
        """.trimIndent()
        val config = mapper.readValue(stored, BoardConfig::class.java)
        assertEquals("#c62828", config.tiles.single().elements.single().backgroundColor)
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

    @Test
    fun `STREAM ist nur als einzige Kachel gueltig`() {
        val invalid = BoardRequest(
            name = "Stream",
            config = BoardConfig(
                columns = 2,
                tiles = listOf(
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.STREAM))),
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.CLOCK))),
                ),
            ),
        )
        assertNotEquals(ValidationResult.Valid, invalid.validate())

        val valid = BoardRequest(
            name = "Stream",
            config = BoardConfig(
                columns = 1,
                tiles = listOf(
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.STREAM))),
                ),
            ),
        )
        assertEquals(ValidationResult.Valid, valid.validate())
    }

    @Test
    fun `streamMode ist nur an STREAM-Elementen erlaubt`() {
        val invalid = BoardRequest(
            name = "Uhr",
            config = BoardConfig(
                columns = 1,
                tiles = listOf(
                    BoardTile(
                        elements = listOf(
                            BoardElement(type = BoardElementType.CLOCK, streamMode = StreamOverlayMode.AUTO)
                        )
                    ),
                ),
            ),
        )
        assertNotEquals(ValidationResult.Valid, invalid.validate())
    }

    @Test
    fun `streamCrew ist nur an STREAM-Elementen erlaubt`() {
        val invalid = BoardRequest(
            name = "Uhr",
            config = BoardConfig(
                columns = 1,
                tiles = listOf(BoardTile(elements = listOf(
                    BoardElement(type = BoardElementType.CLOCK, streamCrew = StreamCrewDisplay.CLUBS_ONLY)
                ))),
            ),
        )
        assertNotEquals(ValidationResult.Valid, invalid.validate())
    }
}
