package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

sealed interface StartListFileType {

    data object PDF : StartListFileType
    data class CSV(val config: UUID) : StartListFileType
}