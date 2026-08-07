# CRF 2026 — Folge-MR nach PR #97

PR [#97](https://github.com/lambda9-gmbh/ready2race/pull/97) (Programmcode) und
[#98](https://github.com/lambda9-gmbh/ready2race/pull/98) (Arbeitsdokumente) sind am
07.08.2026 nach `main` gemergt; der Stand läuft seither auf rkf.ready2race.info. Das
vorherige Übersichtsdokument beschrieb den Inhalt von #97 und ist damit erledigt — es wurde
durch dieses hier ersetzt.

Dieses Dokument beschreibt, was **seit** dem Merge auf `feature/crf-2026` dazugekommen ist
und in einem Folge-MR an lambda9 gehen soll.

---

## 0. Migrations-Umbenennung: übernommen

Nach dem Merge von #97 hat lambda9 in `27cd66b9` („Update migration version for ocrrect
order") alle zehn CRF-Migrationen umbenannt, etwa
`V202608031200__event_schedule_slot.sql` → `V202608061205__…`. Der Branch trug bis zum
Merge-Commit `e657f8a4` noch die alten Namen.

Der Merge von `origin/main` hat das aufgelöst: weil der Branch seit dem Abzweig keine dieser
Dateien angefasst hat, übernimmt der Drei-Wege-Merge durchgängig mains Seite — die zehn
kommen als reine Umbenennungen ohne Inhaltsänderung an, die alten Namen sind weg. Damit ist
auch die Doppelbelegung von `V202608061200` aufgelöst: die Sichtbarkeitsregel für öffentliche
Ergebnisse heißt jetzt `V202608061210`, die Nummer `V202608061200` gehört lambda9s
Invoice-Hotfix.

Der Branch behält seine eigene `V202608071300__event_timing_presets.sql`, die nach allem
einsortiert, was von main kam.

**Achtung bei bestehenden Datenbanken:** die Umbenennung ist nur für *neue* Datenbanken
folgenlos. Eine Dev- oder Produktionsdatenbank, auf der die Migrationen noch unter den alten
Namen verzeichnet sind, sieht die umbenannten als unbekannt, läuft mit `outOfOrder(true)`
([Application.kt:49](../backend/src/main/kotlin/de/lambda9/ready2race/backend/Application.kt))
erneut hinein und scheitert an bereits existierenden Objekten. Für lokale Arbeit auf diesem
Stand eine frische Datenbank nehmen.

---

## 1. Änderungen seit dem Merge

### 1.1 Zeitnahme-Vorgaben an der Veranstaltung (`9f7d6003`, `98b17e19`)

Die Zeitnahme wird einmal je Veranstaltung gesetzt statt in jedem Wettkampf erneut. Der
Wettkampf-Tab zeigt die geerbten Werte als Text mit Link zur Veranstaltung, plus einen
Schalter: aus heißt „folgt der Veranstaltung und übernimmt spätere Änderungen dort", an gibt
dem Wettkampf eigene Werte. Beim Einschalten werden die aktuell geltenden Werte vorbelegt,
man ändert also von einem Zustand weg statt aus leeren Feldern heraus.

`98b17e19` zieht auch die beiden Dateiformate hoch (Startlisten-Export, Rennergebnis-Import,
Migration `V202608071300__event_timing_presets.sql`) — sechs geerbte Werte statt vier. Die
Lesestellen `coalesce`n Wettkampf vor Veranstaltung (`CompetitionMatchRepo` für den Export,
`CompetitionExecutionService` für den Import).

Gegenrichtung: die Zeitnahme-Karte der Veranstaltung listet, welche Wettkämpfe abweichen und
worin — System, Zeitfahren-Adresse, Läufe-Adresse je einzeln benannt
(`CompetitionTimingDeviationDto`). Eine **teilweise** Abweichung ist sonst am leichtesten zu
übersehen.

Nebenbei umbenannt: „Preset" heißt jetzt „Startlisten Export" und „Rennergebnisse Import" —
so wie ready2race es in der Konfiguration schon nennt.

### 1.2 Beispieldatei neben dem Zeitplan-Import (`cc87ca07`)

Der Import-Dialog setzte voraus, dass man das Spaltenlayout kennt. Er verlinkt jetzt eine
generierte Beispieldatei mit genau den Spalten, die der Import liest. Die Kopfzeile steht an
**einer** Stelle (`ScheduleImportTemplate`), auf die sich Parser und Vorlage beziehen — die
beiden können nicht auseinanderlaufen. Die Beispielzeilen sind auf den ersten
Veranstaltungstag datiert und decken die drei Fälle ab: Verknüpfung über Rennnummer, über
Kurzname, und eine Zeile ohne Wettkampf, die zum freien Programmpunkt wird.

### 1.3 Benennung des Kettenmodus

Das Feld hieß „Kettenmodus" mit den Werten „Schiedsrichter / Regattabüro / Deaktiviert". Das
liest sich wie **eine** Achse (wer ist zuständig), tatsächlich sind es zwei:

| | wer darf beenden | Kette schaltet weiter |
|---|---|---|
| `SCHIEDSRICHTER` | Schiedsrichter + Organisatoren | ja |
| `REGATTABUERO` | nur Organisatoren, über den Zeitplan | ja |
| `DEAKTIVIERT` | Schiedsrichter + Organisatoren | nein |

`DEAKTIVIERT` liegt gar nicht auf der Zuständigkeits-Achse — es ist „wie Schiedsrichter, nur
ohne Automatik". Die Anzeigetexte beantworten jetzt in jeder Zeile beide Fragen, das Feld
heißt „Lauf beenden & nächsten aktivieren", und ein Hinweis darunter sagt, dass die
Organisatoren über den Zeitplan **in jedem Fall** beenden können — das galt schon immer
([EventScheduleService.finishSlot](../backend/src/main/kotlin/de/lambda9/ready2race/backend/app/eventSchedule/boundary/EventScheduleService.kt)
ruft `finishMatchInternal` ungegatet auf), stand aber nirgends.

Geändert wurden ausschließlich Anzeigetexte in de/en/da. Die Enum-Werte in Datenbank, API und
Code bleiben `SCHIEDSRICHTER`/`REGATTABUERO`/`DEAKTIVIERT` (siehe Entscheidung 2.3).

### 1.4 Oberflächen-Angleichung (`49eca20d`, `116f8425`)

Beide Zeitnahme-Ansichten und die Wertungskategorien saßen in Karten, während ihre Nachbarn
im selben Tab schlichte Abschnitte mit h2-Überschrift und Hinweistext sind — eine Karte
dazwischen liest sich wie ein Fragment von einem anderen Screen. Jetzt einheitlich. Der
Button der Wertungskategorien heißt „zuordnen" statt eines nackten Plus, weil die Kategorien
in der Konfiguration entstehen und hier nur zugeordnet werden.

---

## 2. Entscheidungen

### 2.1 Vererbung statt Kopie

Beim Einschalten der Abweichung werden die geltenden Werte vorbelegt, aber nicht dauerhaft
kopiert: solange der Schalter aus ist, liest der Wettkampf **live** von der Veranstaltung und
übernimmt spätere Änderungen. Eine Kopie hätte genau das Problem erzeugt, das die
Vererbung lösen soll — man ändert die Adresse einmal oben und findet am Regattatag heraus,
dass drei Wettkämpfe noch auf das alte Rennen zeigen.

### 2.2 Eine Kopfzeile für Parser und Vorlage

`ScheduleImportTemplate` hält die Spaltenüberschriften; Import-Parser und generierte
Beispieldatei beziehen sich darauf. Die naheliegende Alternative — die Vorlage als statische
Datei ins Repo legen — hätte eine zweite Wahrheit geschaffen, die beim nächsten Spaltenwechsel
still veraltet.

### 2.3 Anzeigetexte umbenannt, Enum-Werte nicht

Die technischen Werte umzubenennen (etwa auf `REFEREE`/`OFFICE`/`MANUAL`) hätte eine
Flyway-Migration auf einer Spalte gebraucht, die lambda9 gerade erst gemergt hat, und wäre
mit deren Umbenennung aus Abschnitt 0 kollidiert. Sauberer wäre mittelfristig, das Feld in
**zwei** aufzuteilen (Zuständigkeit + Automatik an/aus) — das ist eine API-Änderung mit
Migration und Frontend-Umbau und gehört nicht in die Woche vor der Regatta.

**Folge:** Anzeigetexte und Enum-Werte sagen jetzt Unterschiedliches. Beim Lesen des Codes
ist `DEAKTIVIERT` weiterhin `DEAKTIVIERT`, im UI steht dort „nächster Lauf wird von Hand
aktiviert".

---

## 3. Verifikation

Stand: Merge-Commit `e657f8a4`.

- **Frontend: 406 Tests, 0 Failures** (14 Testdateien, vitest), `npm run build` grün.
  Enthält Konsistenztests der Übersetzungsschlüssel und neue Tests für
  `timingConfigForm`/`eventTimingConfigForm` (Vorbelegung beim Einschalten, Vererbung,
  Abweichungserkennung).
- **Migrationen gegen eine leere Datenbank:** alle **74** versionierten Migrationen in
  Versionsreihenfolge sauber durchgelaufen, danach `afterMigrate.sql` ebenfalls — Ergebnis
  107 Tabellen und 72 Views. Damit ist der gemergte Migrationssatz aus Abschnitt 0
  nachweislich widerspruchsfrei.
- **Backend:** `ScheduleImportTemplateTest` neu (Kopfzeile deckungsgleich mit dem Parser,
  Beispielzeilen datieren auf den ersten Veranstaltungstag). Der vollständige `./mvnw test`
  ist auf diesem Stand **noch nicht** gelaufen — nachzuholen, bevor der MR gestellt wird.
  Die Backend-Änderungen dieses Merges stammen allerdings sämtlich von main (Invoice-Hotfix);
  der Branch hat keine davon angefasst.
- **Real-Test:** die Zeitnahme-Vererbung, die Import-Beispieldatei und die umbenannten
  Anzeigetexte sind noch nicht gegen die laufende Anwendung durchgespielt.

---

## 4. Offene Punkte

- **`./mvnw test` steht aus** (siehe Abschnitt 3) — vor dem MR nachzuholen.
- **Sechs dänische Übersetzungsschlüssel fehlen**, alle unter
  `event.schedule.importDialog`: `template`, `templateError`, `templateHint` (aus `cc87ca07`)
  sowie `rowCompetitionNotFound`, `rowMatchNotFound`, `rowMatchNotFoundEmpty` (aus
  `7cad2501`). de und en sind vollständig. Fällt nicht in einen Test, weil die
  Konsistenztests nur die Fehlercode-Module abdecken — ein Test über **alle** Schlüssel wäre
  die eigentliche Lücke.
- **Anzeigetexte ≠ Enum-Werte** beim Kettenmodus (Entscheidung 2.3) — für Reviewer sichtbar,
  bewusst so.
- **Urkundenvorlagen als Paket teilen** wird gerade auf dem eigenen Branch
  `feature/urkundenvorlagen-teilen` (abgezweigt bei `cc87ca07`) gebaut und mündet später
  hierher. Noch nicht Teil dieses Dokuments.

---

## 5. Betrieb: was am gemergten Stand noch von Hand gesetzt werden musste

Kein Code-Thema, aber für das Review aufschlussreich — beim Inbetriebnehmen auf Prod fiel auf:

Neue Privilegien bekommt beim Start **nur die Admin-Rolle** automatisch
([initializeDatabase.kt:113](../backend/src/main/kotlin/de/lambda9/ready2race/backend/database/initializeDatabase.kt)).
Jede selbst angelegte Rolle bleibt außen vor. Auf Prod hatte deshalb ausgerechnet die Rolle
„Schiedsrichter:in" — Beschreibung: *„Erlaubnis, das Schiedsrichter Dashboard zu sehen"* —
kein `READ LIVE_DASHBOARD`, und die Kachel fehlte für alle außer Admins. Dasselbe galt für
„Orga Regatta Büro".

Das ist Bestandsverhalten, kein Fehler dieses Branches. Es heißt aber: **jede Auslieferung mit
einer neuen Privilegien-Ressource braucht einen manuellen Schritt in der Rollenverwaltung**,
und nichts im Produkt weist darauf hin. Ein Hinweis nach der Migration oder eine Rollen-Ansicht,
die neue, noch nirgends zugeordnete Privilegien ausweist, würde die Klasse Fehler abstellen.
