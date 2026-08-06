# Siegerurkunden als PDF und Word — Implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Urkunden für Wettkampf-Platzierungen als druckfertiges PDF und als nachbearbeitbare
Word-Datei direkt aus ready2race herunterladen; die bestehende Teilnahmeurkunde erhält denselben
Word-Download.

**Architecture:** Die vorhandene Gap-Dokument-Mechanik (PDF-Vorlage plus visuell platzierte
Platzhalter) wird um einen Dokumenttyp `AWARD_CERTIFICATE`, um Schriftattribute je Platzhalter und
um einen optionalen Schrift-Upload erweitert. Die Zuordnung Platzhaltertyp → Inhalt wandert in eine
reine Funktion, die von allen Urkundenarten genutzt wird. Zwei Renderer erzeugen aus derselben
Platzhalterliste eine Serie: PDFBox für PDF (eine Seite je Urkunde, Design standardmäßig aus, weil
auf vorgedrucktes Papier gedruckt wird) und POI XWPF für DOCX (absolut positionierte Textrahmen über
`w:framePr`).

**Tech Stack:** Kotlin, Ktor, KIO (`de.lambda9.tailwind`), jOOQ, Flyway, PDFBox 3.0.4, POI 5.4.1
(`poi-ooxml`), React/TypeScript mit generiertem Client (`@hey-api/openapi-ts`), kotlin.test.

**Spec:** `docs/superpowers/specs/2026-08-05-urkunden-pdf-word-design.md`

## Global Constraints

- Kotlin-Services folgen dem KIO-Muster: `App<E, A>`, `KIO.comprehension { }`, `!`-Operator zum
  Auspacken, `.orDie()` für Infrastrukturfehler, `.onNullFail { }` für typisierte Fehler.
- **`KIO.fail` ohne `!` ist ein No-Op** — jeder Fehlerpfad in einer Comprehension braucht `!`.
- Reine Logik gehört in ein `*Logic`-Objekt und wird ohne Datenbank getestet, wie
  `AthleteBoardLogic` / `AthleteBoardLogicTest`. Tests mit `kotlin.test` (`@Test`, `assertEquals`),
  Kommentare auf Deutsch.
- Deutsche Texte immer mit echten Umlauten (ä, ö, ü, ß).
- Migrationen heißen `V<YYYYMMDDHHmm>__<name>.sql`. Views liegen **nicht** in der Migration, sondern
  in `backend/src/main/resources/db/migration/afterMigrate.sql`, wo sie zuerst gedroppt und dann neu
  angelegt werden.
- **Jeder Maven-Aufruf braucht die eigene Build-Datenbank dieses Worktrees.** Der geteilte Container
  `backend-build-db-1` ist mit den Migrationen dieses Branches nicht kompatibel (Flyway-Validate-Fehler
  von einem anderen Worktree) und darf **nicht** angefasst werden. Stattdessen läuft der Container
  `urkunden-build-db` auf Port 17660; jeder Aufruf hängt deshalb
  `-Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build` an. Läuft er nicht, mit
  `docker start urkunden-build-db` wieder hochfahren.
- **`./mvnw jooq:generate` funktioniert auf diesem Rechner nicht** (kein `org.jooq`-pluginGroup in
  `~/.m2/settings.xml`). Codegen läuft über den Lifecycle, der auch Flyway zuerst migriert:
  `./mvnw generate-sources -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build`.
  Ein direkter Goal-Aufruf überspringt Flyway und generiert stillschweigend gegen ein veraltetes Schema.
- Referenzstand vor dieser Arbeit: 131 Tests, alle grün.
- `JAVA_HOME` fehlt in der Shell, und `/usr/libexec/java_home` findet das JDK **nicht** — das
  Homebrew-JDK 21 ist keg-only. Jeder Maven-Aufruf braucht deshalb wörtlich
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- OpenAPI-Quelle ist `backend/src/main/resources/openapi/documentation.yaml` (handpflegt). Neue
  Pfade **vor** dem `components:`-Schlüssel einfügen, danach im Frontend `npm run generate`.
  Das Verzeichnis `api/*.tsp` wird nicht angefasst.
- Berechtigung für alle neuen Endpunkte: `Privilege.ReadEventGlobal`.
- Keine Commits nach `main`. Gearbeitet wird auf dem aktuellen Branch
  `claude/urkunden-pdf-word-download-be5e16`.
- Commit-Nachrichten englisch, Imperativ, ohne Hinweis auf KI-Werkzeuge.

## Dateistruktur

| Datei | Verantwortung |
|---|---|
| `backend/src/main/resources/db/migration/V202608051200__award_certificate_templates.sql` | Neue Spalten und Schrift-Tabelle |
| `backend/src/main/resources/db/migration/afterMigrate.sql` | Views `gap_document_template_view`, `gap_document_template_assignment` erweitern |
| `app/documentTemplate/entity/GapDocumentType.kt` | Dokumenttypen plus erlaubte Platzhalter je Typ |
| `app/documentTemplate/entity/GapDocumentPlaceholderType.kt` | Platzhaltertypen |
| `app/documentTemplate/entity/GapPlaceholder.kt` | DB-unabhängige Platzhalterbeschreibung plus Werteobjekt |
| `app/documentTemplate/boundary/GapPlaceholderLogic.kt` | Reine Zuordnung Platzhalter → `AdditionalText` |
| `backend/pdf/AdditionalText.kt` | Schriftattribute ergänzen |
| `backend/pdf/documents.kt` | Serien-PDF, Schrifteinbettung, Mehrzeiler |
| `backend/docx/GapDocumentsDocx.kt` | DOCX-Renderer mit `w:framePr` |
| `app/certificate/entity/AwardCertificate.kt` | Optionen, Modus, Eintrag, Fehler |
| `app/certificate/boundary/AwardCertificateLogic.kt` | Reine Auswahl-, Filter- und Sortierlogik |
| `app/certificate/boundary/AwardCertificateService.kt` | Datenbeschaffung und Rendering-Anstoß |
| `app/certificate/boundary/awardCertificate.kt` | Routen |
| `frontend/src/components/gapDocumentTemplate/*` | Editor um Schriftattribute erweitern |
| `frontend/src/components/awardCertificate/AwardCertificateDialog.tsx` | Download-Dialog |

---

### Task 1: Datenbank, Views, jOOQ

**Files:**
- Create: `backend/src/main/resources/db/migration/V202608051200__award_certificate_templates.sql`
- Modify: `backend/src/main/resources/db/migration/afterMigrate.sql:1344-1363`

**Interfaces:**
- Consumes: nichts.
- Produces: Spalten `gap_document_placeholder.font_size` (int, nullable), `.bold` (boolean, not null
  default false), `.italic` (boolean, not null default false), `.static_text` (text, nullable);
  `gap_document_template.font_name` (text, nullable); Tabelle `gap_document_template_font(template
  uuid pk, file_name text not null, data bytea not null)`. Die View
  `gap_document_template_assignment` liefert zusätzlich `font_name` und `font_data`, die View
  `gap_document_template_view` zusätzlich `font_name` und `has_font` (boolean). Daraus generiert
  jOOQ `GapDocumentTemplateFontRecord` sowie die erweiterten View-Records.

- [ ] **Step 1: Migration schreiben**

`backend/src/main/resources/db/migration/V202608051200__award_certificate_templates.sql`:

```sql
alter table gap_document_placeholder
    add column font_size   int,
    add column bold        boolean not null default false,
    add column italic      boolean not null default false,
    add column static_text text;

alter table gap_document_template
    add column font_name text;

create table gap_document_template_font
(
    template  uuid primary key references gap_document_template on delete cascade,
    file_name text  not null,
    data      bytea not null
);
```

- [ ] **Step 2: Views in afterMigrate.sql erweitern**

Die beiden `create view`-Anweisungen ab Zeile 1344 ersetzen durch:

```sql
create view gap_document_template_view as
select gdt.id,
       gdt.name,
       gdt.type,
       gdt.font_name,
       (f.template is not null)                                            as has_font,
       coalesce(array_agg(gdp) filter ( where gdp.id is not null ), '{}')   as placeholders
from gap_document_template gdt
         left join gap_document_template_font f on f.template = gdt.id
         left join gap_document_placeholder gdp on gdp.template = gdt.id
group by gdt.id, f.template
;

create view gap_document_template_assignment as
select u.type,
       td.data,
       gdt.font_name,
       f.data                                                            as font_data,
       coalesce(array_agg(gdp) filter ( where gdp.id is not null ), '{}') as placeholders
from gap_document_template_usage u
         join gap_document_template gdt on gdt.id = u.template
         join gap_document_template_data td on gdt.id = td.template
         left join gap_document_template_font f on f.template = gdt.id
         left join gap_document_placeholder gdp on gdt.id = gdp.template
group by u.type, td.data, gdt.font_name, f.data
;
```

Die `drop view`-Zeilen am Anfang der Datei bleiben unverändert, sie deckten beide Views schon ab.

- [ ] **Step 3: Datenbank starten und Migration anwenden**

```bash
cd backend && docker compose up -d
```

Dann:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw jooq:generate
```

Erwartung: Build erfolgreich, `flyway` wendet `V202608051200` an, jOOQ generiert neu.

- [ ] **Step 4: Generierte Klassen prüfen**

```bash
grep -rl "GapDocumentTemplateFontRecord" backend/target/generated-sources/jooq | head -3
grep -n "fontSize\|staticText" backend/target/generated-sources/jooq/de/lambda9/ready2race/backend/database/generated/tables/records/GapDocumentPlaceholderRecord.* | head
```

Erwartung: Die Font-Record-Klasse existiert, der Placeholder-Record hat `fontSize`, `bold`,
`italic`, `staticText`.

Falls `grep` im Placeholder-Record nichts findet, wurde die Migration nicht angewandt — Logs mit
`docker compose logs` prüfen, nicht weitermachen.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration
git commit -m "Add font and text style columns to gap document templates"
```

---

### Task 2: Platzhaltertypen und zentrale Befüllung

Heute wird die Zuordnung Platzhaltertyp → Inhalt an vier Stellen mit je einem erschöpfenden `when`
wiederholt (`CertificateService` drei Mal, `GapDocumentTemplateService.getPreview` einmal). Neue
Typen würden alle vier brechen. Diese Task zieht die Zuordnung in eine reine Funktion.

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapPlaceholder.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapPlaceholderLogic.kt`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/GapPlaceholderLogicTest.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentPlaceholderType.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentType.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/pdf/AdditionalText.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/boundary/CertificateService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt`

**Interfaces:**
- Consumes: die generierten Placeholder-Records aus Task 1. Wichtig: `placeholders` ist auf **beiden**
  View-Records `Array<GapDocumentPlaceholderRecord?>?` — ein Kotlin-Array, keine `List`, und beide Views
  liefern denselben Record-Typ. Deshalb `template.placeholders!!.toList()` vor der Umwandlung, und eine
  einzige `toGapPlaceholder()`-Erweiterung genügt.
- Produces:
  - `GapPlaceholder(type, page, relLeft, relTop, relWidth, relHeight, textAlign, fontSize: Int?, bold: Boolean, italic: Boolean, staticText: String?)` mit `type: GapDocumentPlaceholderType`, `textAlign: TextAlign`.
  - `GapPlaceholderValues` mit den nullbaren Feldern `firstName`, `lastName`, `fullName`, `result`, `eventName`, `place`, `competitionName`, `competitionShortName`, `clubName`, `teamName`, `eventDate`, `eventLocation`.
  - `GapPlaceholderLogic.fill(placeholders: List<GapPlaceholder>, values: GapPlaceholderValues): List<AdditionalText>`
  - `GapDocumentType.allowedPlaceholders: Set<GapDocumentPlaceholderType>`
  - `AdditionalText` zusätzlich mit `fontSize: Float? = null`, `bold: Boolean = false`, `italic: Boolean = false`.

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/GapPlaceholderLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate

