package de.lambda9.ready2race.backend.app.liveDashboard

import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardError
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingError
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Anzeigen, die am Renntag draußen bedient werden: Schiedsrichter-Dashboard, Bändchen-Ausgabe
 * und Check-in. Alle drei hatten 0 % ErrorCode-Abdeckung, und alle drei fassen Helfer an, die
 * keinen Kontext haben und niemanden fragen können.
 */
class LiveDisplayErrorTest {

    /**
     * Der häufigste Fehlerfall am Steg - und gar keine Störung: die Veranstaltung steht auf
     * chainProgressionMode = REGATTABUERO, dort beendet das Büro über den Zeitplan. Bisher las das
     * Steg-Personal "Der Lauf konnte nicht geändert werden" und probierte es erneut.
     */
    @Test
    fun finishReservedForOfficeIsRecognisable() {
        assertEquals(
            ErrorCode.LIVE_DASHBOARD_FINISH_RESERVED_FOR_OFFICE,
            LiveDashboardError.FinishReservedForOffice.respond().errorCode,
        )
    }

    @Test
    fun theDoubleAssignedWristbandIsRecognisable() {
        assertEquals(
            ErrorCode.QR_CODE_ALREADY_IN_USE,
            QrCodeError.QrCodeAlreadyInUse.respond().errorCode,
        )
        // QrCodeNotFound ist toter Code (der einzige Aufruf in QrCodeAppService.loadQrCode ist
        // auskommentiert) und bekommt deshalb bewusst keinen Code.
        assertNull(QrCodeError.QrCodeNotFound.respond().errorCode)
    }

    @Test
    fun everyCheckInReasonHasItsOwnCode() {
        val codes = ParticipantTrackingError.entries.map { it.respond().errorCode }
        assertTrue(codes.all { it != null }, "Jeder Grund braucht einen Code: $codes")
        assertEquals(
            ParticipantTrackingError.entries.size,
            codes.toSet().size,
            "Codes müssen eindeutig sein",
        )
    }

    /**
     * "Ist schon eingecheckt" ist keine Störung, sondern die Auskunft, dass nichts mehr zu tun ist -
     * das darf nicht denselben Text bekommen wie "ist gar nicht eingecheckt".
     */
    @Test
    fun alreadyCheckedInIsToldApartFromNotCheckedIn() {
        val already = ParticipantTrackingError.TeamAlreadyCheckedIn.respond()
        val notYet = ParticipantTrackingError.TeamNotCheckedIn.respond()

        assertEquals(ErrorCode.TRACKING_TEAM_ALREADY_CHECKED_IN, already.errorCode)
        assertEquals(ErrorCode.TRACKING_TEAM_NOT_CHECKED_IN, notYet.errorCode)
    }
}
