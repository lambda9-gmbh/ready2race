Danke für die ausführliche Spec und vor allem für die beiden öffentlichen Testrennen — daran ließ sich fast alles ohne Account verifizieren. Umgesetzt ist die Integration inzwischen; hier der Stand und die Stellen, an denen wir bewusst vom Vorschlag abgewichen sind.

## Abweichung 1: Zuordnung über die Registrierungs-UUID statt über Bib + Wave

Das ist die wichtigste Änderung, und sie hat mehrere andere Punkte aufgelöst.

Der Vorschlag matcht über `Bib number`. Das kollidiert mit einer Eigenschaft von ready2race, die von außen nicht sichtbar ist: `competition_match_team.start_number` ist trotz des Namens **keine wettkampfweite Startnummer**, sondern eine Bahn-/Positionsnummer *innerhalb eines Laufs*. Bei 2-Boot-Läufen ist sie in jedem Lauf wieder 1 und 2. Dein Testdatensatz hat dagegen boot-stabile Bibs (1–13 / 21–26). Für einen Bib-basierten Rückweg hätten wir also erst eine stabile, rennenweit eindeutige Nummernvergabe bauen müssen — inklusive Offsets zwischen CM1x und CF1x, die im selben RaceClocker-Rennen liegen.

Stattdessen reist jetzt die `competition_registration`-UUID mit, wie im bestehenden Webscorer-Weg. Damit ist die Bib reine Anzeige und die ganze Eindeutigkeitsfrage entfällt.

**Nicht das Custom-Feld, sondern „Extra info".** §6 verwirft das Custom-Feld aus UX-Gründen — der Punkt ist berechtigt, es gibt aber noch zwei härtere Gründe. Ich habe dazu 85 öffentliche RaceClocker-Feeds ausgewertet:

- **Der JSON-Key des Custom-Felds wandert mit dem Label.** Bei `CustomLabel: "Dog's Name"` heißt der Key `"Dog's Name"`, und `"Custom"` verschwindet. Dasselbe bei benannten Splits (`"Footbridge"` statt `"Split 1"`). Ein fest verdrahtetes `"Custom"` wäre also brüchig gewesen.
- **Kollisionsgefahr:** in einem Feed steht `CustomLabel: "Gender"` — das Custom-Feld überschreibt dort das Standardfeld gleichen Namens.
- Bei einer Ruderregatta ist das Custom-Feld ohnehin für den Bootsnamen verplant (nennt RaceClocker selbst als Anwendungsfall).

`ExtraInfo` hat dagegen einen festen Key, erlaubt mehrere Spalten und liefert selbstbeschreibende Paare:

```json
"ExtraInfo": [["R2R-ID", "3f7a1c2e-…"], ["Bootsname", "Aquarius"]]
```

Gelesen wird label-unabhängig: gesucht wird der Wert, der als UUID parst. Ein Umbenennen der Exportspalte kann den Rückweg damit nicht brechen.

**Wichtig:** Der Wave-Name bleibt trotzdem nötig. Ein Team steht mehrfach im Feed — Henrik Andersen (Bib 4) hat im Läufe-Rennen vier Zeilen (AF1, VF2, HF1, Finale B). Die UUID identifiziert das *Team*, nicht die *Zeile*. Der Schlüssel ist also **(Wave, UUID)**.

## Abweichung 2: Penalty wird ignoriert — und das ist die korrekte Behandlung

§8 listet „Strafe optional auf ready2race-Ergebnis abbilden". Das wäre eine Doppelzählung gewesen. An 42 Zeilen mit `Penalty ≠ 0` aus drei öffentlichen Rennen geprüft:

| | |
|---|---|
| `Result` ≠ `Finish − Start`, Differenz = Penalty | **42 von 42** |
| `Result` = `Finish − Start` (Strafe fehlt) | 0 |

**`Result` ist die fertige Wertungszeit inklusive Strafzeit.** Nebenbefund: Das Vorzeichen ist nicht verlässlich (ein Rennen nutzt `-58` als Zuschlag, ein anderes `-41` als Gutschrift), und es gibt Zeilen mit `Result: "DNS"` bei `Penalty: "-41"`. Gut, dass wir nicht selbst rechnen.

