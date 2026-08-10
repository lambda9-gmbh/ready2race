# Design: ready2race ↔ RaceClocker Integration (halb-automatisch)

**Stand:** 2026-07-19
**Status:** Entwurf zur Umsetzung
**Betroffene Domains:** `competitionExecution`, `startListConfig`, `matchResultImportConfig`, `competition`, `competitionSetup`

---

## 1. Ziel & Umfang

ready2race verwaltet Events, Anmeldungen, Wettkämpfe und die **Setzung/Progression** (Runden, Matches, Ergebnisse). [RaceClocker](https://www.raceclocker.com/) ist ein Cloud-Tool für **manuelle Zeitmessung**. Ziel dieser Integration ist ein **halb-automatischer** Datenfluss:

- **Hinweg (Startliste → RaceClocker):** bleibt datei-/clipboard-basiert (RaceClocker hat keine Import-API), wird aber durch mitgelieferte Spalten-Presets und Deep-Links vereinfacht.
- **Rückweg (Ergebnisse → ready2race):** wird echt automatisiert über einen **On-Demand-JSON-Pull** aus RaceClockers öffentlichem Ergebnis-Feed und ersetzt das manuelle „Excel herunterladen und wieder hochladen".

**Rollenverteilung (Kernentscheidung):**

> **ready2race ist das Bracket-Hirn** — es macht die komplette Setzung/Progression und kennt jeden Lauf und jede Crew.
> **RaceClocker ist die Stoppuhr pro Lauf.** ready2race erzeugt je Lauf eine Startliste, appended sie in RaceClocker, RaceClocker misst, ready2race holt die Zeiten per JSON zurück und ordnet sie dem richtigen Lauf zu.

Nicht Teil dieser Spec (YAGNI): Live-/Polling-Sync, ein Push von Startlisten in RaceClocker (technisch nicht möglich), RaceClocker-Kontoverwaltung.

---

## 2. Realitätsgrenzen von RaceClocker (verifiziert am 2026-07-19)

Diese Punkte wurden durch ein echtes Testrennen im RaceClocker-UI belegt (siehe §9):

1. **Kein Upsert, nur Insert.** Ein erneuter Import derselben Crews erzeugt Duplikate, kein Update. Jede Wave darf nur **einmal** importiert werden.
2. **Kein Push-/Import-API.** Startlisten kommen nur per Datei-Upload (.xlsx/.csv) oder Copy-Paste + Spalten-Mapper rein.
3. **Nur Einzelstart/Zeitfahren hat einen echten Countdown.** Die Time-Trial-Runde muss deshalb als eigenes RaceClocker-Rennen vom Typ *Einzelstarts (Zeitfahren)* laufen, die K.o.-Läufe als eigenes Rennen vom Typ *Start in mehreren Läufen (Wave starts)*.
4. **Eine gemappte „Lauf"-Spalte schaltet ein Rennen automatisch in den Wave-Modus.** Wird beim Import eine Spalte auf das RaceClocker-Feld *Lauf* gemappt, wechselt der Starttyp selbsttätig auf *Start in mehreren Läufen*. → Der TT-Export darf **keine** Lauf-Spalte enthalten (Trennung der Klassen über die *Kategorie*), der Heats-Export **muss** sie enthalten.
5. **Ergebnis-Feed als JSON:** jede öffentliche Ergebnis-URL liefert mit `?json=1` die Rohdaten (siehe §5).

**Konsequenz:** Pro ready2race-Wettkampf sind **zwei RaceClocker-Rennen** und damit **zwei Ergebnis-URLs** nötig — eines für die TT, eines für die Läufe.

---

## 3. Datenmodell-Ergänzungen (ready2race)

Pro Wettkampf (bzw. auf passender Ebene in `competition`/`competitionProperties`):

| Feld | Zweck |
|---|---|
| `raceclocker_tt_results_url` | Öffentliche Ergebnis-URL des Einzelstart-Rennens (TT-Runde) |
| `raceclocker_heats_results_url` | Öffentliche Ergebnis-URL des Wave-Rennens (alle übrigen Runden) |

Pro Match:

| Feld | Zweck |
|---|---|
| `raceclocker_wave_name` | Der beim Startlisten-Export erzeugte Wave-Name (z.B. `VF2 CM1x`). Round-Trip-Schlüssel gegen das JSON-Feld `Wave`. Wird ohnehin beim Export gebildet und muss nur persistiert werden. |

**Configs:**

- **Zwei `StartListConfig`** (Presets „RaceClocker TT" ohne Lauf-Spalte, „RaceClocker Heats" mit Lauf-Spalte). Basiert auf der bestehenden `startListConfig`-Domain; `colRoundName`/`colMatchName` bilden die Wave.
- **Neue JSON-Import-Config** analog `matchResultImportConfig`, aber auf JSON-Feldnamen statt Excel-Spalten (siehe §5). Ein Default-Preset wird mitgeliefert. Der bestehende `.xlsx`-Upload (`PUT /competitionExecution/{matchId}/results-file`) bleibt unverändert als Fallback erhalten.

**Startnummern-Eindeutigkeit:** ready2race muss Bibs **pro RaceClocker-Rennen eindeutig** vergeben (im Test: CM1x 1–13, CF1x 21–26). Der Rückweg matcht über die Bib; Kollisionen innerhalb eines Rennens würden Ergebnisse falsch zuordnen.

---

## 4. Flow A — Startliste raus (Wave-für-Wave-Append)

Baut auf `GET /competitionExecution/{competitionMatchId}/startList?fileType=CSV` + `StartListConfig` auf.

### TT-Rennen (Einzelstart)
Einmaliger Export/Import aller TT-Boote, **ohne** Lauf-Spalte. Kategorie (CM1x/CF1x) trennt die Klassen. Danach in RaceClocker: Rennen vom Typ *Einzelstarts (Zeitfahren)*, Import per Drag&Drop + Spalten-Mapper (Kategorie → Kategorie, Bib → Bugnummer, Name → Name, Verein → Verein).

### Läufe-Rennen (Wave starts) — Insert-only
Wegen Insert-only (kein Update) wird **pro Lauf genau einmal** importiert: Nach der TT appended ready2race jeden Folgelauf einzeln als eigene Wave, sobald die Setzung berechnet ist. Kein Re-Import, keine Duplikate. Das passt genau zur ready2race-seitigen Progression — ready2race liefert fertig gesetzte Läufe, RaceClocker misst sie.

> Andere Import-Wege (ein einmaliges Platzhalter-Skelett oder RaceClockers eigene Progression) wurden bewusst verworfen, damit die Zuständigkeit für die Setzung eindeutig bei ready2race bleibt.

Der Wave-Name (`colRoundName`/`colMatchName`) wird beim Export gebildet, muss **eindeutig** sein (Runde + Kürzel + Kategorie, z.B. `VF2 CM1x`) und am Match gespeichert werden (§3) — er ist der Round-Trip-Schlüssel.

Optionale Vereinfachung (kein Muss): am Wettkampf hinterlegte RaceClocker-Event-URL + „In RaceClocker öffnen"-Deep-Link + „Für RaceClocker kopieren" (clipboard-fertig, damit RaceClockers gemerktes Mapping greift).

---

## 5. Flow B — Ergebnisse rein (On-Demand-JSON-Pull)

### Auslöser
Button **„Ergebnisse von RaceClocker holen"** → neuer Endpoint, z.B. `POST /competitionExecution/{matchId}/results/from-raceclocker` (bzw. auf Wettkampfebene für alle Matches). Backend holt `{results_url}?json=1`.

### Die zwei Join-Keys
- **`Wave`** → ready2race-Match (via `raceclocker_wave_name`). TT-Boote haben `Wave: "Kein"` → über die TT-URL + Kategorie/Runde zugeordnet.
- **`Bib number`** → Team im Match (= `startNumber`, intern via `match.teams.find { it.startNumber == ... }?.competitionRegistration` auf die Team-UUID aufgelöst — die UUID bleibt vollständig in ready2race).

### JSON-Struktur (real, verifiziert)
Der Feed ist ein Array von Teilnehmer-Objekten + ein abschließendes `RaceInfo`-Objekt. Relevante Felder pro Teilnehmer:

| JSON-Feld | Bedeutung | Hinweis |
|---|---|---|
| `Bib number` | Startnummer | **String**, nicht Zahl |
| `Wave` | Lauf-Name | bei Einzelstart `"Kein"` |
| `Category` | Kategorie (CM1x …) | |
| `Result` | formatierte Zeit `HH:MM:SS.d` **oder** Status-Text | siehe DNS/DNF/DQ |
| `Result in seconds` | Zeit in Sekunden | **String**; `"0"` bei No-Result |
| `Ziel` / `Finish` | Zielzeitstempel | **lokalisierter Feldname!** (DE-Account: `Ziel`, EN: `Finish`) |
| `Start` | Startzeitstempel | |
| `Penalty` / `Penalty note` | Strafe (Sekunden) + Notiz | |
| `Split 1..4` | Zwischenzeiten | für diese Integration ignoriert |

`RaceInfo` enthält u.a. `Name`, `StartType`, `HasCategories`, `HasBibNumbers`.

### Kritische Verarbeitungsregeln
1. **Platz wird berechnet, nicht gelesen** — der Feed hat **kein** Platz-/Rang-Feld. Rang = Sortierung nach `Result in seconds` (aufsteigend) **innerhalb des Waves**.
2. **DNS/DNF/DQ stehen im Feld `Result` als Text** (`"DNS"`/`"DNF"`/`"DQ"`, dazu `Result in seconds: "0"`, `Ziel: "00:00:00.0"`). Es gibt **kein** separates Status-Feld. → Nicht-numerischer `Result` ⇒ `noResultReason`, kein Platz, keine Zeit.
3. **Lokalisierte Feldnamen** — das Zielfeld heißt je nach Account-Sprache `Ziel` oder `Finish`. Das Mapping muss beide kennen (oder `RaceInfo`/Sprache auswerten). Gleiches Risiko bei künftigen Feldern.
4. **String-Parsing** — `Bib number` und `Result in seconds` sind Strings; robust parsen.
5. **Teil-Ergebnis** — nur Zeilen mit gültigem `Result` (Zeit oder Status) übernehmen; Rest überspringen → mehrfach abrufbar.
6. **Idempotenz** — erneuter Pull überschreibt denselben Match (bestehender `prepareForNewPlaces`-Pfad); da nach Wave+Bib gelesen wird, keine Duplikate.

### Wiederverwendung
Nach dem Mapping (Bib → Team, `Result` → Zeit/Status, berechneter Platz) mündet der Flow in die bestehenden Ergebnis-Schreibpfade (`prepareForNewPlaces`, `TimecodeRepo`, Platz-Persistenz). Danach berechnet ready2race die **eigene** Progression (`createNextRound`), exportiert den nächsten Lauf, der appended wird → Schleife.

---

## 6. Custom-Feld / Team-Identifikation

Der Bib (Startnummer) ist der einzige benötigte Schlüssel; die Team-UUID bleibt intern in ready2race. RaceClockers **Custom-Feld** wird für die Integration **nicht** als technischer Identifikator genutzt: Es wird in der RaceClocker-UI aktuell **nicht ausgeblendet** und ist rein zu **informatorischen**, nicht zu technischen Zwecken entworfen. Ein Round-Trip technischer IDs über dieses Feld ist daher nicht vorgesehen.

---

## 7. Endpunkte & Bausteine (Umsetzung)

- **Bestehend, wiederverwenden:**
  - `GET /competitionExecution/{matchId}/startList?fileType=CSV&config=…` (Startlisten-CSV)
  - `PUT /competitionExecution/{matchId}/results-file` (xlsx-Upload, Fallback)
  - `startListConfig`-Domain, `matchResultImportConfig`-Domain
- **Neu:**
  - Datenmodell-Felder aus §3 (zwei URLs pro Wettkampf, Wave-Name pro Match)
  - JSON-Import-Config (Feldnamen-Mapping + Default-Preset, sprachrobust)
  - `POST /competitionExecution/{matchId}/results/from-raceclocker` (JSON-Pull, Platzberechnung, DNS/DNF/DQ-Erkennung)
  - Zwei mitgelieferte `StartListConfig`-Presets (TT ohne Lauf, Heats mit Lauf)
  - Frontend: URL-Felder am Wettkampf, „Ergebnisse holen"-Button, optional Deep-Link/Copy

---

## 8. Edge Cases

- Ungültige/nicht-öffentliche Ergebnis-URL, kein JSON, HTTP-Fehler → klare Fehlermeldung.
- Bib im Feed ohne passendes Team im Match (z.B. falsche URL) → Zeile melden/überspringen.
- Doppelte Bibs innerhalb eines RaceClocker-Rennens → Import ablehnen (Eindeutigkeit vorausgesetzt, §3).
- Strafe (`Penalty`) → optional auf ready2race-Ergebnis abbilden.
- Gleichstand in `Result in seconds` → definierte Tie-Break-Regel (z.B. Bib) festlegen.
- Wave-Name-Drift (RaceClocker-UI manuell umbenannt) → Pull findet Match nicht; Wave-Name als Vertrag dokumentieren.

---

## 9. Referenz-Testrennen (RaceClocker, Account „Thomas", angelegt 2026-07-19)

Vollständiger Beispieldatensatz „Schnellste 13" (Beach-Sprint-Modus, CM1x 13 Boote + CF1x 6 Boote):

| Rennen | Typ | Öffentliche URL | JSON |
|---|---|---|---|
| R2R Devtest — TT (Einzelstarts) | Einzelstart | `https://www.raceclocker.com/7ffb822a` | `?json=1` |
| R2R Devtest — Läufe (Wave starts) | Wave starts | `https://www.raceclocker.com/7c854955` | `?json=1` |

Das Läufe-Rennen enthält alle Runden (AF1–AF5 / VF1–VF4 / HF1–HF2 / Finale A+B) mit Zeiten sowie bewusst eingebaute Sonderfälle: **Piet Hauschild DNF** (AF1), **Nils Boysen DNS** (AF4), **Thore Hansen DQ** (VF4), **Hanna Petersen DNF** (VF2 CF1x).

Import-Quelldateien: `~/Developer/privat/raceclocker-user/` — `rc_1_timetrial_einzelstarts.csv` (TT), `rc_heats_all.csv` bzw. `laeufe/lauf_01..19_*.csv` (Läufe, einzeln appendbar).

RaceClocker-CSV-Format: **keine Kopfzeile**, Spalten `Kategorie, Bib, Name, Verein[, Lauf]`.
