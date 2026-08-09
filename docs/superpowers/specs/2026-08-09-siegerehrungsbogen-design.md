# Design: Siegerehrungsbogen (druckbare Unterlage für die Sprecherin)

**Stand:** 2026-08-09
**Status:** Design abgenommen, Implementierung ausstehend
**Branch:** `claude/ready2race-award-ceremony-sheet-210a37`

---

## 1. Problem

Bei der Medaillenvergabe steht die Sprecherin mit dem, was gerade greifbar ist: dem
Ergebnis-PDF der ganzen Veranstaltung (`ResultsService.downloadResultsDocument`) oder dem
Platzierungs-Tab am Bildschirm. Beides ist zum Vorlesen ungeeignet.

- Die Ergebnisliste ist nach Wettkampf gegliedert und listet **alle** Boote, nicht nur die
  Geehrten. Wer die drei Ersten sucht, sucht.
- Sie kennt keine Wertungskategorien. Wird in einem Rennen nach Kategorien getrennt geehrt,
  steht die dafür nötige Reihenfolge nirgends.
- Der Heimatverein einer Person taucht dort nur als Fußnote auf, der meldende Verein ohne
  Kennzeichnung. Für die Ansage („für den Ruderclub Nürtingen startend, …") ist beides nötig
  und muss unterscheidbar sein.
- Die Siegerurkunde (`AwardCertificateService`) ist das Gegenteil: eine Seite je Person bzw.
  Boot, ohne Zeiten, ohne Lauf, ohne die anderen Ränge. Sie geht in die Hand des Ruderers,
  nicht auf das Pult.

Es fehlt also die Unterlage dazwischen: **ein Blatt je Ehrung, mit allem, was gesagt werden
muss, in Vorlesereihenfolge.**

## 2. Zielbild

Ein zusammenhängendes PDF. **Eine DIN-A4-Seite entspricht genau einer Wertungskategorie einer
Siegerehrung**, mit den Rängen 1, 2 und 3 gemeinsam auf dem Blatt. Die Nutzer:innen wählen
vorher aus, welche Ehrungen ins PDF sollen.

Einzige Ausnahme, beim Bau entschieden: passt eine Ehrung auch in der kleinsten noch
vorlesbaren Schrift nicht auf ein Blatt, bekommt sie eine Fortsetzungsseite mit wiederholtem
Kopf — siehe [Der Boden der Stufenleiter](#der-boden-der-stufenleiter).

Ausdrücklich **kein** Athletenzertifikat: der Bogen trägt kein Vorlagen-Design, keine
Unterschriftszeile, keine Urkundensprache. Er ist ein Ablauf- und Informationsbogen.

## 3. Begriffe

| Begriff | Bedeutung in diesem Design |
|---|---|
| **Wettkampf** | `competition` — das Rennen, z. B. „17-NC / CM 4x+ / Mixed-Coastal-Vierer" |
| **Wertungskategorie** | `rating_category`, hängt an der Meldung (`competition_registration.rating_category`). Innerhalb eines Wettkampfs wird je Kategorie getrennt geehrt. |
| **Ehrung** | Die Einheit einer Seite: das Paar (Wettkampf, Wertungskategorie). Ein Wettkampf ohne gepflegte Kategorien ergibt genau eine Ehrung mit Kategorie `null`. |
| **Meldender Verein** | `competition_registration.club` → `team.clubName`. Wer die Meldung abgegeben hat. Reine Verwaltung. |
| **Heimatverein** | Der Verein, den eine Person *trägt*: `ClubComposition.clubWorn(external, externalClubName, clubName)`. Bei Gastruderern der Freitext an der Person, sonst deren eigener Verein. |

## 4. Datenquellen

Nichts wird neu erhoben, es gibt keine Migration und kein neues Feld in der Datenbank.

| Information | Quelle |
|---|---|
| Platzierungen | `CompetitionExecutionService.computeCompetitionPlaces(competitionId)` → `List<Pair<CompetitionMatchTeamWithRegistration, Int>>` |
| Wertungskategorie | `CompetitionMatchTeamWithRegistration.ratingCategory` (Name; kommt aus der View über `competition_registration.rating_category`) |
| Lauf, Runde, Zeitpunkt | `CompetitionSetupService.getSetupRoundsWithMatches(competitionId)`: Rundenname (`setupRoundName`), Laufname (`setupMatches[].name`), `startTime` / `startedAt` / `finishedAt` des Laufs |
| Zeit, Startnummer, Strafe | am Team: `timeString`, `startNumber`, `penaltySeconds`, `penaltyNote` |
| Bootsname | `team.registrationName` |
| Meldender Verein | `team.clubName` |
| Heimatvereine | je Person `ClubComposition.clubWorn(...)`, Kette über `ClubComposition.of(..., ClubShortNameSettings.none)` |
| Wettkampf-Stammdaten | `CompetitionRepo.getByEvent(eventId)`: `identifier`, `shortName`, `name` |
| Veranstaltung | `EventRepo.get(eventId)`, `EventDayRepo.getByEvent(eventId)`; Datumsformat über `AwardCertificateLogic.formatEventDate` |

**Zuordnung Team → Lauf.** `computeCompetitionPlaces` liefert das Team ohne Rundenkontext.
Statt die (große, empfindliche) Funktion umzubauen, lädt der neue Service die Runden separat
und findet den Lauf über die Meldung:

```kotlin
rounds.asReversed().firstNotNullOfOrNull { round ->
    round.matches.firstOrNull { m -> m.teams.any { it.competitionRegistration == regId } }
        ?.let { round to it }
}
```

Gesucht wird die **letzte** Runde, in der die Meldung vorkommt — dort ist ihr Platz entstanden;
deshalb die umgekehrte Reihenfolge. `rounds` ist die bereits mit `sortRounds` geordnete Liste.

### Geplanter Zeitpunkt der Siegerehrung

Existiert nicht als Struktur. `event_schedule_slot` kennt freie Slots mit Freitext-Namen (u. a.
„Siegerehrung"), aber ohne Bezug zu einem Wettkampf; eine Zuordnung wäre geraten.

Der Bogen druckt deshalb **keinen** Ehrungstermin. Er druckt den Zeitpunkt des **Laufs**, der
sauber am Slot hängt und für die Ansage ohnehin die relevantere Angabe ist.

Das Feld `AwardCeremonySheet.ceremonyTime: LocalDateTime?` ist vorgesehen und bleibt vorerst
immer `null`. Ist es `null`, entfällt die Zeile **ersatzlos** — kein Platzhalter, kein Doppel-
punkt ins Leere. Kommt der Zeitplan später, ist nur die Befüllung nachzutragen.

## 5. Seiteneinheit und Auswahl

### Bildung der Ehrungen

1. Alle Wettkämpfe der Veranstaltung, sortiert mit `lexiNumberComp { it.identifier }` (wie
   Ergebnisliste und Urkunden).
2. Je Wettkampf die Platzierungen berechnen, ausgeschlossene Boote entfernen
   (`deregistered || out || failed` — dieselbe Regel wie im Urkundengenerator).
3. Nach `ratingCategory` gruppieren. `null` ist eine eigene, gültige Gruppe.
4. Gruppen ohne platziertes Boot entfallen.
5. Reihenfolge: Wettkampf nach Rennnummer, innerhalb dessen Kategorien alphabetisch, die
   Gruppe ohne Kategorie zuletzt.

### API

Unter `/event/{eventId}/awardCeremony`, Privileg `ReadEventGlobal` (wie der Urkunden-Download):

**`GET /`** — die wählbaren Ehrungen:

```json
{"data": [
  {"competitionId": "…", "competitionIdentifier": "17-NC", "competitionShortName": "CM 4x+",
   "competitionName": "Mixed-Coastal-Vierer mit Steuermann",
   "ratingCategoryName": "Masters A", "placedTeams": 3}
]}
```

**`POST /pdf`** — das PDF:

```json
{"selection": [{"competitionId": "…", "ratingCategoryName": "Masters A"}]}
```

Seiten in derselben Reihenfolge wie die Liste, unabhängig von der Reihenfolge in `selection`.
Leere oder fehlende `selection` bedeutet **alle** Ehrungen. POST statt GET, weil die Auswahl
bei einer Regatta mit 40 Rennen für einen Query-String zu lang wird.

Der Schlüssel einer Ehrung ist `(competitionId, ratingCategoryName?)`. Bewusst der **Name**,
nicht die ID: die Platzberechnung liefert nur ihn, und ein Nachladen der ID brächte nichts,
was auf dem Blatt stünde.

### Fehlerfälle

`AwardCeremonyError` mit ErrorCodes, analog `AwardCertificateError`:

| Fehler | Bedeutung | Status |
|---|---|---|
| `IsChallengeEvent` | Challenge-Events kennen keine Läufe und keine Platzierungen | 400 |
| `CompetitionNotInEvent` | ein `competitionId` der Auswahl gehört nicht zur Veranstaltung | 400 |
| `UnknownRatingCategory` | Paar (Wettkampf, Kategorie) hat keine platzierten Boote | 400 |
| `NoResults` | die Auswahl ergibt keine einzige Seite | 400 |

`IsChallengeEvent` wird wie in `AwardCertificateService` **vor** allem anderen geprüft, damit
die Antwort nicht davon abhängt, was das Event sonst enthält.

## 6. Platzvergabe innerhalb der Kategorie

Reine Funktion in `AwardCeremonyLogic`, ohne Datenbankzugriff:

1. Ausgeschlossene Boote sind bereits entfernt (Schritt 2 oben).
2. Sortieren nach berechnetem Wettkampfplatz, bei Gleichstand nach Startnummer — stabil und
   reproduzierbar.
3. **Standard-Ranking neu ab 1**: gleicher Wettkampfplatz ⇒ gleicher Kategorie-Rang; der
   nächste Rang überspringt entsprechend viele Stellen.
4. Alle Boote mit Kategorie-Rang ≤ 3 kommen auf die Seite.

Beispiele (Wettkampfplätze → Kategorie-Ränge → Blöcke auf der Seite):

| Wettkampfplätze der Kategorie | Kategorie-Ränge | Blöcke |
|---|---|---|
| 1, 2, 3, 7 | 1, 2, 3, 4 | 1., 2., 3. |
| 2, 5, 7, 9 | 1, 2, 3, 4 | 1., 2., 3. |
| 1, 2, 2, 5 | 1, 2, 2, 4 | 1., 2., 2. — **keine Bronze** |
| 1, 1, 3 | 1, 1, 3 | 1., 1., 3. |
| 1, 2 | 1, 2 | 1., 2. |

Bei geteiltem Rang stehen alle betroffenen Boote als eigene Blöcke unter derselben Rangzahl.
Die Zahl wird nur beim ersten Block gesetzt, alle Blöcke des geteilten Rangs tragen den
Vermerk „geteilt".

## 7. Seitenlayout

Ein `document(format = PDRectangle.A4) { page { … } }` je Ehrung, im DSL der Ergebnisliste
(`ResultsService.buildPdf`).

**Korrektur gegenüber dem ursprünglichen Entwurf:** Ein `page { }` ist **keine** harte
Seitengrenze. `Page.render` gibt eine Liste von `PDPage` zurück und legt bei Überlauf über einen
Callback nach. „Eine Kategorie = eine Seite" ist damit nicht strukturell garantiert, sondern
muss erzwungen werden — siehe [Seitenpassung](#seitenpassung).

```
                        SIEGEREHRUNG                         18 pt fett, zentriert
        Küstenregatta Kiel · 15.–16. August 2026 · Kiel       11 pt, zentriert

  17-NC · CM 4x+                                             14 pt fett
  Mixed-Coastal-Vierer mit Steuermann                        12 pt
  Wertung: Masters A               Finale A · 15.08., 14:35  12 pt
  ═══════════════════════════════════════════════════════
   1.   Ruderclub Nürtingen                       4:12,7     Rang 20 pt fett / Zeit 14 pt
        Boot „RCN I" · Startnummer 3                         10 pt grau
        Meldender Verein: Ruderclub Nürtingen                10 pt grau
        Anna Meier (Schlagfrau)                              12 pt
        Bernd Groß (Schlagmann) — RG Hansa Kiel              12 pt
  ───────────────────────────────────────────────────────
   2.   …
```

### Verdichtung

- **Vereine.** Tragen alle Personen des Bootes denselben Verein und ist das der meldende
  Verein, steht er einmal als Titelzeile des Blocks; die Zeile „Meldender Verein" **entfällt**
  und keine Person trägt einen Vereinszusatz. Weichen Heimatvereine ab, ist die Titelzeile die
  `ClubComposition.full`-Kette in Bootsreihenfolge (nicht das pauschale „Renngemeinschaft"),
  darunter steht „Meldender Verein: …", und Personen tragen ihren Heimatverein hinter dem
  Namen, sofern er von der Titelzeile abweicht.
- **Zeit** rechtsbündig auf Höhe der Rangzahl. Fehlt sie, bleibt die Stelle leer — keine
  Striche, kein „—".
- **Lauf-Angabe** ohne Wochentag („Finale A · 15.08., 14:35"): dessen Abkürzung hängt an der
  CLDR-Fassung des JDK und wäre keine verlässliche Testgrundlage.
- **Strafe** nur wenn `penaltySeconds != null`: „Zeitstrafe +10 s (Frühstart)"; die Klammer
  entfällt ohne `penaltyNote`.
- Die Zeile „Boot … · Startnummer …" reduziert sich auf die vorhandenen Teile; ohne
  `registrationName` bleibt „Startnummer 3".
- Die Kopfzeile „Wertung: …" entfällt für Ehrungen ohne Kategorie.
- Die Lauf-Angabe rechts entfällt in Teilen, die fehlen (kein Laufname, keine Uhrzeit).

### Seitenpassung

Der erste Entwurf wollte die Schriftgröße aus der **Personenzahl** ableiten („ab 19 Zeilen eine
Stufe enger"). Das ist beim Bau widerlegt worden und wurde ersetzt.

Der Grund: die Zahl der Personen sagt nichts über die Zahl der **gesetzten** Zeilen. Eine
Renngemeinschaft trägt eine mehrgliedrige Vereinskette in der Titelzeile, eine Zeile „Meldender
Verein" und hinter jedem Namen den Heimatverein — und jede dieser Zeilen kann umbrechen, wenn
die Vereine ausgeschrieben sind. Gemessen: drei Boote à **vier** Ruderern, abwechselnd aus „Der
Hamburger und Germania Ruder Club von 1836 e.V." und „Rostocker Ruderclub von 1885 e.V.",
liefen auf zwei Seiten — bei zwölf Personenzeilen, weit unter der geplanten Schwelle. Das
Verhalten war über die Bootsgröße nicht einmal monoton: bei sieben Personen passte es zufällig
wieder, weil dort die kompakte Stufe einsetzte. Und die Überlaufseite trägt keinen Kopf: die
Sprecherin bekäme ein Blatt ohne Veranstaltung, Rennnummer und Wertung.

Stattdessen wird **gemessen statt geschätzt**. `AwardCeremonyPdf` kennt eine geordnete Folge von
Schriftstufen, von großzügig nach eng. Je Bogen wird probeweise gesetzt und `numberOfPages`
geprüft; genommen wird die erste Stufe, die genau eine Seite ergibt. Erst danach entsteht das
eigentliche Dokument mit allen Bögen in ihrer jeweiligen Stufe. Die Rangzahl bleibt in allen
Stufen gleich groß — sie muss vom Pult aus lesbar bleiben.

Der Preis sind zwei Durchgänge über dieselbe Layoutfunktion. Bei einer Handvoll Blättern je
Ehrung ist das nicht messbar, und es ist die einzige Art, die Zusicherung tatsächlich zu geben
statt sie zu behaupten.

### Der Boden der Stufenleiter

Die Leiter endet bei einer Größe, die man noch vorlesen kann (**8,5 pt** für Namenszeilen). Ein
erster Bau schrumpfte bis 7 pt weiter und erreichte diese Stufe mit ganz gewöhnlichen Daten —
drei Achter einer Renngemeinschaft genügen. Das ist Kleingedrucktes und widerspricht dem Zweck
des Blattes.

**Passt eine Ehrung auch auf der untersten Stufe nicht auf ein Blatt, bekommt sie mehrere
Seiten.** Jede davon trägt den **vollständigen Kopf**, jede ab der zweiten zusätzlich den
Vermerk „Fortsetzung". Der Umbruch läuft ausschließlich **zwischen** Rangblöcken, nie mitten
durch eine Mannschaft, und die Aufteilung wird ebenso gemessen wie die Stufe.

Das weicht bewusst von „genau eine Seite je Kategorie" ab und ist so entschieden worden. Der
Grund: die Alternativen sind schlechter. Weiterschrumpfen ergibt ein unlesbares Blatt; hart
abbrechen lässt das Regattabüro kurz vor der Ehrung ohne Ausdruck stehen. Eine zweite Seite mit
wiederholtem Kopf ist für die Sprecherin in jedem Fall benutzbar — anders als die kopflose
Folgeseite, die der Renderer vorher still erzeugte.

Erreicht wird der Fall selten: ein geteilter Rang mit vier Achtern, jede Mannschaft eine
Renngemeinschaft. Der Normalfall bleibt eine Seite ohne jeden Vermerk.

## 8. Aufbau im Code

Neues Modul `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/awardCeremony/`, nach
dem Schnitt der bestehenden Domänen:

| Datei | Aufgabe |
|---|---|
| `entity/AwardCeremony.kt` | `AwardCeremonyKey`, `AwardCeremonySelectionRequest`, `AwardCeremonyChoiceDto`, `AwardCeremonySheet`, `AwardCeremonyRank`, `AwardCeremonyTeam`, `AwardCeremonyAthlete` |
| `entity/AwardCeremonyError.kt` | die vier Fehler samt ErrorCodes |
| `boundary/AwardCeremonyLogic.kt` | **rein**: Gruppierung nach Kategorie, Standard-Ranking mit Gleichständen, Vereinsverdichtung, Formatierung von Strafe/Bootszeile/Laufangabe |
| `boundary/AwardCeremonyService.kt` | lädt Daten, baut die Bögen, ruft den Renderer, liefert `ApiResponse.File` |
| `boundary/AwardCeremonyPdf.kt` | das `document { }`-Layout, sonst nichts |
| `boundary/awardCeremony.kt` | Ktor-Routen |

Die Trennung Logic/Service/Pdf ist der Grund, warum die Testliste in Abschnitt 9 fast ohne
Datenbank auskommt: alles, was fachlich schiefgehen kann, sitzt in `AwardCeremonyLogic`.

Registrierung der Route in `plugins/Routing.kt` neben `results()` und `awardCertificate()`.

## 9. Frontend

- `components/awardCeremony/AwardCeremonyDialog.tsx` — lädt die Ehrungen per `GET`, zeigt sie
  nach Wettkampf gruppiert mit Checkboxen, „alle aus-/abwählen", und lädt das PDF per `POST`.
  Download-Mechanik (verstecktes `<a>`, `getFilename`) und Fehleranzeige 1:1 nach dem Muster
  von `AwardCertificateDialog.tsx`.
- Prop `competitionId?`: gesetzt, wird die Liste auf diesen Wettkampf vorgefiltert und alle
  seine Kategorien sind vorausgewählt.
- Einstiegspunkte: Button im Wettkämpfe-Tab der Veranstaltung (`EventPage`) **und** neben dem
  Urkunden-Button in `CompetitionPlaces.tsx`.
- `documentation.yaml` um beide Endpoints erweitern, danach `npm run generate`.
- Texte in `de`, `en`, `da` unter dem Schlüssel `awardCeremony.*`.

## 10. Tests

**`AwardCeremonyLogicTest`** (rein, ohne Datenbank) — der fachliche Kern:

1. Mehrere Kategorien in einem Wettkampf ⇒ mehrere Bögen, jeder mit eigener Rangfolge ab 1.
2. Wettkampf ohne gepflegte Kategorien ⇒ genau ein Bogen, `ratingCategoryName == null`.
3. Team mit mehreren Athlet:innen ⇒ alle Personen in Bootsreihenfolge, mit Rolle.
4. Alle Personen im meldenden Verein ⇒ eine Vereinszeile, kein „Meldender Verein", keine
   Personenzusätze.
5. Abweichende Heimatvereine inkl. Gastruderer ⇒ Vereinskette als Titel, meldender Verein
   eigene Zeile, abweichende Personen mit Zusatz.
6. Gleichstand auf 1 ⇒ zwei Blöcke „1.", nächster Rang 3.
7. Gleichstand auf 2 ⇒ Blöcke 1., 2., 2. und **kein** dritter Rang.
8. DNF / DSQ / abgemeldet ⇒ fallen raus und verschieben die Ränge der übrigen.
9. Fehlende Zeit, fehlender Bootsname, fehlende Strafe, fehlender Laufname ⇒ Zeile bzw.
   Bestandteil entfällt, nirgends ein leerer Platzhalter.
10. Seitenpassung: der Härtefall aus [Seitenpassung](#seitenpassung) — Renngemeinschaft mit
    ausgeschriebenen Vereinsnamen, abweichender meldender Verein, Zeitstrafe — ergibt genau
    eine Seite, und der letzte Rang steht noch darauf.

**`AwardCeremonyPdfTest`** — Seitenzahl gleich Zahl der Ehrungen; Text je Seite über
PDFBox-Extraktion (`PDFTextStripper` mit `startPage`/`endPage`), so wie es `PdfTest` bereits
tut: Überschrift, Rennnummer und Kategorie stehen auf der richtigen Seite, Inhalte zweier
Kategorien vermischen sich nicht.

**`AwardCeremonyServiceTest`** — Testcontainers (`testComprehension`, siehe
`GapDocumentTemplateServiceTest`): Auswahl und Sortierung der Ehrungen, Filterung auf einen
Wettkampf, leere Auswahl = alle, `IsChallengeEvent` und `NoResults`.

## 11. Bewusst nicht enthalten

- **Ersatzleute / Substitutionen.** Liegen vor (`SubstitutionDto`), gehören aber ins Protokoll,
  nicht auf das Pult. Eine Ansage nennt die Crew, die gefahren ist — die steht bereits da.
- **Ehrungstermin und Ehrungsreihenfolge aus dem Zeitplan.** Siehe Abschnitt 4; die Struktur
  dafür existiert nicht, und geraten wird nichts.
- **Vorlagen-Design / Hintergrund.** Der Bogen ist ein Arbeitsblatt, kein Zertifikat.
- **Öffentlicher Zugriff.** Der Bogen ist eine interne Unterlage; `ReadEventGlobal` genügt.
- **DOCX.** Anders als bei der Urkunde gibt es keinen Grund, dieses Blatt nachzubearbeiten.
