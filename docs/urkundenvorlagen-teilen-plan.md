# Urkundenvorlagen teilen — Umsetzungsplan

> **Für agentische Bearbeiter:** Die Schritte sind als Checkboxen (`- [ ]`) geführt und werden
> Aufgabe für Aufgabe abgearbeitet.

**Ziel:** Eine eingerichtete Urkundenvorlage lässt sich als ZIP exportieren und in einer anderen
Instanz importieren, und Platzhalter lassen sich über Zahlenwerte und Tastatur statt nur mit der
Maus setzen.

**Architektur:** Das Paket ist dasselbe Datenmodell wie ein normaler Upload, nur gebündelt:
`template.json` trägt `GapDocumentTemplateRequest` plus Name, dazu `template.pdf` und optional die
Schriftdatei. Lesen und Schreiben des Pakets ist reine Logik ohne Datenbank und damit direkt
testbar; der Service übersetzt das Ergebnis in KIO-Fehler und ruft für den Import denselben
`addTemplate`-Weg wie der Upload. Im Frontend wandert die Geometrie der Platzhalter in ein eigenes
Logikmodul, das die Seitenleiste und der Editor gemeinsam nutzen.

**Tech-Stack:** Kotlin/Ktor mit KIO (tailwind-core), jOOQ, PDFBox, Jackson, `java.util.zip`;
React mit MUI, react-hook-form-mui, vitest.

## Global Constraints

- Branch: `feature/urkundenvorlagen-teilen`. Niemals nach `main` committen oder mergen. Nichts
  pushen, solange Thomas es nicht ausdrücklich sagt.
- Commit-Nachrichten ohne jede KI-Attribution (kein `Co-Authored-By`, keine Hinweise auf Claude).
- Vor jedem Maven-Aufruf: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Backend-Tests laufen in `backend/` mit `./mvnw test`, Frontend-Tests in `frontend/` mit
  `npm run test`.
- Tests sind in diesem Projekt reine Logiktests (`kotlin.test` im Backend, vitest auf `.ts`-Modulen
  im Frontend). Es gibt keine Datenbank- oder Komponententests — Service- und Routen-Code sowie
  `.tsx`-Komponenten bleiben ungetestet, die Logik dahinter nicht.
- `frontend/src/api/sdk.gen.ts` und `types.gen.ts` sind generiert. Nach jeder Änderung an
  `backend/src/main/resources/openapi/documentation.yaml` in `frontend/`: `npm run generate`.
- Neue Oberflächentexte immer in allen drei Sprachdateien: `frontend/src/i18n/de/translations.json`,
  `en/translations.json`, `da/translations.json`.
- Deutsche Texte mit echten Umlauten (ä, ö, ü, ß).

---

### Task 1: Datei-Prüfungen in den Service ziehen

Der Import darf nicht an den Prüfungen des Uploads vorbeikommen. `checkValidPdf` und die
Font-Endung hängen heute in der Route
([documentTemplate.kt:118](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt:118)),
die übrigen Prüfungen liegen bereits im Service. Diese Aufgabe verschiebt die beiden fehlenden
Prüfungen, damit danach beide Wege dieselbe Prüfung sehen.

Dabei ändert sich ein Fehler am bestehenden Upload bewusst: ein unlesbares PDF beantwortet die
Route heute mit `RequestError.File.UnsupportedType` und landet im Frontend in der Sammelmeldung.
Künftig ist es `GapDocumentTemplateError.InvalidPdf` mit eigenem Fehlercode und eigener Meldung.

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentTemplateError.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt:85`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt:64-109`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt:28-30,109-143`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/GapDocumentTemplateFileCheckTest.kt`

**Interfaces:**
- Produces: `GapDocumentTemplateLogic.hasValidFontExtension(fileName: String): Boolean` — von Task 2
  nicht gebraucht, aber vom Import über den Service.
- Produces: `GapDocumentTemplateError.InvalidPdf` mit `ErrorCode.DOCUMENT_TEMPLATE_INVALID_PDF`.
- Produces: `GapDocumentTemplateService.addTemplate(file: File, request: GapDocumentTemplateRequest,
  font: File?)` prüft ab jetzt selbst PDF und Font-Endung. Signatur unverändert.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Neue Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/GapDocumentTemplateFileCheckTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

