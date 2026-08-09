# Vereinskette statt „Renngemeinschaft"

**Datum:** 09.08.2026
**Branch:** `feature/crf-2026`
**Belegte Migrationsnummer:** `V202608091200`

## Problem

Auf dem Schiedsrichter-Board tragen mehrere Boote desselben Laufs die identische Zeile
„Renngemeinschaft". Der Schiedsrichter kann sie nicht auseinanderhalten.

Ursache: Das Backend bildet je Mannschaft die Menge der Vereine aller Crew-Mitglieder. Ist sie
nicht eindeutig, wird pauschal `event.mixed_team_term` („Renngemeinschaft") eingesetzt —
`singletonOrFallback(clubs, mixedTeamTerm)`, siehe `LiveDashboardService.kt:142` und sechs weitere
Stellen.

Ausmaß im Produktivstand der CRF 2026 (`docs/seeds/seed-prod-crf-2026.sql`):

- **42 von 100 Meldungen sind vereinsgemischt**
- viele davon mit **4 oder 5 verschiedenen Vereinen** in einem Boot
- 58 Vereins-Datensätze stehen **46 verschiedenen freien Vereinsschreibweisen** an den Personen
  gegenüber; nur **17** davon lassen sich normalisiert einem Vereins-Datensatz zuordnen
- dieselbe Mannschaft kommt mehrfach verschieden geschrieben vor: `ARV Kiel` /
  `Akademischer Ruderverein Kiel e.V.`, `RC Bergedorf` / `Ruderclub Bergedorf e.V.`,
  `Rostocker Ruderclub` / `Rostocker Ruder-Club von 1885 e.V.`

## Entscheidungen

Getroffen im Gespräch vom 09.08.2026:

1. **Der meldende Verein ist für die Durchführung bedeutungslos** — reine Verwaltung. Angezeigt
   wird ausschließlich der Verein, den die Athleten tragen.
2. **Vollständige Kette, keine Kappung auf „+3"**: alle Vereine eines Bootes werden gezeigt, die
   Zeile bricht wie heute auf zwei Zeilen um; was dann nicht mehr passt, wird abgeschnitten.
3. **Kurzformen werden gepflegt, mit Heuristik als Fallback** — und zwar über den *Namen*, nicht
   über den Vereins-Datensatz, weil die Vereine der Gastruderer Freitext sind.
4. **Umfang:** Schiedsrichter-Board, Athleten-Anzeige, Urkunden. Startlisten und Ergebnisse
   bleiben vorerst unverändert (möglicher Nachzug im Laufe der Woche).
5. **Urkunden zeigen immer die vollen Vereinsnamen** — keine Kürzung, auch keine heuristische.
6. **Kein Handschalter auf dem Board**: die Karte zeigt mehr, je breiter sie ist — gestuft über
   volle Namen bis zur Crew je Person.

## Architektur

### Neue Tabelle `club_short_name`

Migration `V202608091200__club_short_names.sql`:

| Spalte | Typ | |
|---|---|---|
| `name_key` | text | Primärschlüssel, normalisierte Form des Vereinsnamens |
| `sample_name` | text not null | eine Original-Schreibweise, damit die Pflegeseite Lesbares zeigt |
| `short_name` | text not null | die gepflegte Kurzform |
| `created_at`, `created_by`, `updated_at`, `updated_by` | | wie in allen Tabellen des Projekts |

Bewusst **kein** Fremdschlüssel auf `club`: der Verein eines Athleten ist bei Gastruderern reiner
Freitext; ein Schlüssel auf die Vereinstabelle würde 29 der 46 vorkommenden Namen ausschließen.

### Normalisierung — `ClubNameKey`

Reine Kotlin-Funktion, keine Abhängigkeiten:

1. Unicode-Normalisierung (NFC), Kleinschreibung
2. Rechtsform entfernen: `e.V.`, `e. V.`, `eV`
3. Jahreszahlen und Klammerzusätze mit Ziffern entfernen (`von 1889`, `(1879/83)`, `v. 1899`)
4. alle übrigen Nicht-Buchstaben/Ziffern entfernen (Bindestriche, Punkte, Leerzeichen)

Damit fallen automatisch zusammen, was sich nur in Ballast unterscheidet
(`Rostocker Ruderclub` = `Rostocker Ruder-Club von 1885 e.V.`). Echte Abkürzungsvarianten
(`ARV Kiel` ↔ `Akademischer Ruderverein Kiel e.V.`) erkennt keine Regel — die führt die
Pflegeseite zusammen, indem beide Zeilen dieselbe Kurzform bekommen. Das ist eine bewusste Grenze
und wird als Test festgeschrieben.

### Kurzform eines Vereinsnamens

```
kurzform(name) = club_short_name[ClubNameKey.of(name)]?.short_name ?: heuristik(name)
```

`heuristik` ist die heutige `shortClubName` aus
`frontend/src/components/event/liveDashboard/common.ts` — Rechtsform und Gründungsjahre entfallen,
gängige Vereinstypen werden abgekürzt (`Ruderclub` → `RC`, `Rudergesellschaft` → `RG`, …). Sie
zieht **nach Kotlin um**, weil Board, Athleten-Anzeige und Pflegeseite jetzt dieselbe Regel
brauchen; die TS-Fassung samt ihrem Test entfällt.

### Verein je Athlet

```
vereinDerPerson = if (participant.external) participant.external_club_name
                  else club.name des eigenen Vereins der Person
```

Heute ist in allen betroffenen Abfragen `CLUB` auf den **meldenden** Verein gejoint. Jede dieser
Abfragen braucht einen zweiten, aliasierten `CLUB`-Join auf `PARTICIPANT.CLUB`.

### `ClubComposition`

Ein Baustein ersetzt die verstreuten `singletonOrFallback(clubs, mixedTeamTerm)`-Aufrufe. Eingabe
ist die Crew in Bootsreihenfolge, Ausgabe:

- `full` — volle Vereinsnamen, mit ` / ` verbunden, Duplikate zusammengefasst, Reihenfolge =
  Reihenfolge im Boot
- `short` — dieselbe Kette in Kurzformen
- bei genau einem Verein beide Male schlicht dieser Verein; für die 58 reinen Vereinsboote ändert
  sich also nichts
- Personen ohne Verein und Platzhalter (`N.N.`, kommt in den echten Daten vor) fallen still raus,
  statt eine leere Stelle in die Kette zu setzen

`event.mixed_team_term` bleibt bestehen und wird weiterhin von Startlisten, Ergebnissen und der
Meldeansicht verwendet — nur die drei umgestellten Anzeigen zeigen ihn nicht mehr.

## Anzeigen

### Schiedsrichter-Board

Drei Stufen, gesteuert von der **Kartenbreite** per Container-Query — dieselbe Mechanik, die seit
`e222ee27` Kopfzeile und Status-Schild steuert (`WIDE_CARD_PX`):

| Kartenbreite | Zeile unter der Bahn |
|---|---|
| < 480 px | Kurzform-Kette: `Mainzer RV / Marburger RV / RK Flensburg / RC Nürtingen / 1. KRC` |
| ≥ 480 px | dieselbe Kette in vollen Vereinsnamen |
| ≥ 700 px | zusätzlich die Crew: je Person Nachname · Vereinskurzform, Rolle in Klammern |

Die dritte Stufe braucht Personendaten, die heute bewusst nicht im Sekunden-Poll stecken. Deshalb
bekommt `GET /event/{id}/liveDashboard` einen zweiten Schalter neben `scope`: `crew=true`, gesetzt
nur bei einer Fensterbreite ab 1440 px — analog zu `dashboardScope(wide, tab)` in
`liveDashboard/common.ts`. Auf dem Telefon bleibt die Nutzlast unverändert; am Laptop kommen grob
5 KB gzip je Abruf dazu. **Fehlt die Crew im Datensatz, rendert die Karte Stufe 2** statt einer
leeren Fläche — das ist der Fall unmittelbar nach dem Verbreitern des Fensters.

DTO-Änderung an `LiveDashboardTeamDto`:

- `actualClubName` entfällt, ersetzt durch `clubsShort` und `clubsFull`
- neu `crew: List<LiveDashboardCrewMemberDto>?` — Nachname, Vereinskurzform, Rollenkürzel; null,
  solange `crew=false`
- `clubName` (meldender Verein) bleibt im Datensatz, wird aber nirgends mehr angezeigt

Das Overlay zeigt unverändert die volle Mannschaft — künftig mit dem Verein **jeder Person**
statt dem pauschalen Teamverein.

### Athleten-Anzeige

Dieselbe Kette, zwei Stufen statt drei: volle Vereinsnamen auf dem großen Schirm, Kurzform bei
schmalem Viewport. Keine Crew-Stufe — das Board am Steg muss auf Abstand lesbar bleiben.
Betroffen sind die drei `actualClubName`-Stellen in `EventInfoService.kt` (465, 504, 537) und ihre
Weiterreichung in `eventInfo/control/Conversions.kt`.

### Urkunden

`AwardCertificateService.kt:135` setzt statt `singletonOrFallback(...)` die **volle** Kette ein,
mit ` / ` verbunden, ohne jede Kürzung.

**Offener Vorbehalt:** bei fünf Vereinen sind das rund 130 Zeichen in einem Feld, das bisher einen
Vereinsnamen erwartet hat. Die Vorlagen haben feste Textkästen. Ob das Feld umbricht oder
überläuft, wird **nicht** blind eingebaut, sondern im Urkunden-Editor nachgesehen und als
Handtest im Testkatalog geführt.

## Pflegeseite

Ort: Stammdaten → **Vereinskurzformen**, global (Vereinsnamen hängen nicht an einer
Veranstaltung).

Rechte: Lesen mit `ReadClubGlobal`, Ändern mit `UpdateClubGlobal` — **kein neues Privileg**. Neue
Privilegien landen erfahrungsgemäß nur an der Admin-Rolle und fehlen allen anderen still
(siehe `crf-2026-mr-vorbereitung`).

Die Liste zeigt jede Schreibweise, die im System vorkommt — `club.name` und
`participant.external_club_name` zusammengeworfen, gruppiert nach `name_key`:

```
Rostocker Ruderclub                              [ Rostocker RC        ]  (automatisch)
  auch: Rostocker Ruder-Club von 1885 e.V.
Akademischer Ruderverein Kiel e.V.               [ ARV Kiel            ]  (gepflegt)
ARV Kiel                                         [ ARV Kiel            ]  (gepflegt)
Erster Kieler Ruder-Club von 1862 e.V.           [ 1. KRC              ]  (gepflegt)
```

Eigenschaften, die die Seite tragen:

- Das Eingabefeld ist mit der **automatisch erzeugten** Kurzform vorbelegt, nicht leer — 46 Zeilen
  sind so in einer Sitzung durchgesehen, und wer nichts anfasst, verliert nichts.
- Leeren = Eintrag löschen, danach greift wieder die Heuristik.
- Zusammengefasste Schreibweisen stehen sichtbar untereinander (`auch: …`) — die Kontrolle gegen
  eine Normalisierung, die zwei verschiedene Vereine verschmilzt.
- Filter „nur Vereine dieser Veranstaltung", damit vor der CRF genau die 46 relevanten Namen
  dastehen und nicht der gesamte Bestand.

Endpunkte:

- `GET /clubShortName?eventId=…` — Liste der vorkommenden Namen, gruppiert, mit aufgelöster
  Kurzform und der Angabe, ob sie gepflegt oder automatisch ist
- `PUT /clubShortName/{nameKey}` — Kurzform setzen
- `DELETE /clubShortName/{nameKey}` — Kurzform entfernen, zurück zur Heuristik

## Tests

**Backend, ohne DB**

- `ClubNameKey`: die echten Paare aus den Meldedaten (`Rostocker Ruderclub` ↔ `… von 1885 e.V.`,
  `Pirnaer Ruderverein` ↔ `… 1872 e.V.`, `Kölner Ruderverein von 1877` ↔ `… e.V.`) landen auf
  demselben Schlüssel — und `ARV Kiel` bewusst **nicht** auf dem von
  `Akademischer Ruderverein Kiel`, damit die Grenze der Automatik festgeschrieben ist.
- Portierte Heuristik: die Fälle aus dem heutigen TS-Test wandern nach Kotlin.
- `ClubComposition`: ein Verein → unverändert dieser Name; fünf verschiedene → Kette in
  Bootsreihenfolge; doppelter Verein → einmal; Person ohne Verein und `N.N.` fallen raus.

**Backend, gegen echtes Postgres** (`testComprehension`, Testcontainers — die Ebene, die hier
gern übersehen wird, siehe `backend-db-tests-testcontainers`)

- eine gemischte Meldung liefert im Live-Dashboard die erwartete Kette
- ein gepflegter Alias schlägt die Heuristik
- eine **Ummeldung** ändert die Kette, wenn die Ersatzperson aus einem anderen Verein kommt — der
  Fall, der im Live-Betrieb wirklich weh tut

**Frontend**

Die Stufen selbst sind reines CSS (Container-Query) und in jsdom nicht prüfbar. Getestet wird die
Entscheidungsfunktion: fehlt die Crew im Datensatz, rendert die Karte Stufe 2.

**Handtests** (in `docs/superpowers/specs/2026-08-05-testkatalog-crf-2026.md` nachtragen)

- die drei Stufen am echten Prod-Abzug durchklicken: Telefon / Tablet-Spalte / Laptop
- Athleten-Anzeige am großen Schirm
- Urkunden-Fall mit fünf Vereinen im Vorlagen-Editor — der wahrscheinlichste Überraschungspunkt

## Nicht in diesem Entwurf

- Startlisten (Spalte „Team", Aushang/CSV) und Ergebnisausgabe — als möglicher Nachzug im Laufe
  der Woche vorgemerkt
- ein frei eingegebener Mannschaftsname je Meldung. `competition_registration.name` bleibt, was es
  ist: der automatische `#1`/`#2`-Zähler, den `CompetitionRegistrationService` beim Anlegen und
  Löschen neu vergibt
- eine Vorschlagsliste der 46 CRF-Kurzformen zum Gegenlesen — sinnvoll, aber getrennt von der
  Implementierung
