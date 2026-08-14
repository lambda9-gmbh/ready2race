package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.control.toDto
import de.lambda9.ready2race.backend.app.participantRequirement.control.toRecord
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Die Freigabe für "Mein Event" ist die einzige Stelle, an der eine Aussage über eine Person
 * öffentlich wird. Ein stillschweigend auf `true` gekipptes Standardverhalten wäre ein
 * Datenschutzfehler, deshalb prüfen diese Tests beide Richtungen der Abbildung samt Standard.
 */
class ParticipantRequirementConversionsTest {

    private fun upsert(
        publiclyVisible: Boolean?,
        perEventDay: Boolean? = false,
        perCompetition: Boolean? = false,
    ) = ParticipantRequirementUpsertDto(
        name = "Aktivenpass",
        description = null,
        publicNote = null,
        optional = false,
        checkInApp = false,
        publiclyVisible = publiclyVisible,
        perEventDay = perEventDay,
        perCompetition = perCompetition,
        checkEarliestMinutesBefore = null,
        checkLatestMinutesBefore = null,
    )

    @Test
    fun upsertCarriesPublicNoteIntoRecordAndBack() {
        // Der öffentliche Text und die interne Beschreibung sind zwei Spalten; die Abbildung
        // darf sie in keiner Richtung vertauschen.
        val record = upsert(false)
            .copy(description = "intern", publicNote = "öffentlich")
            .toRecord(UUID.randomUUID())
            .unsafeRunSync().getOrNull()
        assertNotNull(record)
        assertEquals("intern", record.description)
        assertEquals("öffentlich", record.publicNote)

        val dto = record.toDto().unsafeRunSync().getOrNull()
        assertNotNull(dto)
        assertEquals("intern", dto.description)
        assertEquals("öffentlich", dto.publicNote)
    }

    @Test
    fun upsertCarriesPubliclyVisibleIntoRecord() {
        val record = upsert(true).toRecord(UUID.randomUUID()).unsafeRunSync().getOrNull()
        assertNotNull(record)
        assertEquals(true, record.publiclyVisible)
    }

    @Test
    fun missingPubliclyVisibleDefaultsToFalse() {
        val record = upsert(null).toRecord(UUID.randomUUID()).unsafeRunSync().getOrNull()
        assertNotNull(record)
        assertEquals(false, record.publiclyVisible)
    }

    @Test
    fun recordCarriesPubliclyVisibleIntoDto() {
        val now = LocalDateTime.of(2026, 8, 9, 12, 0)
        val record = ParticipantRequirementRecord(
            id = UUID.randomUUID(),
            name = "Aktivenpass",
            description = null,
            optional = false,
            checkInApp = false,
            publiclyVisible = true,
            checkEarliestMinutesBefore = null,
            checkLatestMinutesBefore = null,
            createdAt = now,
            createdBy = null,
            updatedAt = now,
            updatedBy = null,
        )
        val dto = record.toDto().unsafeRunSync().getOrNull()
        assertNotNull(dto)
        assertEquals(true, dto.publiclyVisible)

        record.publiclyVisible = false
        val dtoOff = record.toDto().unsafeRunSync().getOrNull()
        assertNotNull(dtoOff)
        assertFalse(dtoOff.publiclyVisible)
    }

    /**
     * Der Geltungsbereich (V202608141900) läuft durch dieselbe Abbildung. Wichtig ist der
     * Standard: eine Bedingung ohne Angabe bleibt bei "gilt je Veranstaltung" und damit beim
     * Verhalten vor dem 14.08.2026 — ein versehentlich eingeschalteter Schalter ließe eine
     * längst erfüllte Bedingung über Nacht wieder als offen erscheinen.
     */
    @Test
    fun scopeSwitchesTravelBothWaysAndDefaultToOff() {
        val record = upsert(false, perEventDay = true, perCompetition = true)
            .toRecord(UUID.randomUUID())
            .unsafeRunSync().getOrNull()
        assertNotNull(record)
        assertEquals(true, record.perEventDay)
        assertEquals(true, record.perCompetition)

        val dto = record.toDto().unsafeRunSync().getOrNull()
        assertNotNull(dto)
        assertEquals(true, dto.perEventDay)
        assertEquals(true, dto.perCompetition)

        val ohneAngabe = upsert(false, perEventDay = null, perCompetition = null)
            .toRecord(UUID.randomUUID())
            .unsafeRunSync().getOrNull()
        assertNotNull(ohneAngabe)
        assertEquals(false, ohneAngabe.perEventDay)
        assertEquals(false, ohneAngabe.perCompetition)
    }
}
