package de.lambda9.ready2race.backend.app.timingConfig

import de.lambda9.ready2race.backend.app.timingConfig.entity.EventTimingConfigRequest
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Validierung der Abruf-Einstellungen. Sie soll den Tippfehler beim Bearbeiten abfangen — die
 * harte Untergrenze im Job (RaceClockerPollLogic.intervalSeconds) bleibt trotzdem bestehen, weil
 * Werte auch auf anderem Weg in die Datenbank kommen können.
 */
class EventTimingConfigRequestTest {

    private fun request(
        intervalActiveSeconds: Int = 5,
        intervalUpcomingSeconds: Int = 60,
        watchBeforeMinutes: Int = 15,
        watchAfterMinutes: Int = 120,
    ) = EventTimingConfigRequest(
        timingSystem = TimingSystem.RACECLOCKER,
        raceQualification = null,
        raceRounds = null,
        startlistConfigQualification = null,
        startlistConfigRounds = null,
        resultImportConfig = null,
        autoPull = true,
        intervalActiveSeconds = intervalActiveSeconds,
        intervalUpcomingSeconds = intervalUpcomingSeconds,
        watchBeforeMinutes = watchBeforeMinutes,
        watchAfterMinutes = watchAfterMinutes,
    )

    @Test
    fun theDefaultsAreValid() {
        assertEquals(ValidationResult.Valid, request().validate())
    }

    @Test
    fun anIntervalBelowTheFloorIsRejected() {
        assertTrue(request(intervalActiveSeconds = 1).validate() is ValidationResult.Invalid)
        assertTrue(request(intervalUpcomingSeconds = 0).validate() is ValidationResult.Invalid)
    }

    @Test
    fun negativeWindowsAreRejected() {
        assertTrue(request(watchBeforeMinutes = -1).validate() is ValidationResult.Invalid)
        assertTrue(request(watchAfterMinutes = -1).validate() is ValidationResult.Invalid)
    }

    @Test
    fun aWindowOfZeroMinutesIsAllowed() {
        assertEquals(ValidationResult.Valid, request(watchBeforeMinutes = 0, watchAfterMinutes = 0).validate())
    }
}