In `backend/`:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=GapDocumentTemplateFileCheckTest
```

Erwartet: Übersetzungsfehler, `hasValidFontExtension` ist kein Member von
`GapDocumentTemplateLogic`.

- [ ] **Step 3: Die Prüfung in die Logik verschieben**

In `GapDocumentTemplateLogic.kt` ergänzen (der private Helfer in `documentTemplate.kt:29-30` wird in
Step 5 entfernt):

```kotlin
    /** Grobe Vorprüfung des Font-Uploads anhand der Dateiendung, bevor der Inhalt gelesen wird. */
    fun hasValidFontExtension(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf")
```

- [ ] **Step 4: Test laufen lassen, Erfolg prüfen**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=GapDocumentTemplateFileCheckTest
```

Erwartet: PASS, 2 Tests.

- [ ] **Step 5: Fehlerfall ergänzen und Prüfungen in den Service ziehen**

In `ErrorCode.kt` nach Zeile 85 einfügen:

```kotlin
    DOCUMENT_TEMPLATE_INVALID_PDF,
```

In `GapDocumentTemplateError.kt` den Eintrag `InvalidPdf` zur Enum-Liste hinzufügen und im
`when` behandeln:

```kotlin
        InvalidPdf ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Template file could not be read as PDF",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_INVALID_PDF,
            )
```

In `GapDocumentTemplateService.addTemplate` als erste Prüfung einfügen (vor
`placeholdersFitOnSinglePage`):

```kotlin
        !KIO.failOn(!checkValidPdf(file.bytes)) { GapDocumentTemplateError.InvalidPdf }
```

Import ergänzen: `import de.lambda9.ready2race.backend.pdf.checkValidPdf`.

In `addTemplate` und `updateTemplate` die Font-Endung prüfen — in beiden direkt vor dem vorhandenen
`checkValidFont`-Aufruf:

```kotlin
        if (font != null && font.bytes.isNotEmpty()) {
            !KIO.failOn(!GapDocumentTemplateLogic.hasValidFontExtension(font.name)) {
                GapDocumentTemplateError.InvalidFont
            }
            !KIO.failOn(!checkValidFont(font.bytes)) { GapDocumentTemplateError.InvalidFont }
        }
```

In `documentTemplate.kt` entfallen damit: der private `hasValidFontExtension` (Zeilen 28-30), die
`checkValidPdf`-Zeile im POST (Zeile 118), beide `hasValidFontExtension`-Blöcke (Zeilen 119-121 und
137-139) sowie die Importe von `checkValidPdf` und `GapDocumentTemplateError`, falls dort sonst
nicht mehr gebraucht. Der POST behält die Prüfung auf ein fehlendes File:

```kotlin
                val file = !KIO.failOnNull(parsed.templateFile) { RequestError.File.Missing }
```

- [ ] **Step 6: Gesamten Backend-Test laufen lassen**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
```

Erwartet: BUILD SUCCESS, keine Regression in `GapDocumentTemplateLogicTest`.

- [ ] **Step 7: Committen**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin && git commit -m "Move template file checks into the service so every path sees them"
```

---

### Task 2: Paketformat lesen und schreiben

Reine Logik, keine Datenbank — hier liegt der eigentliche Testschwerpunkt des Features.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplatePackage.kt`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/GapDocumentTemplatePackageTest.kt`

**Interfaces:**
- Consumes: `GapDocumentTemplateRequest(type, placeholders, fontName)`,
  `GapDocumentPlaceholderRequest(name, type, page, relLeft, relTop, relWidth, relHeight, textAlign,
  fontSize, bold, italic, staticText)`, `File(name, bytes)`, `jsonMapper`.
- Produces:
  - `GapDocumentTemplatePackage.Content(name: String, request: GapDocumentTemplateRequest,
    pdf: ByteArray, font: File?)`
  - `GapDocumentTemplatePackage.write(content: Content): ByteArray`
  - `GapDocumentTemplatePackage.read(bytes: ByteArray): ReadResult` mit
    `ReadResult.Ok(content)`, `ReadResult.Invalid`, `ReadResult.UnsupportedVersion`
  - `GapDocumentTemplatePackage.FORMAT_VERSION = 1`, `MANIFEST_ENTRY = "template.json"`,
    `PDF_ENTRY = "template.pdf"`

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Neue Datei `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/GapDocumentTemplatePackageTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate

import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplatePackage
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.text.TextAlign
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GapDocumentTemplatePackageTest {

    private val placeholder = GapDocumentPlaceholderRequest(
        name = null,
        type = GapDocumentPlaceholderType.PLACE,
        page = 1,
        relLeft = 0.0,
        relTop = 0.447,
        relWidth = 1.0,
        relHeight = 0.04,
        textAlign = TextAlign.CENTER,
        fontSize = 20,
        bold = true,
        italic = false,
        staticText = null,
    )

    private val pdfBytes = byteArrayOf(37, 80, 68, 70)

    private fun content(font: File? = null) = GapDocumentTemplatePackage.Content(
        name = "urkunde-vorlage.pdf",
        request = GapDocumentTemplateRequest(
            type = GapDocumentType.AWARD_CERTIFICATE,
            placeholders = listOf(placeholder),
            fontName = "TheSansOffice",
        ),
        pdf = pdfBytes,
        font = font,
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun roundTripWithoutFont() {
        val result = GapDocumentTemplatePackage.read(GapDocumentTemplatePackage.write(content()))

        val ok = assertIs<GapDocumentTemplatePackage.ReadResult.Ok>(result)
        assertEquals("urkunde-vorlage.pdf", ok.content.name)
        assertEquals(GapDocumentType.AWARD_CERTIFICATE, ok.content.request.type)
        assertEquals("TheSansOffice", ok.content.request.fontName)
        assertEquals(listOf(placeholder), ok.content.request.placeholders)
        assertContentEquals(pdfBytes, ok.content.pdf)
        assertNull(ok.content.font)
    }

    @Test
    fun roundTripWithFont() {
        val font = File("TheSansOffice.otf", byteArrayOf(1, 2, 3))

        val result = GapDocumentTemplatePackage.read(GapDocumentTemplatePackage.write(content(font)))

        val ok = assertIs<GapDocumentTemplatePackage.ReadResult.Ok>(result)
        assertEquals("TheSansOffice.otf", ok.content.font?.name)
        assertContentEquals(byteArrayOf(1, 2, 3), ok.content.font?.bytes)
    }

    @Test
    fun brokenArchiveIsRejected() {
        val result = GapDocumentTemplatePackage.read(byteArrayOf(1, 2, 3, 4, 5))

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun archiveWithoutManifestIsRejected() {
        val result = GapDocumentTemplatePackage.read(zipOf("template.pdf" to pdfBytes))

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun archiveWithoutPdfIsRejected() {
        val full = GapDocumentTemplatePackage.write(content())
        val manifest = readEntry(full, "template.json")

        val result = GapDocumentTemplatePackage.read(zipOf("template.json" to manifest))

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun unreadableManifestIsRejected() {
        val result = GapDocumentTemplatePackage.read(
            zipOf(
                "template.json" to "kein json".toByteArray(),
                "template.pdf" to pdfBytes,
            )
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun unknownFormatVersionIsRejected() {
        val manifest = """
            {"formatVersion":2,"name":"x.pdf","type":"AWARD_CERTIFICATE","fontName":null,
             "fontFile":null,"placeholders":[]}
        """.trimIndent().toByteArray()

        val result = GapDocumentTemplatePackage.read(
            zipOf("template.json" to manifest, "template.pdf" to pdfBytes)
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.UnsupportedVersion>(result)
    }

    @Test
    fun fontEntryWithDirectoryComponentIsRejected() {
        // Ein Eintrag mit Pfadanteil wird nicht gelesen; die im Manifest angekündigte Schrift
        // fehlt damit, und das Paket ist unbrauchbar statt halb übernommen.
        val manifest = """
            {"formatVersion":1,"name":"x.pdf","type":"AWARD_CERTIFICATE","fontName":"X",
             "fontFile":"../evil.otf","placeholders":[]}
        """.trimIndent().toByteArray()

        val result = GapDocumentTemplatePackage.read(
            zipOf(
                "template.json" to manifest,
                "template.pdf" to pdfBytes,
                "../evil.otf" to byteArrayOf(9),
            )
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    private fun readEntry(zipBytes: ByteArray, name: String): ByteArray {
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        error("entry $name not found")
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=GapDocumentTemplatePackageTest
```

Erwartet: Übersetzungsfehler, `GapDocumentTemplatePackage` existiert nicht.

- [ ] **Step 3: Das Paketmodul schreiben**

Neue Datei `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplatePackage.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Das Austauschformat einer Lückentext-Vorlage: ein ZIP aus `template.json` (Metadaten und
 * Platzhalter, feldgleich mit [GapDocumentTemplateRequest]), `template.pdf` und optional der
 * Schriftdatei unter ihrem eigenen Dateinamen. Bewusst ohne Datenbank und ohne KIO, damit Lesen
 * und Schreiben unmittelbar testbar bleiben; die Übersetzung in Fehlerantworten macht der Service.
 */
object GapDocumentTemplatePackage {

    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "template.json"
    const val PDF_ENTRY = "template.pdf"

    /** Obergrenze je entpacktem Eintrag, damit ein kleines Archiv nicht beliebig viel Speicher wird. */
    const val MAX_ENTRY_BYTES = 20 * 1024 * 1024

    data class Manifest(
        val formatVersion: Int,
        val name: String,
        val type: GapDocumentType,
        val fontName: String?,
        val fontFile: String?,
        val placeholders: List<GapDocumentPlaceholderRequest>,
    )

    data class Content(
        val name: String,
        val request: GapDocumentTemplateRequest,
        val pdf: ByteArray,
        val font: File?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Content

            if (name != other.name) return false
            if (request != other.request) return false
            if (!pdf.contentEquals(other.pdf)) return false
            if (font != other.font) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + request.hashCode()
            result = 31 * result + pdf.contentHashCode()
            result = 31 * result + (font?.hashCode() ?: 0)
            return result
        }
    }

    sealed interface ReadResult {
        data class Ok(val content: Content) : ReadResult
        data object Invalid : ReadResult
        data object UnsupportedVersion : ReadResult
    }

    fun write(content: Content): ByteArray {
        val manifest = Manifest(
            formatVersion = FORMAT_VERSION,
            name = content.name,
            type = content.request.type,
            fontName = content.request.fontName,
            fontFile = content.font?.name,
            placeholders = content.request.placeholders,
        )

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(jsonMapper.writeValueAsBytes(manifest))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PDF_ENTRY))
            zip.write(content.pdf)
            zip.closeEntry()

            content.font?.let { font ->
                zip.putNextEntry(ZipEntry(font.name))
                zip.write(font.bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): ReadResult {
        val entries = mutableMapOf<String, ByteArray>()

        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isSafeEntryName(entry.name)) {
                        val data = zip.readAtMost(MAX_ENTRY_BYTES) ?: return ReadResult.Invalid
                        entries[entry.name] = data
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return ReadResult.Invalid
        }

        val manifestBytes = entries[MANIFEST_ENTRY] ?: return ReadResult.Invalid

        val manifest = try {
            jsonMapper.readValue<Manifest>(manifestBytes)
        } catch (e: Exception) {
            return ReadResult.Invalid
        }

        if (manifest.formatVersion != FORMAT_VERSION) {
            return ReadResult.UnsupportedVersion
        }

        val pdf = entries[PDF_ENTRY] ?: return ReadResult.Invalid

        val font = manifest.fontFile?.let { fileName ->
            val data = entries[fileName] ?: return ReadResult.Invalid
            File(fileName, data)
        }

        return ReadResult.Ok(
            Content(
                name = manifest.name,
                request = GapDocumentTemplateRequest(
                    type = manifest.type,
                    placeholders = manifest.placeholders,
                    fontName = manifest.fontName,
                ),
                pdf = pdf,
                font = font,
            )
        )
    }

    /** Nur flache Einträge; alles mit Pfadanteil kommt aus einem präparierten Archiv. */
    private fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != ".."

    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg prüfen**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=GapDocumentTemplatePackageTest
```

Erwartet: PASS, 8 Tests.

- [ ] **Step 5: Committen**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin && git commit -m "Add a package format for gap document templates"
```

---

### Task 3: Export im Backend

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt` (nach `download`, ab Zeile 179)
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt` (Route-Block `/{gapDocumentTemplateId}`, nach dem `preview`-Eintrag)

**Interfaces:**
- Consumes: `GapDocumentTemplatePackage.write`, `GapDocumentTemplatePackage.Content` aus Task 2.
- Consumes: `GapDocumentTemplateRepo.get(id)`, `GapDocumentTemplateDataRepo.getData(id)`,
  `GapDocumentTemplateFontRepo` — der Font-Repo braucht ein Lesen nach Template-Id; falls es das
  noch nicht gibt, in `GapDocumentTemplateFontRepo` ergänzen (Muster: `GapDocumentTemplateDataRepo.getData`).
- Produces: `GapDocumentTemplateService.exportTemplate(id: UUID): App<GapDocumentTemplateError,
  ApiResponse.File>`, Dateiname `<vorlagenname ohne Endung>.r2rtpl.zip`.

- [ ] **Step 1: Repo-Lesezugriff auf die Schrift sicherstellen**

In `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/control/GapDocumentTemplateFontRepo.kt`
prüfen, ob ein Lesen nach Template-Id existiert. Falls nicht, nach dem Muster von
`GapDocumentTemplateDataRepo.getData` ergänzen:

```kotlin
    fun get(template: UUID) = GAP_DOCUMENT_TEMPLATE_FONT.selectOne { TEMPLATE.eq(template) }
```

(Exakte Schreibweise an den Nachbarn im selben Repo anpassen — die Repos folgen dort einem festen
jOOQ-Muster.)

- [ ] **Step 2: Export im Service ergänzen**

In `GapDocumentTemplateService.kt` nach `download` einfügen:

```kotlin
    /**
     * Die Vorlage als Austauschpaket: PDF, Platzhalter und Schrift in einer Datei, damit dieselbe
     * Einrichtung in einer anderen Instanz nicht von Hand nachgebaut werden muss.
     */
    fun exportTemplate(
        id: UUID,
    ): App<GapDocumentTemplateError, ApiResponse.File> = KIO.comprehension {
        val pdf = !GapDocumentTemplateDataRepo.getData(id).orDie().onNullFail { GapDocumentTemplateError.NotFound }
        val template = !GapDocumentTemplateRepo.get(id).orDie().onNullDie("foreign key constraint")
        val fontRecord = !GapDocumentTemplateFontRepo.get(id).orDie()

        val placeholders = template.placeholders!!.toList().map {
            GapDocumentPlaceholderRequest(
                name = it.name,
                type = GapDocumentPlaceholderType.valueOf(it.type!!),
                page = it.page!!,
                relLeft = it.relLeft!!,
                relTop = it.relTop!!,
                relWidth = it.relWidth!!,
                relHeight = it.relHeight!!,
                textAlign = TextAlign.valueOf(it.textAlign!!),
                fontSize = it.fontSize,
                bold = it.bold ?: false,
                italic = it.italic ?: false,
                staticText = it.staticText,
            )
        }

        val bytes = GapDocumentTemplatePackage.write(
            GapDocumentTemplatePackage.Content(
                name = template.name!!,
                request = GapDocumentTemplateRequest(
                    type = GapDocumentType.valueOf(template.type!!),
                    placeholders = placeholders,
                    fontName = template.fontName,
                ),
                pdf = pdf,
                font = fontRecord?.let { File(it.fileName, it.data) },
            )
        )

        KIO.ok(
            ApiResponse.File(
                name = "${template.name!!.substringBeforeLast('.')}.r2rtpl.zip",
                bytes = bytes,
            )
        )
    }
```

Die Feldnamen der View-Records (`template.placeholders`, `it.relLeft`, …) an die tatsächlich
generierten jOOQ-Namen anpassen; `toDto()` in
`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/control/Conversions.kt`
zeigt für dieselbe View, wie die Felder heißen. Wenn dort bereits eine Umwandlung von Record nach
Platzhalter existiert, diese wiederverwenden statt die Liste hier erneut aufzubauen.

- [ ] **Step 3: Route ergänzen**

In `documentTemplate.kt` innerhalb von `route("/{gapDocumentTemplateId}")` nach dem
`get("/preview")`-Block:

```kotlin
            get("/export") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val id = !pathParam("gapDocumentTemplateId", uuid)
                    GapDocumentTemplateService.exportTemplate(id)
                }
            }
