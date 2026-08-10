# Konfigurierbare Boards — Design

**Datum:** 10.08.2026
**Branch:** claude/athleten-board-restructure-9e6f74
**Status:** freigegeben (Ansatz 1: JSONB-Konfiguration)

## Problem

Die Info-Seite kennt vier View-Typen: drei Listen (nächste Läufe, letzte Ergebnisse,
laufende Läufe) und das Athleten-Board, das inhaltlich alle drei kombiniert. Das ist
inkonsistent: dieselben Inhalte existieren einmal als eigenständige Views und einmal fest
verdrahtet in der Bühne des Athleten-Boards. Die Bühne selbst ist nicht konfigurierbar
(feste Spalten: läuft / als Nächstes / letztes Ergebnis), und es gibt genau eine davon
je Event.

## Ziel

Ein einheitliches Board-System:

1. Ein **Board** hat ein Layout (1, 2 oder 3 Spalten oder 6 Kacheln als 3×2-Raster).
   In jede Kachel können **Elemente** gelegt werden, z. B. ein „Lauf" mit Timeline-Offset
   (0 = aktuell, −1 = davor, +1 = danach, Bereich −6…+6). Je Element ist konfigurierbar,
   was gezeigt wird. Eine Kachel mit mehreren Elementen **rotiert** durch sie.
2. Es können **mehrere Boards** je Event erzeugt und einzeln abgerufen werden.
3. **Vollbild** bleibt möglich.

Das neue System **ersetzt die bisherige Info-Seite komplett** (View-Rotation,
`info_view_configuration`, alte Konfigurationsmasken).

## Entschiedene Grundsatzfragen

| Frage | Entscheidung |
|---|---|
| Scope | Komplett-Ersatz der Info-Seite; alte View-Typen werden Elemente |
| Offset bei Parallel-Läufen | Timeline-Index (s. u.), keine Sonderrolle für 0 |
| Abruf | Öffentliche URL je Board, ohne Anmeldung |
| Negativer Offset | Darstellung folgt dem Lauf-Zustand (beendet → Ergebnis-Karte) |
| Element-Typen | Lauf, Laufliste, Uhr/Kopfzeile, Freitext |
| Rotation | Mehrere Elemente je Kachel, konfigurierbares Intervall |
| Lauf-Element-Optionen | Crew, Countdown, Zeiten/Zwischenstände, Kontrastmodus, Auto-Scroll/Shrink |
| Migration | Default-Board je Event mit ATHLETE_BOARD-Konfiguration; alte URL leitet um |
| Speicherung | Eine Tabelle `board` mit JSONB-Konfiguration |

## Datenmodell

```sql
board (
    id         UUID PRIMARY KEY,
    event_id   UUID NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    config     JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
)
```

Die Migration:

1. legt `board` an,
2. erzeugt für jedes Event mit einer aktiven ATHLETE_BOARD-Zeile ein Default-Board
   „Athleten-Anzeige" (3 Spalten: Lauf 0 / Lauf +1 / Lauf −1 — die heutige Bühne) und
   übernimmt `showCountdown` sowie den Poll-Takt aus der alten Konfiguration,
3. droppt `info_view_configuration` und den Enum-Typ `info_view_type`.

Kein Fremdschlüssel zeigt aus der JSONB-Konfiguration heraus; relationale Integrität
innerhalb der Konfiguration trägt daher nichts. Der Editor schreibt die Konfiguration
immer im Ganzen — das Zugriffsmuster, für das JSONB passt.

## Konfigurations-Schema (JSONB, in Kotlin typisiert)

```
BoardConfig
├─ layout: ONE_COLUMN | TWO_COLUMNS | THREE_COLUMNS | SIX_TILES   (6 = 3×2)
├─ refreshIntervalSeconds: Int   (Default 15, Untergrenze 10 wie heute)
└─ tiles: List<BoardTile>        (Länge muss zum Layout passen: 1/2/3/6)
   └─ BoardTile
      ├─ rotationIntervalSeconds: Int   (Default 10, greift ab 2 Elementen)
      └─ elements: List<BoardElement>   (mindestens 1)
```

`BoardElement` ist eine sealed class mit Jackson-Typfeld `type`:

| Typ | Felder |
|---|---|
| `MATCH` | `offset` (−6…+6), `showCrew`, `showCountdown`, `showTimes`, `contrastColors`, `autoFit` (alle Boolean) |
| `MATCH_LIST` | `mode` (`UPCOMING` \| `RESULTS` \| `RUNNING`), `limit` (1–20) |
| `CLOCK` | `showEventName` |
| `TEXT` | `text` |

Validierung beim Schreiben über das bestehende Validators-Muster: Kachelzahl passt zum
Layout, jede Kachel hat mindestens ein Element, Offset im Bereich, Limits im Bereich,
Intervalle über den Untergrenzen.

## Timeline-Semantik

Serverseitig als pure, einzeln testbare Logik (`BoardLogic`, analog `AthleteBoardLogic`):

- **Sequenz** = beendete Läufe (chronologisch) → laufende Läufe (nach tatsächlichem
  Start) → anstehende Läufe (nach geplantem Start, inkl. Runden-Platzhalter und Pausen
  wie heute).
- **Cursor 0** = der zuletzt gestartete, noch laufende Lauf. Laufen zwei parallel, ist
  −1 der andere (noch laufende).
