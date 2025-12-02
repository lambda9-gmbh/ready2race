package de.lambda9.ready2race.backend.app.timecode.control

import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.tables.records.TimecodeRecord
import java.util.UUID


fun Timecode.toRecord(id: UUID) =
    TimecodeRecord(
        id = id,
        time = millis,
        baseUnit = baseUnit.name,
        millisecondPrecision = millisecondPrecision.name
    )

fun TimecodeRecord.toTimecode() = Timecode(
    millis = time,
    baseUnit = Timecode.BaseUnit.valueOf(baseUnit),
    millisecondPrecision = Timecode.MillisecondPrecision.valueOf(millisecondPrecision)
)