```

- [ ] **Step 4: Übersetzen und Tests laufen lassen**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
```

Erwartet: BUILD SUCCESS.

- [ ] **Step 5: Committen**

```bash
git add backend/src/main/kotlin && git commit -m "Export a gap document template as a package"
```

---

### Task 4: Import im Backend

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentTemplateError.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/calls/responses/ErrorCode.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt`

**Interfaces:**
- Consumes: `GapDocumentTemplatePackage.read` aus Task 2, `addTemplate` aus Task 1.
- Produces: `GapDocumentTemplateService.createTemplate(file: File, request:
  GapDocumentTemplateRequest, font: File?): App<GapDocumentTemplateError, UUID>` — der bisherige
  Rumpf von `addTemplate`, der jetzt die erzeugte Id zurückgibt.
- Produces: `GapDocumentTemplateService.importTemplate(pkg: File): App<GapDocumentTemplateError,
  ApiResponse.Created>` — die Id wird gebraucht, damit die Oberfläche die importierte Vorlage direkt
  öffnen kann.
- Produces: `GapDocumentTemplateError.InvalidPackage` mit `ErrorCode.DOCUMENT_TEMPLATE_INVALID_PACKAGE`,
  `GapDocumentTemplateError.UnsupportedPackageVersion` mit
  `ErrorCode.DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION`.

- [ ] **Step 1: Fehlerfälle ergänzen**

In `ErrorCode.kt` nach `DOCUMENT_TEMPLATE_INVALID_PDF`:

```kotlin
    DOCUMENT_TEMPLATE_INVALID_PACKAGE,
    DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION,