import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapPlaceholderLogic
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholder
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
import de.lambda9.ready2race.backend.text.TextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GapPlaceholderLogicTest {

    private fun placeholder(
        type: GapDocumentPlaceholderType,
        fontSize: Int? = null,
        bold: Boolean = false,
        italic: Boolean = false,
        staticText: String? = null,
    ) = GapPlaceholder(
        type = type,
        page = 1,
        relLeft = 0.1,
        relTop = 0.2,
        relWidth = 0.8,
        relHeight = 0.05,
        textAlign = TextAlign.CENTER,
        fontSize = fontSize,
        bold = bold,
        italic = italic,
        staticText = staticText,
    )

    private val values = GapPlaceholderValues(
        firstName = "Carina",
        lastName = "Hein",
        fullName = "Carina Hein",
        result = "33:17,7 min",
        eventName = "Deutsche Coastal Meisterschaften",
        place = "1. Platz",
        competitionName = "CF 1x Frauen-Einer",
        competitionShortName = "CF 1x",
        clubName = "RC Allemannia Hamburg v. 1866",
        teamName = null,
        eventDate = "16.–17. August 2025",
        eventLocation = "Flensburg",
    )

    @Test
    fun everyPlaceholderTypeIsMapped() {
        // Schützt davor, dass ein neuer Platzhaltertyp beim Befüllen vergessen wird.
        val placeholders = GapDocumentPlaceholderType.entries.map { placeholder(it, staticText = "Fest") }
        val filled = GapPlaceholderLogic.fill(placeholders, values)
        assertEquals(GapDocumentPlaceholderType.entries.size, filled.size)
    }

    @Test
    fun contentComesFromValues() {
        val filled = GapPlaceholderLogic.fill(
            listOf(
                placeholder(GapDocumentPlaceholderType.PLACE),
                placeholder(GapDocumentPlaceholderType.COMPETITION_NAME),
                placeholder(GapDocumentPlaceholderType.FULL_NAME),
                placeholder(GapDocumentPlaceholderType.CLUB_NAME),
                placeholder(GapDocumentPlaceholderType.RESULT),
                placeholder(GapDocumentPlaceholderType.EVENT_LOCATION),
                placeholder(GapDocumentPlaceholderType.EVENT_DATE),
            ),
            values,
        )

        assertEquals(
            listOf(
                "1. Platz",
                "CF 1x Frauen-Einer",
                "Carina Hein",
                "RC Allemannia Hamburg v. 1866",
                "33:17,7 min",
                "Flensburg",
                "16.–17. August 2025",
            ),
            filled.map { it.content },
        )
    }

    @Test
    fun missingValueBecomesEmptyString() {
        val filled = GapPlaceholderLogic.fill(
            listOf(placeholder(GapDocumentPlaceholderType.TEAM_NAME)),
            values,
        )
        assertEquals("", filled.single().content)
    }

    @Test
    fun freeTextUsesStaticText() {
        val filled = GapPlaceholderLogic.fill(
            listOf(placeholder(GapDocumentPlaceholderType.FREE_TEXT, staticText = "Moritz Petri – Präsident")),
            values,
        )
        assertEquals("Moritz Petri – Präsident", filled.single().content)
    }

    @Test
    fun styleAttributesArePassedThrough() {
        val filled = GapPlaceholderLogic.fill(
            listOf(placeholder(GapDocumentPlaceholderType.PLACE, fontSize = 20, bold = true, italic = true)),
            values,
        ).single()

        assertEquals(20f, filled.fontSize)
        assertTrue(filled.bold)
        assertTrue(filled.italic)
        assertEquals(TextAlign.CENTER, filled.textAlign)
        assertEquals(1, filled.page)
    }

    @Test
    fun awardCertificateAllowsPlaceAndClub() {
        val allowed = GapDocumentType.AWARD_CERTIFICATE.allowedPlaceholders
        assertTrue(allowed.contains(GapDocumentPlaceholderType.PLACE))
        assertTrue(allowed.contains(GapDocumentPlaceholderType.CLUB_NAME))
    }

    @Test
    fun certificateOfParticipationKeepsItsOriginalPlaceholders() {
        // Die Teilnahmeurkunde soll sich durch die neuen Typen nicht verändern.
        assertEquals(
            setOf(
                GapDocumentPlaceholderType.FIRST_NAME,
                GapDocumentPlaceholderType.LAST_NAME,
                GapDocumentPlaceholderType.FULL_NAME,
                GapDocumentPlaceholderType.RESULT,
                GapDocumentPlaceholderType.EVENT_NAME,
            ),
            GapDocumentType.CERTIFICATE_OF_PARTICIPATION.allowedPlaceholders,
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=GapPlaceholderLogicTest
```

Erwartung: Compile-Fehler, `GapPlaceholder` und `GapPlaceholderLogic` existieren nicht.

- [ ] **Step 3: Platzhaltertypen erweitern**

`GapDocumentPlaceholderType.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate.entity

enum class GapDocumentPlaceholderType {
    FIRST_NAME,
    LAST_NAME,
    FULL_NAME,
    RESULT,
    EVENT_NAME,
    PLACE,
    COMPETITION_NAME,
    COMPETITION_SHORT_NAME,
    CLUB_NAME,
    TEAM_NAME,
    EVENT_DATE,
    EVENT_LOCATION,
    FREE_TEXT,
}
```

- [ ] **Step 4: Dokumenttypen mit erlaubten Platzhaltern**

`GapDocumentType.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate.entity

enum class GapDocumentType(val allowedPlaceholders: Set<GapDocumentPlaceholderType>) {
    CERTIFICATE_OF_PARTICIPATION(
        setOf(
            GapDocumentPlaceholderType.FIRST_NAME,
            GapDocumentPlaceholderType.LAST_NAME,
            GapDocumentPlaceholderType.FULL_NAME,
            GapDocumentPlaceholderType.RESULT,
            GapDocumentPlaceholderType.EVENT_NAME,
        )
    ),
    AWARD_CERTIFICATE(
        setOf(
            GapDocumentPlaceholderType.FIRST_NAME,
            GapDocumentPlaceholderType.LAST_NAME,
            GapDocumentPlaceholderType.FULL_NAME,
            GapDocumentPlaceholderType.RESULT,
            GapDocumentPlaceholderType.EVENT_NAME,
            GapDocumentPlaceholderType.PLACE,
            GapDocumentPlaceholderType.COMPETITION_NAME,
            GapDocumentPlaceholderType.COMPETITION_SHORT_NAME,
            GapDocumentPlaceholderType.CLUB_NAME,
            GapDocumentPlaceholderType.TEAM_NAME,
            GapDocumentPlaceholderType.EVENT_DATE,
            GapDocumentPlaceholderType.EVENT_LOCATION,
            GapDocumentPlaceholderType.FREE_TEXT,
        )
    ),
}
```

- [ ] **Step 5: `GapPlaceholder` und `GapPlaceholderValues` anlegen**

`app/documentTemplate/entity/GapPlaceholder.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.text.TextAlign

/**
 * Platzhalterbeschreibung ohne Bezug zu einem generierten Datenbank-Record, damit das Befüllen
 * rein und ohne Datenbank testbar bleibt.
 */
data class GapPlaceholder(
    val type: GapDocumentPlaceholderType,
    val page: Int,
    val relLeft: Double,
    val relTop: Double,
    val relWidth: Double,
    val relHeight: Double,
    val textAlign: TextAlign,
    val fontSize: Int?,
    val bold: Boolean,
    val italic: Boolean,
    val staticText: String?,
)

data class GapPlaceholderValues(
    val firstName: String? = null,
    val lastName: String? = null,
    val fullName: String? = null,
    val result: String? = null,
    val eventName: String? = null,
    val place: String? = null,
    val competitionName: String? = null,
    val competitionShortName: String? = null,
    val clubName: String? = null,
    val teamName: String? = null,
    val eventDate: String? = null,
    val eventLocation: String? = null,
)
```

- [ ] **Step 6: `GapPlaceholderLogic` implementieren**

`app/documentTemplate/boundary/GapPlaceholderLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholder
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
import de.lambda9.ready2race.backend.pdf.AdditionalText

object GapPlaceholderLogic {

    fun fill(
        placeholders: List<GapPlaceholder>,
        values: GapPlaceholderValues,
    ): List<AdditionalText> = placeholders.map { placeholder ->
        AdditionalText(
            content = content(placeholder, values),
            page = placeholder.page,
            relLeft = placeholder.relLeft,
            relTop = placeholder.relTop,
            relWidth = placeholder.relWidth,
            relHeight = placeholder.relHeight,
            textAlign = placeholder.textAlign,
            fontSize = placeholder.fontSize?.toFloat(),
            bold = placeholder.bold,
            italic = placeholder.italic,
        )
    }

    private fun content(
        placeholder: GapPlaceholder,
        values: GapPlaceholderValues,
    ): String = when (placeholder.type) {
        GapDocumentPlaceholderType.FIRST_NAME -> values.firstName
        GapDocumentPlaceholderType.LAST_NAME -> values.lastName
        GapDocumentPlaceholderType.FULL_NAME -> values.fullName
        GapDocumentPlaceholderType.RESULT -> values.result
        GapDocumentPlaceholderType.EVENT_NAME -> values.eventName
        GapDocumentPlaceholderType.PLACE -> values.place
        GapDocumentPlaceholderType.COMPETITION_NAME -> values.competitionName
        GapDocumentPlaceholderType.COMPETITION_SHORT_NAME -> values.competitionShortName ?: values.competitionName
        GapDocumentPlaceholderType.CLUB_NAME -> values.clubName
        GapDocumentPlaceholderType.TEAM_NAME -> values.teamName
        GapDocumentPlaceholderType.EVENT_DATE -> values.eventDate
        GapDocumentPlaceholderType.EVENT_LOCATION -> values.eventLocation
        GapDocumentPlaceholderType.FREE_TEXT -> placeholder.staticText
    } ?: ""
}
```

- [ ] **Step 7: `AdditionalText` erweitern**

`backend/pdf/AdditionalText.kt`:

```kotlin
package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.TextAlign

data class AdditionalText(
    val content: String,
    val page: Int,
    val relLeft: Double,
    val relTop: Double,
    val relWidth: Double,
    val relHeight: Double,
    val textAlign: TextAlign,
    val fontSize: Float? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
)
```

- [ ] **Step 8: Test laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=GapPlaceholderLogicTest
```

Erwartung: alle sieben Tests grün.

- [ ] **Step 9: Umwandlung der Records ergänzen**

In `app/documentTemplate/control/Conversions.kt` am Ende ergänzen. Die beiden Views aggregieren
denselben Composite-Typ; welcher generierte Typ in `placeholders` steckt, zeigt der Aufrufcode in
`CertificateService`. Für den generierten Placeholder-Record:

```kotlin
fun GapDocumentPlaceholderRecord.toGapPlaceholder() =
    GapPlaceholder(
        type = GapDocumentPlaceholderType.valueOf(type),
        page = page,
        relLeft = relLeft,
        relTop = relTop,
        relWidth = relWidth,
        relHeight = relHeight,
        textAlign = TextAlign.valueOf(textAlign),
        fontSize = fontSize,
        bold = bold,
        italic = italic,
        staticText = staticText,
    )

/**
 * Platzhalter eines Vorlagen-Datensatzes in die datenbankfreie Form bringen. Unbekannte
 * Platzhaltertypen werden übersprungen, damit eine Vorlage nach einem Enum-Umbau nicht bricht.
 */
fun List<GapDocumentPlaceholderRecord?>.toGapPlaceholders(): List<GapPlaceholder> =
    mapNotNull { record ->
        try {
            record?.toGapPlaceholder()
        } catch (ex: IllegalArgumentException) {
            null
        }
    }
```

Nötige Importe ergänzen: `GapPlaceholder`, `GapDocumentPlaceholderType`, `TextAlign` ist bereits
importiert.

Sollte der Compiler melden, dass `placeholders` in einer der beiden Views einen anderen
Record-Typ hat, eine zweite, gleich aufgebaute `toGapPlaceholder()`-Erweiterung für diesen Typ
anlegen — nicht die Aufrufer umbauen.

- [ ] **Step 10: `CertificateService` auf die zentrale Befüllung umstellen**

In `app/certificate/boundary/CertificateService.kt` alle drei `template.placeholders!!.mapNotNull {
… }`-Blöcke ersetzen. Beispiel für `downloadCertificateOfParticipation` (Zeilen 178–204), die beiden
anderen Stellen analog mit den dort vorhandenen Variablen:

```kotlin
        val bytes = participantForEvent(
            additions = GapPlaceholderLogic.fill(
                placeholders = template.placeholders!!.toList().toGapPlaceholders(),
                values = GapPlaceholderValues(
                    firstName = participant.firstname,
                    lastName = participant.lastname,
                    fullName = "${participant.firstname} ${participant.lastname}",
                    result = "$resultTotal $resultUnit",
                    eventName = event.name,
                ),
            ),
            template = template.data!!,
        )
```

Danach sind die Importe `GapDocumentPlaceholderType`, `AdditionalText` und `TextAlign` in dieser
Datei ungenutzt und werden entfernt; `java.lang.Exception` ebenfalls, falls kein `try` mehr übrig
ist.

- [ ] **Step 11: Vorschau im `GapDocumentTemplateService` umstellen**

In `app/documentTemplate/boundary/GapDocumentTemplateService.kt` die Funktion `getPreview`
(Zeilen 127–172) ersetzen:

```kotlin
    fun getPreview(
        id: UUID
    ): App<GapDocumentTemplateError, ApiResponse.File> = KIO.comprehension {

        val templateBytes =
            !GapDocumentTemplateDataRepo.getData(id).orDie().onNullFail { GapDocumentTemplateError.NotFound }
        val template = !GapDocumentTemplateRepo.get(id).orDie().onNullDie("foreign key constraint")

        val bytes = CertificateService.participantForEvent(
            additions = GapPlaceholderLogic.fill(
                placeholders = template.placeholders!!.toList().toGapPlaceholders(),
                values = previewValues,
            ),
            template = templateBytes,
        )

        KIO.ok(
            ApiResponse.File(
                name = "sample.pdf",
                bytes = bytes,
            )
        )
    }

    private val previewValues = GapPlaceholderValues(
        firstName = "Max",
        lastName = "Mustermann",
        fullName = "Max Mustermann",
        result = "3492 m",
        eventName = "Summer Sport Festival",
        place = "1. Platz",
        competitionName = "CF 1x Frauen-Einer",
        competitionShortName = "CF 1x",
        clubName = "Ruderklub Flensburg",
        teamName = "Flensburg I",
        eventDate = "16.–17. August 2026",
        eventLocation = "Flensburg",
    )
```

Der bisherige `when (type)`-Block über `GapDocumentType` entfällt damit; die Variable `type` und die
Importe `GapDocumentType`, `GapDocumentPlaceholderType`, `AdditionalText`, `TextAlign`,
`java.lang.Exception` werden entfernt, sofern nicht anderweitig genutzt.

- [ ] **Step 12: Gesamten Testlauf und Compile prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build
```

Erwartung: Build erfolgreich, alle Tests grün.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin
git commit -m "Centralize gap document placeholder filling and add award certificate placeholders"
```

---

### Task 3: PDF-Renderer für Urkundenserien

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/pdf/documents.kt:15-70`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/pdf/GapDocumentsTest.kt`

**Interfaces:**
- Consumes: `AdditionalText` mit `fontSize`, `bold`, `italic` aus Task 2.
- Produces:
  - `fun gapDocuments(template: ByteArray, font: ByteArray?, withBackground: Boolean, pages: List<List<AdditionalText>>): PDDocument`
  - `fun document(original: ByteArray, additions: List<AdditionalText>): PDDocument` bleibt bestehen und delegiert (Signatur unverändert).

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/pdf/GapDocumentsTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.TextAlign
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GapDocumentsTest {

    /** Einseitige A4-Vorlage mit einem erkennbaren Text, der nur aus dem Design stammt. */
    private fun templateBytes(): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val content = PDPageContentStream(doc, page)
        content.beginText()
        content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
        content.newLineAtOffset(50f, 50f)
        content.showText("DESIGN")
        content.endText()
        content.close()

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun addition(content: String, relTop: Double, fontSize: Float? = 20f) = AdditionalText(
        content = content,
        page = 1,
        relLeft = 0.0,
        relTop = relTop,
        relWidth = 1.0,
        relHeight = 0.05,
        textAlign = TextAlign.CENTER,
        fontSize = fontSize,
    )

    private fun text(doc: PDDocument, page: Int): String {
        val stripper = PDFTextStripper()
        stripper.startPage = page
        stripper.endPage = page
        return stripper.getText(doc)
    }

    @Test
    fun onePagePerCertificate() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(
                listOf(addition("1. Platz", 0.45), addition("Carina Hein", 0.5)),
                listOf(addition("2. Platz", 0.45), addition("Malte Hein", 0.5)),
            ),
        )

        assertEquals(2, doc.numberOfPages)
        assertTrue(text(doc, 1).contains("Carina Hein"))
        assertTrue(text(doc, 2).contains("Malte Hein"))
        doc.close()
    }

    @Test
    fun withoutBackgroundTheDesignIsAbsent() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45))),
        )

        assertFalse(text(doc, 1).contains("DESIGN"))
        assertTrue(text(doc, 1).contains("1. Platz"))
        doc.close()
    }

    @Test
    fun withBackgroundTheDesignIsPresent() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = true,
            pages = listOf(listOf(addition("1. Platz", 0.45))),
        )

        val content = text(doc, 1)
        assertTrue(content.contains("DESIGN"))
        assertTrue(content.contains("1. Platz"))
        doc.close()
    }

    @Test
    fun pageFormatMatchesTheTemplate() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45))),
        )

        assertEquals(PDRectangle.A4.width, doc.getPage(0).mediaBox.width)
        assertEquals(PDRectangle.A4.height, doc.getPage(0).mediaBox.height)
        doc.close()
    }

    @Test
    fun multipleLinesAreRenderedSeparately() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("Carina Hein\nMalte Hein", 0.45))),
        )

        val lines = text(doc, 1).lines().filter { it.isNotBlank() }
        assertEquals(listOf("Carina Hein", "Malte Hein"), lines)
        doc.close()
    }

    @Test
    fun boldAndItalicDoNotBreakRendering() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(
                listOf(
                    addition("1. Platz", 0.45).copy(bold = true),
                    addition("33:17,7 min", 0.55).copy(italic = true),
                )
            ),
        )

        val content = text(doc, 1)
        assertTrue(content.contains("1. Platz"))
        assertTrue(content.contains("33:17,7 min"))
        doc.close()
    }

    @Test
    fun existingSingleDocumentApiStillWorks() {
        // Rückwärtskompatibilität: die Teilnahmeurkunde nutzt weiterhin document(original, additions)
        // und erwartet das Design auf der Seite.
        val doc = document(
            original = templateBytes(),
            additions = listOf(addition("Max Mustermann", 0.45, fontSize = null)),
        )

        assertEquals(1, doc.numberOfPages)
        val content = text(doc, 1)
        assertTrue(content.contains("DESIGN"))
        assertTrue(content.contains("Max Mustermann"))
        doc.close()
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=GapDocumentsTest
```

Erwartung: Compile-Fehler, `gapDocuments` existiert nicht.

- [ ] **Step 3: Renderer implementieren**

In `backend/pdf/documents.kt` die bestehende Funktion `document(original, additions)` (Zeilen 15–70)
ersetzen durch:

```kotlin
private class GapFonts(
    val regular: PDFont,
    val bold: PDFont,
    val italic: PDFont,
    val boldItalic: PDFont,
    /** Bei einer eingebetteten Vorlagenschrift gibt es nur einen Schnitt; Fett und Kursiv werden simuliert. */
    val synthesizeStyles: Boolean,
) {
    fun forStyle(bold: Boolean, italic: Boolean): PDFont = when {
        bold && italic -> boldItalic
        bold -> this.bold
        italic -> this.italic
        else -> regular
    }

    companion object {
        fun load(doc: PDDocument, font: ByteArray?): GapFonts {
            if (font != null) {
                val embedded = PDType0Font.load(doc, font.inputStream())
                return GapFonts(embedded, embedded, embedded, embedded, synthesizeStyles = true)
            }

            return GapFonts(
                regular = PDType1Font(Standard14Fonts.FontName.HELVETICA),
                bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                italic = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE),
                boldItalic = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE),
                synthesizeStyles = false,
            )
        }
    }
}

