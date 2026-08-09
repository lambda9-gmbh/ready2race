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
 *
 * Der Name endet auf `Test` und nicht auf `IT`: die Vorgabe von Surefire erfasst nur `Test*`,
 * `*Test`, `*Tests` und `*TestCase`, und backend/pom.xml stellt nichts anderes ein. Unter dem
 * alten Namen liefen ausgerechnet die Zugriffsprüfungen in keinem gewöhnlichen Testlauf mit.
 * Inhaltlich passt `Test` ohnehin besser, denn hier wird über `testComprehension` gegen die
 * Datenbank geprüft und nicht über `testApplicationComprehension` end-to-end.
 */
class MyEventServiceTest {

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
    fun roleBoundRequirementOnlyAppearsForThatRole() = testComprehension {
        // Eine an eine Rolle gebundene Bedingung darf nicht bei allen anderen als "nicht erfüllt"
        // stehen - das schickt Leute am Veranstaltungstag ohne Grund zur Meldestelle.
        val fixture = !MyEventFixture.create()

        val forCox = !MyEventService.getMyEvent(fixture.eventId, fixture.coxQrCode)
        assertTrue(forCox.dto.requirements.map { it.name }.contains(fixture.coxRequirementName))
        // Die rollenfreie Bedingung gilt daneben weiter für beide.
        assertTrue(forCox.dto.requirements.map { it.name }.contains(fixture.publicRequirementName))

        val forRower = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertFalse(forRower.dto.requirements.map { it.name }.contains(fixture.coxRequirementName))
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

    @Test
    fun deregisteredRegistrationIsNotUnscheduled() = testComprehension {
        // Vor der Auslosung zurückgezogen: es kommt kein Lauf mehr, "gemeldet, noch kein Lauf"
        // wäre eine falsche Ansage.
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertFalse(response.dto.unscheduled.any { it.teamName == fixture.deregisteredTeamName })
    }
}
