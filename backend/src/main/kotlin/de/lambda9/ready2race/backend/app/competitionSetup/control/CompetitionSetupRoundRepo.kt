package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundWithMatchesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND_WITH_MATCHES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_TEMPLATE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.time.LocalDateTime
import java.util.UUID

object CompetitionSetupRoundRepo {
    fun create(records: Collection<CompetitionSetupRoundRecord>) = COMPETITION_SETUP_ROUND.insert(records)

    fun delete(key: UUID) = COMPETITION_SETUP_ROUND.delete {
        COMPETITION_SETUP.eq(key).or(COMPETITION_SETUP_TEMPLATE.eq(key))
    }

    fun deleteByIds(ids: Collection<UUID>) = COMPETITION_SETUP_ROUND.delete { ID.`in`(ids) }

    // Clears every next_round pointer of a setup so rounds can be deleted/reinserted without violating the self FK.
    fun clearNextRounds(key: UUID) = COMPETITION_SETUP_ROUND.updateMany(
        f = { nextRound = null },
        condition = { COMPETITION_SETUP.eq(key).or(COMPETITION_SETUP_TEMPLATE.eq(key)) },
    )

    fun updateNextRound(id: UUID, nextRoundId: UUID?) = COMPETITION_SETUP_ROUND.update(
        f = { nextRound = nextRoundId },
        condition = { ID.eq(id) },
    )

    /**
     * Hält fest, dass diese Runde gesetzt wurde. Nur beim ersten Mal — der Zeitstempel ist die
     * Auskunft "es gab sie schon einmal" und darf beim Wiederholen nicht vorrücken, sonst ginge
     * genau die Unterscheidung verloren, für die er existiert.
     */
    fun markMaterialized(roundId: UUID, at: LocalDateTime) = COMPETITION_SETUP_ROUND.update(
        f = { materializedAt = at },
        condition = { ID.eq(roundId).and(MATERIALIZED_AT.isNull) },
    )

    fun get(id: UUID) = COMPETITION_SETUP_ROUND.selectOne { ID.eq(id) }

    fun getBySetupId(key: UUID): JIO<List<CompetitionSetupRoundRecord>> = Jooq.query {
        with(COMPETITION_SETUP_ROUND) {
            selectFrom(this)
                .where(COMPETITION_SETUP.eq(key).or(COMPETITION_SETUP_TEMPLATE.eq(key)))
                .fetch()
        }
    }

    fun getWithMatchesBySetup(setupId: UUID): JIO<List<CompetitionSetupRoundWithMatchesRecord>> = Jooq.query {
        with(COMPETITION_SETUP_ROUND_WITH_MATCHES) {
            selectFrom(this)
                .where(COMPETITION_SETUP.eq(setupId))
                .fetch()
        }
    }

    fun getWithMatches(id: UUID) = COMPETITION_SETUP_ROUND_WITH_MATCHES.selectOne { SETUP_ROUND_ID.eq(id) }

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_SETUP_ROUND.select({ ID }) { ID.`in`(ids) }

    fun getIdsBySetupIds(keys: List<UUID>) = COMPETITION_SETUP_ROUND.select({ ID }) {
        COMPETITION_SETUP.`in`(keys).or(COMPETITION_SETUP_TEMPLATE.`in`(keys))
    }

    fun getBySetupIdsAsJson(keys: List<UUID>) =
        COMPETITION_SETUP_ROUND.selectAsJson { COMPETITION_SETUP.`in`(keys).or(COMPETITION_SETUP_TEMPLATE.`in`(keys)) }

    fun insertJsonData(data: String) = COMPETITION_SETUP_ROUND.insertJsonData(data)

    /**
     * Ob der Ablauf dieses Wettkampfs eine Qualifikationsrunde enthaelt. Der Ablauf haengt nicht am
     * Wettkampf selbst, sondern an dessen competition_properties -- dieselbe Join-Kette wie in
     * [de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo.getStartListConfigTarget],
     * nur ohne den konkreten Lauf.
     */
    fun existsQualificationRound(competitionId: UUID): JIO<Boolean> = Jooq.query {
        fetchExists(
            selectFrom(
                COMPETITION_SETUP_ROUND
                    .join(COMPETITION_PROPERTIES)
                    .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            )
                .where(COMPETITION_PROPERTIES.COMPETITION.eq(competitionId))
                .and(COMPETITION_SETUP_ROUND.IS_QUALIFICATION.isTrue)
        )
    }

}