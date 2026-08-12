package de.lambda9.ready2race.backend.app.eventInfo.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.BoardRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.BOARD
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.UUID

/** „Weiter kommen [seats] Boote → [nextRoundName]"; [seats] null bei Massenfeld-Folgerunde. */
data class BoardAdvancement(val nextRoundName: String?, val seats: Int?)

object BoardRepo {

    /**
     * Aufsteigend nach Anlagedatum: eine stabile Reihenfolge, deren erstes Element die
     * Umleitung der alten Athleten-Board-URL trägt.
     */
    fun findByEvent(eventId: UUID): JIO<List<BoardRecord>> =
        Jooq.query {
            selectFrom(BOARD)
                .where(BOARD.EVENT_ID.eq(eventId))
                .orderBy(BOARD.CREATED_AT.asc(), BOARD.ID.asc())
                .fetch()
        }

    fun findById(id: UUID) = BOARD.selectOne { ID.eq(id) }

    fun create(record: BoardRecord) = BOARD.insertReturning(record) { ID }

    fun update(id: UUID, f: BoardRecord.() -> Unit) = BOARD.update(f) { ID.eq(id) }

    fun delete(id: UUID) = BOARD.delete { ID.eq(id) }

    fun getAsJson(eventId: UUID) = BOARD.selectAsJson { EVENT_ID.eq(eventId) }

    fun insertJsonData(data: String) = BOARD.insertJsonData(data)

    /**
     * Je Setup-Lauf die Folgerunde und ihre Platzzahl — die Grundlage der
     * Sprecherinnen-Zeile „Weiter kommen N Boote → Finale". Die Platzzahl ist die Summe
     * der fest gesetzten Bootszahlen der Folgerunde; enthält sie ein Massenfeld
     * (teams IS NULL), bleibt sie null und die Anzeige nennt nur die Runde. Läufe der
     * letzten Runde fehlen in der Antwort — dort gibt es kein Weiterkommen.
     */
    fun advancementBySetupMatch(setupMatchIds: Collection<UUID>): JIO<Map<UUID, BoardAdvancement>> =
        Jooq.query {
            if (setupMatchIds.isEmpty()) return@query emptyMap()

            val round = COMPETITION_SETUP_ROUND.`as`("round")
            val nextRound = COMPETITION_SETUP_ROUND.`as`("next_round_t")
            val nextMatch = COMPETITION_SETUP_MATCH.`as`("next_match")

            val seatSum = DSL.sum(nextMatch.TEAMS).`as`("seat_sum")
            val matchCount = DSL.count(nextMatch.ID).`as`("match_count")
            val sizedCount = DSL.count(nextMatch.TEAMS).`as`("sized_count")

            select(COMPETITION_SETUP_MATCH.ID, nextRound.NAME, seatSum, matchCount, sizedCount)
                .from(COMPETITION_SETUP_MATCH)
                .join(round).on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(round.ID))
                .join(nextRound).on(round.NEXT_ROUND.eq(nextRound.ID))
                .join(nextMatch).on(nextMatch.COMPETITION_SETUP_ROUND.eq(nextRound.ID))
                .where(COMPETITION_SETUP_MATCH.ID.`in`(setupMatchIds))
                .groupBy(COMPETITION_SETUP_MATCH.ID, nextRound.NAME)
                .fetch()
                .associate { r ->
                    val allSized = r.get(matchCount) == r.get(sizedCount)
                    r.get(COMPETITION_SETUP_MATCH.ID)!! to BoardAdvancement(
                        nextRoundName = r.get(nextRound.NAME),
                        seats = if (allSized) r.get(seatSum)?.toInt() else null,
                    )
                }
        }

    /**
     * Der gemessene Start je Boot (`competition_match_team.started_at`) für die
     * Sprecher-Kachel — nur Boote mit Stempel. Schlüssel ist (Lauf, Startnummer), weil die
     * Anzeige-DTOs keine Meldungs-IDs führen; die Startnummer ist je Lauf eindeutig und
     * NOT NULL (Migration V202507040930). Beide `!!` lesen NOT-NULL-Spalten der führenden
     * Tabelle bzw. eine per `isNotNull` gefilterte — Begründungsmuster wie bei
     * `EventInfoService.getMatchResultTeams`.
     */
    fun boatStarts(matchIds: Collection<UUID>): JIO<Map<Pair<UUID, Int>, LocalDateTime>> =
        Jooq.query {
            if (matchIds.isEmpty()) return@query emptyMap()
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_MATCH,
                COMPETITION_MATCH_TEAM.START_NUMBER,
                COMPETITION_MATCH_TEAM.STARTED_AT,
            )
                .from(COMPETITION_MATCH_TEAM)
                .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.`in`(matchIds))
                .and(COMPETITION_MATCH_TEAM.STARTED_AT.isNotNull)
                .fetch()
                .associate { r ->
                    (r.get(COMPETITION_MATCH_TEAM.COMPETITION_MATCH)!! to
                        r.get(COMPETITION_MATCH_TEAM.START_NUMBER)!!) to
                        r.get(COMPETITION_MATCH_TEAM.STARTED_AT)!!
                }
        }
}
