package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.app.ratingcategory.entity.AgeRestriction
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantView
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_VIEW
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.util.*

object ParticipantRepo {

    private fun ParticipantView.searchFields() = listOf(FIRSTNAME, LASTNAME, EXTERNAL_CLUB_NAME)

    fun create(record: ParticipantRecord) = PARTICIPANT.insertReturning(record) { PARTICIPANT.ID }
    fun create(records: List<ParticipantRecord>) = PARTICIPANT.insert(records)

    fun get(id: UUID) = PARTICIPANT.selectOne { ID.eq(id) }

    fun getOverlapIds(ids: List<UUID>) = PARTICIPANT.select({ ID }) { ID.`in`(ids) }

    fun getAgeRange(participantIds: List<UUID>): JIO<Pair<Int, Int>?> = Jooq.query {
        with(PARTICIPANT) {
            selectFrom(this)
                .where(ID.`in`(participantIds))
                .fetch()
                .let { records ->
                    if (records.isEmpty()) null
                    else {
                        val years = records.map { it.year }
                        Pair(years.min(), years.max())
                    }
                }
        }
    }

    fun all() = PARTICIPANT.select()

    fun update(id: UUID, f: ParticipantRecord.() -> Unit) = PARTICIPANT.update(f) { ID.eq(id) }

    fun any() = PARTICIPANT.exists { DSL.trueCondition() }

    fun update(
        id: UUID,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        f: ParticipantRecord.() -> Unit
    ) = PARTICIPANT.update(f) {
        ID.eq(id)
            .and(clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition())
            .and(filterScope(scope, user.club))
    }

    fun delete(
        id: UUID,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ) = PARTICIPANT.delete {
        ID.eq(id).and(clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition()).and(filterScope(scope, user.club))
    }

    fun exists(id: UUID) = PARTICIPANT.exists { PARTICIPANT.ID.eq(id) }

    fun existsByIdAndClub(id: UUID, clubId: UUID) =
        PARTICIPANT.exists { PARTICIPANT.ID.eq(id).and(PARTICIPANT.CLUB.eq(clubId)) }

    fun findByIdAndClub(id: UUID, clubId: UUID) =
        PARTICIPANT.findOneBy { PARTICIPANT.ID.eq(id).and(PARTICIPANT.CLUB.eq(clubId)) }

    fun getByClubId(clubId: UUID): JIO<List<ParticipantRecord>> = PARTICIPANT.select { CLUB.eq(clubId) }

    fun count(
        search: String?,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_VIEW) {
            fetchCount(
                this, search.metaSearch(searchFields())
                    .and(
                        clubId?.let { PARTICIPANT_VIEW.CLUB.eq(it) } ?: DSL.trueCondition()
                    )
                    .and(filterScopeForView(scope, user.club))
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantSort>,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<List<ParticipantViewRecord>> = Jooq.query {
        with(PARTICIPANT_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    DSL.and(
                        clubId?.let { PARTICIPANT_VIEW.CLUB.eq(it) } ?: DSL.trueCondition(),
                        filterScopeForView(scope, user.club)
                    )
                }
                .fetch()
        }
    }

    fun getByClubAndAgeRestriction(
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        ageRestriction: AgeRestriction?,
    ): JIO<List<ParticipantViewRecord>> = PARTICIPANT_VIEW.select {
        DSL.and(
            clubId?.let { PARTICIPANT_VIEW.CLUB.eq(it) } ?: DSL.trueCondition(),
            filterScopeForView(scope, user.club)
                .and(filterAgeRestriction(ageRestriction))
        )
    }

    fun getParticipant(
        id: UUID,
        clubId: UUID?,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<ParticipantViewRecord?> = Jooq.query {
        with(PARTICIPANT_VIEW) {
            selectFrom(this)
                .where(ID.eq(id))
                .and(
                    clubId?.let { PARTICIPANT_VIEW.CLUB.eq(it) } ?: DSL.trueCondition()
                )
                .and(filterScopeForView(scope, user.club))
                .fetchOne()
        }
    }

    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) PARTICIPANT.CLUB.eq(clubId) else DSL.trueCondition()

    private fun filterScopeForView(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) PARTICIPANT_VIEW.CLUB.eq(clubId) else DSL.trueCondition()

    private fun filterAgeRestriction(ageRestriction: AgeRestriction?): Condition {
        if (ageRestriction == null) return DSL.trueCondition()

        val fromCondition = ageRestriction.from?.let { PARTICIPANT_VIEW.YEAR.greaterOrEqual(it) } ?: DSL.trueCondition()
        val toCondition = ageRestriction.to?.let { PARTICIPANT_VIEW.YEAR.lessOrEqual(it) } ?: DSL.trueCondition()

        return fromCondition.and(toCondition)
    }

    fun allAsJson() = PARTICIPANT.selectAsJson()

    fun insertJsonData(data: String) = PARTICIPANT.insertJsonData(data)

}