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

    private fun upsert(publiclyVisible: Boolean?) = ParticipantRequirementUpsertDto(
        name = "Aktivenpass",
        description = null,
        optional = false,
        checkInApp = false,
        publiclyVisible = publiclyVisible,
        checkEarliestMinutesBefore = null,
        checkLatestMinutesBefore = null,
    )

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
}
