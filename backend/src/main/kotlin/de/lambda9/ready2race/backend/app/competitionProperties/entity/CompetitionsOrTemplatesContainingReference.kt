package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.count

data class CompetitionsOrTemplatesContainingReference(
    val templates: List<CompetitionPropertiesContainingReference>?,
    val competitions: List<CompetitionPropertiesContainingReference>?,
) {
    fun errorMessage(
        name: String,
    ): String {
        val builder = StringBuilder()
            .append("$name is contained in ")

        if (competitions != null) {
            builder.append("competition".count(competitions.size))
            if (templates != null) {
                builder.append(" and ")
            }
        }
        if (templates != null) {
            builder.append("templates".count(templates.size))
        }

        return builder.toString()
    }

    fun containsEntries(): Boolean {
        return !competitions.isNullOrEmpty() || !templates.isNullOrEmpty()
    }
}

