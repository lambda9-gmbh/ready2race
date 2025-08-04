package de.lambda9.ready2race.backend.app.eventInfo.control

import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.backend.app.eventInfo.entity.InfoViewConfigurationDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.InfoViewConfigurationRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.InfoViewConfigurationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.INFO_VIEW_CONFIGURATION
import org.jooq.JSONB
import java.time.LocalDateTime
import java.util.*

fun InfoViewConfigurationRecord.toDto() = InfoViewConfigurationDto(
    id = id!!,
    eventId = eventId!!,
    viewType = viewType!!,
    displayDurationSeconds = displayDurationSeconds!!,
    dataLimit = dataLimit!!,
    filters = filters?.let { ObjectMapper().readTree(it.data()) },
    sortOrder = sortOrder!!,
    isActive = isActive!!,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!
)

fun InfoViewConfigurationRequest.toRecord(eventId: UUID): InfoViewConfigurationRecord {
    val record = INFO_VIEW_CONFIGURATION.newRecord()
    record.id = UUID.randomUUID()
    record.eventId = eventId
    record.viewType = this.viewType
    record.displayDurationSeconds = this.displayDurationSeconds
    record.dataLimit = this.dataLimit
    record.filters = this.filters?.let { JSONB.jsonb(it.toString()) }
    record.sortOrder = this.sortOrder
    record.isActive = this.isActive
    record.createdAt = LocalDateTime.now()
    record.updatedAt = LocalDateTime.now()
    return record
}