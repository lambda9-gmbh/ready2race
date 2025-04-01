package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test

class PdfTest {

    @Test
    fun makePdf() {

        val templateDoc = PDDocument()
        val templatePage = PDPage()
        templateDoc.addPage(templatePage)
        val c = PDPageContentStream(templateDoc, templatePage)
        c.beginText()
        c.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12F)
        c.showText("Hello Template!")
        c.endText()
        c.close()

        val baos = ByteArrayOutputStream()
        templateDoc.save(baos)
        val bytes = baos.toByteArray()
        baos.close()

        val pageTemplate = PageTemplate(
            bytes = bytes,
            pageMargin = PageMargin.default
        )

        val doc = document(pageTemplate) {
            page {
                text("Hello world")
            }
            page {
                text("Hello world again")
            }
        }

        Files.createDirectories(Paths.get("testOutputs"))
        doc.save("testOutputs/testPdf.pdf")
        doc.close()
    }
}