## Kleinere Abweichungen

- **Kein `raceclocker_wave_name` pro Match.** Der Wave-Name *ist* der Match-Name (`competition_setup_match.name`) und damit schon persistiert. Ein zusätzlicher Snapshot hätte einen Schreib-Seiteneffekt in den `GET`-Startlisten-Endpoint gebracht. Bei Namensdrift meldet der Pull jetzt „Wave X nicht im Feed" statt still danebenzugreifen.
- **Keine JSON-Import-Config.** Wir brauchen genau vier Felder (`ExtraInfo`, `Wave`, `Result`, `Bib number`), alle im Feed englisch — auch bei DE-Accounts (in allen 85 Feeds `Finish`, nie `Ziel`). `Result in seconds` und die Zielzeitstempel brauchen wir gar nicht, womit sich das Lokalisierungsrisiko aus §5.3 weitgehend erledigt. Fest verdrahtet statt konfigurierbar, wie gewünscht.
- **Keine Kopfzeilen-Sonderbehandlung.** RaceClockers Importer kann die Kopfzeile ausblenden und übernimmt daraus die Feldlabels — der Export bleibt also unverändert mit Kopfzeile.
- **Zwei URLs am Wettkampf**, wie vorgeschlagen. Welche gilt, leitet ready2race aus `competition_setup_round.is_qualification` ab (kam gerade mit dem Match-Naming-Branch dazu) — es muss nichts zugeordnet werden.
- **Host-Beschränkung:** Der Pull akzeptiert nur `https` auf `raceclocker.com`. Die URL ist nutzergepflegt, ohne diese Einschränkung wäre der Endpoint ein SSRF-Hebel.
- **Duplikate** (RaceClocker ist insert-only): Kommt dieselbe (Wave, UUID) mehrfach vor, bricht der Pull mit Namensnennung ab und schreibt nichts. Generell gilt: Es wird erst geschrieben, wenn alle Prüfungen bestanden sind.

## Was gebaut ist

- Migration: zwei URL-Spalten an `competition`, eine `col_participant_fullname`-Exportspalte (RaceClocker hat nur *ein* Namensfeld, ready2race konnte bisher nur Vor-/Nachname getrennt) und die zwei Presets „RaceClocker Zeitfahren" / „RaceClocker Läufe"
- `POST …/competitionExecution/{matchId}/results/from-raceclocker` — mündet in den bestehenden Schreibpfad (`prepareForNewPlaces`, `TimecodeRepo`, Platzberechnung). Platz und DNS/DNF/DQ mussten wir nicht neu bauen: ready2race berechnet Plätze aus Zeiten bereits, wenn keine mitgeliefert werden, und `failed`/`failedReason` passt 1:1 auf die Statustexte.
- `GET`/`PUT …/competitionExecution/raceclocker-config` plus Dialog im Ausführungs-Tab
- „von RaceClocker holen" als dritter Eintrag im bestehenden „Ergebnis eintragen"-Menü
- 12 Tests gegen eine Fixture aus den echten Feeds inkl. der vier eingebauten Sonderfälle

**Webscorer bleibt vollständig unangetastet** — eigene Presets, eigener Upload-Pfad, keine geänderte Semantik.

## Zwei offene Verifikationen

Beides braucht einen Testimport mit Account-Zugang:

1. Reicht RaceClocker eine 36-stellige UUID durch `Extra info` unverändert durch? Längster in freier Wildbahn beobachteter Wert: **33 Zeichen**. Ein 32-Zeichen-Limit ist damit ausgeschlossen (das war das gefährliche Szenario, weil eine UUID ohne Bindestriche genau 32 hat), 36 ist aber nicht positiv belegt.
2. Bleibt das Label `R2R-ID` im `ExtraInfo`-Paar erhalten? Für uns unkritisch — wir suchen nach UUID-Form, nicht nach dem Label —, aber gut zu wissen.

Falls (1) scheitert, fällt die Kernentscheidung und wir landen wieder bei Bib + Wave, inklusive der Nummernvergabe. Deshalb wäre das der erste Test vor dem Produktiveinsatz.
