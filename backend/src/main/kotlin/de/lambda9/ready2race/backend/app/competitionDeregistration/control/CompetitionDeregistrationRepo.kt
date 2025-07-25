package de.lambda9.ready2race.backend.app.competitionDeregistration.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import java.util.*

object CompetitionDeregistrationRepo {

    fun create(record: CompetitionDeregistrationRecord) = COMPETITION_DEREGISTRATION.insert(record)

    fun get(competitionRegistrationId: UUID) =
        COMPETITION_DEREGISTRATION.selectOne { COMPETITION_REGISTRATION.eq(competitionRegistrationId) }

    fun delete(competitionRegistrationId: UUID) =
        COMPETITION_DEREGISTRATION.delete { COMPETITION_REGISTRATION.eq(competitionRegistrationId) }

    fun exists(competitionRegistrationId: UUID) =
        COMPETITION_DEREGISTRATION.exists { COMPETITION_REGISTRATION.eq(competitionRegistrationId) }

    fun getByRegistrations(competitionRegistrationIds: List<UUID>) =
        COMPETITION_DEREGISTRATION.select { COMPETITION_REGISTRATION.`in`(competitionRegistrationIds) }

}