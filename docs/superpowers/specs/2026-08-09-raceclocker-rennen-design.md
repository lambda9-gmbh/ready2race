# Design: Benannte RaceClocker-Rennen und sparsamer Abruf

**Stand:** 2026-08-09
**Status:** Entwurf zur Umsetzung
**Branch:** `claude/raceclocker-polling-optimization-d69666`
**Betroffene Domains:** `raceclocker`, `timingConfig`, `competitionExecution`
**Vorgänger:** [2026-07-19 RaceClocker-Integration](2026-07-19-raceclocker-integration-design.md), [2026-08-05 Zeitnahme-Tab](2026-08-05-zeitnahme-tab-design.md), [2026-08-07 RaceClocker-Polling](2026-08-07-raceclocker-polling-design.md)

---

## 1. Ziel & Umfang

Eine Regatta fährt mehr als zwei RaceClocker-Rennen. Bei der Coastal-Rowing-Regatta sind es drei — **Timetrials**, **Langstrecke**, **Kurzstrecke** —, die sich in Startmechanismus und Zeitnahme unterscheiden. Das heutige Datenmodell kennt genau zwei Adressen, und welche gilt, wird aus `is_qualification` erraten statt angegeben.

Diese Arbeit ersetzt beides:

- Eine Veranstaltung führt **beliebig viele benannte RaceClocker-Rennen**. Jedes trägt seine Adresse einmal.
- Ein Wettkampf **wählt** seine Rennen an, statt Adressen zu wiederholen.
- Der automatische Abruf holt je Takt nur die Rennen, die **gerade gebraucht** werden, und den Rückfall nur **bei Bedarf**.

**Nicht Teil dieser Spec (YAGNI):**

- **Rundenzeiten.** Die Langstrecke erfasst sie, und sie sollen erfasst werden — aber das ist ein eigenes Vorhaben (neuer Feed-Parser, n Zeiten je Boot statt einer, Anzeige in Durchführung und Live-Dashboard). Vor allem fehlt die entscheidende Tatsache: Wie RaceClocker Rundenzeiten im JSON ausliefert, ist unbekannt. Im Repo gibt es kein Muster, in der Korrespondenz mit Cees steht nichts dazu, und die Testfixtures decken nur Start, Ergebnis, Rang und Strafe ab. Ein Parser auf Vermutungen ist die teuerste Art zu raten. Diese Spec legt mit `captures_laps` nur den Andockpunkt.
- **Startlisten-Presets.** Sie haben denselben Zwei-Slot-Zuschnitt und werden auf Dauer denselben Bruch erleiden (§8). Bewusst vertagt: kleinerer Eingriff kurz vor der Regatta.
- **Webscorer.** Kennt keine Adresse und bleibt unberührt.

---

## 2. Ausgangslage

**Datenmodell.** Zwei Adress-Spalten, je einmal auf `competition` ([V202607211200](../../../backend/src/main/resources/db/migration/V202607211200__raceclocker_integration.sql)) und als Voreinstellung auf `event` ([V202608062100](../../../backend/src/main/resources/db/migration/V202608062100__event_timing_defaults.sql)). Der Wettkampf hat Vorrang, gelesen per `coalesce`.

**Anwahl.** `RaceClockerMatchTarget` leitet aus `is_qualification` der Runde ab, welche Adresse gilt, und behandelt das ausdrücklich als Vermutung: Die andere Adresse ist Rückfall, falls der Lauf im ersten Rennen nicht auftaucht.

**Abruf.** `RaceClockerPollService.pollEvent` sammelt `candidateUrls` **aller** beobachteten Läufe, entdoppelt sie und holt jede genau einmal. `candidateUrls` liefert stets *beide* Adressen — die angewählte und den Rückfall.

**Der Knopf ist bereits sparsam.** `updateMatchResultFromRaceClocker` bricht ab, sobald der Lauf zugeordnet ist. Nur der automatische Abruf holt den Rückfall bedingungslos.

### Wo tatsächlich Kapazität verbrannt wird

Zwei Stellen, beide real:

1. **Der bedingungslose Rückfall.** Bei einer Veranstaltung mit beiden Adressen wird jeder zweite Abruf nie gebraucht — dauerhaft, im Fünf-Sekunden-Takt.
2. **Die Skalierung.** Drei Rennen lassen sich heute nur über Wettkampf-Overrides abbilden. Dann trägt jeder Wettkampf sein eigenes Adresspaar bei, und die Zahl der Abrufe je Takt wächst mit der Zahl der **Wettkämpfe** statt mit der Zahl der **Rennen**.

