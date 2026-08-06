# Design: Athleten-Dashboard an Start und Ziel

**Stand:** 2026-08-02
**Status:** Entworfen und abgestimmt, noch nicht umgesetzt.
**Herkunft:** Backlog-Punkt B1 aus `2026-07-30-schiedsrichter-dashboard-backlog.md`.

## Zweck

Athleten sollen an Start und Ziel auf einen Blick sehen, welcher Lauf gerade auf dem Wasser ist,
welcher als nächstes kommt und wie der letzte ausgegangen ist. Dieselbe Seite läuft auf einem fest
montierten Bildschirm und auf dem Telefon der Athleten — ein Inhalt, ein Layout, kein Login.

## Ausgangslage

Drei öffentliche Endpoints liefern die Daten inhaltlich bereits vollständig
(`backend/.../app/eventInfo/boundary/eventInfo.kt`):

- `GET /event/{eventId}/info/running-matches`
- `GET /event/{eventId}/info/upcoming-matches`
- `GET /event/{eventId}/info/latest-match-results`

Ihre DTOs tragen alles Benötigte: `startNumber` (= Bahn, unique pro Lauf laut
`starting_position_unique_in_match`), Verein, Teamname, Teilnehmer, `startTime`, Platz, Zeit,
DNF-Kennzeichen.

Die rotierende Kiosk-Seite `/event/$eventId/info` (`frontend/src/pages/event/EventInfoPage.tsx`)
zeigt jeweils **eine** konfigurierte View und blendet weiter. Ihre Konfiguration liegt in
`info_view_configuration` (Enum `info_view_type`, `display_duration_seconds`, `data_limit`,
`filters` JSONB, `sort_order`, `is_active`).

Drei Eigenschaften dieser Seite passen für Athleten nicht:

1. **Rotation.** Ein Athlet am Start, der zehn Sekunden warten muss, bis „sein" Block wieder
   erscheint, ist der Fehlerfall, nicht der Normalfall.
2. **Login.** `GET /event/{eventId}/info-views` verlangt `Privilege.ReadEventGlobal`. Die Route
   selbst ist ungeschützt, aber ohne Anmeldung kommt keine View-Konfiguration zurück und die Seite
   zeigt „noViews". Die Kiosk-Seite ist damit faktisch angemeldet-only — im Backlog steht das
   anders.
3. **Bedienelemente.** Fest verdrahteter „Konfigurieren"-Button, Vollbild-Dialog, Maus-Hover-Timer,
   Tastatursteuerung. Auf einem montierten Schirm und auf dem Telefon gleichermaßen Ballast.

## Entscheidung

Eine Komponente, zwei Träger: eine eigene, sehr schlanke öffentliche Route für den Regelfall, und
derselbe Baustein zusätzlich als View-Typ in der bestehenden Kiosk-Rotation. Konfiguration und
Admin-Maske werden aus dem bestehenden Framework geerbt, die Kiosk-Mechanik bleibt außen vor.

Die Konfiguration wird **im Backend** aufgelöst, nicht im Client. Dadurch genügt ein Request je
Aktualisierung, und das Login-Problem aus Punkt 2 entfällt vollständig, statt umgangen zu werden.

## Architektur

### Backend

**Migration.** `ATHLETE_BOARD` als vierter Wert in `info_view_type`. `ALTER TYPE … ADD VALUE` läuft
ab PostgreSQL 12 innerhalb einer Transaktion, solange der neue Wert nicht in derselben Transaktion
verwendet wird — das ist hier der Fall, Flyway braucht keine Sonderbehandlung.

**Endpoint.** `GET /event/{eventId}/info/athlete-board`, öffentlich, ohne `authenticate`, neben den
drei bestehenden Info-Endpoints im selben `route("/event/{eventId}/info")`-Block.

**Service.** `EventInfoService.getAthleteBoard(eventId)`

1. sucht die aktive `ATHLETE_BOARD`-Konfiguration der Veranstaltung,
2. liest daraus Limits und Countdown-Schalter,
3. ruft damit die vorhandenen `getRunningMatches`, `getUpcomingCompetitionMatches` und
   `getLatestMatchResults` auf,
4. bildet deren Ergebnisse auf ein schlankes Antwort-DTO ab.

Fehlt die Konfiguration, greifen die Defaults. Die Seite ist ohne jede Einrichtung brauchbar.

**Konfiguration** im vorhandenen `filters`-JSONB:

```json
{"running": 3, "upcoming": 3, "results": 1, "showCountdown": true}
```

Defaults bei fehlender oder unvollständiger Konfiguration: `running` 3, `upcoming` 3, `results` 1,
`showCountdown` `true`, Aktualisierungstakt 15 Sekunden. Jeder Wert wird einzeln gegen den Default
aufgelöst — eine Konfiguration, die nur `showCountdown` setzt, behält die Standard-Limits.

