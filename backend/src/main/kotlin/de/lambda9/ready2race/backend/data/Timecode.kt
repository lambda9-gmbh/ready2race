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

    companion object {

        /**
         * Kleinste Präzision ab [minimum], mit der alle [times] (Millisekunden) unterscheidbar
         * bleiben. Zwei gleiche Zeiten (totes Rennen) erzwingen keine feinere Anzeige, nur
         * unterschiedliche Zeiten, die bei der groben Darstellung zusammenfallen würden.
         * Die Abschneide-Logik entspricht [toString] (Ganzzahldivision, kein Runden).
         */
        fun displayPrecision(
            times: Collection<Long>,
            minimum: MillisecondPrecision = MillisecondPrecision.ONE,
        ): MillisecondPrecision =
            MillisecondPrecision.entries
                .filter { it >= minimum }
                .firstOrNull { precision ->
                    val divisor = when (precision) {
                        MillisecondPrecision.NONE -> 1000L
                        MillisecondPrecision.ONE -> 100L
                        MillisecondPrecision.TWO -> 10L
                        MillisecondPrecision.THREE -> 1L
                    }
                    times.groupBy { it / divisor }.values.none { group -> group.toSet().size > 1 }
                }
                ?: MillisecondPrecision.THREE
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