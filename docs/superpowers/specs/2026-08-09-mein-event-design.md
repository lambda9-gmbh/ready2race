# Design: „Mein Event" — persönliches Dashboard über QR-Code

**Stand:** 2026-08-09
**Status:** Design abgenommen, Implementierung ausstehend
**Kontext:** Teilnehmende tragen ein Band mit QR-Code am Handgelenk. Der Code enthält eine URL der
Form `https://<host>/results/<qrCodeId>`. Diese Route existiert bereits, löst den Code auf und
leitet auf die öffentliche Ergebnisseite weiter — der Code geht dabei verloren. Ziel ist ein
dritter Reiter „Mein Event" neben „Ergebnisse" und „Live", der beim Einstieg über den QR-Code
geöffnet wird und die eigenen Läufe, Ergebnisse und Bedingungen zeigt.

---

## 1. Ausgangslage

| Baustein | Ort | Zustand |
| --- | --- | --- |
| QR-Route | `frontend/src/pages/results/ResultsQrCodePage.tsx` | leitet weiter, verwirft den Code |
| Code-Auflösung | `GET /app/{qrCodeId}` (`checkQrCode`) | anonym nur `QrCodePublicResponse` (nur `eventId`) |
| Reiter | `frontend/src/pages/results/ResultsPage.tsx:22` | `['latest-results', 'live', 'upcoming']` |
| Öffentliche Anzeige | `GET /event/{eventId}/info/athlete-board` | öffentlich, ohne `authenticate`, ratenbegrenzt |
| Bedingungen | `GET /event/{eventId}/participantRequirement/participant/{participantId}` | nur authentifiziert |
| Code → Person | Tabelle `qr_codes` | `qr_code_id` → entweder `participant` oder `app_user`, plus `event` |

Der Code ist eine nicht erratbare UUID; das Risiko ist ausschließlich Weitergabe, nicht
Durchprobieren.

## 2. Datenschutz: was tatsächlich neu sichtbar wird

Ohne Login sind heute bereits abrufbar: Wettkampf, Runde, Lauf, geplante und echte Startzeit,
Startposition, Verein, Teamname, **Vor- und Nachname jedes Teilnehmenden**, Rolle, Platz, Zeit,
Strafsekunden mit Notiz, DNS/DNF/DSQ sowie Abmeldung mit Grund
(`/event/{eventId}/info/*`, `eventInfo.kt:19`). Das Meldeergebnis-PDF druckt zusätzlich
Geschlecht und Jahrgang je Person (`EventRegistrationService.kt:1220`), und
`UpcomingMatchParticipantInfo` liefert beides ohnehin schon an die öffentliche Ergebnisseite.

„Mein Event" ist damit zu weiten Teilen ein **Filter auf bereits öffentliche Daten**. Wirklich neu
sind nur zwei Dinge:

1. **Die Zuordnung Code → Person.** Bänder tragen im Regelfall keinen Namen; der Link benennt
   also die Person. Gegenmaßnahme: der Code bleibt nicht in der URL stehen (§ 3) und die Seite
   wird nicht indexiert (§ 7).
2. **Der Status persönlicher Bedingungen.** Das ist die einzige Stelle, an der eine Aussage *über*
   eine Person entsteht statt einer Tatsache der Veranstaltung. Gegenmaßnahme: Freigabe je
   Bedingung durch den Veranstalter, Freitext-Notiz niemals öffentlich (§ 5).

Bewusst **nicht** aufgenommen: E-Mail-Adresse, Check-in/-out (Ereignisprotokoll ohne Kopplung an
die Rennlogik; wer das Auschecken vergisst, gilt weiter als unterwegs — eine Anzeige, die
regelmäßig lügt, erzeugt Diskussionen mit der Seite statt mit der Meldestelle).

## 3. Einstieg und Gerätezustand

`/results/{qrCode}` wird von einer Weiterleitung zur **Einstiegsseite**:

1. Code auflösen (`checkQrCode` wie bisher, liefert `eventId`).
2. Eintrag `{qrCode, eventId, displayName}` im `localStorage` unter einer **Liste** ablegen.
3. Auf `/results/event/{eventId}` weiterleiten, Reiter „Mein Event" aktiv.

Die URL trägt den Code danach nicht mehr. Der Reiter überlebt das Schließen des Browsers, ohne
dass ein personenbezogener Link durch Chatgruppen wandert. Gerätewechsel heißt neu scannen — das
Band ist am Arm.

`displayName` stammt **nicht** aus `checkQrCode`, sondern aus der ersten Antwort des neuen
Endpunkts. So entscheidet genau eine Stelle im Backend, was öffentlich ist; `checkQrCode` bleibt
für anonyme Aufrufer unverändert reduziert.