Die Spalte `data_limit` bleibt für diesen View-Typ ungenutzt: sie trägt eine Zahl, gebraucht werden
drei. Alle drei liegen deshalb in `filters`. Existieren mehrere aktive `ATHLETE_BOARD`-Zeilen für
eine Veranstaltung, gewinnt die mit der kleinsten `sort_order`; gedacht ist genau eine.

**Antwort-DTO.** Bewusst schlanker als die internen DTOs. Übernommen werden je Mannschaft nur Bahn,
Verein, Teamname und die Teilnehmer als Name plus Rolle. Nicht übernommen werden `year`, `gender`,
`participantId` und `externalClubName` — sie werden auf dieser Seite nicht angezeigt und kosten im
Mobilfunknetz.

```
AthleteBoardDto
  eventName               String
  serverTime              LocalDateTime
  refreshIntervalSeconds  Int
  showCountdown           Boolean
  running                 List<AthleteBoardMatch>
  upcoming                List<AthleteBoardMatch>
  results                 List<AthleteBoardResult>

AthleteBoardMatch
  matchId, competitionName, categoryName?, roundName?, matchName?, startTime?
  teams  List<AthleteBoardTeam>

AthleteBoardTeam
  lane (aus startNumber), clubName, teamName?
  participants  List<{name, role?}>

AthleteBoardResult
  matchId, competitionName, categoryName?, roundName?, matchName?, startTime?
  teams  List<{place?, lane, clubName, teamName?, timeString?, failed, failedReason?}>
```

`serverTime` ist die Bezugsgröße für den Countdown. Ein montierter Bildschirm oder ein Telefon mit
falsch gestellter Uhr würde sonst Unsinn anzeigen.

`refreshIntervalSeconds` stammt aus `display_duration_seconds`. Für eine einzelne View benutzt
`EventInfoPage` dieses Feld heute schon als Aktualisierungstakt; die Bedeutung ist nicht neu
erfunden.

### Frontend

- **`AthleteBoard`** — eine Komponente ohne eigene Datenhoheit über die einzelnen Blöcke. Sie holt
  den Endpoint einmal und rendert daraus drei Bereiche.
- **Route `/board/$eventId`** — neue öffentliche Seite ohne `beforeLoad`-Wächter, trägt die
  Komponente. Kein „Konfigurieren"-Button, kein Vollbild-Dialog, keine Maus-Timer, keine
  Tastatursteuerung.

  Die Route hängt **direkt an `rootRoute`**, nicht unter `eventRoute`. Der Grund zeigte sich erst im
  Sichttest: Unterhalb von `mainLayoutRoute` rendert `RootLayout` die App-Kopfleiste samt Logo,
  Sprachwahl und Anmelde-Symbol sowie die linke Seitenleiste — auf einem montierten
  Athletenbildschirm alles fehl am Platz. `resultsRoute` löst dasselbe Problem seit jeher genauso.
  Deshalb `/board/$eventId` statt des ursprünglich vorgesehenen `/event/$eventId/board`.
- **Kiosk-Einbindung** — derselbe Baustein bekommt einen `case` in `InfoViewDisplay`, damit die
  Ansicht auch in der Rotation läuft. Er bekommt dort nur `eventId` übergeben und ignoriert die
  Felder der durchgereichten View-Zeile: Limits und Countdown-Schalter kommen aus der Antwort des
  Endpoints, damit beide Träger dieselbe Konfiguration sehen. Eine Ausnahme braucht die
  Kiosk-Seite selbst: Ist die Athleten-Anzeige die einzige aktive View, entfällt ihr
  „Einzelansicht im Takt neu laden"-Remount — die Komponente lädt sich selbst nach, und der
  Remount würde ihren „letzter guter Stand bleibt stehen"-Mechanismus im Takt durch einen
  Spinner ersetzen.
- **Admin-Maske** — `ViewConfigurationForm` erhält den neuen Typ in der Auswahlliste und, nur für
  diesen Typ, drei Zahlenfelder und ein Häkchen. Anmerkung für die Umsetzung: `filters` wird vom
  Formular heute nur durchgereicht, nie bearbeitet; dies ist das erste Eingabefeld darauf.

## Oberfläche

**Aufteilung.** Drei gleichwertige Spalten nebeneinander auf breiten Schirmen — Aktuell, Nächster,
Letztes Ergebnis —, unterhalb eines Umbruchpunkts untereinander in derselben Reihenfolge. Ein
Denkmodell für beide Geräte, keine zweite Layout-Variante zu pflegen. Schriftgrößen über `clamp()`
an die Viewport-Breite gekoppelt, damit dieselbe Seite aus fünf Metern vor einem großen Bildschirm
und aus vierzig Zentimetern auf dem Telefon lesbar ist.