private fun drawAddition(
    doc: PDDocument,
    page: PDPage,
    addition: AdditionalText,
    fonts: GapFonts,
) {
    val content = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)

    val w = (page.mediaBox.width * addition.relWidth).toFloat()
    val h = (page.mediaBox.height * addition.relHeight).toFloat()
    val x = (page.mediaBox.width * addition.relLeft).toFloat()
    val y = (page.mediaBox.height * (1 - addition.relTop) - h).toFloat()

    val fontSize = addition.fontSize ?: h
    val font = fonts.forStyle(addition.bold, addition.italic)

    content.setFont(font, fontSize)
    content.setNonStrokingColor(Color.DARK_GRAY)

    // Ohne echte Schnitte werden Fett und Kursiv nachgebildet: Fett über einen dünnen Rand um die
    // Glyphen, Kursiv über eine Schrägstellung der Textmatrix.
    val synthesizeBold = fonts.synthesizeStyles && addition.bold
    val shear = if (fonts.synthesizeStyles && addition.italic) 0.25f else 0f

    if (synthesizeBold) {
        content.setRenderingMode(RenderingMode.FILL_STROKE)
        content.setStrokingColor(Color.DARK_GRAY)
        content.setLineWidth(fontSize * 0.02f)
    }

    val capHeight = fontSize * font.fontDescriptor.capHeight / 1000
    val lineHeight = fontSize * 1.2f
    val lines = addition.content.split("\n").map { it.sanitizeNonPrintable() }
    val blockTop = y + h / 2 + lineHeight * lines.size / 2

    lines.forEachIndexed { index, line ->
        val textWidth = font.getStringWidth(line) / 1000 * fontSize
        val xOffset = when (addition.textAlign) {
            TextAlign.LEFT -> x
            TextAlign.CENTER -> x + (w - textWidth) / 2
            TextAlign.RIGHT -> x + w - textWidth
        }
        val baseline = blockTop - lineHeight * index - (lineHeight + capHeight) / 2

        content.beginText()
        content.setTextMatrix(Matrix(1f, 0f, shear, 1f, xOffset, baseline))
        content.showText(line)
        content.endText()
    }

    content.close()
}

