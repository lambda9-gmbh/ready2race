package de.lambda9.ready2race.backend.pdf

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test

class PdfTest {

    @Test
    fun makePdf() {

        val doc = document {
            page {
                text("Hello world")
            }
            page {
                text("Hello world again")
            }
        }.render()

        Files.createDirectories(Paths.get("testOutputs"))
        doc.save("testOutputs/testPdf.pdf")

    }
}