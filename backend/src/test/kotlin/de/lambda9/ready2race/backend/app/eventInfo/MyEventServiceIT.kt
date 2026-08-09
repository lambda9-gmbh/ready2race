package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventService
import de.lambda9.ready2race.backend.app.eventInfo.entity.EventInfoProblem
import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.testing.testComprehension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Prüft die Zugriffsgrenzen des öffentlichen Endpunkts an einer echten Datenbank. Genau hier
 * entscheidet sich, ob ein fremder oder ein Helfer-Code an persönliche Daten kommt — das
 * lässt sich nicht sinnvoll mit Attrappen prüfen.
 */
class MyEventServiceIT {

    @Test
    fun unknownCodeIsNotFound() = testComprehension {
        val fixture = !MyEventFixture.create()
        assertKIOFails(EventInfoProblem.QrCodeNotFound("gibt-es-nicht")) {
            MyEventService.getMyEvent(fixture.eventId, "gibt-es-nicht")
        }
    }

    @Test
    fun codeOfAnotherEventIsNotFound() = testComprehension {
        val fixture = !MyEventFixture.create()
        val other = !MyEventFixture.create()
        assertKIOFails(EventInfoProblem.QrCodeNotFound(fixture.participantQrCode)) {
            MyEventService.getMyEvent(other.eventId, fixture.participantQrCode)
        }
    }

    @Test
    fun appUserCodeIsNotFound() = testComprehension {
        val fixture = !MyEventFixture.create()
        assertKIOFails(EventInfoProblem.QrCodeNotFound(fixture.appUserQrCode)) {
            MyEventService.getMyEvent(fixture.eventId, fixture.appUserQrCode)
        }
    }

    @Test
    fun ownMatchesAreReturnedAndForeignAreNot() = testComprehension {
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        val dto = response.dto
        val allMatchIds = (dto.running + dto.upcoming).map { it.matchId } + dto.results.map { it.matchId }
        assertTrue(allMatchIds.contains(fixture.ownMatchId))
        assertFalse(allMatchIds.contains(fixture.foreignMatchId))
    }

    @Test
    fun onlyPubliclyVisibleRequirementsAreReturned() = testComprehension {
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        val names = response.dto.requirements.map { it.name }
        assertEquals(listOf(fixture.publicRequirementName), names)
    }

    @Test
    fun internalNoteNeverLeavesTheServer() = testComprehension {
        // Gegen die ausgelieferte JSON-Darstellung geprüft, nicht gegen die Datenklasse:
        // ein später ergänztes Feld oder eine eingebettete Struktur würde die Notiz sonst
        // unbemerkt nach außen tragen. Der Test muss scheitern, sobald sie irgendwo auftaucht.
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        val json = ObjectMapper().findAndRegisterModules().writeValueAsString(response.dto)
        assertFalse(json.contains(fixture.internalNote))
    }

    @Test
    fun registrationWithoutMatchAppearsAsUnscheduled() = testComprehension {
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertTrue(response.dto.unscheduled.any { it.competitionId == fixture.unscheduledCompetitionId })
    }
}