/**
 * Erzeugt eine Serie: eine Seite je Eintrag in [pages], im Seitenformat der Vorlage.
 *
 * @param withBackground legt die Vorlagenseite als Layer unter den Text. Für den Druck auf
 * vorgedrucktes Papier bleibt das aus, sonst läge das Design doppelt auf dem Blatt.
 * @param font optionale Schriftdatei (TTF/OTF), die eingebettet wird; ohne sie wird Helvetica genutzt.
 */
fun gapDocuments(
    template: ByteArray,
    font: ByteArray?,
    withBackground: Boolean,
    pages: List<List<AdditionalText>>,
): PDDocument {
    val templateDoc = Loader.loadPDF(template)
    val templatePage = templateDoc.getPage(0)
    val format = templatePage.mediaBox

    val result = PDDocument()
    val fonts = GapFonts.load(result, font)

    val layerUtil = if (withBackground) LayerUtility(result) else null
    val templateForm = layerUtil?.importPageAsForm(templateDoc, templatePage)

    pages.forEachIndexed { index, additions ->
        val page = PDPage(format)
        result.addPage(page)

        if (layerUtil != null && templateForm != null) {
            layerUtil.appendFormAsLayer(page, templateForm, AffineTransform(), "template-layer-$index")
        }

        additions.filter { it.page == 1 }.forEach { drawAddition(result, page, it, fonts) }
    }

    templateDoc.close()

    return result
}

/**
 * Befüllt die Vorlage selbst — eine Urkunde, Design inklusive. Wird von der Teilnahmeurkunde genutzt.
 */
fun document(
    original: ByteArray,
    additions: List<AdditionalText>,
): PDDocument {

    val pdf = Loader.loadPDF(original)
    val fonts = GapFonts.load(pdf, null)

    additions.forEach { addition ->
        if (addition.page > pdf.numberOfPages) {
            return@forEach
        }
        drawAddition(pdf, pdf.getPage(addition.page - 1), addition, fonts)
    }

    return pdf
}
```

Importe in `documents.kt` ergänzen:

```kotlin
import de.lambda9.ready2race.backend.text.TextAlign
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.PDPageContentStream.RenderingMode
import org.apache.pdfbox.util.Matrix
```

Der bisherige vollqualifizierte Zugriff `de.lambda9.ready2race.backend.text.TextAlign.LEFT` entfällt
durch den Import.

- [ ] **Step 4: Test laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=GapDocumentsTest
```

Erwartung: alle sieben Tests grün. Schlägt `multipleLinesAreRenderedSeparately` fehl, weil
`PDFTextStripper` beide Namen in eine Zeile legt, ist der Zeilenabstand zu klein — `lineHeight`
prüfen, nicht den Test aufweichen.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/pdf backend/src/test/kotlin/de/lambda9/ready2race/backend/pdf
git commit -m "Render gap document series with font styles and embedded fonts"
```

---

### Task 4: DOCX-Renderer

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/docx/GapDocumentsDocx.kt`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/docx/GapDocumentsDocxTest.kt`

**Interfaces:**
- Consumes: `AdditionalText` aus Task 2.
- Produces:
  - `fun gapDocumentsDocx(pageWidthPoints: Float, pageHeightPoints: Float, fontName: String?, pages: List<List<AdditionalText>>): XWPFDocument`
  - `fun XWPFDocument.toByteArray(): ByteArray`

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/docx/GapDocumentsDocxTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.docx

import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.text.TextAlign
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.pdfbox.pdmodel.common.PDRectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GapDocumentsDocxTest {

    private fun addition(
        content: String,
        relTop: Double,
        bold: Boolean = false,
        italic: Boolean = false,
    ) = AdditionalText(
        content = content,
        page = 1,
        relLeft = 0.0,
        relTop = relTop,
        relWidth = 1.0,
        relHeight = 0.05,
        textAlign = TextAlign.CENTER,
        fontSize = 20f,
        bold = bold,
        italic = italic,
    )

    private fun doc(pages: List<List<AdditionalText>>) = gapDocumentsDocx(
        pageWidthPoints = PDRectangle.A4.width,
        pageHeightPoints = PDRectangle.A4.height,
        fontName = "TheSansOffice",
        pages = pages,
    )

    @Test
    fun pageSizeIsTakenFromTheTemplateInTwips() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45))))
        val pgSz = document.document.body.sectPr.pgSz

        // A4 = 595.27563 x 841.8898 pt, 1 pt = 20 twips, gerundet: 11906 x 16838
        assertEquals(11906L, pgSz.w.toString().toLong())
        assertEquals(16838L, pgSz.h.toString().toLong())
        document.close()
    }

    @Test
    fun everyPlaceholderBecomesAFramedParagraph() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45), addition("Carina Hein", 0.5))))

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(2, framed.size)
        assertEquals(listOf("1. Platz", "Carina Hein"), framed.map { it.text })
        document.close()
    }

    @Test
    fun frameIsAnchoredToThePageAtTheRelativePosition() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45))))

        val frame = document.paragraphs.first { it.ctp.pPr?.framePr != null }.ctp.pPr.framePr
        assertNotNull(frame)
        // Kastenoberkante = 0.45 * 841.8898 = 378.85 pt, Kastenhöhe = 0.05 * 841.8898 = 42.09 pt,
        // Zeilenhöhe = 20 pt * 1.2 = 24 pt. Eine Zeile, senkrecht zentriert:
        // 378.85 + (42.09 - 24) / 2 = 387.90 pt -> 7758 Twips. Rahmenhöhe = 24 pt -> 480 Twips.
        assertEquals(0L, frame.x.toString().toLong())
        assertEquals(7758L, frame.y.toString().toLong())
        assertEquals(11906L, frame.w.toString().toLong())
        assertEquals(480L, frame.h.toString().toLong())
        document.close()
    }

    @Test
    fun runCarriesFontNameSizeAndStyle() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45, bold = true, italic = true))))

        val run = document.paragraphs.first { it.ctp.pPr?.framePr != null }.runs.first()
        assertEquals("TheSansOffice", run.fontFamily)
        assertEquals(20, run.fontSize)
        assertTrue(run.isBold)
        assertTrue(run.isItalic)
        document.close()
    }

    @Test
    fun alignmentIsTakenFromTextAlign() {
        val document = gapDocumentsDocx(
            pageWidthPoints = PDRectangle.A4.width,
            pageHeightPoints = PDRectangle.A4.height,
            fontName = null,
            pages = listOf(
                listOf(
                    addition("links", 0.4).copy(textAlign = TextAlign.LEFT),
                    addition("mitte", 0.5).copy(textAlign = TextAlign.CENTER),
                    addition("rechts", 0.6).copy(textAlign = TextAlign.RIGHT),
                )
            ),
        )

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(
            listOf(ParagraphAlignment.LEFT, ParagraphAlignment.CENTER, ParagraphAlignment.RIGHT),
            framed.map { it.alignment },
        )
        document.close()
    }

    @Test
    fun certificatesAreSeparatedByPageBreaks() {
        val document = doc(
            listOf(
                listOf(addition("1. Platz", 0.45)),
                listOf(addition("2. Platz", 0.45)),
                listOf(addition("3. Platz", 0.45)),
            )
        )

        val breaks = document.paragraphs.sumOf { paragraph ->
            paragraph.runs.sumOf { run -> run.ctr.brList.count { it.type?.toString() == "page" } }
        }
        // Zwei Umbrüche für drei Urkunden.
        assertEquals(2, breaks)
        document.close()
    }

    @Test
    fun multipleLinesBecomeStackedFramedParagraphs() {
        val document = doc(listOf(listOf(addition("Carina Hein\nMalte Hein", 0.45))))

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(listOf("Carina Hein", "Malte Hein"), framed.map { it.text })

        // Die zweite Zeile sitzt genau eine Zeilenhöhe (24 pt = 480 Twips) unter der ersten.
        val ys = framed.map { it.ctp.pPr.framePr.y.toString().toLong() }
        assertEquals(480L, ys[1] - ys[0])
        document.close()
    }

    @Test
    fun documentCanBeWrittenAndReadBack() {
        val bytes = doc(listOf(listOf(addition("1. Platz", 0.45)))).toByteArray()

        val reopened = XWPFDocument(bytes.inputStream())
        assertTrue(reopened.paragraphs.any { it.text == "1. Platz" })
        reopened.close()
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=GapDocumentsDocxTest
```

Erwartung: Compile-Fehler, `gapDocumentsDocx` existiert nicht.

- [ ] **Step 3: Renderer implementieren**

`backend/src/main/kotlin/de/lambda9/ready2race/backend/docx/GapDocumentsDocx.kt`:

```kotlin
package de.lambda9.ready2race.backend.docx

import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.ready2race.backend.text.sanitizeNonPrintable
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHAnchor
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHeightRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVAnchor
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STWrap
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import kotlin.math.roundToLong

/** Word rechnet in Twips: 1 Punkt = 20 Twips. */
private const val TWIPS_PER_POINT = 20f

private fun twips(points: Float): BigInteger =
    BigInteger.valueOf(points.times(TWIPS_PER_POINT).roundToLong())

/**
 * Erzeugt eine Urkundenserie als Word-Dokument: eine Seite je Eintrag in [pages], jeder Platzhalter
 * als absolut positionierter Textrahmen (`w:framePr`) an derselben Stelle wie im PDF.
 *
 * Ein Hintergrundbild wird bewusst nicht gesetzt — gedruckt wird auf vorgedrucktes Papier.
 */
fun gapDocumentsDocx(
    pageWidthPoints: Float,
    pageHeightPoints: Float,
    fontName: String?,
    pages: List<List<AdditionalText>>,
): XWPFDocument {
    val document = XWPFDocument()

    val sectPr = document.document.body.addNewSectPr()
    val pgSz = sectPr.addNewPgSz()
    pgSz.w = twips(pageWidthPoints)
    pgSz.h = twips(pageHeightPoints)
    val pgMar = sectPr.addNewPgMar()
    pgMar.top = BigInteger.ZERO
    pgMar.bottom = BigInteger.ZERO
    pgMar.left = BigInteger.ZERO
    pgMar.right = BigInteger.ZERO
    pgMar.header = BigInteger.ZERO
    pgMar.footer = BigInteger.ZERO
    pgMar.gutter = BigInteger.ZERO

    pages.forEachIndexed { pageIndex, additions ->
        // Ein normaler Absatz trägt den Seitenumbruch und verankert den Textfluss auf dieser Seite;
        // die gerahmten Absätze werden daraus herausgelöst und absolut positioniert.
        val anchor = document.createParagraph()
        val anchorRun = anchor.createRun()
        if (pageIndex > 0) {
            anchorRun.addBreak(BreakType.PAGE)
        }

        additions.filter { it.page == 1 }.forEach { addition ->
            val lines = addition.content.split("\n").map { it.sanitizeNonPrintable() }

            // Wie im PDF wird der Textblock senkrecht im Platzhalterkasten zentriert, damit beide
            // Formate dieselbe Stelle auf dem Papier treffen.
            val boxTop = pageHeightPoints * addition.relTop.toFloat()
            val boxHeight = pageHeightPoints * addition.relHeight.toFloat()
            val lineHeight = (addition.fontSize ?: boxHeight) * 1.2f
            val blockTop = boxTop + (boxHeight - lineHeight * lines.size) / 2

            lines.forEachIndexed { lineIndex, line ->
                val paragraph = document.createParagraph()
                paragraph.alignment = when (addition.textAlign) {
                    TextAlign.LEFT -> ParagraphAlignment.LEFT
                    TextAlign.CENTER -> ParagraphAlignment.CENTER
                    TextAlign.RIGHT -> ParagraphAlignment.RIGHT
                }

                // Jede Zeile erhält ihren eigenen Rahmen, eine Zeile hoch. Dadurch braucht Word
                // keinen Zeilenumbruch zu berechnen und die Zeilen sitzen exakt wie im PDF.
                applyFrame(
                    paragraph = paragraph,
                    xPoints = pageWidthPoints * addition.relLeft.toFloat(),
                    yPoints = blockTop + lineHeight * lineIndex,
                    widthPoints = pageWidthPoints * addition.relWidth.toFloat(),
                    heightPoints = lineHeight,
                )

                val run = paragraph.createRun()
                run.setText(line)
                fontName?.let { run.fontFamily = it }
                addition.fontSize?.let { run.fontSize = it.toInt() }
                run.isBold = addition.bold
                run.isItalic = addition.italic
            }
        }
    }

    return document
}

private fun applyFrame(
    paragraph: XWPFParagraph,
    xPoints: Float,
    yPoints: Float,
    widthPoints: Float,
    heightPoints: Float,
) {
    val pPr = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
    val frame = pPr.framePr ?: pPr.addNewFramePr()
    frame.x = twips(xPoints)
    frame.y = twips(yPoints)
    frame.w = twips(widthPoints)
    frame.h = twips(heightPoints)
    frame.hRule = STHeightRule.AT_LEAST
    frame.hAnchor = STHAnchor.PAGE
    frame.vAnchor = STVAnchor.PAGE
    frame.wrap = STWrap.NOT_BESIDE
}

fun XWPFDocument.toByteArray(): ByteArray {
    val out = ByteArrayOutputStream()
    write(out)
    val bytes = out.toByteArray()
    out.close()
    return bytes
}
```

