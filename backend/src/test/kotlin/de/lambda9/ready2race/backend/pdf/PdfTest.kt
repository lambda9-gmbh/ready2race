package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test

class PdfTest {

    @Test
    fun exampleRegRes() {

        Files.createDirectories(Paths.get("testOutputs"))

        val doc = document {
            page {
                block(
                    padding = Padding(0f, 0f, 0f, 20f)
                ) {
                    text(
                        fontStyle = FontStyle.BOLD,
                        fontSize = 12f,
                    ) {
                        "Wettkampf / "
                    }
                    text(
                        fontSize = 11f,
                        newLine = false,
                    ) {
                        "Competition"
                    }
                    table(
                        padding = Padding(5f, 20f, 0f, 0f)
                    ) {
                        column(0.1f)
                        column(0.25f)
                        column(0.65f)

                        row {
                            cell {
                                text(
                                    fontSize = 12f,
                                ) { "1" }
                            }
                            cell {
                                text(
                                    fontSize = 12f,
                                ) { "CF 1x" }
                            }
                            cell {
                                text(
                                    fontSize = 12f,
                                ) { "Frauen Einer" }
                            }
                        }
                    }
                }

                repeat(20) {
                    block(
                        padding = Padding(0f, 0f, 0f, 20f),
                        keepTogether = true,
                    ) {
                        text(
                            fontStyle = FontStyle.BOLD
                        ) {
                            "Ruderklub Flensburg"
                        }
                        table(
                            padding = Padding(5f, 0f, 0f, 0f),
                            withBorder = true,
                        ) {
                            column(0.25f)
                            column(0.25f)
                            column(0.1f)
                            column(0.4f)

                            repeat(3) { idx ->
                                row(
                                    color = if (idx % 2 == 1) Color(220, 220, 220) else null,
                                ) {
                                    cell {
                                        text { "Mario" }
                                    }
                                    cell {
                                        text { "Köhler" }
                                    }
                                    cell {
                                        text { "1990" }
                                    }
                                    cell {
                                        text { "Ruderklub Flensburg" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        doc.save("testOutputs/examplePdf.pdf")
        doc.close()
    }

    @Test
    fun makeTemplate() {

        Files.createDirectories(Paths.get("testOutputs"))

        val templateDoc = PDDocument()
        val templatePage = PDPage(PDRectangle.A4)
        templateDoc.addPage(templatePage)
        val c = PDPageContentStream(templateDoc, templatePage)

        c.addRect(5f, 5f, templatePage.mediaBox.width - 10f, 20 * POINTS_PER_MM)
        c.setNonStrokingColor(Color.BLUE)
        c.fill()

        c.close()

        templateDoc.save("testOutputs/templatePdf.pdf")
        templateDoc.close()
    }

    @Test
    fun makePdf() {

        Files.createDirectories(Paths.get("testOutputs"))

        val templateDoc = PDDocument()
        val templatePage = PDPage(PDRectangle.A4)
        templateDoc.addPage(templatePage)
        val c = PDPageContentStream(templateDoc, templatePage)

        c.addRect(0f, 0f, templatePage.mediaBox.width, templatePage.mediaBox.height)
        c.setNonStrokingColor(Color.LIGHT_GRAY)
        c.fill()
        c.setNonStrokingColor(Color.BLACK)

        c.beginText()
        c.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12F)
        c.newLineAtOffset(10f, 10f)
        c.showText("Hello Template!")
        c.endText()
        c.close()

        val baos = ByteArrayOutputStream()
        templateDoc.save(baos)
        val bytes = baos.toByteArray()
        baos.close()

        val pageTemplate = PageTemplate(
            bytes = bytes,
            pagepadding = Padding.defaultPagePadding
        )

        val doc = document(pageTemplate) {
            page {

                repeat(3) { i ->
                    table(padding = Padding(0f, 2f)) {
                        column(100F)
                        column(100F)
                        column(100F)

                        repeat(2) { j ->
                            row(color = Color.CYAN) {
                                cell {
                                    text { "Hello Wogrld!" }
                                    text { "Test $i-$j" }
                                }
                                cell {
                                    text { "Helglo World!" }
                                }
                                cell {
                                    text { "Hello World!" }
                                }
                            }
                            row(color = Color.YELLOW) {
                                cell {
                                    text { "gHello World!" }
                                }
                                cell {
                                    text { "Hello World! Hello World!" }
                                }
                                cell {
                                    text { "gHello World!" }
                                }

                            }
                            row(color = Color.RED) {
                                cell {
                                    text { "gHello World!" }
                                }
                                cell {
                                    text { "Hello World! Hello World! Hello World! Hello World!" }
                                }
                                cell {
                                    text { "gHello World!" }
                                    text { "Test 2" }
                                    text(newLine = false) { "Test3etwaslänb gerundnocchknaösdfasdfsfdg" }
                                }
                            }
                        }
                    }
                }

                block(keepTogether = true) {
                    text {
                        "Text after table gg, more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more"
                    }
                }

                block(keepTogether = false, padding = Padding(0f, 0f)) {

                    text {
                        "Text after table gg, more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more"
                    }

                    table(padding = Padding(10f, 2f)) {
                        column(100F)
                        column(100F)
                        column(100F)

                        row(color = Color.CYAN) {
                            cell {
                                text { "Hello Wogrld!" }
                                text { "Test" }
                            }
                            cell {
                                text { "Helglo World!" }
                            }
                            cell {
                                text { "Hello World!" }
                            }
                        }
                        row(color = Color.YELLOW) {
                            cell {
                                text { "gHello World!" }
                            }
                            cell {
                                text { "Hello World! Hello World!" }
                            }
                            cell {
                                text { "gHello World!" }
                            }

                        }
                        row(color = Color.RED) {
                            cell {
                                text { "gHello World!" }
                            }
                            cell {
                                text { "Hello World! Hello World! Hello World! Hello World!" }
                            }
                            cell {
                                text { "gHello World!" }
                                text { "Test 2" }
                                text(newLine = false) { "Test3etwaslänb gerundnocchknaösdfasdfsfdg" }
                            }
                        }
                    }
                    text {
                        "Text after table gg, more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more"
                    }
                }

                text {
                    "Text after table gg, more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more and more"
                }
            }
        }

        doc.save("testOutputs/testPdf.pdf")
        doc.close()
    }
}