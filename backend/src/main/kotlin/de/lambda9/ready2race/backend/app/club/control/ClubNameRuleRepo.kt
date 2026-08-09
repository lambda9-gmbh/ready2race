package de.lambda9.ready2race.backend.app.club.control

import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleKind
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubNameRuleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB_NAME_RULE
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.UUID

object ClubNameRuleRepo {

    /**
     * Immer in `sort_order`. Die Reihenfolge ist Teil der Bedeutung, nicht der Darstellung: stünde
     * `Verein` vor `Ruderverein`, bliebe aus `Ruder-Verein` ein `Ruder-V` stehen.
     */
    fun all(): JIO<List<ClubNameRuleRecord>> = Jooq.query {
        selectFrom(CLUB_NAME_RULE).orderBy(CLUB_NAME_RULE.SORT_ORDER, CLUB_NAME_RULE.ID).fetch()
    }

    fun get(id: UUID): JIO<ClubNameRuleRecord?> = Jooq.query {
        selectFrom(CLUB_NAME_RULE).where(CLUB_NAME_RULE.ID.eq(id)).fetchOne()
    }

    /** Der Schalter einer strukturellen Art, falls er an ist. */
    fun findSwitch(kind: ClubNameRuleKind): JIO<ClubNameRuleRecord?> = Jooq.query {
        selectFrom(CLUB_NAME_RULE)
            .where(CLUB_NAME_RULE.KIND.eq(kind.name))
            .and(CLUB_NAME_RULE.TERM.isNull)
            .fetchOne()
    }

    /** Wortgenau heißt auch: zweimal derselbe Bestandteil ist keine zweite Regel, sondern ein Versehen. */
    fun findByTerm(kind: ClubNameRuleKind, term: String): JIO<ClubNameRuleRecord?> = Jooq.query {
        selectFrom(CLUB_NAME_RULE)
            .where(CLUB_NAME_RULE.KIND.eq(kind.name))
            .and(CLUB_NAME_RULE.TERM.likeIgnoreCase(term))
            .fetchOne()
    }

    fun create(record: ClubNameRuleRecord): JIO<UUID> = CLUB_NAME_RULE.insertReturning(record) { ID }

    fun update(id: UUID, f: ClubNameRuleRecord.() -> Unit): JIO<ClubNameRuleRecord?> =
        CLUB_NAME_RULE.update(f) { ID.eq(id) }

    fun delete(id: UUID): JIO<Int> = CLUB_NAME_RULE.delete { ID.eq(id) }

    fun nextSortOrder(): JIO<Int> = Jooq.query {
        val highest = select(DSL.max(CLUB_NAME_RULE.SORT_ORDER))
            .from(CLUB_NAME_RULE)
            .fetchOne()
            ?.value1()

        (highest ?: 0) + 10
    }

    /**
     * Schreibt die Reihenfolge in einem Rutsch. Der Aufrufer schickt alle Regeln - eine Umsortierung
     * ist keine Änderung an einer einzelnen Zeile, und zwei nacheinander geschriebene Zeilen
     * könnten dazwischen eine Reihenfolge ergeben, die es nie geben sollte.
     */
    fun writeOrder(ruleIds: List<UUID>, userId: UUID, now: LocalDateTime): JIO<Unit> = Jooq.query {
        ruleIds.forEachIndexed { index, ruleId ->
            update(CLUB_NAME_RULE)
                .set(CLUB_NAME_RULE.SORT_ORDER, (index + 1) * 10)
                .set(CLUB_NAME_RULE.UPDATED_AT, now)
                .set(CLUB_NAME_RULE.UPDATED_BY, userId)
                .where(CLUB_NAME_RULE.ID.eq(ruleId))
                .execute()
        }
    }
}
