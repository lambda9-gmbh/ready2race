package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementUpsertDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParticipantRequirementUpsertDtoTest {

    private fun dto(earliest: Int?, latest: Int?) = ParticipantRequirementUpsertDto(
        name = "Waage",
        description = null,
        publicNote = null,
        optional = false,
        checkInApp = false,
        publiclyVisible = false,
        perEventDay = false,
        perCompetition = false,
        checkEarliestMinutesBefore = earliest,
        checkLatestMinutesBefore = latest,
    )

    @Test
    fun validWithoutWindow() {
        assertEquals(ValidationResult.Valid, dto(null, null).validate())
    }

    @Test
    fun validWithOneSidedWindow() {
        assertEquals(ValidationResult.Valid, dto(120, null).validate())
        assertEquals(ValidationResult.Valid, dto(null, 15).validate())
    }

    @Test
    fun validWithFullWindow() {
        assertEquals(ValidationResult.Valid, dto(120, 15).validate())
    }

    @Test
    fun invalidWhenEarliestNotGreaterThanLatest() {
        assertNotEquals(ValidationResult.Valid, dto(15, 120).validate())
        assertNotEquals(ValidationResult.Valid, dto(60, 60).validate())
    }

    @Test
    fun invalidWhenNotPositive() {
        assertNotEquals(ValidationResult.Valid, dto(0, null).validate())
        assertNotEquals(ValidationResult.Valid, dto(null, -5).validate())
    }
}
