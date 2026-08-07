package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListConfigTarget
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Regel, die entscheidet, mit welchem Spalten-Preset die Startliste eines Laufs exportiert wird.
 * Sie hat dieselbe Form wie die URL-Auswahl in RaceClockerMatchTarget und denselben Grund: RaceClocker
 * braucht pro Wettkampf zwei Rennen, und das Zeitfahren-Preset darf die Lauf-Spalte nicht enthalten.
 */
class StartListConfigTargetTest {

    private val timeTrial = UUID.randomUUID()
    private val heats = UUID.randomUUID()

    @Test
    fun qualificationRoundUsesTheQualificationConfig() {
        val target = StartListConfigTarget(
            isQualification = true,
            timingSystem = TimingSystem.RACECLOCKER,
            qualificationConfig = timeTrial,
            roundsConfig = heats,
        )

        assertEquals(timeTrial, target.configId)
    }

    @Test
    fun otherRoundsUseTheRoundsConfig() {
        val target = StartListConfigTarget(
            isQualification = false,
            timingSystem = TimingSystem.RACECLOCKER,
            qualificationConfig = timeTrial,
            roundsConfig = heats,
        )

        assertEquals(heats, target.configId)
    }

    @Test
    fun raceClockerQualificationDoesNotBorrowTheHeatsPreset() {
        // Der Defekt, den dieser Fix behebt: ein leerer Zeitfahren-Slot darf bei RaceClocker nicht
        // auf das Laeufe-Preset zurueckfallen. Das Laeufe-Preset traegt die Lauf-Spalte, und die
        // kippt das Zeitfahren-Rennen in den Wave-Modus -- der Countdown waere am Start weg. Also
        // liefert configId hier null statt heimlich das falsche Preset zu exportieren.
        val target = StartListConfigTarget(
            isQualification = true,
            timingSystem = TimingSystem.RACECLOCKER,
            qualificationConfig = null,
            roundsConfig = heats,
        )

        assertNull(target.configId)
    }

    @Test
    fun webscorerQualificationFallsBackToTheRoundsConfig() {
        // Webscorer kennt die Zweiteilung nicht: dort wird nur der Runden-Slot gefuellt, und er muss
        // dann auch fuer die Qualifikation gelten.
        val target = StartListConfigTarget(
            isQualification = true,
            timingSystem = TimingSystem.WEBSCORER,
            qualificationConfig = null,
            roundsConfig = heats,
        )

        assertEquals(heats, target.configId)
    }

    @Test
    fun qualificationWithoutTimingSystemFallsBackToTheRoundsConfig() {
        // Unkonfigurierte Wettkaempfe (kein Zeitnahmesystem gesetzt) verhalten sich weiterhin
        // durchlaessig -- unveraendertes Verhalten fuer den Fall, dass niemand etwas eingetragen hat.
        val target = StartListConfigTarget(
            isQualification = true,
            timingSystem = null,
            qualificationConfig = null,
            roundsConfig = heats,
        )

        assertEquals(heats, target.configId)
    }

    @Test
    fun otherRoundsDoNotFallBackToTheQualificationConfig() {
        // Kein Rueckfall in diese Richtung: das Zeitfahren-Preset ohne Lauf-Spalte wuerde in einem
        // Laeufe-Rennen die Zuordnung zum Lauf verlieren.
        val target = StartListConfigTarget(
            isQualification = false,
            timingSystem = TimingSystem.RACECLOCKER,
            qualificationConfig = timeTrial,
            roundsConfig = null,
        )

        assertNull(target.configId)
    }

    @Test
    fun nothingConfiguredResolvesToNull() {
        val target = StartListConfigTarget(
            isQualification = true,
            timingSystem = null,
            qualificationConfig = null,
            roundsConfig = null,
        )

        assertNull(target.configId)
    }
}
