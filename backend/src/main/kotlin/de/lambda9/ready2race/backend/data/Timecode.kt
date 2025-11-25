package de.lambda9.ready2race.backend.data

data class Timecode(
    val millis: Long,
    val baseUnit: BaseUnit,
    val millisecondPrecision: MillisecondPrecision,
) {
    enum class BaseUnit {
        HOURS,
        MINUTES,
        SECONDS,
    }
    enum class MillisecondPrecision {
        NONE,
        ONE,
        TWO,
        THREE,
    }

    override fun toString(): String {

        val timeString = buildString {
            val locMillis = if (millis < 0) {
                append('-')
                -millis
            } else {
                millis
            }
            append(
                when(baseUnit) {
                    BaseUnit.HOURS ->
                        String.format("%d:%02d:%02d", locMillis / 3600000, (locMillis % 3600000) / 60000, (locMillis % 60000) / 1000)
                    BaseUnit.MINUTES ->
                        String.format("%d:%02d", locMillis / 60000, (locMillis % 60000) / 1000)
                    BaseUnit.SECONDS ->
                        String.format("%d", locMillis / 1000)
                }
            )
            append(
                when(millisecondPrecision){
                    MillisecondPrecision.NONE -> ""
                    MillisecondPrecision.ONE -> String.format(".%01d", (locMillis % 1000) / 100)
                    MillisecondPrecision.TWO -> String.format(".%02d", (locMillis % 1000) / 10)
                    MillisecondPrecision.THREE -> String.format(".%03d", locMillis % 1000)
                }
            )
        }

        return timeString
    }
}