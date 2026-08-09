# Freilosläufe sichtbar machen

Stand 09.08.2026. Ziel: Ein Freilos soll in Zeitplan, Schiedsrichter-Dashboard und
Durchführungsübersicht nicht mehr wie ein gewöhnlicher, noch ergebnisoffener Lauf aussehen.
Sichtbar bleibt es überall — ein still verschwundener Lauf ist am Steg nicht von einem
Anzeigefehler zu unterscheiden.

**Nicht Gegenstand dieser Änderung:** Knöpfe, Berechtigungen, Statusübergänge und die Quittierung.
Der Schiedsrichter quittiert das Freilos weiterhin genau wie heute.

## 1. Bestandsaufnahme

### Wie ein Freilos heute erkannt wird

Drei Stellen meinen dasselbe:

| Ort | Regel |
|---|---|
| `CompetitionExecutionService.checkUpdateMatchResult` (sperrt die Ergebniseingabe) | Runde nicht `required` **und** genau eine Team-Zeile |
| `CompetitionExecution.matchesFiltered` + Panel „Teams mit Freilos" | dasselbe, auf dem DTO, in dem `out`-Zeilen bereits fehlen |
| `automaticFirstPlace` bei der Rundenerzeugung | Runde nicht `required` **und** genau ein aktives Boot → Platz 1 |

`getProgress` filtert `out`-Zeilen heraus, bevor das DTO entsteht; die Frontend-Regel
`teams.length === 1` ist damit **gleichbedeutend** mit „genau eine nicht-`out`-Zeile".

### Statusmodell

`LiveDashboardLogic.deriveMatchState` → `MatchStatusDto` → `matchStatusChip.ts` ist bereits die
einzige Quelle für den Lauf-Zustand, gelesen von allen drei Ansichten. Hier hängt sich die
Ergänzung an.

### Quittierung

Kein eigener Mechanismus. Das Freilos bekommt bei der Rundenerzeugung Platz 1, der Lauf steht
damit auf `AWAITING_FINISH`, der Schiedsrichter drückt „Lauf beenden" → `FINISHED`. Entfällt die
ganze Runde, wird der Slot `SKIPPED`. Es gibt nichts anzufassen.

### Was die Ansichten heute sehen

Die `out`-Zeilen — und damit der abgemeldete Gegner — fehlen in **allen drei** Wegen:
`CompetitionExecutionService.getProgress` filtert sie, `LiveDashboardRepo.getTeams` filtert sie
per `OUT.isTrue.not()`, und der Zeitplan bekommt ohnehin nur Zähler. Die Ursache eines Freiloses
kann deshalb nur das Backend feststellen.

## 2. Welche Ursachen belastbar sind

**„Freilos wegen Abmeldung" — belastbar**, wenn eine der nicht fahrenden Zeilen des Laufs einen
`competition_deregistration`-Datensatz trägt. Die Tabelle hat einen Unique-Index auf
`competition_registration`; eine Meldung ist also entweder abgemeldet oder nicht, unabhängig davon,
in welcher Runde das geschah. Damit deckt die Prüfung beide Fälle ab: in dieser Runde abgemeldet,
und früher abgemeldet und als `out`-Zeile mitgeführt.

Der Freitext-Grund wird **nur** übernommen, wenn genau eine abgemeldete Zeile im Lauf steckt. Bei
mehreren wäre die Zuordnung Name → Grund geraten; dann bleiben nur die Namen.

**„Freilos – kein Gegner in dieser Runde" — der neutrale Fallback** für alles andere:

- Es wurde von vornherein nur ein Boot in diesen Lauf gesetzt (strukturell).
- Die Gegnerzeile ist `out`, weil sie ausgeschieden ist (`failed`) oder den Platz für die nächste
  Runde nicht geschafft hat. Das ist keine Abmeldung, und ohne Abmeldedatensatz wird auch keine
  behauptet.

## 3. Backend: eine Ableitung, drei Aufrufer

