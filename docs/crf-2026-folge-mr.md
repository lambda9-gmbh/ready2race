# CRF 2026 — Folge-MR nach PR #97

PR [#97](https://github.com/lambda9-gmbh/ready2race/pull/97) (Programmcode) und
[#98](https://github.com/lambda9-gmbh/ready2race/pull/98) (Arbeitsdokumente) sind am
07.08.2026 nach `main` gemergt; der Stand läuft seither auf rkf.ready2race.info. Das
vorherige Übersichtsdokument beschrieb den Inhalt von #97 und ist damit erledigt — es wurde
durch dieses hier ersetzt.

Dieses Dokument beschreibt, was **seit** dem Merge auf `feature/crf-2026` dazugekommen ist
und in einem Folge-MR an lambda9 gehen soll.

**Stand:** `3b78e87c`. Der Branch enthält `origin/main` vollständig.

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

Der Branch bringt zwei eigene Migrationen mit, beide nach allem einsortiert, was von main kam:

| Version | Inhalt | Abschnitt |
|---|---|---|
| `V202608071200__referee_check_severity.sql` | `competition_properties.check_in_out_required`, Tabelle `competition_check_severity` | 1.7 |
| `V202608071300__event_timing_presets.sql` | Dateiformate an der Veranstaltung | 1.1 |

Beide sind rein additiv. `V202608071600` ist für die RaceClocker-Polling-Arbeit reserviert,
die noch außerhalb des Branches liegt (Abschnitt 4).

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

### 1.5 Lauf-Status auf allen Oberflächen (`db043424` … `31135e3b`, Merge `a712c285`)

Die Durchführungsseite kannte nur „aktiv" und „nicht aktiv" — beendet, abgesagt und noch nie
angefasst sahen dort gleich aus. Ursache war das DTO: es trug nur `currentlyRunning`.

Neues Backend-Modul `app/matchStatus` mit `MatchStatusDto` und `MatchStatusLogic`. Die
**Ableitung wurde nicht verschoben**: `MatchStatusLogic` ruft `LiveDashboardLogic.deriveMatchState`
auf. Damit bleibt es bei genau einer Stelle, an der die Zweig-Reihenfolge festgelegt ist, und
genau einem Test, der sie festnagelt. Die Aufzählung bekommt bewusst **keine** neuen Werte —
„überfällig" und „teilweise gewertet" sind Ablesungen aus `teamsScored`/`teamsTotal` und
`startedAt`, kein eigener Zustand.

Im Frontend macht `components/event/match/matchStatusChip.ts` daraus genau eine Aussage:
Anstehend, Überfällig, Läuft, Teilweise gewertet, Wartet auf Beenden, Beendet, Abgesagt,
Ungeplant. Reine Funktionen ohne Rendering, mit Tests. Überfällig und die verstrichenen
Minuten rechnet der Client gegen die Browseruhr (Schwelle 5 Minuten), damit der Chip zwischen
zwei Abrufen weiterzählt statt zu stehen.

Sichtbar wird das an drei Stellen: Chip auf jeder Lauf-Karte der Durchführungsseite plus
Zählerleiste (erst ab zwei Läufen); im Zeitplan entscheidet `stateChipProps` bei verknüpftem
Lauf über den **Lauf**-Status statt über den Slot-Zustand „Verknüpft", der nur eine Aussage
über den Plan war; und auf den öffentlichen Anzeigen bleibt ein **abgesagter Lauf an seiner
geplanten Stelle stehen** — gedimmte Karte, „Findet nicht statt", ohne Mannschaften und
Countdown. Bisher verschwand er spurlos, und für eine Besatzung am Steg ist ein spurlos
verschwundener Lauf nicht von einem Anzeigefehler zu unterscheiden.

Dazu `EventScheduleSlotDto.matchTeamsTotal`/`matchTeamsScored` als korrelierte Unterabfrage in
der vorhandenen Slot-Abfrage — eine Abfrage bleibt eine Abfrage. „Gewertet" ist wortgleich zu
`LiveDashboardLogic.teamHasResult`; die aus der Vorrunde mitgeführten OUT-Zeilen bleiben
draußen, sonst stünde im Zeitplan 2/6, wo Dashboard und Durchführungsseite 2/4 zeigen.

### 1.6 Wasser-Chip (`14fe10bb`)

Neben dem Status-Chip steht, wie viele Mannschaften eines Laufs abgelegt haben („Wasser 2/6").
Die Entscheidung je Mannschaft trifft unverändert `LiveDashboardLogic.teamOnWaterAt` (jede
bekannte Person zuletzt EXIT) — dieselbe Regel wie im Schiedsrichter-Dashboard, ein Ort.

Die Scans holt eine Abfrage **je Wettkampf** statt je Veranstaltung, weil die Seite immer genau
einen Wettkampf zeigt: ein Index-Zugriff auf `participant_tracking`, kein Join je Lauf, kein
N+1 je Mannschaft. Hatte kein Team der Runde je einen Scan, läuft die Veranstaltung ohne
Check-in — dann bleibt `teamsOnWater` **null statt 0**, sonst stünde bei jedem Lauf dauerhaft
„Wasser 0/6".

### 1.7 Prüfungsschweregrad je Wettkampf (`93017cca`)

Die Ampel im Schiedsrichter-Dashboard war fest verdrahtet. Am Regattatag wird aber
unterschiedlich entschieden — eine offene Rechnung soll einem Boot an einem bestimmten Tag
bewusst nicht angelastet werden. Die einzige Möglichkeit war, rote Zeilen zu ignorieren, und
eine Ampel, auf die niemand reagiert, hört auf, eine Ampel zu sein.

Zwei Fragen, bewusst getrennt gehalten:

- **Gilt die Prüfung überhaupt?** Eigenschaft des Rennformats, liegt jetzt am Wettkampf
  (`competition_properties.check_in_out_required`). Beach Sprint hat kein Check-in/out am
  Steg, die Langstrecke schon.
- **Wie hart wird sie angelastet?** Regattatags-Entscheidung, je Wettkampf in
  `competition_check_severity` als `OK`, `WARNING` oder `CRITICAL`.

Die Schweregrad-Tabelle hält **nur Abweichungen**. Ohne Zeile gilt der eingebaute Standard,
und der bildet das bisherige Verhalten exakt ab — daher kein Datenmigrationsschritt, und eine
bestehende Regatta sieht unverändert aus.

### 1.8 Warum eine Importzeile zum freien Slot wurde (`7cad2501`)

Die Vorschau des Zeitplan-Imports beschriftete drei verschiedene Situationen gleich: eine
Zeile, deren Wettkampftext auf nichts passte, eine Zeile mit gefundenem Wettkampf aber anders
geschriebenem Laufnamen, und ein tatsächlich freier Programmpunkt lasen sich alle als „Freier
Slot" — ein Tippfehler in der Datei sah aus wie eine gewollte Pause.

Die Zuordnung läuft jetzt zweistufig, erst Wettkampf, dann Lauf darin. Damit kann eine
verfehlte Zeile sagen, an welcher Stufe sie gescheitert ist; wurde der Wettkampf gefunden, der
Laufname aber nicht, listet die Zeile die Laufnamen auf, die dieser Wettkampf tatsächlich hat.

### 1.9 Urkundenvorlagen als Paket teilen — nur Entwurf (`68d98012`, `de42b32d`)

Auf dem Branch liegen **ausschließlich** das Design (`docs/urkundenvorlagen-teilen-design.md`)
und der Umsetzungsplan (`docs/urkundenvorlagen-teilen-plan.md`). **Implementierungscode ist
nicht enthalten** — der entsteht auf einem eigenen Branch (Abschnitt 4).

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

### 2.4 Lauf-Status: eine Ableitung, keine neue Aufzählung

`MatchStatusLogic` ruft `LiveDashboardLogic.deriveMatchState` auf, statt die Ableitung in ein
neutrales Modul zu ziehen. Das sieht nach falscher Abhängigkeitsrichtung aus und ist bewusst
so: die Zweig-Reihenfolge (was passiert, schlägt den zurückgenommenen Plan) ist die eigentliche
Fachlogik, und sie soll an **einer** Stelle stehen, mit **einem** Test, der sie festnagelt.
Ein zweiter Ableitungsort wäre still auseinandergelaufen.

Aus demselben Grund bekommen „überfällig" und „teilweise gewertet" keine Enum-Werte: beides
ist aus vorhandenen Feldern ablesbar. Ein Zustand, den niemand setzt, kann nicht falsch
gesetzt sein.

Der Preis: `matchStatusChip.ts` bildet dieselbe Reihenfolge im Frontend nach, weil der Zeitplan
kein fertiges `MatchStatusDto` bekommt, sondern eine Zeile je Slot (`slotMatchStatus`). Das ist
eine zweite Stelle mit derselben Ordnung — abgesichert über Tests auf beiden Seiten, aber ein
Punkt, den ein Review sehen sollte.

### 2.5 Schweregrade: Tabelle hält nur Abweichungen

`competition_check_severity` bekommt beim Anlegen eines Wettkampfs keine Zeilen. Ohne Zeile
gilt der eingebaute Standard. Das spart nicht nur die Datenmigration — es heißt auch, dass ein
später geänderter Standard automatisch überall greift, wo niemand bewusst abgewichen ist. Die
Alternative (jede Kombination materialisieren) hätte den Standard beim ersten Anlegen
eingefroren.

### 2.6 `null` statt `0` beim Wasser-Chip

Fehlt jeder Scan, ist `teamsOnWater` null und der Chip verschwindet, statt „Wasser 0/6" zu
zeigen. „Null von sechs" ist eine Aussage über die Boote; bei einer Veranstaltung ohne
Check-in wäre sie schlicht falsch und würde am Steg zu Rückfragen führen.

---

## 3. Verifikation

Alle Zahlen gemessen auf `3b78e87c`.

| | Ergebnis |
|---|---|
| Backend `./mvnw test` | `Tests run: 419, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS` |
| Frontend `npm run test` | `Test Files 16 passed (16)` · `Tests 448 passed (448)` |
| `npm run generate` | kein unstaged Diff — die generierten API-Typen sind aus der gemergten `documentation.yaml` reproduzierbar |
| `./mvnw generate-sources` | Exit 0, jOOQ aus den gemergten Migrationen fehlerfrei |
| `tsc -b` | **6 Fehler in 5 Dateien** — siehe unten |
| `npm run lint` | **136 Meldungen (57 Fehler, 79 Warnungen)** |

**Migrationen gegen eine leere Datenbank:** alle 74 versionierten Migrationen in
Versionsreihenfolge sauber durchgelaufen, danach `afterMigrate.sql` ebenfalls — 107 Tabellen,
72 Views. Gemessen beim Merge von `origin/main` (`e657f8a4`); die beiden seitdem
hinzugekommenen Migrationen aus Abschnitt 0 sind darin noch nicht enthalten.

**Die 6 TypeScript-Fehler und die 136 Lint-Meldungen sind vorbestehend, nicht neu.** Gegen die
Basis gemessen: derselbe Stand ohne die zuletzt gemergte Urkunden-Linie hatte ebenfalls 6
Fehler in denselben 5 Dateien und dieselben 136 Meldungen. Es sind **zwei** Ursachen:

- **4 Fehler in 3 Testdateien** — Fixtures, denen Pflichtfelder fehlen, die die DTOs seit 1.5
  führen: `common.test.ts` und `timelineIndicator.test.ts` lassen
  `matchTeamsTotal`/`matchTeamsScored` an `EventScheduleSlotDto` weg,
  `roundCancellation.test.ts` fehlen `skipped` und `status` an `CompetitionMatchDto`,
  `editMatchForm.test.ts` führt `skipped` optional statt verpflichtend.
- **2 Fehler in Produktivcode** — `CompetitionExecutionRound.tsx:80`: `t()` wird mit einer
  `string`-Variablen statt einem Literal-Schlüssel aufgerufen, liefert dadurch `unknown`, und
  das landet als `label` an einem MUI-`Chip`, der `ReactNode` erwartet.

Die Tests laufen grün, weil vitest nicht typprüft; `npm run build` (`tsc -b && vite build`)
fällt darüber. **Beides ist vor dem MR zu beheben** — der zweite Punkt ist der interessantere,
weil er echten Code betrifft und nicht nur Testdaten.

**Real-Test: nichts davon ist gegen die laufende Anwendung durchgespielt.** Weder der
Lauf-Status auf den drei Oberflächen, noch der Wasser-Chip, noch die Schweregrade, noch die
Zeitnahme-Vererbung, noch die umbenannten Anzeigetexte, noch die Import-Diagnose.

---

## 4. Offene Punkte

**Vor dem MR zu erledigen:**

- **6 TypeScript-Fehler** (Abschnitt 3) — `npm run build` fällt darüber. Vier sind
  Testfixtures ohne die neuen Pflichtfelder, zwei sitzen in
  `CompetitionExecutionRound.tsx` im Produktivcode.
- **Sechs dänische Übersetzungsschlüssel fehlen**, alle unter
  `event.schedule.importDialog`: `template`, `templateError`, `templateHint` (aus `cc87ca07`)
  sowie `rowCompetitionNotFound`, `rowMatchNotFound`, `rowMatchNotFoundEmpty` (aus
  `7cad2501`). de und en sind vollständig. Fällt in keinen Test, weil die Konsistenztests nur
  die Fehlercode-Module abdecken — ein Test über **alle** Schlüssel wäre die eigentliche Lücke.
- **Kein Durchlauf gegen die laufende Anwendung** (Abschnitt 3).

**Arbeit, die noch außerhalb des Branches liegt** — jede davon mündet nach `feature/crf-2026`,
keine ist enthalten:

| Branch | offene Commits | Inhalt |
|---|---|---|
| `worktree-urkundenvorlagen` | 23 | Implementierung des Vorlagen-Austauschs und der Editor-Verbesserungen zu 1.9 |
| `claude/raceclocker-polling-53dac0` | 25 | RaceClocker-Polling; belegt `V202608071600` |
| `claude/schiedsrichter-pr-fungsschweregrad-12750a` | 19 | Vorgängerbranch zu 1.7; Überschneidung vor dem Merge prüfen |
| `claude/kio-fail-audit` | 2 | QR-Code-Löschung meldet wieder 404, mit Test |
| `claude/backlog-b2-fc060e` | 47 | Athleten-Anzeige |
| `feature/zeitstrahl` | 127 | historisch, vermutlich vollständig über andere Wege enthalten — vor dem Aufräumen prüfen |

**Bewusst so belassen:**

- **Anzeigetexte ≠ Enum-Werte** beim Kettenmodus (Entscheidung 2.3).
- **Zwei Orte für die Status-Reihenfolge**, Backend und Frontend (Entscheidung 2.4).

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
