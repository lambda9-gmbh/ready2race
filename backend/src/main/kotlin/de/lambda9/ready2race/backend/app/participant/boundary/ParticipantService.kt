package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionDto
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.participant.control.*
import de.lambda9.ready2race.backend.app.participant.entity.*
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantHasRequirementForEventRepo
import de.lambda9.ready2race.backend.app.participantRequirement.control.toDto
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.pagination.Direction
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object ParticipantService {

    private fun dtoSearchFields(): List<(ParticipantForEventDto) -> String?> =
        listOf({ it.firstname }, { it.lastname }, { it.externalClubName }, { it.clubName })


    fun addParticipant(
        request: ParticipantUpsertDto,
        userId: UUID,
        clubId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val record = !request.toRecord(userId, clubId)
        val participantId = !ParticipantRepo.create(record).orDie()

        KIO.ok(ApiResponse.Created(participantId))
    }

    fun page(
        params: PaginationParameters<ParticipantSort>,
        clubId: UUID? = null,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<Nothing, ApiResponse.Page<ParticipantDto, ParticipantSort>> = KIO.comprehension {
        val total = !ParticipantRepo.count(params.search, clubId, user, scope).orDie()
        val page = !ParticipantRepo.page(params, clubId, user, scope).orDie()

        page.traverse { it.participantDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun pageForEvent(
        params: PaginationParameters<ParticipantForEventSort>,
        eventId: UUID,
        clubId: UUID?,
        scope: Privilege.Scope,
        specificParticipantId: UUID? = null
    ): App<Nothing, ApiResponse.Page<ParticipantForEventDto, ParticipantForEventSort>> = KIO.comprehension {

        val allRegisteredForEventScoped = !ParticipantForEventRepo.getByEvent(eventId, clubId, scope).orDie()

        // Get Participants that were subbed in
        val substitutionsForEventScoped = !SubstitutionRepo.getByEvent(eventId, clubId, scope).orDie()

        // If a participant was subbed into a role and subbed out again in a later round they will still be displayed with that role
        val participantInSubs = substitutionsForEventScoped
            .filter { sub ->
                substitutionsForEventScoped.none { eventSub -> // Checks that this subIn was the last action of that participant or that the last action was a swap (which would mean that the participant is still in)
                    val sameReg = eventSub.competitionRegistrationId == sub.competitionRegistrationId
                    val moreRecent = eventSub.orderForRound!! > sub.orderForRound!!
                    val updatedSubIn = eventSub.participantIn!!.id == sub.participantIn!!.id
                    val isNoSwapAndSubOut =
                        !(eventSub.participantOut!!.id == sub.participantIn!!.id && SubstitutionService.getSwapSubstitution(
                            substitution = eventSub,
                            substitutions = substitutionsForEventScoped.filter { it.competitionRegistrationId == eventSub.competitionRegistrationId }
                        ).let { it != null && it == sub.id })

                    sameReg && moreRecent && (updatedSubIn || isNoSwapAndSubOut)
                }
            }

        val (unknownParticipantSubs, knownParticipantSubs) = participantInSubs
            .partition { sub -> allRegisteredForEventScoped.none { it.id == sub.participantIn!!.id } }

        // This list contains participants that are not in the page and is unique by participantId and namedParticipantId - So a participant can be in this list multiple times with different roles
        val unknownSubInsWithUniqueRole = mutableListOf<SubstitutionViewRecord>()
        unknownParticipantSubs
            .forEach { sub ->
                if (unknownSubInsWithUniqueRole.none { it.participantIn!!.id == sub.participantIn!!.id && it.namedParticipantId == sub.namedParticipantId }) {
                    unknownSubInsWithUniqueRole.add(sub)
                }
            }
        val unknownParticipantsForEvent = unknownSubInsWithUniqueRole
            .groupBy { it.participantIn!!.id }
            .map { (participantId, subs) ->
                val missingData = !getMissingDataForParticipant(participantId, eventId)

                !subs.first().participantInToParticipantForEventDto(
                    namedParticipantIds = subs.map { it.namedParticipantId!! },
                    participantRequirementsChecked = missingData.requirementsChecked,
                    qrCode = missingData.qrCode,
                )
            }


        val allRegisteredWithAddedRoles = !allRegisteredForEventScoped.traverse { p ->
            val newRoles =
                knownParticipantSubs.filter { sub -> sub.participantIn!!.id == p.id && p.namedParticipantIds!!.none { it == sub.namedParticipantId } } // New roles that this participant got through substitutions


            // Todo: val removedRoles - if a participant lost his role due to a substitution


            p.toDto(
                overwriteNamedParticipantIds = if (newRoles.isEmpty()) {
                    null
                } else {
                    p.namedParticipantIds!!.filterNotNull() + newRoles.map { it.namedParticipantId!! }
                }
            )
        }

        val allParticipants = (allRegisteredWithAddedRoles + unknownParticipantsForEvent)
            .filter {
                if (specificParticipantId != null) {
                    it.id == specificParticipantId // todo: refactor - this comes from participantRequirementService
                } else true
            }


        // Fake pagination to include subbedInParticipants that are not in the participant_for_event table

        // Search
        val searchedPs = params.search?.takeIf { it.isNotBlank() }?.let { searchText ->
            val searchTokens = searchText.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (searchTokens.isEmpty()) return@let allParticipants

            allParticipants.filter { dto ->
                val haystack =
                    dtoSearchFields()
                        .asSequence()
                        .map { getter -> getter(dto) }
                        .filterNotNull()
                        .joinToString(" ")
                        .lowercase()

                searchTokens.all { token -> haystack.contains(token) }
            }
        } ?: allParticipants

        // Sort
        val sortedPs = params.sort?.let { orders ->
            val comparator = orders
                .map { if (it.direction == Direction.ASC) it.field.comparator() else it.field.comparator().reversed() }
                .reduce { acc, comparator -> acc.thenComparing(comparator) }

            searchedPs.sortedWith(comparator)
        } ?: searchedPs

        // Page
        val offsetPs = params.offset?.let { sortedPs.drop(it) } ?: sortedPs
        val limitedPs = params.limit?.let { offsetPs.take(it) } ?: offsetPs

        KIO.ok(
            ApiResponse.Page(
                data = limitedPs,
                pagination = params.toPagination(allParticipants.size)
            )
        )
    }

    fun getParticipant(
        id: UUID,
        clubId: UUID? = null,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ParticipantError, ApiResponse.Dto<ParticipantDto>> = KIO.comprehension {
        val participant =
            !ParticipantRepo.getParticipant(id, clubId, user, scope).orDie()
                .onNullFail { ParticipantError.ParticipantNotFound }
        participant.participantDto().map { ApiResponse.Dto(it) }
    }

    fun updateParticipant(
        request: ParticipantUpsertDto,
        userId: UUID,
        clubId: UUID? = null,
        participantId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ParticipantError, ApiResponse.NoData> =
        ParticipantRepo.update(participantId, clubId, user, scope) {
            firstname = request.firstname
            lastname = request.lastname
            year = request.year
            gender = request.gender
            phone = request.phone
            external = request.external
            externalClubName = request.externalClubName?.trim()?.takeIf { it.isNotBlank() }
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { ParticipantError.ParticipantNotFound }
            .map { ApiResponse.NoData }

    fun deleteParticipant(
        id: UUID,
        clubId: UUID? = null,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ParticipantError, ApiResponse.NoData> = KIO.comprehension {

        !CompetitionRegistrationNamedParticipantRepo.existsByParticipantId(id)
            .orDie()
            .onTrueFail {
                ParticipantError.ParticipantInUse
            }

        val deleted = !ParticipantRepo.delete(id, clubId, user, scope).orDie()

        if (deleted < 1) {
            KIO.fail(ParticipantError.ParticipantNotFound)
        } else {
            noData
        }
    }

    fun getMissingDataForParticipant(participantId: UUID, eventId: UUID): App<Nothing, MissingParticipantData> =
        KIO.comprehension {
            val qrCode = !QrCodeRepo.getQrCodeByParticipant(participantId, eventId).orDie().map { it?.qrCodeId }

            val requirementsChecked =
                !ParticipantHasRequirementForEventRepo.getApprovedRequirements(eventId, participantId).orDie()
                    .andThen { checked -> checked.toList().traverse { it.toDto() } }

            val unknownParticipantTracking = !ParticipantTrackingRepo.get(participantId, eventId).orDie()
            val lastScan = unknownParticipantTracking.maxByOrNull { it.scannedAt!! }

            KIO.ok(
                MissingParticipantData(
                    qrCode = qrCode,
                    requirementsChecked = requirementsChecked,
                    lastScan = lastScan,
                )
            )
        }
}