```

In `GapDocumentTemplateError.kt` die Enum-Einträge `InvalidPackage` und
`UnsupportedPackageVersion` ergänzen und im `when` behandeln:

```kotlin
        InvalidPackage ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Template package could not be read",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_INVALID_PACKAGE,
            )

        UnsupportedPackageVersion ->
            ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Template package has an unsupported format version",
                errorCode = ErrorCode.DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION,
            )
```

- [ ] **Step 2: `addTemplate` aufteilen, damit die Id verfügbar wird**

`addTemplate` verwirft heute die Id, die `GapDocumentTemplateRepo.create` liefert. Der Rumpf wandert
unverändert in ein `createTemplate`, das die Id zurückgibt; `addTemplate` bleibt als dünne Hülle und
antwortet weiter mit `noData`, damit die bestehende POST-Route sich nicht ändert. In
`GapDocumentTemplateService.kt`:

```kotlin
    fun addTemplate(
        file: File,
        request: GapDocumentTemplateRequest,
        font: File?,
    ): App<GapDocumentTemplateError, ApiResponse.NoData> =
        createTemplate(file, request, font).map { ApiResponse.NoData }

    private fun createTemplate(
        file: File,
        request: GapDocumentTemplateRequest,
        font: File?,
    ): App<GapDocumentTemplateError, UUID> = KIO.comprehension {
        // ... der bisherige Rumpf von addTemplate, unverändert bis zum Ende ...
        KIO.ok(id)
    }
```

- [ ] **Step 3: Import im Service ergänzen**

In `GapDocumentTemplateService.kt` nach `exportTemplate`:

```kotlin
    /**
     * Legt aus einem Austauschpaket eine neue Vorlage an. Bewusst über [createTemplate], damit der
     * Import genau dieselben Prüfungen durchläuft wie ein normaler Upload und nicht an ihnen vorbei.
     */
    fun importTemplate(
        pkg: File,
    ): App<GapDocumentTemplateError, ApiResponse.Created> = KIO.comprehension {
        val content = when (val result = GapDocumentTemplatePackage.read(pkg.bytes)) {
            is GapDocumentTemplatePackage.ReadResult.Ok -> result.content
            GapDocumentTemplatePackage.ReadResult.Invalid ->
                !KIO.fail(GapDocumentTemplateError.InvalidPackage)
            GapDocumentTemplatePackage.ReadResult.UnsupportedVersion ->
                !KIO.fail(GapDocumentTemplateError.UnsupportedPackageVersion)
        }

        val id = !createTemplate(
            file = File(content.name, content.pdf),
            request = content.request,
            font = content.font,
        )

        KIO.ok(ApiResponse.Created(id))
    }
```

- [ ] **Step 4: Route ergänzen**

In `documentTemplate.kt` innerhalb von `route("/gapDocumentTemplate")`, nach dem `post`-Block:

```kotlin
        post("/import") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)

                val multiPartData = call.receiveMultipart()
                val pkg = !readSingleFilePart(multiPartData)

                GapDocumentTemplateService.importTemplate(pkg)
            }
        }
```

Und oberhalb von `fun Route.documentTemplate()` den Leser dafür, im Stil von
`readGapDocumentTemplateMultipart`:

```kotlin
/** Liest genau einen Datei-Teil; mehrere Dateien oder gar keine sind ein Fehler. */
private suspend fun CallComprehensionScope.readSingleFilePart(
    multiPartData: MultiPartData,
): File {
    var file: File? = null

    var done = false
    while (!done) {
        val part = multiPartData.readPart()
        if (part == null) {
            done = true
        } else {
            if (part is PartData.FileItem) {
                if (file == null) {
                    file = File(
                        part.originalFileName ?: "",
                        part.provider().toByteArray(),
                    )
                } else {
                    !KIO.fail(RequestError.File.Multiple)
                }
            }
            part.dispose()
        }
    }

    return !KIO.failOnNull(file) { RequestError.File.Missing }
}
```

- [ ] **Step 5: Übersetzen und Tests laufen lassen**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
```

Erwartet: BUILD SUCCESS.

- [ ] **Step 6: Committen**

```bash
git add backend/src/main/kotlin && git commit -m "Import a gap document template from a package"
```

---

### Task 5: Export und Import in der Oberfläche

**Files:**
- Modify: `backend/src/main/resources/openapi/documentation.yaml` (bei `/gapDocumentTemplate` und
  `/gapDocumentTemplate/{gapDocumentTemplateId}`, Muster ab Zeile 4115)