Neu in `MatchStatusLogic`, neben `deriveMatchState`:

```kotlin
enum class MatchByeCause { DEREGISTRATION, NO_OPPONENT }

data class MatchByeDto(
    val cause: MatchByeCause,
    /** Namen der abgemeldeten Mannschaften; null bei NO_OPPONENT. */
    val teamName: String?,
    /** Freitext-Grund — nur bei genau einer abgemeldeten Zeile, sonst null. */
    val reason: String?,
)

/** Eine Team-Zeile des Laufs, so weit die Freilos-Frage sie braucht. */
data class MatchByeTeam(
    /** Nicht als `out` aus einer früheren Runde mitgeführt. */
    val racing: Boolean,
    val name: String,
    val deregistered: Boolean,
    val deregistrationReason: String?,
)

fun deriveBye(roundRequired: Boolean, teams: List<MatchByeTeam>): MatchByeDto?
```

Die Regel ist wortgleich zur heutigen: `!roundRequired && teams.count { it.racing } == 1`, sonst
`null`. Danach entscheidet allein die Abmeldung über die Ursache.

`MatchStatusDto` bekommt `val bye: MatchByeDto? = null`. **Kein neuer `MatchState`** — dessen KDoc
warnt ausdrücklich davor, weil neue Werte still in jeden `else`-Zweig fielen, der heute über die
Aufzählung verzweigt. „Freilos" ist wie „Überfällig" und „Teilweise gewertet" eine Ablesung aus
einem Feld, kein Zustand.

### Datenbeschaffung

Eine neue, kleine Abfrage liefert je Setup-Lauf die Eingaben für `deriveBye`:

```
getByeInputs(eventId, competitionId?) -> Zeilen (setup_match_id, round_required,
                                                 out, team_name, deregistered, deregistration_reason)
```

Der Name setzt sich aus Vereinsname und optionalem Meldungsnamen zusammen — dieselbe
Zusammensetzung, die das Panel „Teams mit Freilos" heute schon zeigt.

Eigene Abfrage statt Erweiterung der bestehenden Team-Abfragen: die Nutzlast des
Schiedsrichter-Dashboards und damit sein ETag bleiben unberührt, und die `out`-Filter der
bestehenden Wege bleiben, wie sie sind.

Angeschlossen an drei Stellen:

- `CompetitionExecutionService.getProgress` → über `toCompetitionRoundDto` in `MatchStatusDto.bye`
- `LiveDashboardService.getLiveDashboard` → `LiveDashboardMatchDto.bye`
- `EventScheduleService.getSchedule` → `EventScheduleSlotDto.bye`

## 4. Frontend: Chip und Erklärung

### Der Chip

`matchStatusChip.ts` bekommt einen Zweig **hinter** `PREPARING`/`RUNNING` und **vor** `FINISHED`.
Die Reihenfolge folgt weiter der von `deriveMatchState`: Was tatsächlich passiert, schlägt alles
andere — ein aktivierter Lauf zeigt weiter „Läuft", auch wenn er als Freilos geführt ist.

| Zustand | Chip | Farbe |
|---|---|---|
| `FINISHED` | „Freilos · quittiert" | success |
| `SKIPPED` | „Freilos · entfallen" | default, durchgestrichen |
| sonst | „Freilos · offen" | info |

Damit entfallen für Freilose „Anstehend", „Überfällig" und „Teilweise gewertet" — genau die Chips,
die Ergebnisse erwarten lassen.

`arenaChip` schweigt bei einem Freilos: „Arena 0/1" für ein Boot, das nicht fährt, ist Rauschen.

### Die Erklärung

Neues Modul `matchBye.ts` mit `byeExplanation(bye)` → Übersetzungsschlüssel und Werte:

- `event.match.bye.deregistrationWithReason` — „Freilos wegen Abmeldung — {{team}} ({{reason}})"
- `event.match.bye.deregistration` — „Freilos wegen Abmeldung — {{team}}"
- `event.match.bye.noOpponent` — „Freilos – kein Gegner in dieser Runde"