Hinweis zu POI: einige XmlBeans-Setter nehmen `Object` statt `BigInteger`. `BigInteger` passt in
beiden Fällen. Meldet der Compiler bei `pgSz.w` oder `frame.x` einen Typkonflikt, den Wert weiterhin
als `BigInteger` übergeben und nur den deklarierten Parametertyp beachten — keine Strings setzen.

- [ ] **Step 4: Test laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=GapDocumentsDocxTest
```

Erwartung: alle acht Tests grün.

- [ ] **Step 5: Sichtprüfung mit LibreOffice**

Ein Testlauf schreibt kein Artefakt; für die Sichtprüfung einmalig ein Dokument erzeugen und
rendern:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=GapDocumentsDocxTest
soffice --headless --convert-to pdf --outdir /tmp/docxcheck backend/testOutputs/*.docx 2>/dev/null || true
```

Falls kein `.docx` in `testOutputs` liegt, im Test `documentCanBeWrittenAndReadBack` zusätzlich
`java.io.File("testOutputs/urkunden.docx").writeBytes(bytes)` ergänzen, Test erneut laufen lassen,
konvertieren und das PDF ansehen: Die fünf Zeilen müssen mittig auf der Seite stehen, nicht
übereinander und nicht am Seitenanfang.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/docx backend/src/test/kotlin/de/lambda9/ready2race/backend/docx
git commit -m "Add DOCX renderer for gap document series"
```

---

### Task 5: Auswahl- und Sortierlogik der Urkunden

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/entity/AwardCertificate.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/boundary/AwardCertificateLogic.kt`
- Create: `backend/src/test/kotlin/de/lambda9/ready2race/backend/app/certificate/AwardCertificateLogicTest.kt`

**Interfaces:**
- Consumes: nichts aus früheren Tasks.
- Produces:
  - `enum class AwardCertificateMode { PER_ATHLETE, PER_TEAM }`
  - `data class AwardCertificateOptions(val maxPlace: Int, val mode: AwardCertificateMode, val withBackground: Boolean)`
  - `data class AwardCertificateTeam(val place: Int, val clubName: String, val teamName: String?, val result: String?, val startNumber: Int, val excluded: Boolean, val participants: List<AwardCertificateParticipant>, val registrationId: UUID)`
  - `data class AwardCertificateParticipant(val firstName: String, val lastName: String, val role: String)`
  - `data class AwardCertificateEntry(val place: Int, val competitionIdentifier: String, val competitionName: String, val competitionShortName: String?, val clubName: String, val teamName: String?, val result: String?, val names: List<String>, val registrationId: UUID)`
  - `AwardCertificateLogic.entriesForCompetition(competitionIdentifier: String, competitionName: String, competitionShortName: String?, teams: List<AwardCertificateTeam>, options: AwardCertificateOptions): List<AwardCertificateEntry>`
  - `AwardCertificateLogic.formatPlace(place: Int): String`
  - `AwardCertificateLogic.formatEventDate(days: List<LocalDate>): String`

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/lambda9/ready2race/backend/app/certificate/AwardCertificateLogicTest.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.certificate