Ausdrücklich *keine* Verschwendung: mehrfaches Holen derselben Adresse innerhalb eines Takts. Das entdoppelt der Job bereits.

---

## 3. Datenmodell

### 3.1 Neue Tabelle `raceclocker_race`

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | uuid, PK | |
| `event_id` | uuid, FK → `event`, `on delete cascade` | Ein Rennen gehört zu genau einer Veranstaltung |
| `name` | text, not null | „Timetrials", „Langstrecke", „Kurzstrecke" — wonach angewählt wird |
| `results_url` | text, not null | Die öffentliche Ergebnis-Adresse |
| `start_mode` | text, not null | `INDIVIDUAL` \| `WAVE` |
| `captures_laps` | boolean, not null, default false | Untätig in dieser Spec; Andockpunkt für die Rundenzeiten |
| `position` | int, not null | Sortierung in der Oberfläche |
| Audit | | `created_at`, `created_by`, `updated_at`, `updated_by` wie überall |

`name` ist je `event_id` eindeutig — zwei Rennen gleichen Namens wären in einem Auswahlfeld nicht unterscheidbar, und genau am Renntag ist das der Fehler, der weh tut.

`results_url` ist **not null**: Ein Rennen ohne Adresse hat keinen Zweck, und ein leerer Wert würde als „nicht konfiguriert" durch den Abruf wandern, statt beim Speichern aufzufallen. Die Adresse wird beim Anlegen über `RaceClockerFeed.normalizeUrl` geprüft und normalisiert gespeichert — dieselbe Host-Allowlist, die den Endpunkt vor SSRF schützt.