**Lauf-Karte.** Oben Wettkampf, Runde und Laufname, daneben die Startzeit. Darunter je Mannschaft
eine Zeile: die Bahn als große Ziffer links, dann Verein und Teamname, darunter klein die
Teilnehmernamen mit Rolle. Die Bahn ist das dominante Element — sie ist die Information, wegen der
ein Athlet stehenbleibt.

**Ergebnis-Karte.** Statt der Namen Platz, Bahn, Verein und Zeit. DNF-Boote mit Grund statt Zeit.

**Countdown.** Beim nächsten Lauf steht die geplante Startzeit groß, daneben die Restzeit, berechnet
gegen `serverTime` statt gegen die Geräteuhr. Ist die Startzeit verstrichen, der Lauf aber noch
nicht gestartet, zeigt die Karte die Uhrzeit und „erwartet" — kein negativer Countdown. Das ist
derselbe Fehler, den Backlog-Punkt A2 für „Läuft seit 0 min" beschreibt, und er soll hier nicht
spiegelbildlich neu entstehen. Bei `showCountdown: false` entfällt die Restzeit; die geplante
Uhrzeit bleibt.

**Kopfzeile.** Veranstaltungsname, aktuelle Uhrzeit, dezenter „Stand von HH:MM"-Hinweis.

**Keine Bedienung.** Kein Klick, kein Aufklappen, kein Dialog, keine Tastatur. Ein montierter
Bildschirm hat keine Maus, und auf dem Telefon ist eine Seite, die nur zeigt, schneller verstanden.

**Darstellung** über das bestehende App-Theme, in der Kontrast-Richtung des Schiedsrichter-Dashboards
(Commit `98b786bc`) — draußen bei Sonne ist der Kontrast der begrenzende Faktor.

## Fehlerfälle