- **Kein Lauf im Rennen:** 0 zeigt den Leerzustand („Kein Lauf im Rennen"), −1 das
  letzte Ergebnis, +1 den nächsten Start. Die Struktur eines montierten Bildschirms
  bleibt stehen, egal was gerade fährt.
- **Darstellung folgt dem Zustand, nicht dem Vorzeichen:** ein laufender Lauf erscheint
  als Aufstellungs-Karte mit Zwischenständen, ein beendeter als Ergebnis-Karte, ein
  anstehender als Aufstellungs-Karte mit Countdown.
- Ergebnis-Sichtbarkeit (`isPublicResult`), OVERDUE-Nachfrist und Pausen-Anzeige gelten
  unverändert über die bestehenden Abfragen.

## Backend-Endpoints

**CRUD** (authentifiziert wie bisher: `ReadEventGlobal` lesend, `UpdateEventGlobal`
schreibend):

- `GET    /event/{eventId}/boards` — Liste (id, name, config)
- `POST   /event/{eventId}/boards`
- `PUT    /event/{eventId}/boards/{boardId}`
- `DELETE /event/{eventId}/boards/{boardId}`

**Öffentlich** (Rate-Limit `publicInfo` wie heute, kein `authenticate`):

- `GET /event/{eventId}/info/boards` — nur id + name; trägt die Umleitung der alten
  Athleten-Board-URL und Verlinkungen.
- `GET /event/{eventId}/info/board/{boardId}` — **eine Antwort mit allem**: eventName,
  serverTime, refreshIntervalSeconds, die Board-Konfiguration und die aufgelösten Daten —
  je benötigtem Offset ein Slot (Match-DTO, Ergebnis-DTO oder leer), je Listen-Element
  seine Liste. `AthleteBoardMatch`/`AthleteBoardResult` werden unverändert
  wiederverwendet.

**Cache** je Board (TTL 5 s) nach heutigem Muster; `serverTime` bleibt je Antwort
frisch (Countdown-Anker).

**Entfällt:** `/info/athlete-board`, `/info-views` (CRUD) samt Service-/Repo-/
Entity-Code. **Bleibt:** die Listen-Endpoints (`upcoming-matches`,
`latest-match-results`, `running-matches`, `live-matches`), die öffentliche
Ergebnisseite und „Mein Event" nutzen sie weiter.

## Frontend

**Routen:**

- `/event/$eventId/board/$boardId` — öffentliche Anzeige eines Boards, ohne Anmeldung,
  ohne Bedienelemente außer Vollbild.
- `/event/$eventId/info` — bleibt als Verwaltungsseite: Board-Liste + Editor.
- Die alte Athleten-Board-Route bleibt bestehen und leitet auf das erste Board um
  (Abruf über den öffentlichen Listen-Endpoint).

**Anzeige:**

- `BoardPage` pollt den Resolve-Endpoint über `usePolledEndpoint` — dessen drei
  Eigenschaften bleiben tragend: letzter guter Stand bleibt bei Netzabbruch stehen,
  „nie geladen" bleibt von „geladen, aber leer" unterscheidbar, im Hintergrund wird
  nicht gepollt. Stale-Warnung als dezentes Overlay auf Board-Ebene.
- `BoardRenderer` baut das Grid je Layout; `BoardTileView` übernimmt die clientseitige
  Rotation (Timer + Fade); `BoardElementView` rendert je Elementtyp.
- Wiederverwendet werden unverändert: `AthleteBoardMatchCard`,
  `AthleteBoardResultCard`, `useServerClock`, die Dichte-Skalierung (`densityScale`,
  jetzt je Kachel statt je Bühne, via Container-Queries).
- **Vollbild** wie heute: Button + Taste F, vollflächiger Dialog.

**Editor:**

- Layout-Auswahl mit Miniatur-Vorschau, darunter das Kachel-Raster; je Kachel eine
  Elementliste (hinzufügen, entfernen, sortieren) mit typspezifischem Formular und
  Rotationsintervall.
- Ersetzt `InfoViewConfiguration` und `ViewConfigurationForm`. `InfoViewDisplay`,
  `ViewRotationControl` und die Rotationslogik der alten Info-Seite entfallen.
- Die alten Listen-Views werden zu Element-Renderern umgebaut; ihre Daten kommen aus
  der Board-Antwort statt aus eigenen Fetches.
- Die Ganzseiten-Rotation der alten Info-Seite braucht keinen Ersatz: ein
  1-Spalten-Board mit mehreren Elementen in der einen Kachel rotiert vollflächig.

## Tests

- **Backend:** Unit-Tests für Timeline-/Offset-Auflösung und Config-Validierung;
  ein `testComprehension`-Test gegen Testcontainers für den Resolve-Endpoint
  (eigene Build-DB je Worktree).
- **Frontend:** pure Logik (Slot-Zuordnung, Rotationsfolge) als Vitest, nach dem
  Muster von `boardLayout.test.ts`.

## Risiko

Der Zeitpunkt (Regatta 14.08.) ist das größte Risiko. Abgefedert dadurch, dass die
gehärteten Karten, die Polling-Mechanik und die SQL-Abfragen unangetastet bleiben und
das Default-Board die heutige, getestete Bühne reproduziert. Montierte Bildschirme mit
der alten URL funktionieren über die Umleitung weiter.