- Modify: `frontend/src/components/gapDocumentTemplate/GapDocumentTemplateTable.tsx`
- Modify: `frontend/src/components/certificate/certificateError.ts`
- Modify: `frontend/src/components/certificate/certificateError.test.ts`
- Modify: `frontend/src/i18n/de/translations.json`, `en/translations.json`, `da/translations.json`
- Modify: `frontend/src/pages/ConfigurationPage.tsx` (Tabellen-Props, falls der Import-Knopf dort
  eingehängt wird)

**Interfaces:**
- Consumes: `exportGapDocumentTemplate({path: {gapDocumentTemplateId}})` und
  `importGapDocumentTemplate({body: {file}})` — beide entstehen aus der OpenAPI-Beschreibung durch
  `npm run generate`.
- Produces: `documentTemplateErrorKey` erkennt zusätzlich `DOCUMENT_TEMPLATE_INVALID_PDF`,
  `DOCUMENT_TEMPLATE_INVALID_PACKAGE`, `DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION`.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

In `frontend/src/components/certificate/certificateError.test.ts` ergänzen (die vorhandenen
Testfälle zeigen das Muster):

```ts
    it('benennt ein unlesbares Paket', () => {
        expect(documentTemplateErrorKey({message: '', errorCode: 'DOCUMENT_TEMPLATE_INVALID_PACKAGE'}))
            .toBe('gap.document.template.error.invalidPackage')
    })

    it('benennt eine unbekannte Paketversion', () => {
        expect(
            documentTemplateErrorKey({
                message: '',
                errorCode: 'DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION',
            }),
        ).toBe('gap.document.template.error.unsupportedPackageVersion')
    })

    it('benennt eine Datei, die kein PDF ist', () => {
        expect(documentTemplateErrorKey({message: '', errorCode: 'DOCUMENT_TEMPLATE_INVALID_PDF'}))
            .toBe('gap.document.template.error.invalidPdf')
    })
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

In `frontend/`:

```bash
npm run test -- certificateError
```

Erwartet: FAIL — die Fehlercodes sind im generierten `ErrorCode`-Typ noch nicht vorhanden
(TypeScript-Fehler) beziehungsweise die Zuordnung liefert `undefined`.

- [ ] **Step 3: OpenAPI beschreiben und SDK erzeugen**

In `documentation.yaml` unter `/gapDocumentTemplate` einen Pfad `/gapDocumentTemplate/import`
ergänzen:

```yaml
  /gapDocumentTemplate/import:
    post:
      operationId: importGapDocumentTemplate
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required:
                - file
              properties:
                file:
                  description: Austauschpaket (.r2rtpl.zip) einer Urkundenvorlage.
                  type: string
                  format: binary
      responses:
        201:
          $ref: '#/components/responses/201'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        422:
          $ref: '#/components/responses/422'
        500:
          $ref: '#/components/responses/500'
```

Und unterhalb von `/gapDocumentTemplate/{gapDocumentTemplateId}/preview` (Muster ab Zeile 4262) den
Export:

```yaml
  /gapDocumentTemplate/{gapDocumentTemplateId}/export:
    parameters:
      - in: path
        name: gapDocumentTemplateId
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: exportGapDocumentTemplate
      responses:
        200:
          description: Template package successfully created
          content:
            application/zip:
              schema:
                type: string
                format: binary
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
```

Die neuen Fehlercodes zusätzlich im `ErrorCode`-Schema derselben Datei ergänzen (dort stehen die
vorhandenen `DOCUMENT_TEMPLATE_*`-Werte). Dann in `frontend/`:

```bash
npm run generate
```

- [ ] **Step 4: Fehlerzuordnung ergänzen**

In `certificateError.ts` die Schlüssel erweitern:

```ts
const templateKeys = {
    invalidFont: 'gap.document.template.error.invalidFont',
    invalidPdf: 'gap.document.template.error.invalidPdf',
    invalidPackage: 'gap.document.template.error.invalidPackage',
    unsupportedPackageVersion: 'gap.document.template.error.unsupportedPackageVersion',
    typeMismatch: 'gap.document.template.error.typeMismatch',
    placeholderPageNotSupported: 'gap.document.template.error.placeholderPageNotSupported',
    placeholderTypeNotSupported: 'gap.document.template.error.placeholderTypeNotSupported',
} as const
```

und in `documentTemplateErrorKey` die drei Fälle:

```ts
        case 'DOCUMENT_TEMPLATE_INVALID_PDF':
            return templateKeys.invalidPdf
        case 'DOCUMENT_TEMPLATE_INVALID_PACKAGE':
            return templateKeys.invalidPackage
        case 'DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION':
            return templateKeys.unsupportedPackageVersion
```

- [ ] **Step 5: Test laufen lassen, Erfolg prüfen**

```bash
npm run test -- certificateError
```

Erwartet: PASS.

- [ ] **Step 6: Texte ergänzen**

In `frontend/src/i18n/de/translations.json` unter `gap.document.template`:

```json
"error": {
    "invalidPdf": "Die gewählte Datei lässt sich nicht als PDF lesen.",
    "invalidPackage": "Das Paket lässt sich nicht lesen. Es muss eine unveränderte .r2rtpl.zip-Datei sein.",
    "unsupportedPackageVersion": "Das Paket stammt aus einer neueren Version. Bitte ready2race aktualisieren."
},
"export": "Vorlage exportieren",
"exportHint": "Das Paket enthält PDF, Platzhalter und die hinterlegte Schrift.",
"import": "Vorlage importieren",
"importSucceeded": "Vorlage importiert"
```

(Die bestehenden `error`-Einträge bleiben erhalten; nur ergänzen.) Sinngemäß dasselbe in
`en/translations.json` und `da/translations.json`.

- [ ] **Step 7: Knöpfe einbauen**

In `GapDocumentTemplateTable.tsx` den Export als weitere Aktion neben der Vorschau:

```tsx
    const handleExport = async (entity: GapDocumentTemplateDto) => {
        const {data, error} = await exportGapDocumentTemplate({
            path: {gapDocumentTemplateId: entity.id},
        })
        if (error || !data) {
            feedback.error(t('common.error.unexpected'))
            return
        }
        downloadBlob(data, `${entity.name.replace(/\.pdf$/i, '')}.r2rtpl.zip`)
    }
```

als Eintrag in `customEntityActions`:

```tsx
        <GridActionsCellItem
            icon={<FileDownload />}
            label={t('gap.document.template.export')}
            onClick={() => handleExport(entity)}
            showInMenu
        />,
