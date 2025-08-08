package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participant.control.ParticipantForEventRepo
import de.lambda9.ready2race.backend.app.participantRequirement.control.*
import de.lambda9.ready2race.backend.app.participantRequirement.entity.*
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.tools.csv.CSVReader
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.util.*

object ParticipantRequirementService {

    fun addParticipantRequirement(
        request: ParticipantRequirementUpsertDto,
        userId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        ParticipantRequirementRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<ParticipantRequirementSort>
    ): App<Nothing, ApiResponse.Page<ParticipantRequirementDto, ParticipantRequirementSort>> = KIO.comprehension {
        val total = !ParticipantRequirementRepo.count(params.search).orDie()
        val page = !ParticipantRequirementRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun activateRequirementForEvent(
        requirementId: UUID,
        eventId: UUID,
        userId: UUID,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {

        if (!!EventHasParticipantRequirementRepo.exists(eventId, requirementId).orDie()) {
            !EventHasParticipantRequirementRepo.create(
                EventHasParticipantRequirementRecord(
                    event = eventId,
                    participantRequirement = requirementId,
                    createdAt = LocalDateTime.now(),
                    createdBy = userId
                )
            ).orDie()
        }

        noData

    }

    fun removeRequirementForEvent(
        requirementId: UUID,
        eventId: UUID
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {

        if (!EventHasParticipantRequirementRepo.exists(eventId, requirementId).orDie()) {
            !EventHasParticipantRequirementRepo.delete(eventId, requirementId).orDie()
            !ParticipantHasRequirementForEventRepo.deleteWhereParticipantNotInList(eventId, requirementId, emptyList()).orDie()
        }

        noData

    }

    fun pageForEvent(
        params: PaginationParameters<ParticipantRequirementForEventSort>,
        eventId: UUID
    ): App<Nothing, ApiResponse.Page<ParticipantRequirementForEventDto, ParticipantRequirementForEventSort>> =
        KIO.comprehension {
            val total = !ParticipantRequirementForEventRepo.count(params.search, eventId).orDie()
            val page = !ParticipantRequirementForEventRepo.page(params, eventId).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

    fun getActiveForEvent(
        params: PaginationParameters<ParticipantRequirementForEventSort>,
        eventId: UUID
    ): App<Nothing, ApiResponse.Page<ParticipantRequirementForEventDto, ParticipantRequirementForEventSort>> =
        KIO.comprehension {
            val total = !ParticipantRequirementForEventRepo.count(params.search, eventId, onlyActive = true).orDie()
            val page = !ParticipantRequirementForEventRepo.page(params, eventId, onlyActive = true).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

    fun approveRequirementForEvent(
        eventId: UUID,
        dto: ParticipantRequirementCheckForEventUpsertDto,
        userId: UUID
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {

        if (!!EventHasParticipantRequirementRepo.exists(eventId, dto.requirementId).orDie()) {
            return@comprehension KIO.fail(ParticipantRequirementError.NotFound)
        }

        !ParticipantHasRequirementForEventRepo.deleteWhereParticipantNotInList(
            eventId,
            dto.requirementId,
            dto.approvedParticipants
        ).orDie()

        val alreadyApproved =
            !ParticipantHasRequirementForEventRepo.getApprovedParticipantIds(eventId, dto.requirementId)
                .map { it.toSet() }.orDie()

        !dto.approvedParticipants.filterNot { it in alreadyApproved }.traverse {
            ParticipantHasRequirementForEventRepo.create(
                ParticipantHasRequirementForEventRecord(
                    event = eventId,
                    participant = it,
                    participantRequirement = dto.requirementId,
                    createdBy = userId,
                    createdAt = LocalDateTime.now(),
                )
            )
        }.orDie()

        noData
    }

    fun checkRequirementForEvent(
        eventId: UUID,
        csvList: MutableList<Pair<String, ByteArray>>,
        config: ParticipantRequirementCheckForEventConfigDto,
        userId: UUID
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {

        if (!!EventHasParticipantRequirementRepo.exists(eventId, config.requirementId).orDie()) {
            return@comprehension KIO.fail(ParticipantRequirementError.InvalidConfig("Missing requirement" to config.requirementId.toString()))
        }

        val uncheckedParticipants =
            !ParticipantForEventRepo.getParticipantsForEventWithMissingRequirement(eventId, config.requirementId)
                .orDie()

        // Even if the @uncheckedParticipants list is empty, we should still try to parse and validate the uploaded csv and return any errors.
        val validParticipants = !parseParticipantListUpload(csvList, config)

        KotlinLogging.logger {  }.error { validParticipants.size }

        // persist requirements for all matches
        !validParticipants.traverse { vp ->
            uncheckedParticipants.filter { up ->
                up.firstname.equals(vp.firstname, ignoreCase = true)
                    && up.lastname.equals(vp.lastname, ignoreCase = true)
                    && vp.club?.let { (up.externalClubName ?: up.clubName!!).equals(it, ignoreCase = true) } ?: true
                    && vp.year?.let { up.year?.equals(it) } ?: true
            }.traverse { candidate ->
                ParticipantHasRequirementForEventRepo.create(
                    ParticipantHasRequirementForEventRecord(
                        event = eventId,
                        participant = candidate.id!!,
                        participantRequirement = config.requirementId,
                        createdBy = userId,
                        createdAt = LocalDateTime.now(),
                    )
                )
            }
        }.orDie()

        // TODO return number of found matches and show in FE?
        noData
    }

    private fun parseParticipantListUpload(
        csvList: MutableList<Pair<String, ByteArray>>,
        config: ParticipantRequirementCheckForEventConfigDto,
    ): App<ParticipantRequirementError, List<ValidRequirementParticipant>> = KIO.comprehension {
        val validEntries = csvList.flatMap { (_, csv) ->

            var header: Map<String, Int> = emptyMap()

            val charset = if (config.charset != null) {
                if (Charset.isSupported(config.charset)) {
                    Charset.forName(config.charset)
                } else {
                    return@comprehension KIO.fail(ParticipantRequirementError.InvalidConfig("Charset is not supported" to config.charset))
                }
            } else {
                Charsets.UTF_8
            }

            csv.inputStream().bufferedReader(charset).let { reader ->
                CSVReader(reader, config.separator ?: ';')
                    .asSequence()
                    .toList()
                    .filterNotNull()
                    .mapIndexedNotNull { index, arr ->
                        if (index == 0) {

                            header = arr.mapIndexed { headerIndex, value ->
                                value to headerIndex
                            }.toMap()

                            if (!header.keys.containsAll(config.getColNames())) {
                                return@comprehension KIO.fail(
                                    ParticipantRequirementError.InvalidConfig(
                                        "Missing columns in csv" to config.getColNames()
                                            .filter { !header.keys.contains(it) }
                                            .joinToString("; ")
                                    )
                                )
                            }
                            null
                        } else {
                            val valid =
                                config.requirementColName == null || config.requirementIsValidValue == null || arr.getOrNull(
                                header.getOrDefault(config.requirementColName, -1)
                            ).equals(config.requirementIsValidValue)

                            if (valid) {
                                ValidRequirementParticipant(
                                    firstname = arr.getOrNull(
                                        header.getOrDefault(
                                            config.firstnameColName,
                                            -1
                                        )
                                    ),
                                    lastname = arr.getOrNull(
                                        header.getOrDefault(
                                            config.lastnameColName,
                                            -1
                                        )
                                    ),
                                    year = arr.getOrNull(header.getOrDefault(config.yearsColName, -1))
                                        ?.toIntOrNull(),
                                    club = arr.getOrNull(header.getOrDefault(config.clubColName, -1)),
                                )
                            } else {
                                null
                            }
                        }
                    }
            }
        }

        ok(validEntries)
    }

    private data class ValidRequirementParticipant(
        val firstname: String?,
        val lastname: String?,
        val year: Int?,
        val club: String?
    )

    fun updateParticipantRequirement(
        participantRequirementId: UUID,
        request: ParticipantRequirementUpsertDto,
        userId: UUID,
    ): App<ParticipantRequirementError, ApiResponse.NoData> =
        ParticipantRequirementRepo.update(participantRequirementId) {
            name = request.name
            description = request.description
            optional = request.optional ?: false
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { ParticipantRequirementError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteParticipantRequirement(
        participantRequirementId: UUID,
    ): App<ParticipantRequirementError, ApiResponse.NoData> = KIO.comprehension {

        // TODO check if in use
        val inUse = false

        if (inUse) {
            return@comprehension KIO.fail(
                ParticipantRequirementError.InUse
            )
        }

        val deleted = !ParticipantRequirementRepo.delete(participantRequirementId).orDie()

        if (deleted < 1) {
            KIO.fail(ParticipantRequirementError.NotFound)
        } else {
            noData
        }
    }

}