## 5. Die drei Ansichten

**Zeitplan.** `slotMatchStatus` reicht `slot.bye` durch; der Chip ändert sich dadurch von allein.
Unter dem Slot-Namen steht die Erklärung als graue `caption`-Zeile. Abgesagte Slots bleiben
sichtbar und gekennzeichnet wie heute.

**Schiedsrichter-Dashboard.** Das Status-Objekt, das `LiveDashboardMatchCard` heute inline im JSX
baut, wandert als reine Funktion `dashboardMatchStatus(match)` nach `common.ts` und trägt `bye`
mit — inline war es nicht prüfbar. `common.ts` und nicht `matchStatusChip.ts`, weil die Funktion
`teamHasResult` aus genau diesem Modul braucht, um `teamsScored` zu zählen; ein Import in die
Gegenrichtung hätte `matchStatusChip.ts` unnötig ans Dashboard-Modul gekettet. Die Erklärung steht
unter der Wettkampf-/Runden-Zeile. `matchControls`, Knöpfe und Rechte bleiben unberührt.

**Durchführungsübersicht.** Die Tabelle im Panel „Teams mit Freilos" bekommt eine dritte Spalte mit
demselben `StatusChip` wie die Lauf-Karten. Sonst nichts — die Freilos-Darstellung dort besteht
bereits.

Zusätzlich lesen das Panel und `matchesFiltered` ihre Freilos-Frage künftig aus
`match.status.bye != null` statt aus `teams.length === 1`. Das ist dieselbe Menge Läufe (der Server
leitet aus denselben Eingaben ab, siehe Abschnitt 1), beseitigt aber die zweite Regel.

**Bewusst nicht angefasst:** `timelineIndicator.ts` (der Balken im Zeitstrahl) und
`roundCounterChips` (die Zählerleiste über einer Runde) bekommen keinen Freilos-Topf. Beide zeigen
Verlauf bzw. Arbeitsvorrat; dort ist ein Freilos zu Recht ein Lauf wie jeder andere.

## 6. Tests

**Backend, `MatchStatusLogicTest`** — je ein Fall:

1. Verpflichtende Runde → kein Freilos, auch bei einem einzigen Boot.
2. Zwei fahrende Boote → kein Freilos.
3. Ein Boot, keine weitere Zeile → `NO_OPPONENT` (strukturell).
4. Ein Boot + `out`-Zeile ohne Abmeldung (ausgeschieden) → `NO_OPPONENT`.
5. Ein Boot + abgemeldete Zeile mit gespeichertem Grund → `DEREGISTRATION`, Name und Grund.
6. Dieselbe Zeile ohne Grund → `DEREGISTRATION`, Name, Grund `null`.
7. Zwei abgemeldete Zeilen → `DEREGISTRATION`, beide Namen, Grund `null`.

**Frontend, `matchStatusChip.test.ts`:**

- Die drei Freilos-Chips über `FINISHED`, `SKIPPED` (durchgestrichen) und `AWAITING_FINISH`/`UPCOMING`.
- `RUNNING` und `PREPARING` schlagen das Freilos.
- Ohne `bye` bleibt jeder bestehende Chip unverändert.
- `arenaChip` schweigt beim Freilos.

**Frontend, Sichtbarkeit je Ansicht** — an der Funktion, die die Ansicht speist:

- `slotMatchStatus` trägt `bye` in den Zeitplan-Status.
- `dashboardMatchStatus` trägt `bye` in den Dashboard-Status.
- Die Aufteilung der Durchführung: ein Freilos landet im Panel und nicht in der Kartenliste, ein
  gewöhnlicher Lauf umgekehrt.

**Frontend, `matchBye.test.ts`:** die drei Erklärungstexte, einschließlich Abmeldung ohne
gespeicherten Grund.
