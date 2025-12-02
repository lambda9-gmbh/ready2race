package de.lambda9.ready2race.backend.app.timecode.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.TimecodeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.TIMECODE
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import java.util.UUID

object TimecodeRepo {
    fun create(record: TimecodeRecord) = TIMECODE.insertReturning(record) { ID }

    fun update(id: UUID, f: TimecodeRecord.() -> Unit) = TIMECODE.update(f) { ID.eq(id) }

    fun delete(id: UUID) = TIMECODE.delete { ID.eq(id) }
}