**Liste statt Einzelwert**, weil Eltern mit mehreren Kindern und Betreuende mehrere Bänder scannen.
Sind für die aktuelle Veranstaltung mehrere Einträge hinterlegt, steht im Reiter oben ein schmaler
Umschalter. Jeder Eintrag ist **einzeln löschbar** (geliehenes Telefon, falsch gescanntes Band).

**Reiter ohne hinterlegten Code:** sichtbar, mit dem Hinweis, den QR-Code auf dem Band zu scannen.
Ausblenden wäre sauberer, aber dann erfährt niemand von der Funktion — die Bänder erklären sich
nicht von selbst.

## 4. Backend

### 4.1 Endpunkt

```
GET /event/{eventId}/info/my-event/{qrCode}  →  MyEventDto
```

Öffentlich, im bestehenden `rateLimit(RateLimitName("publicInfo"))`-Block neben der
Athleten-Anzeige (`eventInfo.kt`). Die Veranstaltung steht im Pfad, damit ein Code aus einer
anderen Veranstaltung mit 404 abprallt statt fremde Daten zu liefern.

Fehlerfälle:

| Fall | Antwort |
| --- | --- |
| Code unbekannt | 404 |
| Code gehört zu anderer Veranstaltung | 404 |
| Code gehört zu einem `app_user` (Helferrolle) statt zu einem Teilnehmer | 404 |
| Veranstaltung unbekannt | 404 |

Ein Helfer-Code darf nicht in einen Teilnehmerdatensatz laufen; 404 statt einer eigenen Meldung,
damit die Antwort nichts über die Existenz des Codes verrät.

### 4.2 Antwort

```
MyEventDto
  displayName            String        Vor- und Nachname
  clubName               String?
  eventName              String
  serverTime             DateTime      wie AthleteBoardDto — Countdown rechnet gegen Serverzeit
  refreshIntervalSeconds Int           Untergrenze AthleteBoardLogic.MIN_REFRESH_INTERVAL_SECONDS
  running                MyEventMatch[]
  upcoming               MyEventMatch[]
  results                MyEventResult[]
  unscheduled            MyEventRegistration[]
  requirements           MyEventRequirement[]
```

`MyEventMatch` und `MyEventResult` übernehmen die Felder von `AthleteBoardMatch` bzw.
`AthleteBoardResult`, beschränkt auf die Mannschaft der Person und deren Mitstreitende. Neu ist
`MyEventRegistration` (Wettkampf gemeldet, aber noch kein Lauf terminiert: Wettkampf-Kennung,
Wettkampfname, Kategorie, Teamname, Rolle) sowie `MyEventRequirement` (§ 5).

### 4.3 Wiederverwendung

Diese drei Bausteine werden **wiederverwendet, nicht nachgebaut** — sonst driften die Ansichten
auseinander und ein Ergebnis erscheint im persönlichen Dashboard früher als auf der Anzeige:

- `AthleteBoardLogic.isPublicResult` — respektiert `event.public_results_visibility`
- `AthleteBoardLogic.startState` und `AthleteBoardLogic.sortByStartTime`
- Das Zwischenspeicher-Muster aus `EventInfoService` (`CACHE_TTL_SECONDS = 5`), hier je
  Teilnehmer statt je Veranstaltung. Die Kartengröße ist durch die Teilnehmerzahl der
  Veranstaltung beschränkt.

### 4.4 Neue Abfrage

Die Athleten-Anzeige liefert die nächsten N Läufe **insgesamt**; gebraucht werden **alle eigenen**.
Dafür eine neue Abfrage in `CompetitionMatchRepo` entlang des bereits in
`CompetitionMatchTeamRepo.getTeamsForUpcomingMatch` verwendeten Pfads:

```
PARTICIPANT
  ← COMPETITION_REGISTRATION_NAMED_PARTICIPANT
  → COMPETITION_REGISTRATION
  ← COMPETITION_MATCH_TEAM
  → COMPETITION_MATCH
```

`unscheduled` ergibt sich aus denselben `COMPETITION_REGISTRATION`-Zeilen ohne zugehörigen
`COMPETITION_MATCH_TEAM`-Eintrag.

## 5. Bedingungen

### 5.1 Freigabe je Bedingung

```sql
-- V202608101000__participant_requirement_publicly_visible.sql
set search_path to ready2race, pg_catalog, public;

alter table participant_requirement
    add column publicly_visible boolean not null default false;
```

Muster wie `V202508110946__participant_requirement_check_in_app.sql`. Migrationsnummer liegt nach
`V202608091500` aus der Auto-Abgleich-Arbeit.

Im Bedingungs-Editor eine Checkbox neben „Per App prüfbar". `publiclyVisible` wandert in
`ParticipantRequirementDto`, `ParticipantRequirementForEventDto` und
`ParticipantRequirementUpsertDto`.

