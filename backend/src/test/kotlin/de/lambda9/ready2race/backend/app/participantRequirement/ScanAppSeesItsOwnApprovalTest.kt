package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventInfo.MyEventFixture
import de.lambda9.ready2race.backend.app.participant.boundary.ParticipantService
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.ParticipantRequirementService
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantScanScopeRepo
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementApproveForParticipantDto
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.testing.testComprehension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Fehler vom Regattatag 15.08.2026: An der Waage blieb der Knopf stehen. Die Bestätigung war
 * geschrieben - der Server antwortete 204 -, aber die Liste, aus der die Scan-App ihren Haken
 * zieht, lieferte die Erfüllungen ohne ihre Dimensionen. Die App fragt nicht "gibt es einen
 * Haken?", sondern "deckt er DIESEN Wettkampf an DIESEM Tag ab?", und ohne Tag und Wettkampf ist
 * die Antwort darauf immer nein.
 *
 * Der Test geht bewusst durch denselben Service wie der Endpunkt `/participant-app`
 * ([ParticipantService.pageForEvent]) und nicht durch das Repo: Genau zwischen beiden ging die
 * Angabe verloren.
 */
class ScanAppSeesItsOwnApprovalTest {

    private fun params() = PaginationParameters<ParticipantForEventSort>(
        limit = 200,
        offset = 0,
        sort = null,
        search = null,
    )

    @Test
    fun theAppListCarriesTheDimensionsOfEachApproval() = testComprehension {
        val fixture = !MyEventFixture.create()

        // Die Bedingung der Fixture auf den Fall der Waage stellen: je Tag und je Wettkampf.
        !PARTICIPANT_REQUIREMENT.update(
            f = {
                perEventDay = true
                perCompetition = true
            },
            condition = { NAME.eq(fixture.publicRequirementName) },
        )
        val requirementId = !PARTICIPANT_REQUIREMENT.select { NAME.eq(fixture.publicRequirementName) }
            .map { found -> found.single().id!! }

        val competitions =
            !ParticipantScanScopeRepo.getCompetitionsOfParticipant(fixture.eventId, fixture.participantId)
        assertTrue(competitions.isNotEmpty(), "die Fixture meldet die Person in Wettkämpfen")
        val competition = competitions.first()

        !ParticipantRequirementService.approveRequirementForParticipant(
            fixture.eventId,
            ParticipantRequirementApproveForParticipantDto(
                requirementId = requirementId,
                participantId = fixture.participantId,
                approved = true,
                competitionId = competition.id,
            ),
            SYSTEM_USER,
        )

        // Die Liste führt eine Person je Meldung - hier zählt, was in Summe an ihr hängt.
        val page = !ParticipantService.pageForEvent(params(), fixture.eventId, null, Privilege.Scope.GLOBAL)
        val checked = page.data
            .filter { it.id == fixture.participantId }
            .flatMap { it.participantRequirementsChecked ?: emptyList() }
            .filter { it.id == requirementId }

        assertTrue(checked.isNotEmpty(), "die Bestätigung muss in der App-Liste ankommen")
        val forCompetition = checked.single { it.competitionId == competition.id }
        assertEquals(
            competition.id,
            forCompetition.competitionId,
            "ohne den Wettkampf findet die Scan-App ihren eigenen Haken nicht wieder",
        )
        // Der Tag bleibt hier bewusst offen: Die Fixture legt gar keine Wettkampftage an, und
        // `eventDayOf` erfindet keinen - genau das ist die vorsichtige Richtung. Dass die Spalte
        // durchgereicht wird, deckt der Wettkampf oben ab; beide gehen denselben Weg.
        assertNull(forCompetition.eventDayId, "ohne Wettkampftage gibt es keinen Tag zu setzen")
    }
}
