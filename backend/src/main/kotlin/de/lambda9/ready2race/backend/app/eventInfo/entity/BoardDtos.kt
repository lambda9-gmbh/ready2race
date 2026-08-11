package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.time.LocalDateTime
import java.util.UUID

data class BoardDto(
    val id: UUID,
    val eventId: UUID,
    val name: String,
    val config: BoardConfig,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/** Öffentliche Kurzform: mehr als id und Name braucht weder Umleitung noch Verlinkung. */
data class BoardNameDto(
    val id: UUID,
    val name: String,
)

data class BoardRequest(
    val name: String,
    val config: BoardConfig,
) : Validatable {

    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        configResult(),
    )

    // Die Struktur ist zu verschachtelt für die Feld-DSL; ein sprechender Fehlertext je
    // Regel reicht der Admin-Maske.
    private fun configResult(): ValidationResult {
        val errors = mutableListOf<String>()
        val columns = config.columns
        if (columns == null || columns < BoardLimits.MIN_COLUMNS || columns > BoardLimits.MAX_COLUMNS) {
            // Der Editor sendet immer `columns`; die Alt-Lesart `layout` gilt nur für
            // gespeicherte Stände und wird beim Lesen normalisiert (Conversions).
            errors += "columns must be in ${BoardLimits.MIN_COLUMNS}..${BoardLimits.MAX_COLUMNS}"
        }
        if (config.tiles.isEmpty() || config.tiles.size > BoardLimits.MAX_TILES) {
            errors += "tiles must be 1..${BoardLimits.MAX_TILES}"
        }
        if (config.refreshIntervalSeconds < BoardLimits.MIN_REFRESH_INTERVAL_SECONDS) {
            errors += "refreshIntervalSeconds must be at least ${BoardLimits.MIN_REFRESH_INTERVAL_SECONDS}"
        }
        config.tiles.forEachIndexed { tileIndex, tile ->
            if (tile.elements.isEmpty()) errors += "tile $tileIndex has no elements"
            if (tile.rotationIntervalSeconds < BoardLimits.MIN_ROTATION_INTERVAL_SECONDS) {
                errors += "tile $tileIndex: rotationIntervalSeconds must be at least ${BoardLimits.MIN_ROTATION_INTERVAL_SECONDS}"
            }
            if (columns != null && (tile.colSpan < 1 || tile.colSpan > columns)) {
                errors += "tile $tileIndex: colSpan must be in 1..$columns"
            }
            if (tile.rowSpan < 1 || tile.rowSpan > BoardLimits.MAX_ROW_SPAN) {
                errors += "tile $tileIndex: rowSpan must be in 1..${BoardLimits.MAX_ROW_SPAN}"
            }
            tile.elements.forEachIndexed { elementIndex, element ->
                val at = "tile $tileIndex element $elementIndex"
                when (element.type) {
                    BoardElementType.MATCH -> {
                        val offset = element.offset
                        if (offset == null || offset < -BoardLimits.MAX_OFFSET || offset > BoardLimits.MAX_OFFSET) {
                            errors += "$at: MATCH needs offset in -${BoardLimits.MAX_OFFSET}..${BoardLimits.MAX_OFFSET}"
                        }
                    }

                    BoardElementType.MATCH_DETAIL -> {
                        // Dieselbe Slot-Wahl wie MATCH — und die Vollbild-Regel: die
                        // Sprecher-Kachel duldet keine Nachbarkacheln (siehe BoardElementType).
                        val offset = element.offset
                        if (offset == null || offset < -BoardLimits.MAX_OFFSET || offset > BoardLimits.MAX_OFFSET) {
                            errors += "$at: MATCH_DETAIL needs offset in -${BoardLimits.MAX_OFFSET}..${BoardLimits.MAX_OFFSET}"
                        }
                        if (config.tiles.size > 1) {
                            errors += "$at: MATCH_DETAIL must be the only tile of the board"
                        }
                    }

                    BoardElementType.MATCH_LIST -> {
                        if (element.listMode == null) errors += "$at: MATCH_LIST needs listMode"
                        val limit = element.limit
                        if (limit == null || limit < BoardLimits.MIN_LIST_LIMIT || limit > BoardLimits.MAX_LIST_LIMIT) {
                            errors += "$at: MATCH_LIST needs limit in ${BoardLimits.MIN_LIST_LIMIT}..${BoardLimits.MAX_LIST_LIMIT}"
                        }
                        // scheduleMode steuert nur den Tagesprogramm-Zuschnitt; auf anderen
                        // Listen wäre er ein stiller Konfigurationsfehler.
                        if (element.scheduleMode != null && element.listMode != BoardListMode.SCHEDULE) {
                            errors += "$at: scheduleMode requires listMode SCHEDULE"
                        }
                    }

                    BoardElementType.TEXT -> {
                        if (element.text.isNullOrBlank()) errors += "$at: TEXT needs text"
                    }

                    BoardElementType.AWARD_CEREMONY -> {
                        if (element.competitionId == null) errors += "$at: AWARD_CEREMONY needs competitionId"
                    }

                    BoardElementType.CLOCK -> {}

                    // Wie CLOCK: keine Pflichtfelder — die Verspätung rechnet der Server.
                    BoardElementType.DELAY -> {}
                }
            }
        }
        return if (errors.isEmpty()) ValidationResult.Valid
        else ValidationResult.Invalid.Message { errors.joinToString("; ") }
    }

    companion object {
        val example = BoardRequest(
            name = "Athleten-Anzeige",
            config = BoardConfig(
                columns = 3,
                tiles = listOf(
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH, offset = 0))),
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH, offset = 1))),
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH, offset = -1))),
                ),
            ),
        )
    }
}
