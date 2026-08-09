package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameLogic
import de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubShortNameRecord
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ClubShortNameRepo] gegen ein echtes Postgres. Geprüft wird der Umlauf, den die Pflegeseite
 * auslöst - anlegen, überschreiben, löschen -, weil genau daran der Upsert hängt: schriebe er die
 * Kurzform bei der zweiten Eingabe nicht fort, fiele die Anzeige stillschweigend auf die Heuristik
 * zurück.
 */
class ClubShortNameRepoTest {

    private val createdAt: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)
    private val updatedAt: LocalDateTime = LocalDateTime.of(2026, 8, 9, 13, 30)

    private val name = "Erster Kieler Ruder-Club von 1862 e.V."

    private fun record(
        shortName: String,
        sampleName: String = name,
        updatedAt: LocalDateTime = createdAt,
    ) = ClubShortNameRecord(
        nameKey = ClubNameKey.of(name),
        sampleName = sampleName,
        shortName = shortName,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun aMaintainedShortNameIsWrittenReadBackAndOverwritten() = testComprehension {
        val key = ClubNameKey.of(name)

        !ClubShortNameRepo.upsert(record("1. KRC"))

        assertEquals(mapOf(key to "1. KRC"), !ClubShortNameRepo.aliases())
        // Der Zweck der Tabelle: die gepflegte Form schlägt die Heuristik ("Erster Kieler RC").
        assertEquals("1. KRC", ClubShortNameLogic.shorten(name, !ClubShortNameRepo.aliases()))

        // Dieselbe Zeile ein zweites Mal - die Pflegeseite kennt kein "neu" und "ändern".
        !ClubShortNameRepo.upsert(
            record(shortName = "1. Kieler RC", sampleName = "Erster Kieler Ruder-Club", updatedAt = updatedAt)
        )

        val stored = (!ClubShortNameRepo.all()).single()
        assertEquals("1. Kieler RC", stored.shortName)
        assertEquals("Erster Kieler Ruder-Club", stored.sampleName)
        // Wer ändert, ist nicht, wer angelegt hat: das Anlegedatum bleibt stehen.
        assertEquals(createdAt, stored.createdAt)
        assertEquals(updatedAt, stored.updatedAt)
    }

    /** Leeren heißt "zurück zur Heuristik", nicht "keine Kurzform". */
    @Test
    fun deletingLeavesTheHeuristicInCharge() = testComprehension {
        !ClubShortNameRepo.upsert(record("1. KRC"))

        assertEquals(1, !ClubShortNameRepo.delete(ClubNameKey.of(name)))

        assertEquals(emptyMap<String, String>(), !ClubShortNameRepo.aliases())
        assertEquals("Erster Kieler RC", ClubShortNameLogic.shorten(name, !ClubShortNameRepo.aliases()))
    }
}