**Warum `start_mode` seinen Platz verdient.** Er ist die Eigenschaft, in der der Bediener denkt („bei Timetrials und Kurzstrecke ist der Startmechanismus ein anderer"). Er trägt aber auch eine prüfbare Regel: Eine gemappte Lauf-Spalte kippt ein RaceClocker-Rennen in den Wave-Modus, und dann ist der Countdown am Start weg. Mit dieser Spalte lässt sich das künftig als Warnung zeigen, statt es am Renntag zu bemerken. Die Warnung selbst gehört nicht in diese Spec.

### 3.2 Anwahl auf `event` und `competition`

Je zwei nullable FK-Spalten:

| Spalte | Bedeutung |
|---|---|
| `raceclocker_race_qualification` | Rennen für Qualifikationsrunden |
| `raceclocker_race_rounds` | Rennen für alle übrigen Runden |

Beide `references raceclocker_race on delete set null` — wird ein Rennen gelöscht, verliert die Anwahl ihren Verweis, statt den Löschvorgang zu blockieren.

Gelesen wird wie heute `coalesce(competition.…, event.…)`: Die Veranstaltung ist Voreinstellung, der Wettkampf hat Vorrang. Das erhält die bestehende Anzeige „abweichende Wettkämpfe" im Zeitnahme-Tab; sie nennt künftig das abweichende Rennen beim Namen, statt nur „hat eine eigene Adresse" sagen zu können.

**Warum die Zweiteilung bleibt.** Ein Wettkampf kann eine Qualifikation als Zeitfahren und seine übrigen Runden als Läufe fahren — das ist der Normalfall und der Grund, warum es die Zweiteilung überhaupt gibt. Eine einzige Anwahl je Wettkampf könnte das nicht abbilden. Eine Anwahl je Runde wäre flexibler, kostet aber Pflegeaufwand für einen Fall, den es nicht gibt.

**Integrität über Veranstaltungsgrenzen.** Ein Fremdschlüssel allein verhindert nicht, dass ein Wettkampf ein Rennen einer *anderen* Veranstaltung anwählt. Der Service prüft das beim Speichern; die Datenbank erzwingt es nicht, weil der dafür nötige zusammengesetzte Schlüssel (`event_id, id`) den übrigen Tabellen dieses Projekts fremd wäre.

### 3.3 Was wegfällt

`raceclocker_tt_results_url` und `raceclocker_heats_results_url` — je auf `event` und `competition`, also vier Spalten.

**In zwei Migrationen, nicht in einer** (Ausbau/Abbau). Die erste legt Tabelle, Anwahl und Backfill an und lässt die alten Spalten stehen; eine zweite entfernt sie, sobald kein Code sie mehr liest. Der Grund ist zwingend und wurde bei der Umsetzung entdeckt: Kotlin übersetzt alle Hauptquellen als eine Einheit, und jOOQ erzeugt seine Klassen aus dem migrierten Schema. Fielen die Spalten schon in der ersten Migration, wäre der Zweig von da an bis zur letzten umgestellten Aufrufstelle nicht mehr übersetzbar — keine Zwischenstufe ließe sich testen, auch nicht in Bereichen, die mit der Zeitnahme nichts zu tun haben. Getrennt bleibt jeder Stand prüfbar, und die Umstellung ließe sich sogar in zwei unabhängigen Schritten ausrollen.

---

## 4. Migration (`V202608091600__raceclocker_races.sql`)

Ziel: Für CRF 2026 ändert sich im laufenden Betrieb nichts.

1. Tabelle und Anwahl-Spalten anlegen.
2. **Rennen aus dem Bestand erzeugen, entdoppelt über die Adresse.** Je Veranstaltung wird die Menge aller dort vorkommenden Adressen gebildet — aus `event` und aus allen `competition` derselben Veranstaltung. Zwei Wettkämpfe mit derselben Override-Adresse teilen sich damit **ein** Rennen, statt zwei gleichlautende zu erzeugen. Das ist der Punkt, an dem die Migration die Schulden tilgt, statt sie zu übertragen.
3. **Benennung** aus der Herkunft:
   - Adresse der Veranstaltung → `Zeitfahren` bzw. `Läufe`
   - nur bei Wettkämpfen vorkommende Adresse → `Zeitfahren <Kürzel>` bzw. `Läufe <Kürzel>`. `<Kürzel>` ist der `short_name` des ersten Wettkampfs, der sie benutzt — der liegt auf `competition_properties`, nicht auf `competition`, und ist dort **nullable**. Ist er leer, tritt `identifier` an seine Stelle (not null). Gemeint ist durchweg das Kürzel, nicht die Rennnummer.
   - Namenskollisionen bekommen ein numerisches Suffix — Eindeutigkeit je Veranstaltung ist erzwungen, und eine Migration darf nicht an einem Datenzufall scheitern.
4. **`start_mode`** ergibt sich aus der Herkunftsspalte: `RACECLOCKER_TT_RESULTS_URL` → `INDIVIDUAL`, `RACECLOCKER_HEATS_RESULTS_URL` → `WAVE`. Kommt dieselbe Adresse in beiden Spalten vor, entsteht **ein** Rennen mit `WAVE` — die Adresse kann nur einem Rennen entsprechen, und der Wave-Modus ist der, in den RaceClocker beim Import mit Lauf-Spalte selbsttätig kippt.
5. **Anwahl setzen**: `event` und jeder `competition` zeigen auf die Rennen, die ihren bisherigen Adressen entsprechen. Ein Wettkampf, der nur eine der beiden Adressen überschrieben hatte, behält für die andere `null` und erbt sie weiterhin.
6. `captures_laps` bleibt überall `false`. Die Langstrecke wird nach der Migration von Hand gekennzeichnet — die Migration kann nicht wissen, welches der erzeugten Rennen die Langstrecke ist.
7. Die vier alten Spalten löschen.

**Migrationsnummer.** `V202608091600` ist frei — die höchste im Zweig ist `V202608091410`, und `V202608091500` ist auf einem parallelen Zweig (Auto-Abgleich Durchführung) bereits vergeben. Vor dem Merge erneut prüfen.

**Vor dem Merge** wird die Migration gegen einen Prod-Abzug von CRF 2026 durchgespielt. Das ist keine Kür: Schritt 2 und 3 hängen an echten Daten, und die Regatta ist am 14.08. — in fünf Tagen.

---

## 5. Anwahl im Code

`RaceClockerMatchTarget` behält seine Rolle — „wo finde ich diesen Lauf" — und wechselt von Adressen auf Rennen:

```kotlin
data class RaceClockerMatchTarget(
    val waveName: String?,
    val isQualification: Boolean,
    val qualificationRace: RaceClockerRaceRef?,
    val roundsRace: RaceClockerRaceRef?,
)
```

`resultsUrl`, `alternateResultsUrl` und `candidateUrls` bleiben als abgeleitete Werte erhalten, damit die Aufrufer (`updateMatchResultFromRaceClocker`, die Fehlermeldung `MatchNotInFeed`) unverändert weiterlaufen. Neu ist allein, woher die Adresse kommt.

`RaceClockerRaceRef` trägt `id`, `name` und `resultsUrl`. Den **Namen** braucht die Fehlermeldung: „Lauf im Rennen *Kurzstrecke* nicht gefunden" ist am Renntag brauchbar, eine nackte URL nicht.

Die beiden Repo-Lesestellen — `RaceClockerPollRepo.getCandidates` und `CompetitionMatchRepo.getForRaceClockerPull` — ersetzen ihr `coalesce` über Adressspalten durch ein `coalesce` über die Anwahl-Spalten plus je einen Join auf `raceclocker_race`.

---

## 6. Der Abruf

### 6.1 Warum `pollEvent` in Phasen zerfällt

Ein bedarfsweiser Rückfall verlangt, dass „gefunden oder nicht" **vor** der zweiten Abrufrunde feststeht. Ob ein Lauf gefunden ist, ergibt sich aus `assignedRowsFor(rows, teams, waveName)` — und `teams` stammt aus `checkUpdateMatchResult`. Heute stecken Auflösen, Zuordnen und Schreiben gemeinsam in `pollMatch`; das lässt sich nicht in zwei Abrufrunden teilen.

`pollMatch` zerfällt deshalb in drei Teile mit je einer Aufgabe:

| Teil | Aufgabe | Prüfbar ohne |
|---|---|---|
| Auflösen | Turnierstruktur und Mannschaften eines Laufs, oder „still überspringen" | — (Datenbank) |
| Zuordnen | Welche Feed-Zeilen gehören zu diesem Lauf | Datenbank **und** HTTP |
| Schreiben | Aktivierung, Ist-Start, Ergebnisse | — (Datenbank) |

Das Zuordnen wird damit zu einer reinen Funktion — dieselbe Trennung, die `RaceClockerPollLogic` schon für die Takt-Entscheidungen leistet, und aus demselben Grund: Am Renntag zählt, dass diese Regeln stimmen.

### 6.2 Der neue Ablauf

1. **Vorbereiten.** Turnierstruktur je Wettkampf einmal laden (unverändert), `checkUpdateMatchResult` je beobachtetem Lauf. Läufe, die die Prüfung nicht bestehen — gesperrt, Freilos, leere Struktur —, fallen still heraus. Unverändert: Das ist kein Abruf-Fehler.
2. **Runde 1.** Die *angewählten* Rennen der verbliebenen Läufe, entdoppelt, holen. Je Lauf zuordnen.
3. **Runde 2.** Nur für Läufe ohne Treffer, die ein anderes Rennen kennen: dessen Adresse holen — wieder entdoppelt, und ohne das erneut zu holen, was Runde 1 schon hat — und erneut zuordnen. Im gesunden Betrieb ist diese Runde leer.
4. **Schreiben.** Je Lauf wie heute, mitsamt Fingerabdruck-Abkürzung, Pausen-Prüfung innerhalb der Transaktion und `recordPoll`.

Die Fehlerbehandlung bleibt erhalten: Antwortet keines der Rennen, wandert der Fehlercode wie bisher in `raceclocker_poll_error`. Kennt kein Rennen die Welle, ist das vor dem Start der Normalfall und keine Störung.

### 6.3 Isolation bleibt in jeder Phase

`runIsolated` umschließt heute den ganzen `pollMatch`. Mit der Aufteilung braucht **jede** Phase je Lauf dieselbe Klammer — sonst reißt ein Defekt beim Auflösen des ersten Laufs alle noch nicht besuchten Läufe des Takts mit. Das ist die Falle dieser Umstellung und der Punkt, an dem beim Review hinzusehen ist.

Ebenfalls beizubehalten: `KIO.fail` ohne vorangestelltes `!` ist ein No-Op — eine Fehlerklasse, die dieses Projekt schon einmal getroffen hat. Der neue Code ist voller früher Ausstiege und damit genau der Ort, an dem sie wieder entsteht.

### 6.4 Was das bringt

Die Regel dahinter ist wichtiger als jede einzelne Zahl:

- **heute:** Abrufe je Takt = Zahl aller *konfigurierten* Adressen, unabhängig davon, was gerade gefahren wird.
- **neu:** Abrufe je Takt = Zahl der Rennen, die *gerade* gefahren werden.

Aktiver Takt 5 s, also 12 Takte/min:

| Lage | heute | neu |
|---|---|---|
| Zwei Adressen gesetzt, nur Läufe unterwegs | 24 Abrufe/min | **12** |
| Drei Rennen, Kurzstrecke + Langstrecke gleichzeitig | 36–48 Abrufe/min¹ | **24** |
| Drei Rennen, alle drei gleichzeitig unterwegs | 36–48 Abrufe/min¹ | **36** |

¹ Drei Rennen sind heute nur über Wettkampf-Overrides darstellbar; jeder Wettkampf trägt sein Adresspaar bei.

Im dritten Fall ist der Gewinn klein — und das ist der ehrliche Befund: Wer wirklich drei Rennen gleichzeitig fährt, muss auch dreimal fragen. Der Gewinn liegt in den ersten beiden Zeilen, also im Normalbetrieb einer Regatta, in dem selten alle Rennen zugleich unterwegs sind.

---

## 7. Oberfläche

**Zeitnahme-Tab der Veranstaltung** (`EventTimingConfig.tsx`): An die Stelle der zwei Adressfelder tritt eine Liste der Rennen mit Anlegen, Bearbeiten und Löschen — je Zeile Name, Adresse, Startart und das Kennzeichen für Rundenzeiten. Darunter die zwei Auswahlfelder für die Voreinstellung. Löschen fragt nach, wenn das Rennen noch angewählt ist, und nennt die betroffenen Wettkämpfe.

**Zeitnahme-Tab des Wettkampfs** (`CompetitionTimingConfig.tsx`): Die zwei Adressfelder werden zu zwei Auswahlfeldern über die Rennen der Veranstaltung. Leer bedeutet geerbt — wie heute, und die Oberfläche zeigt weiterhin an, *was* geerbt würde.

**Abweichungs-Anzeige**: Nennt das abweichende Rennen beim Namen.

**Endpunkte**: `raceclocker-race` unterhalb der Event-Route (Liste, Anlegen, Ändern, Löschen), Berechtigung wie beim übrigen Zeitnahme-Tab — `ReadEventGlobal` zum Lesen, `UpdateEventGlobal` zum Ändern.

---

## 8. Bewusst offen gelassen

**Die Startlisten-Presets tragen denselben Bruch.** `startlist_config_qualification` / `startlist_config_rounds` haben den gleichen Zwei-Slot-Zuschnitt wie die Adressen, und [V202608071300](../../../backend/src/main/resources/db/migration/V202608071300__event_timing_presets.sql) begründet ihn genau so: „weil sie in dasselbe Rennen im Fremdsystem importiert werden". Brauchen drei Rennen drei verschiedene Startlisten, bricht das Paar auf dieselbe Weise. Der saubere Ort wäre das Rennen. Vertagt, weil der Umbau `StartListConfigTarget`, den Export-Weg und den Zeitnahme-Tab berührt und die Regatta am 14.08. ist.

**Rundenzeiten** — eigener Zyklus, siehe §1. Erster Schritt dort ist kein Code, sondern ein echter Langstrecken-Feed mit Rundenzeiten zum Ansehen.

---

## 9. Absicherung

**Reine Tests** (ohne Datenbank, ohne HTTP):

- Fetch-Planung: Welche Adressen in Runde 1, welche in Runde 2; ein Lauf ohne Rückfall löst keine zweite Runde aus; Runde 2 holt nichts erneut, was Runde 1 schon hat.
- `RaceClockerMatchTarget` auf dem neuen Modell: Anwahl, Rückfall, beide leer.

**Datenbank-Tests** über `testComprehension` gegen echtes Postgres (Testcontainers — laufen in ~6 s und werden in diesem Projekt regelmäßig übersehen):

- `RaceClockerPollRepoTest`: Vererbung der Anwahl, Wettkampf-Override, Rennen ohne Anwahl.
- Ein Test, dass ein gelöschtes Rennen die Anwahl auf `null` setzt und der Abruf den Lauf danach still überspringt statt zu scheitern.

**Von Hand** (in den Testkatalog CRF 2026 aufnehmen — dort stehen bereits ungetestete Fälle aus anderen Zweigen):

1. Drei Rennen anlegen, einem Wettkampf zuweisen, Abruf beobachten.
2. Wettkampf-Override setzen und wieder leeren; prüfen, dass die Vererbung greift.
3. Absichtlich falsches Rennen anwählen; prüfen, dass der Rückfall greift und die Regatta weiterläuft.
4. Ein angewähltes Rennen löschen; prüfen, dass die Oberfläche warnt.
5. Migration gegen den Prod-Abzug CRF 2026 (§4).

---

## 10. Reihenfolge der Umsetzung

1. Migration und jOOQ-Neugenerierung
2. `raceclocker_race` — Entity, Repo, Service, Endpunkte
3. Anwahl in `RaceClockerMatchTarget` und den beiden Repo-Lesestellen; Knopf-Weg grün
4. `pollEvent` in Phasen zerlegen, Zuordnung als reine Funktion herauslösen
5. Zweistufiger Abruf
6. Oberfläche: Rennen-Liste, Auswahlfelder, Abweichungs-Anzeige
7. Tests und Prod-Abzug-Probe

Schritt 3 hält den bestehenden Weg lauffähig, bevor Schritt 4 ihn umbaut — so bleibt jederzeit ein Zustand, in dem die Regatta gefahren werden könnte.
