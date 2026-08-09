package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.control.ClubNameRuleRepo
import de.lambda9.ready2race.backend.app.club.control.clubNameRuleDto
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleDto
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleError
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleKind
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleOrderRequest
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRuleRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubNameRuleRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

/**
 * Die Kürzungsregeln als eigener Abschnitt derselben Pflegeseite.
 *
 * Wortpaare und Streichliste sind Zeilen, die beiden strukturellen Arten sind Schalter: eine Zeile
 * ohne `term` heißt "an". Deshalb legt [enableSwitch] nichts doppelt an und [disable] verlangt
 * keinen vorhandenen Eintrag - der Bearbeiter klickt einen Schalter, er verwaltet keine Zeilen.
 */
object ClubNameRuleService {

    fun all(): App<Nothing, ApiResponse.ListDto<ClubNameRuleDto>> = KIO.comprehension {
        val records = !ClubNameRuleRepo.all().orDie()
        records.traverse { it.clubNameRuleDto() }.map { ApiResponse.ListDto(it) }
    }

    fun add(
        request: ClubNameRuleRequest,
        userId: UUID,
    ): App<ClubNameRuleError, ApiResponse.Created> = KIO.comprehension {

        val term = request.term?.trim()?.takeIf { it.isNotEmpty() }
        val replacement = request.replacement?.trim()?.takeIf { it.isNotEmpty() }

        if (request.kind.structural) {
            // Ein Schalter, der schon an ist, bleibt an - das zweite Einschalten ist keine
            // Störung, sondern ein doppelter Klick.
            val existing = !ClubNameRuleRepo.findSwitch(request.kind).orDie()
            if (existing != null) {
                KIO.ok(ApiResponse.Created(existing.id))
            } else {
                create(request.kind, term = null, replacement = null, userId = userId)
            }
        } else if (term == null || (request.kind == ClubNameRuleKind.ABBREVIATION && replacement == null)) {
            KIO.fail(ClubNameRuleError.TermMissing)
        } else {
            val duplicate = !ClubNameRuleRepo.findByTerm(request.kind, term).orDie()
            if (duplicate != null) {
                KIO.fail(ClubNameRuleError.DuplicateTerm)
            } else {
                create(
                    kind = request.kind,
                    term = term,
                    replacement = if (request.kind == ClubNameRuleKind.ABBREVIATION) replacement else null,
                    userId = userId,
                )
            }
        }
    }

    private fun create(
        kind: ClubNameRuleKind,
        term: String?,
        replacement: String?,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val now = LocalDateTime.now()
        val sortOrder = !ClubNameRuleRepo.nextSortOrder().orDie()

        val id = !ClubNameRuleRepo.create(
            ClubNameRuleRecord(
                id = UUID.randomUUID(),
                kind = kind.name,
                term = term,
                replacement = replacement,
                sortOrder = sortOrder,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        ).orDie()

        KIO.ok(ApiResponse.Created(id))
    }

    /**
     * Ändert Bestandteil und Kürzel einer Zeile. Die Art bleibt, was sie ist: aus einem Wortpaar
     * einen Schalter zu machen, wäre kein Ändern, sondern ein anderer Eintrag.
     */
    fun update(
        ruleId: UUID,
        request: ClubNameRuleRequest,
        userId: UUID,
    ): App<ClubNameRuleError, ApiResponse.NoData> = KIO.comprehension {

        val existing = !ClubNameRuleRepo.get(ruleId).orDie().onNullFail { ClubNameRuleError.RuleNotFound }
        val kind = ClubNameRuleKind.valueOf(existing.kind)

        val term = request.term?.trim()?.takeIf { it.isNotEmpty() }
        val replacement = request.replacement?.trim()?.takeIf { it.isNotEmpty() }

        if (kind.structural) {
            // Ein Schalter hat nichts zu ändern - an oder aus, das ist Anlegen oder Löschen.
            noData
        } else if (term == null || (kind == ClubNameRuleKind.ABBREVIATION && replacement == null)) {
            KIO.fail(ClubNameRuleError.TermMissing)
        } else {
            val duplicate = !ClubNameRuleRepo.findByTerm(kind, term).orDie()
            if (duplicate != null && duplicate.id != ruleId) {
                KIO.fail(ClubNameRuleError.DuplicateTerm)
            } else {
                !ClubNameRuleRepo.update(ruleId) {
                    this.term = term
                    this.replacement = if (kind == ClubNameRuleKind.ABBREVIATION) replacement else null
                    updatedAt = LocalDateTime.now()
                    updatedBy = userId
                }.orDie()

                noData
            }
        }
    }

    /**
     * Bewusst ohne 404: Löschen heißt für Wortpaare "Zeile weg" und für die strukturellen Regeln
     * "Schalter aus". Wer einen Schalter ausschaltet, der schon aus war, hat keinen Fehler gemacht.
     */
    fun remove(
        ruleId: UUID,
    ): App<Nothing, ApiResponse.NoData> =
        ClubNameRuleRepo.delete(ruleId).orDie().map { ApiResponse.NoData }

    fun reorder(
        request: ClubNameRuleOrderRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        !ClubNameRuleRepo.writeOrder(request.ruleIds, userId, LocalDateTime.now()).orDie()
        noData
    }
}