```

Für das Auslösen des Downloads das im Projekt bereits verwendete Vorgehen übernehmen — wie der
Download in
`frontend/src/components/gapDocumentTemplate/GapDocumentTemplatePreviewDialog.tsx` beziehungsweise
`AwardCertificateDialog.tsx` es macht; keine zweite Hilfsfunktion dafür einführen.

Den Import als Knopf über der Tabelle, mit `SelectFileButton` (`accept={'.zip'}`) und
anschließendem Neuladen:

```tsx
    const handleImport = async (file: File) => {
        const {data, error} = await importGapDocumentTemplate({body: {file}})
        if (error || !data) {
            const key = error ? documentTemplateErrorKey(error) : undefined
            feedback.error(key ? t(key) : t('common.error.unexpected'))
            return
        }
        feedback.success(t('gap.document.template.importSucceeded'))
        props.reloadData()
        props.openDialog(data.id)
    }
```

Die importierte Vorlage wird danach im Bearbeiten-Dialog geöffnet, damit sichtbar ist, was
angekommen ist — dafür liefert der Import die Id (Task 4). Die genaue Art, wie die Tabelle neu
geladen und der Dialog geöffnet wird, an `BaseEntityTableProps` in `frontend/src/utils/types.ts`
ablesen und dem Muster der Nachbartabellen folgen; führt die Tabelle bislang keinen Weg, einen
Dialog für eine bestimmte Id zu öffnen, dann die Id per `useState` halten und den vorhandenen
`GapDocumentTemplateDialog` in `ConfigurationPage.tsx` damit ansteuern.

Am Export-Knopf den Lizenzhinweis als Tooltip mit `gap.document.template.exportHint` zeigen —
das Paket trägt die hinterlegte Schriftdatei mit sich.

- [ ] **Step 8: Bauen und alle Tests laufen lassen**

```bash
npm run test && npm run build
```

Erwartet: alle Tests PASS, Build ohne TypeScript-Fehler.

- [ ] **Step 9: Committen**

```bash
git add backend/src/main/resources/openapi frontend/src && git commit -m "Offer export and import for certificate templates"
```

---

### Task 6: Position und Größe als Zahlenfelder

**Files:**
- Create: `frontend/src/components/gapDocumentTemplate/placeholderGeometry.ts`
- Create: `frontend/src/components/gapDocumentTemplate/placeholderGeometry.test.ts`
- Modify: `frontend/src/components/gapDocumentTemplate/PlaceholderSidebar.tsx:218-242`
- Modify: `frontend/src/components/gapDocumentTemplate/PdfPlaceholderEditor.tsx:88-180`
- Modify: `frontend/src/i18n/*/translations.json`

**Interfaces:**
- Produces:
  - `type PlaceholderRect = {relLeft: number; relTop: number; relWidth: number; relHeight: number}`
  - `parsePercent(value: string): number | undefined` — „44,7" und „44.7" ergeben `0.447`
  - `clampRect(rect: PlaceholderRect): PlaceholderRect`
  - `nudgeRect(rect: PlaceholderRect, direction: 'left' | 'right' | 'up' | 'down', large: boolean):
    PlaceholderRect` (Task 7 nutzt das)

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Neue Datei `frontend/src/components/gapDocumentTemplate/placeholderGeometry.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {clampRect, nudgeRect, parsePercent} from './placeholderGeometry.ts'

const rect = {relLeft: 0.2, relTop: 0.3, relWidth: 0.5, relHeight: 0.1}

describe('parsePercent', () => {
    it('liest Punkt und Komma als Dezimaltrenner', () => {
        expect(parsePercent('44.7')).toBeCloseTo(0.447)
        expect(parsePercent('44,7')).toBeCloseTo(0.447)
    })

    it('gibt undefined für Unlesbares', () => {
        expect(parsePercent('')).toBeUndefined()
        expect(parsePercent('abc')).toBeUndefined()
    })

    it('begrenzt auf 0 bis 100 Prozent', () => {
        expect(parsePercent('-5')).toBe(0)
        expect(parsePercent('140')).toBe(1)
    })
})

describe('clampRect', () => {
    it('lässt einen Kasten innerhalb der Seite unverändert', () => {
        expect(clampRect(rect)).toEqual(rect)
    })

    it('schiebt einen überstehenden Kasten zurück auf die Seite', () => {
        expect(clampRect({relLeft: 0.8, relTop: 0.3, relWidth: 0.5, relHeight: 0.1})).toEqual({
            relLeft: 0.5,
            relTop: 0.3,
            relWidth: 0.5,
            relHeight: 0.1,
        })
    })

    it('kürzt einen Kasten, der breiter als die Seite ist', () => {
        expect(clampRect({relLeft: 0, relTop: 0, relWidth: 1.5, relHeight: 2})).toEqual({
            relLeft: 0,
            relTop: 0,
            relWidth: 1,
            relHeight: 1,
        })
    })
})

describe('nudgeRect', () => {
    it('verschiebt um 0,1 Prozent', () => {
        expect(nudgeRect(rect, 'right', false).relLeft).toBeCloseTo(0.201)
        expect(nudgeRect(rect, 'up', false).relTop).toBeCloseTo(0.299)
    })

    it('verschiebt mit Shift um 1 Prozent', () => {
        expect(nudgeRect(rect, 'down', true).relTop).toBeCloseTo(0.31)
        expect(nudgeRect(rect, 'left', true).relLeft).toBeCloseTo(0.19)
    })

    it('bleibt auf der Seite', () => {
        const atEdge = {relLeft: 0, relTop: 0, relWidth: 0.5, relHeight: 0.1}
        expect(nudgeRect(atEdge, 'left', true).relLeft).toBe(0)
    })
})
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

```bash
npm run test -- placeholderGeometry
```

Erwartet: FAIL, Modul `./placeholderGeometry.ts` existiert nicht.

- [ ] **Step 3: Das Geometriemodul schreiben**

Neue Datei `frontend/src/components/gapDocumentTemplate/placeholderGeometry.ts`:

```ts
/**
 * Die Geometrie eines Platzhalters als Anteile der Seite (0 bis 1). Ziehen im Editor,
 * Zahleneingabe in der Seitenleiste und die Pfeiltasten rechnen alle über diese Funktionen,
 * damit sie sich nicht in Randfällen unterscheiden.
 */
export type PlaceholderRect = {
    relLeft: number
    relTop: number
    relWidth: number
    relHeight: number
}

const NUDGE_SMALL = 0.001
const NUDGE_LARGE = 0.01

const clampUnit = (value: number): number => Math.min(1, Math.max(0, value))

/** Prozenteingabe als Anteil, oder undefined wenn die Eingabe keine Zahl ist. */
export const parsePercent = (value: string): number | undefined => {
    const parsed = Number.parseFloat(value.replace(',', '.'))
    if (Number.isNaN(parsed)) {
        return undefined
    }
    return clampUnit(parsed / 100)
}

