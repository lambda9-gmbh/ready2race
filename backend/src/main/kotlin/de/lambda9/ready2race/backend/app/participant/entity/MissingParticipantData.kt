package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingViewRecord

data class MissingParticipantData(
    val qrCode: String?,
    val requirementsChecked: List<ParticipantHasRequirementForEventRecord>,
    val lastScan: ParticipantTrackingViewRecord?,
)