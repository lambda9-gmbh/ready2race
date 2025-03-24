package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import com.github.timrs2998.pdfbuilder.document
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventRegistration.control.*
import de.lambda9.ready2race.backend.app.eventRegistration.entity.*
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.*
import java.time.LocalDateTime
import java.util.*

object EventRegistrationService {

    fun getEventRegistrationTemplate(
        eventId: UUID,
        clubId: UUID
    ): App<EventRegistrationError, ApiResponse.Dto<EventRegistrationTemplateDto>> = KIO.comprehension {
        val info = !EventRegistrationRepo.getEventRegistrationInfo(eventId).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        val upsertableRegistration = !EventRegistrationRepo.getEventRegistrationForUpdate(eventId, clubId).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        ok(EventRegistrationTemplateDto(info, upsertableRegistration)).map { ApiResponse.Dto(it) }
    }

    fun upsertRegistrationForEvent(
        eventId: UUID,
        registrationDto: EventRegistrationUpsertDto,
        clubId: UUID,
        userId: UUID,
    ): App<EventRegistrationError, ApiResponse.Created> = KIO.comprehension {

        val template = !EventRegistrationRepo.getEventRegistrationInfo(eventId).orDie()

        // TODO Event is open for registrations OR is admin

        val now = LocalDateTime.now()

        val (persistedRegistrationId, isUpdate) =
            !EventRegistrationRepo.findByEventAndClub(eventId, clubId).map { it?.let { it.id to true } }.orDie()
                ?: (!EventRegistrationRepo.create(
                    EventRegistrationRecord(
                        UUID.randomUUID(),
                        eventId,
                        clubId,
                        registrationDto.message,
                        now,
                        userId,
                        now,
                        userId
                    )
                ).orDie() to false)

        if (isUpdate) {
            !EventRegistrationRepo.update(persistedRegistrationId) {
                message = registrationDto.message
                updatedAt = now
                updatedBy = userId
            }.orDie()

            !CompetitionRegistrationRepo.deleteByEventRegistration(persistedRegistrationId).orDie()
        }

        val participantIdMap = !registrationDto.participants.traverse { pDto ->
            handleSingleCompetitionRegistration(pDto, userId, clubId, template, persistedRegistrationId, now)
        }.map { it.toMap(mutableMapOf()) }

        !registrationDto.competitionRegistrations.traverse { competitionRegistrationDto ->
            handleTeamCompetitionRegistrations(
                template,
                competitionRegistrationDto,
                persistedRegistrationId,
                clubId,
                now,
                userId,
                participantIdMap
            )
        }

        ok(ApiResponse.Created(persistedRegistrationId))

    }

    private fun handleSingleCompetitionRegistration(
        pDto: EventRegistrationParticipantUpsertDto,
        userId: UUID,
        clubId: UUID,
        template: EventRegistrationInfoDto?,
        persistedRegistrationId: UUID,
        now: LocalDateTime
    ): App<EventRegistrationError, Pair<UUID, UUID>> = KIO.comprehension {
        val persistedId = if (pDto.isNew == true) {
            !ParticipantRepo.create(!pDto.toRecord(userId, clubId)).orDie()
        } else {
            if (!!ParticipantRepo.existsByIdAndClub(pDto.id, clubId).orDie()) {
                KIO.fail(EventRegistrationError.InvalidRegistration)
            }

            if (pDto.hasChanged == true) {
                !ParticipantRepo.update(pDto.id) {
                    firstname = pDto.firstname
                    lastname = pDto.lastname
                    gender = pDto.gender
                    year = pDto.year
                    external = pDto.external
                    externalClubName = pDto.externalClubName?.trim()?.takeIf { it.isNotBlank() }
                }.orDie()
            }

            pDto.id
        }

        pDto.competitionsSingle?.traverse { competitionRegistrationDto ->

            val competition =
                template?.competitionsSingle?.first { it.id == competitionRegistrationDto.competitionId }
                    ?: return@traverse KIO.fail(EventRegistrationError.InvalidRegistration)

            val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                CompetitionRegistrationRecord(
                    UUID.randomUUID(),
                    persistedRegistrationId,
                    competitionRegistrationDto.competitionId,
                    clubId,
                    null,
                    now,
                    userId,
                    now,
                    userId
                )
            ).orDie()

            !CompetitionRegistrationNamedParticipantRepo.create(
                CompetitionRegistrationNamedParticipantRecord(
                    competitionRegistrationId,
                    competition.namedParticipant?.first()?.id!!,
                    persistedId
                )
            ).orDie()

            competitionRegistrationDto.optionalFees?.traverse {
                handleOptionalFee(
                    competition,
                    competitionRegistrationId,
                    it
                )
            }?.not()

            unit

        }?.not()

