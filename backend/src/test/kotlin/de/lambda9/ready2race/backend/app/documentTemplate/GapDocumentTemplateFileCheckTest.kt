package de.lambda9.ready2race.backend.app.documentTemplate

import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplateLogic
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GapDocumentTemplateFileCheckTest {

    @Test
    fun ttfAndOtfAreAcceptedRegardlessOfCase() {
        assertTrue(GapDocumentTemplateLogic.hasValidFontExtension("TheSansOffice.otf"))
        assertTrue(GapDocumentTemplateLogic.hasValidFontExtension("TheSansOffice.TTF"))
    }

    @Test
    fun otherExtensionsAreRejected() {
        assertFalse(GapDocumentTemplateLogic.hasValidFontExtension("TheSansOffice.woff2"))
        assertFalse(GapDocumentTemplateLogic.hasValidFontExtension("TheSansOffice"))
        assertFalse(GapDocumentTemplateLogic.hasValidFontExtension(""))
    }
}