| Fall | Verhalten |
| --- | --- |
| Veranstaltung unbekannt | 404, neutrale Meldung auf der Seite. Kein Redirect auf `/login` — die Route ist öffentlich und wird von Leuten ohne Konto aufgerufen. |
| Keine Konfiguration hinterlegt | Defaults greifen, kein Fehler. |
| Netzabbruch beim Aktualisieren | Letzter guter Stand bleibt stehen, „Stand von HH:MM" altert sichtbar mit und wird ab zwei verpassten Intervallen hervorgehoben. Nie zurück auf Spinner oder leeren Schirm. |
| Leerer Block | Überschrift bleibt stehen, darunter eine neutrale Zeile („Zurzeit kein Lauf auf dem Wasser"), damit auf dem festen Schirm nichts springt. |
| Lauf ohne Startzeit | Karte ohne Uhrzeit und ohne Countdown, einsortiert ans Ende. Kein Randfall: `competition_match.start_time` ist nullable und kann erst gepflegt werden, wenn die Runde gesetzt wurde — die Lücke, die Backlog-Punkt B2 beschreibt. |
| Lauf im Verzug | Bleibt im Block „Nächster Lauf" stehen und zeigt „erwartet" statt einer Restzeit. |
| Seite im Hintergrund | Polling pausiert über `document.visibilityState`, lädt beim Zurückkehren sofort einmal. |
| Erster Abruf schlägt fehl | Eigene Meldung. Die Anzeige darf niemals „Zurzeit kein Lauf auf dem Wasser" behaupten, wenn sie in Wahrheit nichts weiß — das ist die gefährlichste Falschaussage, die ein Startbildschirm machen kann. |

**Eigene Abfrage für den Block „Nächster Lauf".** Die beiden letztgenannten Fälle sind mit der
vorhandenen `getUpcomingMatches` nicht erreichbar: Sie verlangt `start_time > now()` und schließt
Läufe ohne Startzeit ganz aus. Ein verspäteter, noch nicht gestarteter Lauf erfüllt weder das noch
`currently_running` und verschwände damit vollständig von der Anzeige — genau in dem Moment, in dem
ein Athlet am Start wissen will, ob es weitergeht. Die Athleten-Anzeige bekommt deshalb eine eigene
Abfrage: nicht laufend, noch ohne vollständiges Ergebnis, und Startzeit entweder leer oder nicht
länger als 30 Minuten verstrichen (`AthleteBoardLogic.DEFAULT_OVERDUE_GRACE_MINUTES`). Die
Nachfrist ist bewusst nicht konfigurierbar. Der Endpoint `/upcoming-matches` und die Kiosk-Ansicht
bleiben davon unberührt.

## Betriebshärtung (Nachtrag 2026-08-03)

Ein Abschluss-Review fand fünf betriebliche Risiken für den Einsatz bei einer echten Regatta.
Bewertung und Umsetzung:

1. **Serverseitiger Zwischenspeicher** (umgesetzt). `EventInfoService` hält die fertige Antwort
   je Veranstaltung für `AthleteBoardLogic.CACHE_TTL_SECONDS` (5 s) im Speicher. Das deckelt die
   Datenbanklast auf eine Berechnung je Veranstaltung und TTL, unabhängig von der Zuschauerzahl.
   `serverTime` wird je Antwort frisch gesetzt (`copy(serverTime = now)`), weil sie die
   Bezugsgröße für den Countdown ist; die `startState`-Felder sind höchstens 5 s alt, das trägt
   die Anzeige. Nur per `EventRepo` geprüfte Veranstaltungen landen im Speicher, unbekannte IDs
   können ihn nicht füllen. Nachweis am Seed: eine Berechnung (9 Queries), fünf Folge-Abrufe
   innerhalb der TTL lösten null Datenbank-Statements aus.

2. **N+1-Abfragen** (bewusst nicht umgebaut). `getAthleteBoard` lädt weiterhin je Lauf eine eigene
   Team-Abfrage — rund 9 Queries je Berechnung bei den Vorgabewerten. Mit dem Zwischenspeicher
   zahlt die Datenbank das höchstens einmal je 5 s und Veranstaltung; ein Batch-Umbau der drei
   Team-Abfragen würde davon nur noch ~4 Queries sparen und die Gruppierungslogik kurz vor dem
   ersten Einsatz anfassen. Verhältnis von Risiko zu Nutzen spricht dagegen.

3. **Kein ETag** (bewusst weggelassen). Ein ETag müsste `serverTime` ausklammern, sonst ist jede
   Antwort einzigartig. Bei einem 304 alterte dann aber der mitgelieferte `serverTime`-Stand —
   der Client zeigte „Stand von" fälschlich alt und der Countdown-Bezug verschöbe sich. Die
   gzip-Antwort liegt ohnehin bei ~1,4 kB; das Sparpotenzial rechtfertigt die Komplexität nicht.

4. **Rate-Limit** (umgesetzt, als Notbremse). `RateLimitName("publicInfo")` über allen
   öffentlichen Info-Endpoints: 500 Anfragen je 5 s und Client-IP. Bewusst weit über jedem
   legitimen Aufkommen (500 Telefone im 15-Sekunden-Takt sind ~33 Anfragen/s), weil sich auf
   einer Regatta viele Geräte eine IP teilen (Vereins-WLAN, Carrier-NAT) und hinter einem Proxy
   ohne Forwarded-Header sogar alle Zuschauer auf einen Schlüssel zusammenfallen. Gefangen wird
   nur Amoklaufen und stumpfes Hämmern; die Lastdeckelung leistet der Zwischenspeicher.

5. **Aktualisierungstakt vom Kiosk-Regler entkoppelt** (per Untergrenze).
   `display_duration_seconds` bleibt die Quelle des Takts, aber
   `AthleteBoardLogic.MIN_REFRESH_INTERVAL_SECONDS` (10 s) zieht eine Untergrenze ein: Ein Admin,
   der die Kiosk-Rotation auf 5 s stellt, beschleunigt damit nicht mehr nebenbei alle Telefone.
   Die Admin-Maske weist beim Typ `ATHLETE_BOARD` unter dem Regler darauf hin.

6. **gzip** (in diesem Branch sichergestellt). Der Compression-Commit aus dem Payload-Worktree
   ist per Cherry-pick übernommen (`Compress HTTP responses with gzip`), damit die Anzeige nicht
   von der Merge-Reihenfolge abhängt. Gemessen: 7,8 kB → 1,4 kB am Seed-Szenario.

## Tests

Die entscheidbare Logik wandert in ein `AthleteBoardLogic`-Objekt und wird mit `kotlin.test`
geprüft, nach dem Muster von `LiveDashboardLogicTest`:

- Limits und Countdown-Schalter aus `filters` lesen
- Defaults bei fehlender Konfiguration
- Defaults je Einzelwert bei unvollständiger Konfiguration
- Einordnung von Läufen ohne Startzeit ans Ende
- „Startzeit verstrichen, Lauf nicht gestartet" ergibt keinen negativen Countdown

Was ungetestet bleibt: Das Repository hat neun Testdateien und keine Frontend-Tests. Dafür jetzt
eine Testinfrastruktur aufzubauen wäre ein eigenes Vorhaben und gehört nicht in diesen Umfang. Die
Darstellung wird manuell abgenommen — am Seed-Szenario, einmal auf einem großen Bildschirm und
einmal auf dem Telefon.

## Nicht im Umfang

- Filtern nach eigenem Verein oder Wettkampf
- Unterschiedliche Inhalte für Start und Ziel (eine Aufstellung, ein Inhalt)
- gzip-Kompression (Backlog A5 Punkt 1) — bereits in einem anderen Worktree umgesetzt, hier nur
  Voraussetzung für das Datenvolumen, keine Aufgabe
- Änderungen an den drei bestehenden Info-Views
