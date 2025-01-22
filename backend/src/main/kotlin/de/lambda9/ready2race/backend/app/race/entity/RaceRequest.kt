package de.lambda9.ready2race.backend.app.race.entity

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.plugins.Validatable
import io.ktor.server.plugins.requestvalidation.*
import java.util.*

data class RaceRequest (
    val raceProperties: RacePropertiesRequestDto, // make nullable? or change request to either have payload PropertiesRequest OR template as query?
    val template: UUID?,
): Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: test()
    // test: either raceProperties is not null or template is not null
}