        ok(pDto.id to persistedId)
    }

    private fun handleTeamCompetitionRegistrations(
        template: EventRegistrationInfoDto?,
        competitionRegistrationDto: CompetitionRegistrationUpsertDto,
        persistedRegistrationId: UUID,
        clubId: UUID,
        now: LocalDateTime,
        userId: UUID,
        participantIdMap: MutableMap<UUID, UUID>
    ): App<EventRegistrationError, Unit> = KIO.comprehension {

        val competition = template?.competitionsTeam?.first { it.id == competitionRegistrationDto.competitionId }
            ?: return@comprehension KIO.fail(EventRegistrationError.InvalidRegistration)

        competitionRegistrationDto.teams?.traverse { teamDto ->
            val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                CompetitionRegistrationRecord(
                    UUID.randomUUID(),
                    persistedRegistrationId,
                    competitionRegistrationDto.competitionId,
                    clubId,
                    null,
                    now,
                    userId,
                    now,
                    userId
                )
            ).orDie()

            teamDto.namedParticipants?.forEach { namedParticipantDto ->

                // TODO validate consistency

                namedParticipantDto.participantIds.forEach { participantId ->

                    val persistedId = participantIdMap[participantId]
                        ?: return@traverse KIO.fail(EventRegistrationError.InvalidRegistration)

                    !CompetitionRegistrationNamedParticipantRepo.create(
                        CompetitionRegistrationNamedParticipantRecord(
                            competitionRegistrationId,
                            namedParticipantDto.namedParticipantId,
                            persistedId
                        )
                    ).orDie()
                }

            }

            teamDto.optionalFees?.traverse { handleOptionalFee(competition, competitionRegistrationId, it) }?.not()

            unit

        }?.not()

        ok(Unit)
    }

    private fun handleOptionalFee(
        competition: EventRegistrationCompetitionDto,
        competitionRegistrationId: UUID,
        optionalFee: UUID
    ) = KIO.comprehension {

        if (competition.fees.find { it.id == optionalFee }?.required != false) {
            KIO.fail(EventRegistrationError.InvalidRegistration)
        }

        CompetitionRegistrationOptionalFeeRepo.create(
            CompetitionRegistrationOptionalFeeRecord(
                competitionRegistrationId,
                optionalFee
            )
        ).orDie()
    }

    fun downloadResult(
        eventId: UUID,
        remake: Boolean,
        userId: UUID,
    ): App<EventRegistrationError, ApiResponse.File> = KIO.comprehension {

        val existing = if (remake) {
            KIO.ok(null)
        } else {
            EventRegistrationResultDocumentRepo.getDownload(eventId).orDie()
                .mapNotNull { it.name!! to it.data!! }
        }

        existing.onNull { generateResultDocument(eventId, userId) }
            .map { (name, data) ->
                ApiResponse.File(
                    name = name,
                    bytes = data,
                )
            }
    }

    private fun generateResultDocument(
        eventId: UUID,
        userId: UUID,
    ): App<EventRegistrationError, Pair<String, ByteArray>> = KIO.comprehension {

        val result = !EventRegistrationRepo.getRegistrationResult(eventId).orDie()

        val pdDocument = document {
            
        }

        val filename = "result.pdf"
        val bytes = ByteArray(0)

        val documentRecord = EventRegistrationResultDocumentRecord(
            event = eventId,
            name = filename,
            createdAt = LocalDateTime.now(),
        )
        val id = !EventRegistrationResultDocumentRepo.create(documentRecord).orDie()
        val dataRecord = EventRegistrationResultDocumentDataRecord(
            resultDocument = id,
            data = bytes,
        )
        !EventRegistrationResultDocumentDataRepo.create(dataRecord).orDie()

        KIO.ok(filename to bytes)
    }
}