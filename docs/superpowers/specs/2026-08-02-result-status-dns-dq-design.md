# Design: DNS und DSQ neben DNF sichtbar machen (A3)

**Stand:** 2026-08-02
**Backlog-Punkt:** A3 aus `2026-07-30-schiedsrichter-dashboard-backlog.md`

## Entscheidung

Kein Schemawechsel. `competition_match_team.failed` bleibt der Boolean „zählt nicht in der
Wertung", `failed_reason` bleibt Freitext. Der Status wird **nur visualisiert**: eine reine
Funktion im Frontend liest ihn aus dem vorhandenen Freitext.

Verworfen wurde eine Enum-Spalte neben `failed` und ein Ersatz von `failed` durch einen
Status. Beides wäre nur dann gerechtfertigt, wenn der Status auswertbar sein müsste — Filter,
Statistik, abweichende Wertungsregeln. Das ist nicht der Fall: DNS-Boote werden weiterhin
genauso behandelt wie DNF-Boote.

## Warum das trägt

Alle drei Quellen schreiben den Statustext bereits in dasselbe Feld:

- Manuelle Eingabe: Freitextfeld „Grund für Ausscheiden".
- Excel-/Datei-Import: Ein Wert in der Zeitspalte, der kein Timecode ist, landet als
  `noResultReason` in `failed_reason` (`CompetitionExecutionService.updateMatchResultByFile`).
- RaceClocker (`origin/issue/94`, hier noch nicht gemerged): derselbe Weg, die Kürzel dort sind
  laut Feed genau „DNS", „DNF", „DQ".

Eine Erkennung am Anzeigeort greift damit für alle drei — der RaceClocker-Branch braucht keine
eigene Anpassung.

## Bausteine

### 1. Parser — `frontend/src/utils/matchResultStatus.ts`

```
matchResultStatus(failedReason)
  "DNS"             → {status: 'DNS', note: null}
  "dsq"             → {status: 'DSQ', note: null}
  "DQ – Frühstart"  → {status: 'DSQ', note: 'Frühstart'}
  "Boot gekentert"  → {status: null,  note: 'Boot gekentert'}
  null | ""         → {status: null,  note: null}
```

Erkannt wird ein **führendes** Token aus `DNS | DNF | DSQ | DQ | DISQ`, unabhängig von
Groß-/Kleinschreibung, gefolgt von einer Wortgrenze. `DQ` und `DISQ` werden auf `DSQ`
normalisiert. Trennzeichen zwischen Kürzel und Notiz (Leerzeichen, `-`, `–`, `:`, `,`) fallen
weg. Was nicht passt, bleibt unverändert Freitext.

Bewusst kein Freitext-Durchsuchen: „Boot gedreht, DNS-Wertung strittig" soll nicht als DNS
gelten.

### 2. Eingabe — `CompetitionExecution.tsx`

Beim Haken „Ausgeschieden" erscheint eine `ToggleButtonGroup` mit DNS/DNF/DSQ und daneben das
bisherige Freitextfeld für die Notiz. Das Formular führt beide Werte getrennt
(`failedStatus`, `failedReason`):

- **Laden:** gespeicherter Grund wird per Parser in Status und Notiz zerlegt.
- **Absenden:** `[status, note]` werden mit Leerzeichen zusammengesetzt und als `failedReason`
  übertragen; sind beide leer, wird wie bisher `undefined` gesendet.

Die Auswahl ist optional. Reiner Freitext ohne Status bleibt möglich, ebenso ein Status ohne
Notiz.

### 3. Anzeige

| Ort | heute | künftig |
|---|---|---|
| `LiveDashboardMatchCard` | hart „DNF" | erkannter Status, sonst „DNF" |
| `CompetitionExecutionRound` | „Ausgeschieden (Grund)" | „DSQ (Frühstart)", ohne Status wie bisher |
| `ResultsMatchDialog` (öffentliche Ergebnisse) | dito | dito |

Der Fallback ist Teil der Anforderung: Altbestände und freie Gründe sehen aus wie heute.

### 4. i18n

Neue Keys unter `event.competition.execution.results.status.*` in de/en/da: Langtexte für die
Tooltips der drei Knöpfe. Die Kürzel selbst sind international und bleiben in allen Sprachen
gleich.

## Tests

Das Frontend hat bisher kein Testframework. Vitest wird eingerichtet (devDependency, Config,
`npm test`) und der Parser mit Tests abgedeckt: die drei Kürzel, Groß-/Kleinschreibung, die
DSQ-Normalisierung, Kürzel mit Notiz, reiner Freitext, leer und `null`. Die UI-Komponenten
bleiben ungetestet — der Knackpunkt ist der Parser.

## Nicht Teil dieser Änderung

- Wertungslogik: DNS zählt wie DNF, kein Einfluss auf Platzberechnung oder Rundenaufstieg.
- Backend, Datenbank, API-Schema.
- A4 (Rückfrage beim Beenden mit offenen Ergebnissen) baut auf den Statuswerten auf und wird
  getrennt entworfen.
