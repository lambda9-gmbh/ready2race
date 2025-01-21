package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.entity.RacePropertiesHasNamedParticipantDto
import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.plugins.requestvalidation.*
import java.util.*

data class RaceRequest (
    val raceProperties: RacePropertiesDto,
    val template: UUID?,
    val namedParticipantList: List<RacePropertiesHasNamedParticipantDto>
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: test()
}