import de.lambda9.ready2race.backend.app.certificate.boundary.AwardCertificateLogic
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateParticipant
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateTeam
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AwardCertificateLogicTest {

    private fun participant(firstName: String, lastName: String, role: String = "Ruderer") =
        AwardCertificateParticipant(firstName = firstName, lastName = lastName, role = role)

    private fun team(
        place: Int,
        clubName: String = "RC Allemannia Hamburg v. 1866",
        teamName: String? = null,
        result: String? = "33:17,7 min",
        startNumber: Int = place,
        excluded: Boolean = false,
        participants: List<AwardCertificateParticipant> = listOf(participant("Carina", "Hein")),
    ) = AwardCertificateTeam(
        place = place,
        clubName = clubName,
        teamName = teamName,
        result = result,
        startNumber = startNumber,
        excluded = excluded,
        participants = participants,
        registrationId = UUID.randomUUID(),
    )

    private fun options(
        maxPlace: Int = 3,
        mode: AwardCertificateMode = AwardCertificateMode.PER_ATHLETE,
    ) = AwardCertificateOptions(maxPlace = maxPlace, mode = mode, withBackground = false)

    private fun entries(
        teams: List<AwardCertificateTeam>,
        options: AwardCertificateOptions = options(),
    ) = AwardCertificateLogic.entriesForCompetition(
        competitionIdentifier = "1",
        competitionName = "CF 1x Frauen-Einer",
        competitionShortName = "CF 1x",
        teams = teams,
        options = options,
    )

    @Test
    fun placesBeyondTheLimitAreDropped() {
        val result = entries(listOf(team(1), team(2), team(3), team(4)))
        assertEquals(listOf(1, 2, 3), result.map { it.place })
    }

    @Test
    fun allPlacesArePossible() {
        val result = entries(listOf(team(1), team(2), team(3), team(4)), options(maxPlace = 99))
        assertEquals(listOf(1, 2, 3, 4), result.map { it.place })
    }

    @Test
    fun excludedTeamsGetNoCertificate() {
        val result = entries(listOf(team(1), team(2, excluded = true), team(3)))
        assertEquals(listOf(1, 3), result.map { it.place })
    }

    @Test
    fun perAthleteYieldsOneEntryPerParticipant() {
        val result = entries(
            listOf(
                team(
                    place = 1,
                    participants = listOf(
                        participant("Carina", "Hein"),
                        participant("Malte", "Hein"),
                        participant("Jonas", "Meier", role = "Steuermann"),
                    ),
                )
            )
        )

        assertEquals(3, result.size)
        assertEquals(listOf("Carina Hein"), result[0].names)
        assertEquals(listOf("Jonas Meier"), result[2].names)
        assertTrue(result.all { it.place == 1 })
    }

    @Test
    fun perTeamYieldsOneEntryWithAllNames() {
        val result = entries(
            listOf(
                team(
                    place = 1,
                    participants = listOf(participant("Carina", "Hein"), participant("Malte", "Hein")),
                )
            ),
            options(mode = AwardCertificateMode.PER_TEAM),
        )

        assertEquals(1, result.size)
        assertEquals(listOf("Carina Hein", "Malte Hein"), result.single().names)
    }

    @Test
    fun entriesAreSortedByPlaceThenStartNumber() {
        val result = entries(
            listOf(
                team(place = 2, startNumber = 7, clubName = "Startnummer 7"),
                team(place = 1, startNumber = 4, clubName = "Startnummer 4"),
                team(place = 2, startNumber = 3, clubName = "Startnummer 3"),
            )
        )

        assertEquals(listOf(1, 2, 2), result.map { it.place })
        assertEquals(
            listOf("Startnummer 4", "Startnummer 3", "Startnummer 7"),
            result.map { it.clubName },
        )
    }

    @Test
    fun competitionDataIsCarriedOver() {
        val result = entries(listOf(team(1, teamName = "Flensburg I"))).single()
        assertEquals("1", result.competitionIdentifier)
        assertEquals("CF 1x", result.competitionShortName)
        assertEquals("Flensburg I", result.teamName)
        assertEquals("33:17,7 min", result.result)
        assertEquals("RC Allemannia Hamburg v. 1866", result.clubName)
    }

    @Test
    fun missingResultStaysNull() {
        val result = entries(listOf(team(1, result = null))).single()
        assertEquals(null, result.result)
    }

    @Test
    fun placeIsFormattedGerman() {
        assertEquals("1. Platz", AwardCertificateLogic.formatPlace(1))
        assertEquals("12. Platz", AwardCertificateLogic.formatPlace(12))
    }

    @Test
    fun eventDateIsFormattedAsRange() {
        assertEquals(
            "16.–17. August 2025",
            AwardCertificateLogic.formatEventDate(
                listOf(LocalDate.of(2025, 8, 16), LocalDate.of(2025, 8, 17))
            ),
        )
    }

    @Test
    fun singleEventDayHasNoRange() {
        assertEquals(
            "16. August 2025",
            AwardCertificateLogic.formatEventDate(listOf(LocalDate.of(2025, 8, 16))),
        )
    }

    @Test
    fun eventDateAcrossMonthsSpellsBothMonths() {
        assertEquals(
            "31. Juli – 1. August 2025",
            AwardCertificateLogic.formatEventDate(
                listOf(LocalDate.of(2025, 7, 31), LocalDate.of(2025, 8, 1))
            ),
        )
    }

    @Test
    fun noEventDaysYieldsEmptyString() {
        assertEquals("", AwardCertificateLogic.formatEventDate(emptyList()))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag prüfen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=AwardCertificateLogicTest
```

Erwartung: Compile-Fehler, die Typen existieren nicht.

- [ ] **Step 3: Entitäten anlegen**

`app/certificate/entity/AwardCertificate.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.certificate.entity

import java.util.UUID

enum class AwardCertificateMode {
    PER_ATHLETE,
    PER_TEAM,
}

data class AwardCertificateOptions(
    val maxPlace: Int,
    val mode: AwardCertificateMode,
    val withBackground: Boolean,
) {
    companion object {
        const val DEFAULT_MAX_PLACE = 3
    }
}

data class AwardCertificateParticipant(
    val firstName: String,
    val lastName: String,
    val role: String,
)

/** Ein platziertes Boot, aufbereitet aus der Platzierungsberechnung. */
data class AwardCertificateTeam(
    val place: Int,
    val clubName: String,
    val teamName: String?,
    val result: String?,
    val startNumber: Int,
    /** abgemeldet, ausgeschieden oder disqualifiziert */
    val excluded: Boolean,
    val participants: List<AwardCertificateParticipant>,
    val registrationId: UUID,
)

/** Eine einzelne Urkunde, also genau eine Seite. */
data class AwardCertificateEntry(
    val place: Int,
    val competitionIdentifier: String,
    val competitionName: String,
    val competitionShortName: String?,
    val clubName: String,
    val teamName: String?,
    val result: String?,
    val names: List<String>,
    val registrationId: UUID,
)
```

- [ ] **Step 4: Logik implementieren**

`app/certificate/boundary/AwardCertificateLogic.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateEntry
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateTeam
import java.time.LocalDate

object AwardCertificateLogic {

    private val germanMonths = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )

    fun entriesForCompetition(
        competitionIdentifier: String,
        competitionName: String,
        competitionShortName: String?,
        teams: List<AwardCertificateTeam>,
        options: AwardCertificateOptions,
    ): List<AwardCertificateEntry> = teams
        .filter { !it.excluded && it.place <= options.maxPlace }
        .sortedWith(compareBy({ it.place }, { it.startNumber }))
        .flatMap { team ->
            val names = team.participants.map { "${it.firstName} ${it.lastName}" }

            val nameGroups = when (options.mode) {
                AwardCertificateMode.PER_ATHLETE -> names.map { listOf(it) }
                AwardCertificateMode.PER_TEAM -> listOf(names)
            }

            nameGroups.map { group ->
                AwardCertificateEntry(
                    place = team.place,
                    competitionIdentifier = competitionIdentifier,
                    competitionName = competitionName,
                    competitionShortName = competitionShortName,
                    clubName = team.clubName,
                    teamName = team.teamName,
                    result = team.result,
                    names = group,
                    registrationId = team.registrationId,
                )
            }
        }

    fun formatPlace(place: Int): String = "$place. Platz"

    /**
     * Renntage als Bereich, wie auf der DRV-Vorlage: „16.–17. August 2025", über Monatsgrenzen
     * hinweg „31. Juli – 1. August 2025".
     */
    fun formatEventDate(days: List<LocalDate>): String {
        if (days.isEmpty()) return ""

        val first = days.min()
        val last = days.max()
        val year = last.year

        return when {
            first == last -> "${first.dayOfMonth}. ${germanMonths[first.monthValue - 1]} $year"
            first.month == last.month && first.year == last.year ->
                "${first.dayOfMonth}.–${last.dayOfMonth}. ${germanMonths[first.monthValue - 1]} $year"
            else ->
                "${first.dayOfMonth}. ${germanMonths[first.monthValue - 1]} – " +
                    "${last.dayOfMonth}. ${germanMonths[last.monthValue - 1]} $year"
        }
    }
}
```

- [ ] **Step 5: Test laufen lassen**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build -Dtest=AwardCertificateLogicTest
```

Erwartung: alle Tests grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate backend/src/test/kotlin/de/lambda9/ready2race/backend/app/certificate
git commit -m "Add award certificate selection logic"
```

---

### Task 6: Service, Routen und OpenAPI für Siegerurkunden

**Files:**
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/boundary/AwardCertificateService.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/boundary/awardCertificate.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/entity/AwardCertificateError.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/event/boundary/event.kt:83-96`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: `gapDocuments` (Task 3), `gapDocumentsDocx` und `XWPFDocument.toByteArray()` (Task 4),
  `AwardCertificateLogic` und die Entitäten (Task 5), `GapPlaceholderLogic.fill` und
  `toGapPlaceholders()` (Task 2), `GapDocumentTemplateRepo.getAssigned` (bestehend),
  `CompetitionExecutionService.computeCompetitionPlaces` (bestehend).
- Produces: die drei Endpunkte und `AwardCertificateService.download(...)`.

- [ ] **Step 1: Fehler-Typen anlegen**

`app/certificate/entity/AwardCertificateError.kt`, aufgebaut wie
`app/certificate/entity/CertificateError.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.certificate.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class AwardCertificateError : ServiceError {
    MissingTemplate,
    NoResults,
    CompetitionNotInEvent;

    override fun respond(): ApiError = when (this) {
        MissingTemplate -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "There is no template assigned for award certificates"
        )

        NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No placed teams for these certificates"
        )

        CompetitionNotInEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition does not belong to this event"
        )
    }
}
```

`MissingTemplate` nutzt bewusst `Conflict` wie `CertificateError.MissingTemplate`, damit das
Frontend beide Urkundenarten gleich behandeln kann.

- [ ] **Step 2: Service implementieren**

`app/certificate/boundary/AwardCertificateService.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateEntry
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateError
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateParticipant
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateTeam
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapPlaceholderLogic
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toGapPlaceholders
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.docx.gapDocumentsDocx
import de.lambda9.ready2race.backend.docx.toByteArray
import de.lambda9.ready2race.backend.pdf.gapDocuments
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream
import java.util.UUID

object AwardCertificateService {

    enum class Format { PDF, DOCX }

    fun downloadForEvent(
        eventId: UUID,
        options: AwardCertificateOptions,
        format: Format,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val entries = !entriesForEvent(eventId, options, competitionId = null, registrationId = null)
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        !render(eventId, entries, options, format, "urkunden_${event.name}")
    }

    fun downloadForCompetition(
        eventId: UUID,
        competitionId: UUID,
        options: AwardCertificateOptions,
        format: Format,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val entries = !entriesForEvent(eventId, options, competitionId, registrationId = null)
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val identifier = entries.firstOrNull()?.competitionIdentifier ?: ""

        !render(eventId, entries, options, format, "urkunden_${event.name}_$identifier")
    }

    fun downloadForRegistration(
        eventId: UUID,
        competitionId: UUID,
        registrationId: UUID,
        options: AwardCertificateOptions,
        format: Format,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val entries = !entriesForEvent(eventId, options, competitionId, registrationId)
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val first = entries.firstOrNull()
        val name = listOfNotNull(
            "urkunde",
            event.name,
            first?.competitionIdentifier,
            first?.place?.toString(),
            first?.names?.firstOrNull(),
        ).joinToString("_")

        !render(eventId, entries, options, format, name)
    }

    /**
     * Sammelt die Urkunden der Veranstaltung, optional auf einen Wettkampf und eine Meldung
     * eingegrenzt. Die Wettkämpfe werden wie in der Ergebnisliste nach Identifier sortiert.
     */
    private fun entriesForEvent(
        eventId: UUID,
        options: AwardCertificateOptions,
        competitionId: UUID?,
        registrationId: UUID?,
    ): App<ServiceError, List<AwardCertificateEntry>> = KIO.comprehension {
        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        val competitions = !CompetitionRepo.getByEvent(eventId).orDie()

        val selected = if (competitionId == null) {
            competitions
        } else {
            val match = competitions.filter { it.id == competitionId }
            !KIO.failOn(match.isEmpty()) { AwardCertificateError.CompetitionNotInEvent }
            match
        }

        val entries = !selected
            .sortedWith(lexiNumberComp { it.identifier!! })
            .traverse { competition ->
                KIO.comprehension {
                    val places = !CompetitionExecutionService.computeCompetitionPlaces(competition.id!!)

                    val teams = places.map { (team, place) ->
                        val clubs = team.participants.map { it.externalClubName }.toSet()
                        val clubName = if (clubs.size == 1) {
                            clubs.first() ?: team.clubName
                        } else {
                            team.mixedTeamTerm ?: event.mixedTeamTerm ?: team.clubName
                        }

                        AwardCertificateTeam(
                            place = place,
                            clubName = clubName,
                            teamName = team.registrationName,
                            result = team.timeString,
                            startNumber = team.startNumber,
                            excluded = team.deregistered || team.out || team.failed,
                            participants = team.participants.map {
                                AwardCertificateParticipant(
                                    firstName = it.firstName,
                                    lastName = it.lastName,
                                    role = it.namedParticipantName,
                                )
                            },
                            registrationId = team.competitionRegistration,
                        )
                    }.filter { registrationId == null || it.registrationId == registrationId }

                    KIO.ok(
                        AwardCertificateLogic.entriesForCompetition(
                            competitionIdentifier = competition.identifier!!,
                            competitionName = competition.name!!,
                            competitionShortName = competition.shortName,
                            teams = teams,
                            options = options,
                        )
                    )
                }
            }
            .map { it.flatten() }

        !KIO.failOn(entries.isEmpty()) { AwardCertificateError.NoResults }

        KIO.ok(entries)
    }

    private fun render(
        eventId: UUID,
        entries: List<AwardCertificateEntry>,
        options: AwardCertificateOptions,
        format: Format,
        fileBaseName: String,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val template = !GapDocumentTemplateRepo.getAssigned(GapDocumentType.AWARD_CERTIFICATE).orDie()
            .onNullFail { AwardCertificateError.MissingTemplate }

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }
        val eventDays = !EventDayRepo.getByEvent(eventId).orDie()
        val eventDate = AwardCertificateLogic.formatEventDate(eventDays.map { it.date })

        val placeholders = template.placeholders!!.toList().toGapPlaceholders()

        val pages = entries.map { entry ->
            GapPlaceholderLogic.fill(
                placeholders = placeholders,
                values = GapPlaceholderValues(
                    firstName = entry.names.singleOrNull()?.substringBefore(" "),
                    lastName = entry.names.singleOrNull()?.substringAfter(" "),
                    fullName = entry.names.joinToString("\n"),
                    result = entry.result,
                    eventName = event.name,
                    place = AwardCertificateLogic.formatPlace(entry.place),
                    competitionName = entry.competitionName,
                    competitionShortName = entry.competitionShortName,
                    clubName = entry.clubName,
                    teamName = entry.teamName,
                    eventDate = eventDate,
                    eventLocation = event.location,
                ),
            )
        }

        val bytes = when (format) {
            Format.PDF -> {
                val doc = gapDocuments(
                    template = template.data!!,
                    font = template.fontData,
                    withBackground = options.withBackground,
                    pages = pages,
                )
                val out = ByteArrayOutputStream()
                doc.save(out)
                doc.close()
                out.toByteArray()
            }

            Format.DOCX -> {
                val templateDoc = Loader.loadPDF(template.data!!)
                val format0 = templateDoc.getPage(0).mediaBox
                val width = format0.width
                val height = format0.height
                templateDoc.close()

                gapDocumentsDocx(
                    pageWidthPoints = width,
                    pageHeightPoints = height,
                    fontName = template.fontName,
                    pages = pages,
                ).toByteArray()
            }
        }

        val extension = if (format == Format.PDF) "pdf" else "docx"

        KIO.ok(
            ApiResponse.File(
                name = "$fileBaseName.$extension",
                bytes = bytes,
            )
        )
    }
}
```

Beim Implementieren prüfen und anpassen:
- Die Feldnamen von `CompetitionMatchTeamParticipant` (`firstName`, `lastName`,
  `namedParticipantName`, `externalClubName`) gegen
  `app/competitionExecution/entity/CompetitionMatchTeamWithRegistration.kt` abgleichen.
- `template.fontData` und `template.fontName` stammen aus der in Task 1 erweiterten View
  `gap_document_template_assignment`.
- `EventDayRepo.getByEvent` liefert `EventDayRecord`s mit dem Feld `date`.

- [ ] **Step 3: Routen anlegen**

`app/certificate/boundary/awardCertificate.kt`:

```kotlin
package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.awardCertificate() {

    route("/awardCertificates") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val options = !awardCertificateOptions()
                val format = !awardCertificateFormat()

                AwardCertificateService.downloadForEvent(eventId, options, format)
            }
        }
    }

    route("/competition/{competitionId}/awardCertificates") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)
                val options = !awardCertificateOptions()
                val format = !awardCertificateFormat()

                AwardCertificateService.downloadForCompetition(eventId, competitionId, options, format)
            }
        }

        get("/{registrationId}") {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val competitionId = !pathParam("competitionId", uuid)
                val registrationId = !pathParam("registrationId", uuid)
                val options = !awardCertificateOptions()
                val format = !awardCertificateFormat()

                AwardCertificateService.downloadForRegistration(
                    eventId,
                    competitionId,
                    registrationId,
                    options,
                    format,
                )
            }
        }
    }
}
```

Die beiden Hilfsfunktionen `awardCertificateOptions()` und `awardCertificateFormat()` in derselben
Datei ergänzen. Sie lesen die Query-Parameter über `optionalQueryParam` und setzen die Standardwerte
`maxPlace = AwardCertificateOptions.DEFAULT_MAX_PLACE`, `mode = PER_ATHLETE`,
`withBackground = false`, `format = PDF`. Als Vorbild dient der Umgang mit `optionalQueryParam` in
`app/results/boundary/results.kt` — dort steht der genaue Aufbau innerhalb einer
`respondComprehension`. Ist eine Auslagerung in eine eigene Funktion mit dem dortigen Empfängertyp
nicht ohne Reibung möglich, die Parameter direkt in den drei Handlern lesen; Duplikation von vier
Zeilen ist hier akzeptabel und besser als eine erzwungene Abstraktion.

- [ ] **Step 4: Routen einhängen**

In `app/event/boundary/event.kt` innerhalb von `route("/{eventId}")` neben `certificate()`
ergänzen:

```kotlin
            awardCertificate()
```

Import ergänzen:

```kotlin
import de.lambda9.ready2race.backend.app.certificate.boundary.awardCertificate
```

- [ ] **Step 5: Kompilieren**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q compile -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build
```

Erwartung: erfolgreich. Fehler zu Feldnamen aus den generierten Records oder aus
`CompetitionMatchTeamWithRegistration` hier auflösen, indem die tatsächlichen Namen aus den Quellen
übernommen werden.

- [ ] **Step 6: OpenAPI ergänzen**

In `backend/src/main/resources/openapi/documentation.yaml` direkt vor dem `components:`-Schlüssel
einfügen (die vier Query-Parameter werden bei allen drei Pfaden inline definiert):

```yaml
  /event/{eventId}/awardCertificates:
    parameters:
      - $ref: '#/components/parameters/eventId'
    get:
      operationId: downloadAwardCertificatesForEvent
      parameters:
        - name: format
          in: query
          required: false
          schema:
            type: string
            enum: [ pdf, docx ]
        - name: maxPlace
          in: query
          required: false
          schema:
            type: integer
        - name: mode
          in: query
          required: false
          schema:
            type: string
            enum: [ PER_ATHLETE, PER_TEAM ]
        - name: background
          in: query
          required: false
          schema:
            type: boolean
      responses:
        200:
          $ref: '#/components/responses/file'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'

  /event/{eventId}/competition/{competitionId}/awardCertificates:
    parameters:
      - $ref: '#/components/parameters/eventId'
      - $ref: '#/components/parameters/competitionId'
    get:
      operationId: downloadAwardCertificatesForCompetition
      parameters:
        - name: format
          in: query
          required: false
          schema:
            type: string
            enum: [ pdf, docx ]
        - name: maxPlace
          in: query
          required: false
          schema:
            type: integer
        - name: mode
          in: query
          required: false
          schema:
            type: string
            enum: [ PER_ATHLETE, PER_TEAM ]
        - name: background
          in: query
          required: false
          schema:
            type: boolean
      responses:
        200:
          $ref: '#/components/responses/file'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'

  /event/{eventId}/competition/{competitionId}/awardCertificates/{registrationId}:
    parameters:
      - $ref: '#/components/parameters/eventId'
      - $ref: '#/components/parameters/competitionId'
      - name: registrationId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: downloadAwardCertificate
      parameters:
        - name: format
          in: query
          required: false
          schema:
            type: string
            enum: [ pdf, docx ]
        - name: maxPlace
          in: query
          required: false
          schema:
            type: integer
        - name: mode
          in: query
          required: false
          schema:
            type: string
            enum: [ PER_ATHLETE, PER_TEAM ]
        - name: background
          in: query
          required: false
          schema:
            type: boolean
      responses:
        200:
          $ref: '#/components/responses/file'
        400:
          $ref: '#/components/responses/400'
        401:
          $ref: '#/components/responses/401'
        403:
          $ref: '#/components/responses/403'
        404:
          $ref: '#/components/responses/404'
        500:
          $ref: '#/components/responses/500'
```

Prüfen, dass `#/components/parameters/competitionId` existiert:

```bash
grep -n "^    competitionId:" backend/src/main/resources/openapi/documentation.yaml
```

Fehlt der Parameter, ihn analog zu `eventId` in `components.parameters` ergänzen.

- [ ] **Step 7: Client generieren**

```bash
cd frontend && npm run generate
```

Erwartung: `frontend/src/api/sdk.gen.ts` enthält `downloadAwardCertificatesForEvent`,
`downloadAwardCertificatesForCompetition`, `downloadAwardCertificate`.

```bash
grep -c "downloadAwardCertificate" frontend/src/api/sdk.gen.ts
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin backend/src/main/resources/openapi/documentation.yaml frontend/src/api
git commit -m "Add award certificate download endpoints"
```

---

### Task 7: Word-Download für die Teilnahmeurkunde

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/boundary/CertificateService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/certificate/boundary/certificate.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: `AwardCertificateService.Format`, `gapDocumentsDocx`, `toByteArray()`.
- Produces: `?format=pdf|docx` an den beiden bestehenden GET-Endpunkten unter
  `/certificatesOfParticipation`.

- [ ] **Step 1: Rendering im `CertificateService` austauschbar machen**

In `CertificateService.kt` die Funktion `participantForEvent` erweitern, damit sie beide Formate
bedient. Die bestehende Signatur bleibt für den E-Mail-Versand erhalten:

```kotlin
    fun participantForEvent(
        additions: List<AdditionalText>,
        template: ByteArray,
    ): ByteArray = participantForEvent(additions, template, null, AwardCertificateService.Format.PDF)

    fun participantForEvent(
        additions: List<AdditionalText>,
        template: ByteArray,
        fontName: String?,
        format: AwardCertificateService.Format,
    ): ByteArray = when (format) {
        AwardCertificateService.Format.PDF -> {
            val doc = document(template, additions)
            val out = ByteArrayOutputStream()
            doc.save(out)
            doc.close()
            val bytes = out.toByteArray()
            out.close()
            bytes
        }

        AwardCertificateService.Format.DOCX -> {
            val templateDoc = Loader.loadPDF(template)
            val mediaBox = templateDoc.getPage(0).mediaBox
            val width = mediaBox.width
            val height = mediaBox.height
            templateDoc.close()

            gapDocumentsDocx(
                pageWidthPoints = width,
                pageHeightPoints = height,
                fontName = fontName,
                pages = listOf(additions),
            ).toByteArray()
        }
    }
```

Nötige Importe: `org.apache.pdfbox.Loader`, `de.lambda9.ready2race.backend.docx.gapDocumentsDocx`,
`de.lambda9.ready2race.backend.docx.toByteArray`.

- [ ] **Step 2: Beide Download-Funktionen um das Format erweitern**

`downloadCertificatesOfParticipation(eventId, clubId)` und
`downloadCertificateOfParticipation(eventId, participantId, user, scope)` erhalten jeweils einen
zusätzlichen Parameter `format: AwardCertificateService.Format`, geben ihn an
`participantForEvent` weiter (mit `fontName = template.fontName`) und bilden die Dateiendung daraus:

```kotlin
        val extension = if (format == AwardCertificateService.Format.PDF) "pdf" else "docx"
```

Die Dateinamen werden entsprechend gebildet, im ZIP-Fall auch die Einträge. Der ZIP-Name bleibt
`.zip`. `sendNextCertificateOfParticipation` bleibt unverändert bei PDF.

- [ ] **Step 3: Routen erweitern**

In `certificate.kt` in beiden `get`-Handlern das Format aus dem Query-Parameter lesen und
weitergeben; Standard bleibt PDF. Muster:

```kotlin
                val format = !optionalQueryParam("format", enum<AwardCertificateService.Format>())
```

Da der Query-Wert klein geschrieben ankommt (`pdf`, `docx`), stattdessen über einen eigenen kleinen
Parser gehen:

```kotlin
                val format = (!optionalQueryParam("format", Parser { it.uppercase() }))
                    ?.let { AwardCertificateService.Format.valueOf(it) }
                    ?: AwardCertificateService.Format.PDF
```

Dieselbe Umwandlung gilt für die Routen aus Task 6 — dort identisch umsetzen, falls in Task 6 noch
ein anderer Weg gewählt wurde.

- [ ] **Step 4: OpenAPI ergänzen**

Bei `/event/{eventId}/certificatesOfParticipation` und
`/event/{eventId}/certificatesOfParticipation/{participantId}` je einen Query-Parameter ergänzen:

```yaml
      parameters:
        - name: format
          in: query
          required: false
          schema:
            type: string
            enum: [ pdf, docx ]
```

- [ ] **Step 5: Kompilieren, Tests, Client generieren**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build
```

```bash
cd frontend && npm run generate && npm run lint
```

Erwartung: Backend grün, Client enthält den `format`-Parameter, Lint ohne neue Fehler.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin backend/src/main/resources/openapi/documentation.yaml frontend/src/api
git commit -m "Offer Word format for certificates of participation"
```

---

### Task 8: Vorlagenverwaltung im Backend

**Files:**
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentTemplateRequest.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentPlaceholderRequest.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentPlaceholderDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentTemplateDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/entity/GapDocumentTypeDto.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/control/Conversions.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/GapDocumentTemplateService.kt`
- Modify: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt`
- Create: `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/control/GapDocumentTemplateFontRepo.kt`
- Modify: `backend/src/main/resources/openapi/documentation.yaml`

**Interfaces:**
- Consumes: Tabelle und Views aus Task 1, `GapDocumentType.allowedPlaceholders` aus Task 2.
- Produces:
  - `GapDocumentTemplateRequest` zusätzlich mit `fontName: String?`
  - `GapDocumentPlaceholderRequest` und `GapDocumentPlaceholderDto` zusätzlich mit `fontSize: Int?`, `bold: Boolean`, `italic: Boolean`, `staticText: String?`
  - `GapDocumentTemplateDto` zusätzlich mit `fontName: String?`, `hasFont: Boolean`
  - `GapDocumentTypeDto` zusätzlich mit `allowedPlaceholders: List<GapDocumentPlaceholderType>`
  - `GapDocumentTemplateFontRepo.upsert(record)`, `.delete(template)`

- [ ] **Step 1: Requests und DTOs erweitern**

Die vier Datenklassen um die oben genannten Felder ergänzen. `bold` und `italic` bekommen im Request
den Standardwert `false`, damit ältere Clients weiter funktionieren:

```kotlin
data class GapDocumentPlaceholderRequest(
    val name: String?,
    val type: GapDocumentPlaceholderType,
    val page: Int,
    val relLeft: Double,
    val relTop: Double,
    val relWidth: Double,
    val relHeight: Double,
    val textAlign: TextAlign,
    val fontSize: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val staticText: String? = null,
)
```

- [ ] **Step 2: `GapDocumentTypeDto` erweitern**

```kotlin
data class GapDocumentTypeDto(
    val type: GapDocumentType,
    val assignedTemplate: AssignedTemplateId?,
    val allowedPlaceholders: List<GapDocumentPlaceholderType>,
)
```

Und in `GapDocumentTemplateService.getTypes()` füllen:

```kotlin
                    GapDocumentTypeDto(
                        type = type,
                        assignedTemplate = usages[type.name],
                        allowedPlaceholders = type.allowedPlaceholders.toList(),
                    )
```

- [ ] **Step 3: Conversions anpassen**

`GapDocumentPlaceholderRequest.toRecord` und `GapDocumentPlaceholderRecord.toDto` um die vier neuen
Felder erweitern; `GapDocumentTemplateRequest.toRecord` um `fontName`;
`GapDocumentTemplateViewRecord.toDto` um `fontName` und `hasFont`.

- [ ] **Step 4: Font-Repository anlegen**

`app/documentTemplate/control/GapDocumentTemplateFontRepo.kt`, aufgebaut wie
`GapDocumentTemplateDataRepo.kt` (diese Datei zuerst lesen und Stil sowie Importe übernehmen):

```kotlin
package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateFontRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE_FONT
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.extensions.kio.onNull
import java.util.UUID

object GapDocumentTemplateFontRepo {

    fun upsert(record: GapDocumentTemplateFontRecord) =
        GAP_DOCUMENT_TEMPLATE_FONT.update(f = {
            fileName = record.fileName
            data = record.data
        }) { TEMPLATE.eq(record.template) }
            .onNull { GAP_DOCUMENT_TEMPLATE_FONT.insertReturning(record) }

    fun delete(template: UUID) = GAP_DOCUMENT_TEMPLATE_FONT.delete { TEMPLATE.eq(template) }
}
```

- [ ] **Step 5: Schrift-Upload im Service**

`addTemplate` und `updateTemplate` nehmen zusätzlich `font: File?` und schreiben die Datei über
`GapDocumentTemplateFontRepo.upsert`. Vor dem Speichern wird geprüft, dass PDFBox die Schrift laden
kann — ein kaputter Upload soll beim Anlegen auffallen, nicht erst beim Generieren:

```kotlin
    private fun validateFont(font: File): App<GapDocumentTemplateError, Unit> = KIO.effect {
        val doc = PDDocument()
        PDType0Font.load(doc, font.bytes.inputStream())
        doc.close()
    }.mapError { GapDocumentTemplateError.InvalidFont }.map { }
```

`GapDocumentTemplateError` erhält dazu den Fall `InvalidFont` mit
`HttpStatusCode.BadRequest` und der Meldung `"Font file could not be read"`. Den genauen Aufbau der
`respond()`-Methode aus der bestehenden Datei übernehmen. Falls `mapError` in der genutzten
KIO-Version anders heißt, das Muster einer bestehenden `KIO.effect`-Verwendung im Projekt
übernehmen (`grep -rn "KIO.effect" backend/src/main/kotlin | head`).

- [ ] **Step 5b: Platzhalterseite validieren**

Der Serien-Renderer aus Task 3 zeichnet je Urkunde genau eine Seite und berücksichtigt nur
Platzhalter mit `page == 1`; Platzhalter mit einer höheren Seitenzahl fielen bisher stillschweigend
weg. Eine Urkunde ist per Definition einseitig, deshalb wird das beim Speichern der Vorlage
abgefangen statt beim Generieren verschluckt: In `addTemplate` und `updateTemplate` schlägt eine
Anfrage mit `placeholders.any { it.page != 1 }` für den Typ `AWARD_CERTIFICATE` mit einem neuen Fall
`GapDocumentTemplateError.PlaceholderPageNotSupported` fehl (`HttpStatusCode.BadRequest`, Meldung
`"Award certificates have a single page, placeholders must be on page 1"`). Die Teilnahmeurkunde
bleibt unangetastet, weil ihre Vorlage mehrseitig sein darf.

- [ ] **Step 6: Multipart-Route erweitern**

In `documentTemplate.kt` im `post`-Handler für `/gapDocumentTemplate` den zweiten Dateiteil
akzeptieren. Der bestehende Handler sammelt Dateien in einer Liste; die Zuordnung erfolgt über den
Feldnamen des Parts:

- `part.name == "font"` → Schriftdatei
- alles andere → Vorlagen-PDF

Die Endung wird geprüft: `.ttf` oder `.otf`, sonst `GapDocumentTemplateError.InvalidFont`. Für
`PUT /{gapDocumentTemplateId}` denselben Weg nutzen, damit eine Schrift auch nachträglich gesetzt
oder ersetzt werden kann; ein leerer Part löscht die Schrift über
`GapDocumentTemplateFontRepo.delete`.

Als Vorlage für die Multipart-Verarbeitung dient der Block ab
`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/documentTemplate/boundary/documentTemplate.kt:147`.

- [ ] **Step 7: OpenAPI und Client**

In `documentation.yaml` das Multipart-Schema von `/gapDocumentTemplate` um den optionalen Teil
`font` (`type: string, format: binary`) ergänzen und die neuen Felder in den Schemas
`GapDocumentTemplateRequest`, `GapDocumentPlaceholderRequest`, `GapDocumentPlaceholderDto`,
`GapDocumentTemplateDto` und `GapDocumentTypeDto` nachziehen. Die Schema-Namen mit

```bash
grep -n "GapDocument" backend/src/main/resources/openapi/documentation.yaml | grep -v "/" | head -20
```

ermitteln. Danach:

```bash
cd frontend && npm run generate
```

- [ ] **Step 8: Kompilieren und Tests**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw -q test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build
```

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin backend/src/main/resources/openapi/documentation.yaml frontend/src/api
git commit -m "Support font upload and text styles in gap document templates"
```

---

### Task 9: Frontend — Vorlagen-Editor erweitern

**Files:**
- Modify: `frontend/src/components/gapDocumentTemplate/PlaceholderSidebar.tsx`
- Modify: `frontend/src/components/gapDocumentTemplate/GapDocumentTemplateDialog.tsx`
- Modify: `frontend/src/components/gapDocumentTemplate/PdfPlaceholderEditor.tsx`
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: den generierten Client aus Task 8 (`fontSize`, `bold`, `italic`, `staticText`,
  `fontName`, `hasFont`, `allowedPlaceholders`).
- Produces: einen Editor, der die neuen Felder speichert.

- [ ] **Step 1: Bestandsaufnahme**

Die drei Komponenten lesen und notieren, wie Platzhalter aktuell angelegt und gespeichert werden,
insbesondere welches Formular-Framework genutzt wird:

```bash
grep -n "useForm\|Controller\|register\|onSubmit" frontend/src/components/gapDocumentTemplate/*.tsx | head -30
```

Die vorhandenen Muster übernehmen, kein neues Formular-Framework einführen.

- [ ] **Step 2: Platzhalterauswahl auf `allowedPlaceholders` einschränken**

Die Liste der wählbaren Platzhaltertypen kommt bisher aus dem Enum. Stattdessen wird
`allowedPlaceholders` des ausgewählten Dokumenttyps genutzt, sodass bei der Siegerurkunde nur die
passenden Typen erscheinen und bei der Teilnahmeurkunde alles unverändert bleibt.

- [ ] **Step 3: Schriftfelder je Platzhalter ergänzen**

In `PlaceholderSidebar.tsx` für den ausgewählten Platzhalter ergänzen:
- Zahlenfeld „Schriftgröße (pt)", leer erlaubt (dann Kastenhöhe)
- Schalter „Fett" und „Kursiv"
- Textfeld „Fester Text", nur sichtbar wenn der Typ `FREE_TEXT` ist

- [ ] **Step 4: Vorlagenfelder ergänzen**

In `GapDocumentTemplateDialog.tsx`:
- Textfeld „Schriftname" (wird ins Word-Dokument geschrieben)
- Datei-Upload „Schriftdatei (TTF/OTF, optional)" mit Hinweis, dass ohne Datei im PDF Helvetica
  genutzt wird
- Anzeige, ob bereits eine Schrift hinterlegt ist (`hasFont`), mit Möglichkeit zum Entfernen

- [ ] **Step 5: Übersetzungen**

Die neuen Schlüssel in allen drei Sprachdateien ergänzen. Deutsche Texte mit echten Umlauten. Die
bestehende Schlüsselstruktur der Gap-Vorlagen als Vorbild nehmen:

```bash
grep -n "gapDocument" frontend/src/i18n/de/translations.json | head -20
```

- [ ] **Step 6: Lint und Build**

```bash
cd frontend && npm run lint && npm run build
```

Erwartung: keine neuen Fehler.

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "Add font and text style fields to the gap template editor"
```

---

### Task 10: Frontend — Urkunden-Download

**Files:**
- Create: `frontend/src/components/awardCertificate/AwardCertificateDialog.tsx`
- Modify: die Wettkampf-Ergebnisansicht und die Veranstaltungsansicht (im Schritt 1 ermittelt)
- Modify: `frontend/src/i18n/de/translations.json`, `frontend/src/i18n/en/translations.json`, `frontend/src/i18n/da/translations.json`

**Interfaces:**
- Consumes: `downloadAwardCertificatesForEvent`, `downloadAwardCertificatesForCompetition`,
  `downloadAwardCertificate` aus dem generierten Client (Task 6).
- Produces: Dialog und Buttons.

- [ ] **Step 1: Einstiegspunkte finden**

```bash
grep -rln "downloadResultsDocument\|resultsDocument" frontend/src | head
grep -rln "certificatesOfParticipation" frontend/src/components frontend/src/pages | head
```

Die Datei, die heute den Download des Ergebnisdokuments anbietet, ist der Ort für den
Veranstaltungs-Button; die Wettkampf-Ergebnistabelle ist der Ort für den Wettkampf-Button und den
Einzeldownload je Zeile. Wie ein Datei-Download im Frontend ausgelöst wird, zeigt die bestehende
Stelle für `certificatesOfParticipation` — dasselbe Muster nutzen, keinen eigenen Blob-Download
erfinden.

- [ ] **Step 2: Dialog bauen**

`AwardCertificateDialog.tsx` mit den Feldern:
- Format: PDF / Word (Radiogruppe, Standard PDF)
- „bis Platz": Zahlenfeld, Standard 3
- „pro Athlet / pro Boot": Radiogruppe, Standard „pro Athlet"
- „Design mitdrucken": Schalter, Standard aus, mit Hinweis „nur nötig, wenn nicht auf vorgedrucktes
  Papier gedruckt wird"
- Aktionen: Abbrechen, Herunterladen

Der Dialog nimmt per Prop entweder `{eventId}`, `{eventId, competitionId}` oder
`{eventId, competitionId, registrationId}` und ruft die passende Client-Funktion.

- [ ] **Step 3: Fehlende Vorlage abfangen**

Antwortet der Server mit 404 und der Meldung zur fehlenden Vorlage, zeigt der Dialog einen Hinweis
mit Verweis auf die Konfiguration statt einer allgemeinen Fehlermeldung.

- [ ] **Step 4: Buttons einhängen**

Button „Urkunden" in der Wettkampf-Ergebnisansicht und in der Veranstaltungsansicht, Icon-Button je
Ergebniszeile für den Einzeldownload.

- [ ] **Step 5: Übersetzungen**

Neue Schlüssel in `de`, `en`, `da`.

- [ ] **Step 6: Lint und Build**

```bash
cd frontend && npm run lint && npm run build
```

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "Add award certificate download dialog and entry points"
```

---

## Abschluss

- [ ] Vollständiger Backend-Testlauf: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21 && cd backend && ./mvnw test -Ddatabase.url=jdbc:postgresql://localhost:17660/ready2race-build`
- [ ] Frontend: `cd frontend && npm run lint && npm run build`
- [ ] Manuelle Probe: Vorlage anlegen (PDF-Export der DRV-PPTX), Platzhalter setzen, Urkunden für
  einen Wettkampf als PDF und als Word herunterladen, beide Dateien öffnen und die Positionen gegen
  das vorgedruckte Papier prüfen.
