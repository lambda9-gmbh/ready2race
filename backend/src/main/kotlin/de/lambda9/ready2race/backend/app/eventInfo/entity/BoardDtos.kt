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
        if (config.tiles.size != config.layout.tileCount) {
            errors += "layout ${config.layout} expects ${config.layout.tileCount} tiles, got ${config.tiles.size}"
        }
        if (config.refreshIntervalSeconds < BoardLimits.MIN_REFRESH_INTERVAL_SECONDS) {
            errors += "refreshIntervalSeconds must be at least ${BoardLimits.MIN_REFRESH_INTERVAL_SECONDS}"
        }
        config.tiles.forEachIndexed { tileIndex, tile ->
            if (tile.elements.isEmpty()) errors += "tile $tileIndex has no elements"
            if (tile.rotationIntervalSeconds < BoardLimits.MIN_ROTATION_INTERVAL_SECONDS) {
                errors += "tile $tileIndex: rotationIntervalSeconds must be at least ${BoardLimits.MIN_ROTATION_INTERVAL_SECONDS}"
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

                    BoardElementType.MATCH_LIST -> {
                        if (element.listMode == null) errors += "$at: MATCH_LIST needs listMode"
                        val limit = element.limit
                        if (limit == null || limit < BoardLimits.MIN_LIST_LIMIT || limit > BoardLimits.MAX_LIST_LIMIT) {
                            errors += "$at: MATCH_LIST needs limit in ${BoardLimits.MIN_LIST_LIMIT}..${BoardLimits.MAX_LIST_LIMIT}"
                        }
                    }

                    BoardElementType.TEXT -> {
                        if (element.text.isNullOrBlank()) errors += "$at: TEXT needs text"
                    }

                    BoardElementType.CLOCK -> {}
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
                layout = BoardLayout.THREE_COLUMNS,
                tiles = listOf(
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH, offset = 0))),
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH, offset = 1))),
                    BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH, offset = -1))),
                ),
            ),
        )
    }
}