**Der Standard ist „aus".** Nach dem Einspielen ist der Bedingungsblock also leer, bis jemand
Häkchen setzt. Das gehört in den Übergabetext, sonst wird es als Fehler gemeldet.

### 5.2 Anzeige

Welche Bedingungen für eine Person überhaupt gelten, entscheidet weiterhin die bestehende Logik
hinter `getParticipantRequirementsForParticipant` (aktiv für die Veranstaltung, ggf. an die Rolle
im Team gebunden). „Mein Event" filtert deren Ergebnis lediglich zusätzlich auf
`publicly_visible = true` — es entsteht keine zweite, abweichende Zuordnungsregel.

`MyEventRequirement`: Name, Beschreibung, `optional`, `fulfilled`. **Die Freitext-Notiz
(`CheckedParticipantRequirement.note`) wird nicht ausgeliefert** — sie ist von Hand getippt und
für interne Augen geschrieben.

Gezeigt wird die vollständige Liste freigegebener Bedingungen, erledigte eingeschlossen. Ist
mindestens eine nicht erfüllte, nicht optionale Bedingung dabei, erscheint zusätzlich ein Band
ganz oben auf der Seite. Ist alles erledigt, verschwindet das Band und die Liste bleibt weiter
unten als ruhige Bestätigung stehen.

## 6. Aufbau der Seite

Von oben nach unten:

1. **Band offener Bedingungen** — nur wenn etwas Offenes vorliegt
2. **Nächster eigener Lauf** — Countdown, Startzeit, Startposition, Wettkampf mit Runde und Lauf,
   Mannschaft. Läuft gerade ein eigener Lauf, ersetzt er diese Karte.
3. **Meine Läufe** — chronologisch, vergangene eingeklappt, kommende ausgeklappt
4. **Meine Ergebnisse** — Platz, Zeit, Strafsekunden mit Grund, DNS/DNF/DSQ, Abmeldung mit Grund
5. **Gemeldet, noch nicht terminiert**
6. **Meine Bedingungen** — vollständig, erledigte eingeschlossen

**Tageszeitliche Umsortierung:** Ist kein eigener Lauf mehr offen, rücken die Ergebnisse an die
zweite Stelle und die Läufe darunter.

Die Benennung bleibt **generisch** (Lauf, Startposition, Mannschaft, Wettkampf) — das System ist
nicht auf eine Sportart zugeschnitten.

Platz freigehalten, aber **nicht** Teil dieser Ausbaustufe: Vereinskontext, Veranstaltungsansagen
und Zeitplanänderungen, Lageplan, Urkunden-Download.

## 7. Getroffene Entscheidungen

| Entscheidung | Begründung |
| --- | --- |
| `publicly_visible` Standard „aus" | Freigabe ist eine bewusste Handlung des Veranstalters, kein Nebeneffekt einer Migration |
| Challenge-Veranstaltungen bleiben außen vor | eigener Reiter-Satz (Verein/relativ/einzeln) und anderes Datenmodell |
| `noindex` auf der **gesamten** Ergebnisseite | hängt damit an der Seite statt an der Reiter-Logik |
| Check-in/-out nicht enthalten | reines Ereignisprotokoll, in der Praxis unzuverlässig |
| Code nicht in der URL | verhindert versehentliches Teilen personenbezogener Links |

## 8. Prüfung

**Backend (`testComprehension`, Testcontainers gegen echtes Postgres):**

- Code unbekannt / fremde Veranstaltung / Helferrolle → jeweils 404
- Läufe der Person werden vollständig geliefert, fremde Läufe nicht
- Ergebnis erscheint erst gemäß `public_results_visibility` (`FINISHED_ONLY` vs. `RESULTS_COMPLETE`)
- Nur Bedingungen mit `publicly_visible = true` in der Antwort, `note` in **keiner** Antwort
- Gemeldet-ohne-Lauf erscheint unter `unscheduled` und verschwindet, sobald ein Lauf gesetzt ist
- Zwischenspeicher liefert innerhalb der TTL keine erneute Datenbanklast

**Handtests in der laufenden App:**

1. Band scannen → Reiter „Mein Event" ist offen, URL enthält den Code nicht
2. Browser schließen, Ergebnisseite direkt aufrufen → Reiter weiterhin vorhanden
3. Zweiten Code scannen → Umschalter erscheint, beide Personen abrufbar
4. Eintrag entfernen → Umschalter verschwindet bei nur noch einem Eintrag
5. Ergebnisseite ohne je gescannten Code → Reiter mit Hinweistext
6. Bedingung im Editor freigeben → erscheint im Dashboard; Häkchen entfernen → verschwindet
7. Offene Pflichtbedingung → Band oben; abhaken → Band weg, Liste bleibt
8. Lauf beenden → Ergebnis erscheint im Dashboard zeitgleich mit der Athleten-Anzeige
