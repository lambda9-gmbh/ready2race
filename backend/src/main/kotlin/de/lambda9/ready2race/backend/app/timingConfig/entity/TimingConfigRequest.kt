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
    /** Das eine angewählte RaceClocker-Rennen; null heißt „kein Rennen zugewiesen". */
    val race: UUID?,
    val startlistConfig: UUID?,
    val resultImportConfig: UUID?,
) : Validatable {

    /**
     * Nichts zu prüfen: Die Adresse liegt nicht mehr hier, sondern am Rennen, und ob ein
     * angewähltes Rennen zu dieser Veranstaltung gehört, kann nur der Service beantworten -- er
     * kennt die Veranstaltung, dieses Objekt nicht.
     */
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example
            get() = TimingConfigRequest(
                timingSystem = TimingSystem.RACECLOCKER,
                race = UUID.randomUUID(),
                startlistConfig = UUID.randomUUID(),
                resultImportConfig = UUID.randomUUID(),
            )
    }
}
