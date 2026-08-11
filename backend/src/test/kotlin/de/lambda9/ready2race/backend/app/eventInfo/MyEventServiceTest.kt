package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventService
import de.lambda9.ready2race.backend.app.eventInfo.entity.EventInfoProblem
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventDto
import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.testing.testComprehension
import java.util.UUID
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
    fun roleBoundRequirementAppearsForASubstitute() = testComprehension {
        // Wer als Steuerfrau einrückt, braucht die Steuerprüfung - genau wie die, für die sie
        // einspringt. Ihre Rolle steht in der Auswechslung, nicht in einer Meldung.
        val fixture = !MyEventFixture.create()
        !MyEventFixture.substitute(
            registrationId = fixture.ownRegistrationId,
            roundId = fixture.racedRoundId,
            namedParticipantId = fixture.coxRoleId,
            participantOut = fixture.coxId,
            participantIn = fixture.reserveId,
            orderForRound = 1,
        )

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.reserveQrCode)
        val names = response.dto.requirements.map { it.name }
        assertTrue(names.contains(fixture.coxRequirementName), "Steuerprüfung fehlt: $names")
        // Die rollenfreie Bedingung galt schon vorher für jede Person.
        assertTrue(names.contains(fixture.publicRequirementName))
    }

    @Test
    fun substituteOnlyGetsTheRequirementsOfTheirOwnRole() = testComprehension {
        // Die Rolle kommt aus der Auswechslung, nicht aus dem Boot: wer als Ruderin einrückt,
        // sitzt neben einer Steuerfrau, braucht deren Prüfung aber nicht.
        val fixture = !MyEventFixture.create()
        !MyEventFixture.substitute(
            registrationId = fixture.ownRegistrationId,
            roundId = fixture.racedRoundId,
            namedParticipantId = fixture.rowerRoleId,
            participantOut = fixture.participantId,
            participantIn = fixture.reserveId,
            orderForRound = 1,
        )

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.reserveQrCode)
        val names = response.dto.requirements.map { it.name }
        assertFalse(names.contains(fixture.coxRequirementName), "Fremde Steuerprüfung: $names")
        assertTrue(names.contains(fixture.publicRequirementName))
    }

    @Test
    fun substitutedInParticipantSeesTheMatch() = testComprehension {
        // Wer für eine Runde einrückt, hat an dem Tag einen Lauf - auch ohne eigene Meldung.
        val fixture = !MyEventFixture.create()
        !MyEventFixture.substitute(
            registrationId = fixture.ownRegistrationId,
            roundId = fixture.racedRoundId,
            namedParticipantId = fixture.rowerRoleId,
            participantOut = fixture.participantId,
            participantIn = fixture.reserveId,
            orderForRound = 1,
        )

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.reserveQrCode)
        assertTrue(matchIds(response.dto).contains(fixture.ownMatchId))
    }

    @Test
    fun substitutedOutParticipantNoLongerSeesTheMatch() = testComprehension {
        // Die Kehrseite: sonst steht der Lauf weiter auf dem Telefon von jemandem, der nicht
        // mehr im Boot sitzt.
        val fixture = !MyEventFixture.create()
        !MyEventFixture.substitute(
            registrationId = fixture.ownRegistrationId,
            roundId = fixture.racedRoundId,
            namedParticipantId = fixture.rowerRoleId,
            participantOut = fixture.participantId,
            participantIn = fixture.reserveId,
            orderForRound = 1,
        )

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertFalse(matchIds(response.dto).contains(fixture.ownMatchId))
    }

    @Test
    fun teamListShowsTheSubstituteInsteadOfTheSubstitutedOut() = testComprehension {
        // Aus der Sicht einer unbeteiligten Mitfahrerin geprüft: die Aufstellung ist die des
        // Laufs, nicht die der Meldung.
        val fixture = !MyEventFixture.create()
        !MyEventFixture.substitute(
            registrationId = fixture.ownRegistrationId,
            roundId = fixture.racedRoundId,
            namedParticipantId = fixture.rowerRoleId,
            participantOut = fixture.participantId,
            participantIn = fixture.reserveId,
            orderForRound = 1,
        )

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.teamMateQrCode)
        val match = (response.dto.running + response.dto.upcoming).first { it.matchId == fixture.ownMatchId }
        val names = match.teamMembers.map { it.name }
        assertTrue(names.contains(fixture.reserveName), "Ersatzfrau fehlt in der Aufstellung: $names")
        assertFalse(names.contains(fixture.participantName), "Ausgewechselte steht noch drin: $names")
    }

    @Test
    fun swapMovesBothPersonsToTheOtherBoat() = testComprehension {
        // Ein Tausch sind zwei Zeilen mit aufeinanderfolgender Reihenfolge - je eine pro Boot.
        // Beide Personen wechseln den Lauf, keine verliert ihren.
        val fixture = !MyEventFixture.create()
        !MyEventFixture.substitute(
            registrationId = fixture.ownRegistrationId,
            roundId = fixture.racedRoundId,
            namedParticipantId = fixture.rowerRoleId,
            participantOut = fixture.participantId,
            participantIn = fixture.strangerId,
            orderForRound = 1,
        )
        !MyEventFixture.substitute(
            registrationId = fixture.foreignRegistrationId,
            roundId = fixture.racedRoundId,
            namedParticipantId = fixture.rowerRoleId,
            participantOut = fixture.strangerId,
            participantIn = fixture.participantId,
            orderForRound = 2,
        )

        val forOwn = matchIds((!MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)).dto)
        assertTrue(forOwn.contains(fixture.foreignMatchId))
        assertFalse(forOwn.contains(fixture.ownMatchId))

        val forStranger = matchIds((!MyEventService.getMyEvent(fixture.eventId, fixture.strangerQrCode)).dto)
        assertTrue(forStranger.contains(fixture.ownMatchId))
        assertFalse(forStranger.contains(fixture.foreignMatchId))
    }

    @Test
    fun substitutionOfAnotherRoundDoesNotChangeTheLineup() = testComprehension {
        // Auswechslungen sind rundenscharf. Eine, die zu einer anderen Runde gehört, darf den
        // Lauf hier nicht anfassen - der zweite Wettkampf im Aufbau hat seine eigene Runde.
        val fixture = !MyEventFixture.create()
        !MyEventFixture.substitute(
            registrationId = fixture.ownRegistrationId,
            roundId = fixture.unscheduledRoundId,
            namedParticipantId = fixture.rowerRoleId,
            participantOut = fixture.participantId,
            participantIn = fixture.reserveId,
            orderForRound = 1,
        )

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertTrue(matchIds(response.dto).contains(fixture.ownMatchId))

        val forReserve = !MyEventService.getMyEvent(fixture.eventId, fixture.reserveQrCode)
        assertFalse(matchIds(forReserve.dto).contains(fixture.ownMatchId))
    }

    private fun matchIds(dto: MyEventDto): List<UUID> =
        (dto.running + dto.upcoming).map { it.matchId } + dto.results.map { it.matchId }

    @Test
    fun deregisteredRegistrationIsNotUnscheduled() = testComprehension {
        // Vor der Auslosung zurückgezogen: es kommt kein Lauf mehr, "gemeldet, noch kein Lauf"
        // wäre eine falsche Ansage.
        val fixture = !MyEventFixture.create()
        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertFalse(response.dto.unscheduled.any { it.teamName == fixture.deregisteredTeamName })
    }
}
