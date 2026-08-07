package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverity
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckSeverityEntryDto
import de.lambda9.ready2race.backend.app.liveDashboard.entity.CheckType
import de.lambda9.ready2race.backend.app.liveDashboard.entity.UpdateCheckSeverityRequest
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UpdateCheckSeverityRequestTest {

    private val competition = UUID.randomUUID()
    private val requirement = UUID.randomUUID()
    private val otherRequirement = UUID.randomUUID()

    private fun entry(
        checkType: CheckType,
        requirementId: UUID?,
        competitionId: UUID = competition,
        severity: CheckSeverity = CheckSeverity.CRITICAL,
    ) = CheckSeverityEntryDto(
        competitionId = competitionId,
        checkType = checkType,
        requirementId = requirementId,
        severity = severity,
    )

    @Test
    fun validRequestIsValid() {
        val request = UpdateCheckSeverityRequest(
            entries = listOf(
                entry(CheckType.INVOICE_OPEN, null),
                entry(CheckType.NOT_ON_WATER, null),
                entry(CheckType.REQUIREMENT, requirement),
                entry(CheckType.REQUIREMENT_TIME_WINDOW, requirement),
            )
        )
        assertEquals(ValidationResult.Valid, request.validate())
    }

    @Test
    fun invoiceOpenWithRequirementIdIsInvalid() {
        val request = UpdateCheckSeverityRequest(
            entries = listOf(entry(CheckType.INVOICE_OPEN, requirement))
        )
        assertNotEquals(ValidationResult.Valid, request.validate())
    }

    @Test
    fun requirementWithoutRequirementIdIsInvalid() {
        val request = UpdateCheckSeverityRequest(
            entries = listOf(entry(CheckType.REQUIREMENT, null))
        )
        assertNotEquals(ValidationResult.Valid, request.validate())
    }

    @Test
    fun duplicateEntriesAreInvalid() {
        val request = UpdateCheckSeverityRequest(
            entries = listOf(
                entry(CheckType.REQUIREMENT, requirement),
                entry(CheckType.REQUIREMENT, requirement),
            )
        )
        assertNotEquals(ValidationResult.Valid, request.validate())
    }

    @Test
    fun entriesDifferingOnlyInRequirementIdAreValid() {
        val request = UpdateCheckSeverityRequest(
            entries = listOf(
                entry(CheckType.REQUIREMENT, requirement),
                entry(CheckType.REQUIREMENT, otherRequirement),
            )
        )
        assertEquals(ValidationResult.Valid, request.validate())
    }

    @Test
    fun emptyEntriesIsValid() {
        val request = UpdateCheckSeverityRequest(entries = emptyList())
        assertEquals(ValidationResult.Valid, request.validate())
    }
}
