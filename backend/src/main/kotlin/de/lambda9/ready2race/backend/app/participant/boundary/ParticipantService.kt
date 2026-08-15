package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.club.entity.ClubError
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.participant.control.*
import de.lambda9.ready2race.backend.app.participant.entity.*
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantHasRequirementForEventRepo
import de.lambda9.ready2race.backend.app.participantRequirement.control.toDto
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.ratingcategory.control.EventRatingCategoryRepo
import de.lambda9.ready2race.backend.app.ratingcategory.entity.AgeRestriction
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryError
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.ready2race.backend.csv.CSV
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantAdditionalClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.pagination.Direction
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
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


    fun importParticipants(
        file: File,
        request: ParticipantImportRequest,
        userId: UUID,
        clubId: UUID,
    ): App<ToApiError, ApiResponse.NoData> = KIO.comprehension {

        val genderMap = mapOf(
            request.valueGenderMale to Gender.M,
            request.valueGenderFemale to Gender.F,
            request.valueGenderDiverse to Gender.D,
        )

        val iStream = file.bytes.inputStream()

        val entries = !CSV.read(
            `in` = iStream,
            noHeader = request.noHeader,
            separator = request.separator,
            charset = request.charset,
        ) {

            val externalClubname = !optionalCell(request.colExternalClubname)
            val now = LocalDateTime.now()

            val genderValue = !cell(request.colGender)
            val gender =
                !KIO.failOnNull(genderMap[genderValue]) { ParticipantError.ImportError.UnknownGenderValue(genderValue) }

            ParticipantRecord(
                id = UUID.randomUUID(),
                club = clubId,
                firstname = !cell(request.colFirstname),
                lastname = !cell(request.colLastname),
                year = !cell(request.colYear, int),
                gender = gender,
                external = externalClubname != null,
                externalClubName = externalClubname,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
                email = !optionalCell(request.colEmail), // TODO: validate pattern
            )
        }

        !ParticipantRepo.create(entries).orDie()

        noData
    }

    fun addParticipant(
        request: ParticipantUpsertDto,
        userId: UUID,
        clubId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val record = !request.toRecord(userId, clubId)
        val participantId = !ParticipantRepo.create(record).orDie()

        KIO.ok(ApiResponse.Created(participantId))
    }

    /**
     * Ob dieser Leser die Kontaktdaten der Person sehen darf.
     *
     * Seit der Mehrfach-Zugehörigkeit (Migration V202608142000) steht in der Personenliste eines
     * Vereins auch, wer ihm nur als Zweitverein angehört. Melden darf er die Person, ihre
     * Telefonnummer und E-Mail-Adresse gehen ihn nichts an. Wer global liest (Ausrichter,
     * Verwaltung), sieht weiterhin alles.
     */
    private fun hidesContactData(
        homeClubId: UUID?,
        clubId: UUID?,
        scope: Privilege.Scope,
    ): Boolean = scope == Privilege.Scope.OWN && clubId != null && homeClubId != clubId

    fun page(
        params: PaginationParameters<ParticipantSort>,
        clubId: UUID? = null,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<Nothing, ApiResponse.Page<ParticipantDto, ParticipantSort>> = KIO.comprehension {
        val total = !ParticipantRepo.count(params.search, clubId, user, scope).orDie()
        val page = !ParticipantRepo.page(params, clubId, user, scope).orDie()

        page.traverse { it.participantDto(hidesContactData(it.club, clubId, scope)) }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    /**
     * Die vereinsübergreifende Suche für die Meldemaske.
     *
     * Drei Riegel, alle drei bewusst hier und nicht erst in der Datenbank:
     *
     * 1. Der Schalter der Veranstaltung. Steht er aus — die Vorbelegung —, gibt es keine Treffer.
     *    Bewusst eine leere Liste statt eines Fehlers: die Oberfläche blendet das Suchfeld dann
     *    ohnehin aus, und ein Fehlerhinweis wäre für den Meldenden nur Rauschen.
     * 2. Die Mindestlänge. Ohne Eingabe (und mit einem einzelnen Zeichen) gibt es nichts —
     *    sonst wäre die Suche eine durchblätterbare Liste aller Personen aller Vereine.
     * 3. Der Deckel auf der Trefferzahl, siehe [ParticipantRepo.searchAcrossClubs].
     *
     * Kein Protokoll: ausdrücklich nicht gewünscht.
     */
    fun searchAcrossClubs(
        eventId: UUID,
        clubId: UUID,
        search: String?,
    ): App<ServiceError, ApiResponse.ListDto<ParticipantSearchResultDto>> = KIO.comprehension {
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        val trimmed = search?.trim() ?: ""

        if (event.crossClubRegistration != true || trimmed.length < ParticipantRepo.CROSS_CLUB_SEARCH_MIN_LENGTH) {
            KIO.ok(ApiResponse.ListDto(emptyList()))
        } else {
            ParticipantRepo.searchAcrossClubs(trimmed, clubId).orDie()
                .andThen { records -> records.traverse { it.participantSearchResultDto() } }
                .map { ApiResponse.ListDto(it) }
        }
    }

    /**
     * Eine Person einem weiteren Verein zuordnen.
     *
     * Wer darf das? Der Stammverein und wer global schreiben darf — kein neues Privileg. Das
     * folgt der Grenze, die auch für die Stammdaten gilt: die Person "gehört" ihrem Stammverein,
     * also entscheidet er, wer sie außerdem melden darf. Ein Zweitverein könnte sich sonst selbst
     * eintragen, und der Stammverein erführe es nur aus der Meldeliste.
     *
     * Geprüft wird das an zwei Stellen: die Route stellt sicher, dass ein OWN-Nutzer nur für den
     * eigenen Verein handelt, und [ParticipantRepo.existsByIdAndHomeClub] stellt sicher, dass
     * dieser Verein auch wirklich der Stammverein ist.
     */
    fun addAdditionalClub(
        participantId: UUID,
        homeClubId: UUID,
        additionalClubId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !ParticipantRepo.existsByIdAndHomeClub(participantId, homeClubId).orDie()
            .onFalseFail { ParticipantError.ParticipantNotFound }

        !KIO.failOn(additionalClubId == homeClubId) { ParticipantError.ClubIsHomeClub }

        !ClubRepo.getClub(additionalClubId).orDie().onNullFail { ClubError.ClubNotFound }

        !ParticipantAdditionalClubRepo.exists(participantId, additionalClubId).orDie()
            .onTrueFail { ParticipantError.ClubAlreadyAdded }

        !ParticipantAdditionalClubRepo.create(
            ParticipantAdditionalClubRecord(
                participant = participantId,
                club = additionalClubId,
                createdAt = LocalDateTime.now(),
                createdBy = userId,
            )
        ).orDie()

        noData
    }

    /**
     * Die Zuordnung wieder lösen. Bestehende Meldungen bleiben stehen — sie hängen an
     * `competition_registration_named_participant` und kennen keine Vereinsbedingung. Das ist
     * gewollt: eine gemeldete Mannschaft darf nicht dadurch zerfallen, dass jemand nachträglich
     * eine Zugehörigkeit aufräumt.
     */
    fun removeAdditionalClub(
        participantId: UUID,
        homeClubId: UUID,
        additionalClubId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !ParticipantRepo.existsByIdAndHomeClub(participantId, homeClubId).orDie()
            .onFalseFail { ParticipantError.ParticipantNotFound }

        val deleted = !ParticipantAdditionalClubRepo.delete(participantId, additionalClubId).orDie()

        if (deleted < 1) {
            KIO.fail(ParticipantError.ClubNotAdded)
        } else {
            noData
        }
    }

    fun getByClubFilteredByEventRatingCategory(
        clubId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        eventId: UUID,
        ratingCategoryId: UUID?,
    ): App<ServiceError, ApiResponse.ListDto<ParticipantDto>> = KIO.comprehension {

        val ageRestriction = ratingCategoryId?.let {
            !EventRatingCategoryRepo.getByEventAndRatingCategory(eventId = eventId, ratingCategoryId = ratingCategoryId)
                .orDie()
                .onNullFail { RatingCategoryError.NotFound }
                .map { record ->
                    if (record.yearRestrictionFrom == null && record.yearRestrictionTo == null) {
                        null
                    } else {
                        AgeRestriction(
                            from = record.yearRestrictionFrom,
                            to = record.yearRestrictionTo,
                        )
                    }
                }
        }

        val list = !ParticipantRepo.getByClubAndAgeRestriction(clubId, user, scope, ageRestriction).orDie()

        list.traverse { it.participantDto(hidesContactData(it.club, clubId, scope)) }
            .map { ApiResponse.ListDto(it) }
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
            if (orders.isNotEmpty()) {
                val comparator = orders
                    .map {
                        if (it.direction == Direction.ASC) it.field.comparator() else it.field.comparator().reversed()
                    }
                    .reduce { acc, comparator -> acc.thenComparing(comparator) }

                searchedPs.sortedWith(comparator)
            } else searchedPs
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
        participant.participantDto(hidesContactData(participant.club, clubId, scope))
            .map { ApiResponse.Dto(it) }
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
            email = request.email
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