/** Hält den Kasten vollständig auf der Seite. */
export const clampRect = (rect: PlaceholderRect): PlaceholderRect => {
    const relWidth = clampUnit(rect.relWidth)
    const relHeight = clampUnit(rect.relHeight)
    return {
        relWidth,
        relHeight,
        relLeft: Math.min(clampUnit(rect.relLeft), 1 - relWidth),
        relTop: Math.min(clampUnit(rect.relTop), 1 - relHeight),
    }
}

export const nudgeRect = (
    rect: PlaceholderRect,
    direction: 'left' | 'right' | 'up' | 'down',
    large: boolean,
): PlaceholderRect => {
    const step = large ? NUDGE_LARGE : NUDGE_SMALL
    switch (direction) {
        case 'left':
            return clampRect({...rect, relLeft: rect.relLeft - step})
        case 'right':
            return clampRect({...rect, relLeft: rect.relLeft + step})
        case 'up':
            return clampRect({...rect, relTop: rect.relTop - step})
        case 'down':
            return clampRect({...rect, relTop: rect.relTop + step})
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg prüfen**

```bash
npm run test -- placeholderGeometry
```

Erwartet: PASS, 9 Testfälle.

- [ ] **Step 5: Committen**

```bash
git add frontend/src/components/gapDocumentTemplate && git commit -m "Add placeholder geometry helpers"
```

- [ ] **Step 6: Die Anzeige durch Eingabefelder ersetzen**

In `PlaceholderSidebar.tsx` die beiden `Box`-Blöcke für Position und Größe (Zeilen 218-242) durch
vier Zahlenfelder ersetzen:

```tsx
                        <Stack direction="row" spacing={1}>
                            <TextField
                                label={`${t('gap.document.placeholder.positionX')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relLeft * 100).toFixed(1)}
                                onChange={e => {
                                    const relLeft = parsePercent(e.target.value)
                                    if (relLeft !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relLeft}),
                                        )
                                    }
                                }}
                            />
                            <TextField
                                label={`${t('gap.document.placeholder.positionY')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relTop * 100).toFixed(1)}
                                onChange={e => {
                                    const relTop = parsePercent(e.target.value)
                                    if (relTop !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relTop}),
                                        )
                                    }
                                }}
                            />
                        </Stack>

                        <Stack direction="row" spacing={1}>
                            <TextField
                                label={`${t('gap.document.placeholder.width')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relWidth * 100).toFixed(1)}
                                onChange={e => {
                                    const relWidth = parsePercent(e.target.value)
                                    if (relWidth !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relWidth}),
                                        )
                                    }
                                }}
                            />
                            <TextField
                                label={`${t('gap.document.placeholder.height')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relHeight * 100).toFixed(1)}
                                onChange={e => {
                                    const relHeight = parsePercent(e.target.value)
                                    if (relHeight !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relHeight}),
                                        )
                                    }
                                }}
                            />
                        </Stack>
```

`handlePlaceholderPropertyChange` nimmt heute ein Teilobjekt der Platzhalterdaten entgegen — die
vier Felder aus `clampRect` passen dort hinein. Importe ergänzen: `clampRect`, `parsePercent` aus
`./placeholderGeometry.ts`.

Neue Textschlüssel in allen drei Sprachdateien unter `gap.document.placeholder`:
`positionX`, `positionY`, `width`, `height` (deutsch: „X", „Y", „Breite", „Höhe").

- [ ] **Step 7: Ziehen auf dieselbe Klemmung stellen**

In `PdfPlaceholderEditor.tsx` die eigenen `Math.min`/`Math.max`-Ketten beim Ziehen und Skalieren
(Zeilen 118-168) durch `clampRect` ersetzen, damit Ziehen und Tippen dieselbe Grenze haben.

- [ ] **Step 8: Bauen und Tests laufen lassen**

```bash
npm run test && npm run build
```

Erwartet: alle Tests PASS, Build sauber.

- [ ] **Step 9: Committen**

```bash
git add frontend/src && git commit -m "Let placeholders be positioned by typing exact values"
```

---

### Task 7: Pfeiltasten im Editor

**Files:**
- Modify: `frontend/src/components/gapDocumentTemplate/PdfPlaceholderEditor.tsx`

**Interfaces:**
- Consumes: `nudgeRect` aus Task 6.

- [ ] **Step 1: Tastenbehandlung ergänzen**

Im Editor am Container (dem Element, das die Seite zeichnet) — nicht am Dokument, damit Tippen in
Eingabefeldern unberührt bleibt:

```tsx
    const handleKeyDown = (event: React.KeyboardEvent) => {
        if (!selectedPlaceholder) {
            return
        }
        const direction =
            event.key === 'ArrowLeft'
                ? 'left'
                : event.key === 'ArrowRight'
                  ? 'right'
                  : event.key === 'ArrowUp'
                    ? 'up'
                    : event.key === 'ArrowDown'
                      ? 'down'
                      : undefined
        if (!direction) {
            return
        }
        event.preventDefault()
        const placeholder = placeholders.find(p => p.id === selectedPlaceholder)
        if (!placeholder) {
            return
        }
        onPlaceholdersChange(
            placeholders.map(p =>
                p.id === selectedPlaceholder ? {...p, ...nudgeRect(p, direction, event.shiftKey)} : p,
            ),
        )
    }
```

Der Container braucht `tabIndex={0}` und `onKeyDown={handleKeyDown}`, damit er den Fokus bekommt.
Die genauen Namen der Props (`placeholders`, `onPlaceholdersChange`, `selectedPlaceholder`) an die
im Editor tatsächlich verwendeten anpassen.

- [ ] **Step 2: Im Browser prüfen**

Dev-Server über die Preview starten, Konfiguration → Komponenten Veranstaltung →
Urkundenvorlagen → Vorlage bearbeiten. Einen Platzhalter auswählen, Pfeiltasten drücken: der Kasten
wandert in kleinen Schritten, mit Shift in großen. In ein Textfeld der Seitenleiste klicken und dort
die Pfeiltasten benutzen: der Kasten darf sich nicht bewegen.

- [ ] **Step 3: Bauen und committen**

```bash
npm run test && npm run build
git add frontend/src && git commit -m "Nudge the selected placeholder with the arrow keys"
```

---

### Task 8: Live-Vorschau im Editor

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`
- Create: `frontend/src/components/gapDocumentTemplate/placeholderSample.ts`
- Create: `frontend/src/components/gapDocumentTemplate/placeholderSample.test.ts`
- Modify: `frontend/src/components/gapDocumentTemplate/PdfPlaceholderEditor.tsx`
- Modify: `frontend/src/components/gapDocumentTemplate/GapDocumentTemplateDialog.tsx`
- Modify: `frontend/src/i18n/*/translations.json`

**Interfaces:**
- Produces: `GapDocumentTemplateService.downloadFont(id: UUID): App<GapDocumentTemplateError,
  ApiResponse.File>`, Route `GET /gapDocumentTemplate/{id}/font`, `operationId:
  getGapDocumentTemplateFont`.
- Produces: `sampleTextFor(type: GapDocumentPlaceholderType, staticText?: string): string` in
  `placeholderSample.ts`.

- [ ] **Step 1: Beispieltexte als testbares Modul**

Neue Datei `frontend/src/components/gapDocumentTemplate/placeholderSample.test.ts`:

```ts
import {describe, expect, it} from 'vitest'
import {sampleTextFor} from './placeholderSample.ts'

describe('sampleTextFor', () => {
    it('zeigt für jeden Typ einen Beispieltext', () => {
        expect(sampleTextFor('FULL_NAME')).toBe('Max Mustermann')
        expect(sampleTextFor('PLACE')).toBe('1. Platz')
    })

    it('zeigt bei freiem Text den eingegebenen Text', () => {
        expect(sampleTextFor('FREE_TEXT', 'Moritz Petri — Vorsitzender')).toBe(
            'Moritz Petri — Vorsitzender',
        )
    })

    it('fällt bei leerem freien Text auf einen Hinweis zurück', () => {
        expect(sampleTextFor('FREE_TEXT')).toBe('Fester Text')
    })
})
```

Die erwarteten Werte an `previewValues` in
[GapDocumentTemplateService.kt:205](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt:205)
angleichen — dort steht, was die Server-Vorschau einsetzt; beide sollen dasselbe zeigen. Beim
Schreiben des Tests die Datei öffnen und die Werte übernehmen statt zu raten.

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

```bash
npm run test -- placeholderSample
```

Erwartet: FAIL, Modul existiert nicht.

- [ ] **Step 3: Modul schreiben**

`frontend/src/components/gapDocumentTemplate/placeholderSample.ts` mit einer Abbildung von
`GapDocumentPlaceholderType` auf denselben Beispielwert wie die Server-Vorschau, plus dem
Sonderfall `FREE_TEXT` (eingegebener Text, sonst der Hinweis „Fester Text").

- [ ] **Step 4: Test laufen lassen, Erfolg prüfen**

```bash
npm run test -- placeholderSample
```

Erwartet: PASS.

- [ ] **Step 5: Font-Endpunkt im Backend**

In `GapDocumentTemplateService.kt`:

```kotlin
    /** Die hinterlegte Schrift, damit der Editor die Vorschau in derselben Schrift zeichnen kann. */
    fun downloadFont(
        id: UUID,
    ): App<GapDocumentTemplateError, ApiResponse.File> = KIO.comprehension {
        val font = !GapDocumentTemplateFontRepo.get(id).orDie().onNullFail { GapDocumentTemplateError.NotFound }

        KIO.ok(
            ApiResponse.File(
                name = font.fileName,
                bytes = font.data,
            )
        )
    }
```

Route in `documentTemplate.kt` innerhalb von `route("/{gapDocumentTemplateId}")`:

```kotlin
            get("/font") {
                call.respondComprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val id = !pathParam("gapDocumentTemplateId", uuid)
                    GapDocumentTemplateService.downloadFont(id)
                }
            }
