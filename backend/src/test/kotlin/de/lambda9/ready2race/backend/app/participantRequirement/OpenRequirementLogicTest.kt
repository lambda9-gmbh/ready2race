package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.boundary.OpenRequirementLogic
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.OpenRequirementLogic.RequirementScope
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenRequirementLogicTest {

    private val aktivenpassId = UUID.randomUUID()
    private val waageId = UUID.randomUUID()
    private val steuerleute = UUID.randomUUID()
    private val seniorIn = UUID.randomUUID()

    private val aktivenpass = RequirementScope(aktivenpassId, "Aktivenpass", namedParticipantId = null)
    private val waageFuerSteuerleute = RequirementScope(waageId, "Waage 55 kg", namedParticipantId = steuerleute)

    private fun open(
        requirements: List<RequirementScope> = listOf(aktivenpass, waageFuerSteuerleute),
        roles: List<UUID> = listOf(seniorIn),
        checked: List<UUID> = emptyList(),
    ) = OpenRequirementLogic.openFor(requirements, roles, checked).map { it.name }

    @Test
    fun aGlobalRequirementIsOpenForEveryone() {
        assertEquals(listOf("Aktivenpass"), open())
    }

    @Test
    fun aRoleBoundRequirementOnlyShowsUpForThatRole() {
        // Der Punkt, an dem der Export sonst wertlos wäre: "Waage 55 kg" gilt nur für
        // Steuerleute. Die Abfrage der offenen Personen filtert nicht nach Rolle - täte der
        // Export das auch nicht, stünde die Bedingung bei jedem Gemeldeten als offen.
        assertEquals(listOf("Aktivenpass"), open(roles = listOf(seniorIn)))
        assertEquals(
            listOf("Aktivenpass", "Waage 55 kg"),
            open(roles = listOf(seniorIn, steuerleute)),
        )
    }

    @Test
    fun anAlreadyCheckedRequirementIsNotOpen() {
        assertEquals(
            listOf("Waage 55 kg"),
            open(roles = listOf(steuerleute), checked = listOf(aktivenpassId)),
        )
        assertTrue(
            open(roles = listOf(steuerleute), checked = listOf(aktivenpassId, waageId)).isEmpty(),
        )
    }

    @Test
    fun aParticipantWithoutRolesOnlyOwesGlobalRequirements() {
        assertEquals(listOf("Aktivenpass"), open(roles = emptyList()))
    }

    @Test
    fun aRequirementBoundToSeveralRolesCountsOnce() {
        // Dieselbe Bedingung kann mehreren Rollen zugeordnet sein - das sind mehrere Zeilen in
        // event_has_participant_requirement. Wer beide Rollen fährt, soll sie trotzdem nur
        // einmal in der Liste haben.
        val zweiteRolle = RequirementScope(waageId, "Waage 55 kg", namedParticipantId = seniorIn)

        assertEquals(
            listOf("Waage 55 kg"),
            open(
                requirements = listOf(waageFuerSteuerleute, zweiteRolle),
                roles = listOf(seniorIn, steuerleute),
            ),
        )
    }

    @Test
    fun theOrderFollowsTheGivenRequirements() {
        // Die Reihenfolge der Spalte "Fehlende Bedingungen" soll stabil sein, damit sich
        // zwei Exporte vergleichen lassen.
        assertEquals(
            listOf("Aktivenpass", "Waage 55 kg"),
            open(
                requirements = listOf(aktivenpass, waageFuerSteuerleute),
                roles = listOf(steuerleute),
            ),
        )
        assertEquals(
            listOf("Waage 55 kg", "Aktivenpass"),
            open(
                requirements = listOf(waageFuerSteuerleute, aktivenpass),
                roles = listOf(steuerleute),
            ),
        )
    }
}
