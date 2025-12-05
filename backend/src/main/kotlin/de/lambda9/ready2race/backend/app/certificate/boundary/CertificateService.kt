package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.pdf.document
import java.io.ByteArrayOutputStream

object CertificateService {

    fun participantForEvent(
        additions: List<AdditionalText>,
        template: ByteArray,
    ): ByteArray {
        val doc = document(template, additions)

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }
}