```

In `documentation.yaml` analog zum Export aus Task 5 beschreiben (`operationId:
getGapDocumentTemplateFont`, 200 mit `application/octet-stream`, 404 wenn keine Schrift hinterlegt
ist), danach in `frontend/`: `npm run generate`.

- [ ] **Step 6: Vorschau im Editor zeichnen**

In `PdfPlaceholderEditor.tsx` in jedem Platzhalterkasten den Beispieltext ausgeben statt des leeren
Kastens: `sampleTextFor(placeholder.type, placeholder.staticText)`, mit `fontSize` (fällt auf die
Kastenhöhe zurück, wenn leer — dieselbe Regel wie im Backend), `textAlign`, `fontWeight` aus `bold`,
`fontStyle` aus `italic`, vertikal zentriert. Der Text darf nicht überlaufen: `overflow: hidden`,
`whiteSpace: 'nowrap'`.

Die Schrift wird im Dialog geladen und als CSS-Familienname an den Editor gereicht. In
`GapDocumentTemplateDialog.tsx`:

```tsx
    const [fontFamily, setFontFamily] = useState<string | undefined>(undefined)

    useEffect(() => {
        const source = fontFile
            ? Promise.resolve(fontFile)
            : props.entity?.hasFont
              ? getGapDocumentTemplateFont({path: {gapDocumentTemplateId: props.entity.id}}).then(
                    r => r.data ?? undefined,
                )
              : Promise.resolve(undefined)

        let objectUrl: string | undefined
        let cancelled = false

        source.then(async blob => {
            if (!blob || cancelled) {
                setFontFamily(undefined)
                return
            }
            objectUrl = URL.createObjectURL(blob)
            const family = `gapTemplateFont-${Date.now()}`
            const face = new FontFace(family, `url(${objectUrl})`)
            try {
                await face.load()
                document.fonts.add(face)
                if (!cancelled) {
                    setFontFamily(family)
                }
            } catch {
                setFontFamily(undefined)
            }
        })

        return () => {
            cancelled = true
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl)
            }
        }
    }, [fontFile, props.entity])
```

`fontFamily` als Prop an `PdfPlaceholderEditor` weiterreichen; ist es `undefined`, zeichnet der
Editor mit der Standardschrift.

- [ ] **Step 7: Hinweis ergänzen**

Unter dem Editor einen Hinweistext einblenden, neuer Schlüssel
`gap.document.template.preview.approximate` in allen drei Sprachdateien, deutsch: „Die Darstellung
im Editor ist eine Näherung. Verbindlich ist die Vorschau der gespeicherten Vorlage."

- [ ] **Step 8: Im Browser prüfen**

Vorlage mit hochgeladener Schrift öffnen: die Kästen zeigen Beispieltexte in der hochgeladenen
Schrift. Schrift entfernen: die Texte bleiben, in der Standardschrift. Anschließend speichern und
die Server-Vorschau öffnen — Positionen müssen übereinstimmen.

- [ ] **Step 9: Bauen, Tests, committen**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
npm run test && npm run build
git add backend/src frontend/src && git commit -m "Show sample text in the placeholder editor"
```

---

## Abschluss

- [ ] **Alle Tests grün**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
```

```bash
npm run test && npm run build
```

- [ ] **Durchstich von Hand**: In der laufenden Instanz die DRV-Vorlage einrichten, exportieren, die
  Vorlage löschen, das Paket wieder importieren, Vorschau öffnen — das Ergebnis muss dem vor dem
  Export entsprechen.

- [ ] **Nicht pushen**, bis Thomas es sagt. Der Branch mündet später in `feature/crf-2026`.
