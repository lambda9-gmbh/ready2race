package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
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

    fun get(id: UUID) = PARTICIPANT.selectOne { ID.eq(id) }

    fun update(id: UUID, f: ParticipantRecord.() -> Unit) = PARTICIPANT.update(f) { ID.eq(id) }

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

    fun existsByIdAndClub(id: UUID, clubId: UUID) =
        PARTICIPANT.exists { PARTICIPANT.ID.eq(id).and(PARTICIPANT.CLUB.eq(clubId)) }

    fun findByIdAndClub(id: UUID, clubId: UUID) =
        PARTICIPANT.findOneBy { PARTICIPANT.ID.eq(id).and(PARTICIPANT.CLUB.eq(clubId)) }

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

}