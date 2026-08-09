package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.anyOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.oneOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.select
import java.util.UUID

/** Ein Wettkampf, wie ihn die Verwaltung braucht - Kennung, Name und ob er eine An-/Abmeldung verlangt. */
data class CheckSeverityCompetitionDto(
    val competitionId: UUID,
    val identifier: String,
    val name: String,
    val checkInOutRequired: Boolean,
)

/**
 * Eine Zeile der Verwaltungs-Matrix. [requirementId] ist nur bei den Bedingungs-Prüfungen gesetzt,
 * [name] trägt bei ihnen den Namen der Bedingung - die beiden festen Prüfungen benennt die
 * Oberfläche selbst.
 */
data class CheckSeverityRowDto(
    val checkType: CheckType,
    val requirementId: UUID?,
    val name: String?,
)

data class CheckSeverityEntryDto(
    val competitionId: UUID,
    val checkType: CheckType,
    val requirementId: UUID?,
    val severity: CheckSeverity,
)

/**
 * [entries] enthält NUR Abweichungen vom Standard. Die Oberfläche zeigt für jede Kombination aus
 * [competitions] und [rows] den passenden Eintrag oder den Standard aus [defaults].
 */
data class CheckSeverityConfigDto(
    val competitions: List<CheckSeverityCompetitionDto>,
    val rows: List<CheckSeverityRowDto>,
    val defaults: List<CheckSeverityRowDefaultDto>,
    val entries: List<CheckSeverityEntryDto>,
)

data class CheckSeverityRowDefaultDto(
    val checkType: CheckType,
    val requirementId: UUID?,
    val severity: CheckSeverity,
)

data class UpdateCheckSeverityRequest(
    val entries: List<CheckSeverityEntryDto>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        // Muss zur DB-Check-Constraint chk_ccs_requirement_matches_check_type passen: requirementId
        // ist genau dann gesetzt, wenn checkType eine der beiden bedingungsbezogenen Prüfungsarten
        // ist - sonst schlägt eine widersprüchliche Anfrage erst in der Datenbank fehl statt hier
        // als Eingabefehler.
        this::entries validate collection(
            oneOf(
                allOf(
                    select(
                        anyOf(isValue(CheckType.REQUIREMENT), isValue(CheckType.REQUIREMENT_TIME_WINDOW)),
                        CheckSeverityEntryDto::checkType,
                    ),
                    select(notNull, CheckSeverityEntryDto::requirementId),
                ),
                allOf(
                    select(
                        anyOf(isValue(CheckType.INVOICE_OPEN), isValue(CheckType.NOT_IN_ARENA)),
                        CheckSeverityEntryDto::checkType,
                    ),
                    select(isNull, CheckSeverityEntryDto::requirementId),
                ),
            )
        ),
        // Entspricht dem Unique-Index (competition, check_type, participant_requirement) nulls not
        // distinct - zwei Einträge mit derselben Kombination würden sonst erst beim Insert scheitern.
        this::entries validate noDuplicates(
            CheckSeverityEntryDto::competitionId,
            CheckSeverityEntryDto::checkType,
            CheckSeverityEntryDto::requirementId,
        ),
    )

    companion object {
        val example
            get() = UpdateCheckSeverityRequest(
                entries = listOf(
                    CheckSeverityEntryDto(
                        competitionId = UUID.randomUUID(),
                        checkType = CheckType.INVOICE_OPEN,
                        requirementId = null,
                        severity = CheckSeverity.CRITICAL,
                    )
                ),
            )
    }
}
