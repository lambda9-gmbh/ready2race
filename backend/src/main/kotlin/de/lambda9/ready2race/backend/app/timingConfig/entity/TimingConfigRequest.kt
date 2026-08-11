package de.lambda9.ready2race.backend.app.timingConfig.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

/**
 * Die Zeitnahme-Konfiguration eines Wettkampfs. Jedes Feld ist optional: die RaceClocker-Rennen
 * entstehen dort erst kurz vor der Regatta, die Konfiguration muss also unvollstaendig speicherbar
 * sein. Woran es fehlt, zeigt die Oberflaeche im Zeitnahme- und im Durchfuehrungs-Tab.
 */
data class TimingConfigRequest(
    val timingSystem: TimingSystem?,
    /** Das angewählte RaceClocker-Rennen je Rundenart; null heißt „erbt von der Veranstaltung". */
    val raceQualification: UUID?,
    val raceRounds: UUID?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
) : Validatable {

    /**
     * Nichts zu prüfen: Die Adressen liegen nicht mehr hier, sondern am Rennen, und ob ein
     * angewähltes Rennen zu dieser Veranstaltung gehört, kann nur der Service beantworten -- er
     * kennt die Veranstaltung, dieses Objekt nicht.
     */
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example
            get() = TimingConfigRequest(
                timingSystem = TimingSystem.RACECLOCKER,
                raceQualification = UUID.randomUUID(),
                raceRounds = UUID.randomUUID(),
                startlistConfigQualification = UUID.randomUUID(),
                startlistConfigRounds = UUID.randomUUID(),
                resultImportConfig = UUID.randomUUID(),
            )
    }
}
