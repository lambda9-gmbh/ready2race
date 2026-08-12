package de.lambda9.ready2race.backend.app.eventInfo

import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.backend.app.eventInfo.boundary.BoardService
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardConfig
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardElement
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardElementType
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardRequest
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardTile
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die DATENSCHUTZ-Leitplanke der Sprecher-Kachel: Boards sind öffentliche Endpunkte, deshalb
 * dürfen ausschließlich Bedingungen mit `publicly_visible = true` erscheinen, und davon nur die,
 * die für die Rolle der Person überhaupt gelten — dieselbe Regel wie das persönliche Dashboard.
 * Interne Bedingungen und die Freitext-Notiz der Erfüllung dürfen die Antwort NIE erreichen.
 * Genau das wird hier an einer echten Datenbank belegt, inklusive einer Volltextsuche über die
 * serialisierte Antwort nach der internen Notiz.
 */
class BoardRequirementFilterTest {

    private fun activateMatch(matchId: UUID) = Jooq.query {
        update(COMPETITION_MATCH)
            .set(COMPETITION_MATCH.ACTIVATED_AT, LocalDateTime.now())
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
            .execute()
    }

    private fun detailBoard() = BoardRequest(
        name = "Sprecherin",
        config = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH_DETAIL, offset = 0)))
            ),
        ),
    )

    @Test
    fun onlyPubliclyVisibleAndApplicableRequirementsReachTheBoard() = testComprehension {
        val fixture = !MyEventFixture.create()
        !activateMatch(fixture.ownMatchId)

        val board = (!BoardService.createBoard(fixture.eventId, detailBoard())).dto
        val view = (!BoardService.getBoardView(fixture.eventId, board.id)).dto

        val match = view.slots.single { it.offset == 0 }.match
        val participants = match!!.teams.single().participants
        val mia = participants.single { it.name == fixture.participantName }
        val cox = participants.single { it.role != null && it.role != "Ruderin" }

        // Die freigegebene, rollenfreie Bedingung erscheint — samt Erfüllt-Status.
        assertTrue(mia.requirements.any { it.name == fixture.publicRequirementName && it.fulfilled })
        // Die rollengebundene Steuerprüfung gilt nur für die Steuerfrau — bei ihr offen,
        // bei der Ruderin gar nicht erst gelistet (Fehlalarm-Regel des Dashboards).
        assertTrue(cox.requirements.any { it.name == fixture.coxRequirementName && !it.fulfilled })
        assertFalse(mia.requirements.any { it.name == fixture.coxRequirementName })

        // Die unfreigegebene Bedingung (publicly_visible = false) erreicht NIEMANDEN.
        assertTrue(participants.flatMap { it.requirements }.none { it.name.startsWith("Interne Prüfung") })

        // Und die interne Freitext-Notiz steht nirgends in der Antwort — auch nicht versteckt
        // in einem Feld, an das hier niemand gedacht hat.
        val mapper = ObjectMapper().findAndRegisterModules()
        assertFalse(mapper.writeValueAsString(view).contains(fixture.internalNote))
    }

    // Ohne Sprecher-Kachel bleibt die Antwort schlank: ein gewöhnliches MATCH-Element mit
    // Crew-Details lädt keine Bedingungen (needs.requirements greift nur bei MATCH_DETAIL).
    @Test
    fun plainMatchElementsCarryNoRequirements() = testComprehension {
        val fixture = !MyEventFixture.create()
        !activateMatch(fixture.ownMatchId)

        val board = (!BoardService.createBoard(
            fixture.eventId,
            BoardRequest(
                name = "Steg",
                config = BoardConfig(
                    columns = 1,
                    tiles = listOf(
                        BoardTile(
                            elements = listOf(
                                BoardElement(type = BoardElementType.MATCH, offset = 0, showCrewDetails = true)
                            )
                        )
                    ),
                ),
            ),
        )).dto
        val view = (!BoardService.getBoardView(fixture.eventId, board.id)).dto
        val participants = view.slots.single { it.offset == 0 }.match!!.teams.single().participants
        assertEquals(emptyList(), participants.flatMap { it.requirements })
    }
}
