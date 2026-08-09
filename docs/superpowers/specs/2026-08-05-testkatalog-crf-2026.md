# Testkatalog `feature/crf-2026`

**Stand:** 2026-08-07, Sammelbranch bei `4149d7de` (alle Worktrees des Tages zusammengeführt)
**Zweck:** Ein Katalog aller Fälle, die vor der Regatta am 14.08. auf **einem** gebauten Stand
durchlaufen werden. Er sammelt die Fälle über alle Arbeitsstränge (Athleten-Anzeige, Zeitstrahl,
RaceClocker, Schiedsrichter-Dashboard, Betrieb, Zeitnahme-Einstellungen, Urkunden, Lauf-Status,
Prüfungsschweregrad), damit der große Test in der Woche vom 10.08. nicht aus dem Gedächtnis
zusammengesucht werden muss.

> **Am 07.08. dazugekommen.** An diesem Tag liefen sechs Worktrees parallel; sie sind alle in
> `feature/crf-2026` zusammengeführt und danach gelöscht worden. Was sie beigetragen haben, steht
> als **C15–C26** (automatischer RaceClocker-Abruf), **E9** (QR-Code löschen), **G25–G35**
> (Vorlagen-Austausch), **H1–H10** (Lauf-Status-Chips) und **I1–I8** (Prüfungsschweregrad).
> Für **keinen** dieser Blöcke gibt es einen Nachweis aus einer laufenden Anwendung — sie sind
> ausschließlich durch Unit-Tests und Code-Reviews abgesichert. Hier zuerst hinsehen.

## Wie dieser Katalog benutzt wird

Jeder Fall trägt zwei Spalten:

- **testbar ab** — der Commit, der den Fall möglich macht. Ein älterer Stand kann ihn nicht
  bestehen; nach einem Rebase/Cherry-pick den neuen Hash nachtragen.
- **Nachweis** — Commit und Datum, auf dem der Fall zuletzt **von Hand** grün war, plus Kürzel des
  Testers. Leer heißt: nie nachgewiesen. Automatisierte Unit-Tests zählen hier nicht — sie laufen
  in `./mvnw test` und `npm run test` und decken die Rechenlogik ab, nicht den Ablauf.

Beim großen Test einen Stand bauen, alle Fälle eines Bereichs in einem Zug durchgehen und die
Nachweis-Spalte in **diesem** Dokument fortschreiben (ein Commit „Record test evidence for
<Datum>"). Fällt ein Fall durch: Fall stehen lassen, Befund als eigenen Punkt darunter notieren,
nicht die Erwartung anpassen.

## Vorbereitung

1. **Stand festlegen.** `git log --oneline -1` im Haupt-Checkout, Hash ins Testprotokoll.
2. **Worktree-Falle.** Es laufen mehrere Worktrees parallel; ein Dev-Server auf einem anderen Port
   gehört womöglich zu einem anderen Stand. Vor dem Test prüfen:
   ```bash
   lsof -nP -iTCP -sTCP:LISTEN | grep node
   ```
   und für den gefundenen Prozess `lsof -p <pid> -a -d cwd`. Nur ein Server aus dem Checkout, der
   den zu testenden Commit trägt, ist aussagekräftig. (Am 05.08. lief die Anzeige auf `:5124` aus
   dem `zeitstrahl`-Worktree — die Änderungen aus dem Hauptcheckout waren dort schlicht nicht drin.)
3. **Umgebung.** `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`,
   `cd backend && docker compose up -d`, Backend starten, `npm run dev` im Frontend. `.env` in
   `backend/` und `frontend/` sind gitignored und fehlen in frischen Worktrees.
4. **Migrationen.** Wechselt die Dev-DB zwischen Branches, kann Flyway Lücken melden;
   `-Dflyway.outOfOrder=true` beim Maven-Aufruf lässt die fehlenden Migrationen nachlaufen.
5. **Daten.** Veranstaltung mit Zeitplan, mindestens einem Wettkampf mit ≥ 4 Booten, davon eines
   abgemeldet, und einem Wettkampf mit gepflegten RaceClocker-URLs. Für den Bahn-Ablauf (C8) einen
   Lauf mit 6 Booten, davon eines ohne Zeit und eines gar nicht in RaceClocker. Für G eine
   Siegerurkunden-Vorlage, ein Mannschaftsboot und eine Renngemeinschaft; zusätzlich **ein Verein mit
   Sonderzeichen im Namen** (z. B. `AZS Łódź` oder `ČVK Praha`) für G20, eine **mehrseitige**
   Teilnahmeurkunden-Vorlage für G22 und eine zweite, absichtlich falsch typisierte Vorlage für G23.
6. **Amtspapier.** Für G4, G10 und G21 ein paar Blatt des vorgedruckten DRV-Papiers und ein Drucker,
   der randlos genug einzieht. Ohne echtes Papier lässt sich der eigentliche Zweck der Urkunden —
   Text an der richtigen Stelle auf ein bereits bedrucktes Blatt — nicht abnehmen.
7. **Seiten.** Athleten-Anzeige `/board/{eventId}` · Kiosk `/event/{eventId}/info` ·
   Schiedsrichter `/event/{eventId}/live-dashboard` · Zeitplan-Tab der Veranstaltung ·
   Wettkampf-Durchführung · Zeitnahme-Tab des Wettkampfs · Gap-Vorlagenverwaltung unter `/config`.
8. **Für C15–C26 ein echtes RaceClocker-Rennen.** Der automatische Abruf lässt sich nicht trocken
   prüfen: gebraucht werden eine Veranstaltung mit gepflegten RaceClocker-URLs, eingeschalteter
   Automatik und mindestens zwei Läufen mit geplanter Startzeit — einer im Vorlauf-Fenster, einer
   weit davor. Für C24 zusätzlich ein Lauf, bei dem in RaceClocker dieselbe Crew zweimal steht.
   Nützlich beim Zusehen: `raceclocker_polled_at`, `raceclocker_poll_error` und
   `raceclocker_auto_paused_at` auf `competition_match` direkt in der DB mitlesen.
9. **Für H den Vergleich mit dem alten Stand.** H prüft vor allem, dass sich die Kette **nicht**
   anders verhält. Am einfachsten mit zwei Fenstern: der neue Stand und ein Checkout von
   `4b98dd09` daneben, dieselbe Runde in beiden.
10. **Für I5 einen Wettkampf ohne Check-in/out.** `checkInOutRequired` abschalten und die QR-App
   auf dem Telefon offen haben — der Fall betrifft beide Oberflächen.
11. **Seed-Daten für den Zeitstrahl.** Zwei gitignorierte Skripte im `zeitstrahl`-Worktree unter
   `.superpowers/sdd/`: `seed-zeitstrahl.sql` (kleines Szenario, UUID-Präfix `5eed`) und
   `seed-foerde.sql` (Nachbau der Regatta 2025, Präfix `f0de`: 7 Wettkämpfe, 2 Renntage, 21 Slots,
   Sprint-Wettkämpfe mit Zeitfahren → Halbfinale → Finale A/B). Grundlage für die B-Fälle.

---

## A — Athleten-Anzeige

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| A1 | Spaltenreihenfolge | Links „Letztes Ergebnis", Mitte „Aktueller Lauf", rechts „Nächster Lauf" | `1e0ab80e` | |
| A2 | Uhr auf der Kiosk-Seite | „Konfigurieren"/„Vollbild" und das Vollbild-Beenden-Symbol verdecken Uhr und „Stand" nicht | `1e0ab80e` | |
| A3 | Lauf aktuell, nicht gestartet | Geplante Startzeit groß, darunter „in Vorbereitung"; keine Zeiten | `5d5506ad` | |
| A4 | Lauf gestartet | Darunter „gestartet HH:MM"; geplante Zeit bleibt sichtbar | `5d5506ad` | |
| A5 | Teilergebnisse im laufenden Lauf | Gewertete Boote zeigen „Platz. Zeit", ungewertete zeigen rechts nichts | `5d5506ad` | |
| A6 | Zeitstrafe (Detailablauf unten) | Zeit steigt um die Strafe, darunter „inkl. N s Zeitstrafe · Grund" | `0c8e378c` | |
| A7 | Manuelle Zeitstrafe | Hinweis erscheint, Zeit bleibt unverändert (nur ausgewiesen, nie verrechnet) | `5d5506ad` | |
| A8 | Ergebnis nach Laufende | Lauf wandert nach links, Kopf zeigt geplanten und echten Start, Zeiten bleiben | `5d5506ad` | |
| A9 | DNF/DNS/DSQ | Grund statt Zeit, gedämpft, kein Platz — im laufenden und im beendeten Lauf | `c1072f54` | |
| A10 | Abgemeldetes Boot | Steht unten im Ergebnis als „abgemeldet · Grund", Platz „–", keine Zeit | `5d5506ad` | |
| A11 | Mannschaft ohne Namen | „<Verein> | Team 2" aus `team_number` statt nur Vereinsname | `5d5506ad` | |
| A12 | Bahn = Startposition | Große Zahl links entspricht `start_number` des Laufs | `135e8a90` | |
| A13 | Noch nicht gesetzte Runde | Platzhalter „Lauf noch nicht gesetzt" an der Stelle des Zeitplan-Slots | `e5744801` | |
| A14 | Programmpunkt (Pause) | „Mittagspause" erscheint nur, wenn die Veranstaltung Pausen auf öffentlichen Anzeigen erlaubt | `5e9d0bda` | |
| A15 | Überfälliger Lauf | Verstrichene Startzeit → „erwartet" statt negativem Countdown; Lauf verschwindet erst nach 30 min | `6d699ec5`, `dcb4745f` | |
| A16 | Lauf ohne Startzeit | „Zeit offen", Lauf bleibt sichtbar | `6d699ec5` | |
| A17 | Verbindungsverlust | Letzter guter Stand bleibt stehen, nach ~2 Takten „Verbindung unterbrochen"; nie „kein Lauf auf dem Wasser" | `0f6812bd` | |
| A18 | Telefon-Layout | Blöcke untereinander, Namen brechen um, keine waagerechte Rolle | `8c013ff0` | |
| A19 | Takt | Anzeige holt im konfigurierten Takt, nie schneller als 10 s; Änderung ist nach höchstens ~15 s sichtbar | `77fca680` | |
| A20 | Ohne Anmeldung | `/board/{eventId}` funktioniert im privaten Fenster vollständig | `8eda6dc1` | |
| A21 | Überfälliger Platzhalter | Wartender Slot und Programmpunkt verschwinden 30 min nach ihrer Startzeit aus „Nächster Lauf" — dieselbe Nachfrist wie A15. Die Morgenbesprechung darf am Nachmittag nicht mehr die drei Plätze der Spalte belegen | `23e7c170` | |
| A22 | Abgesagter Lauf | Ein abgesagter Slot verschwindet aus „Nächster Lauf" und aus der Kiosk-Ansicht — auch wenn die Runde bereits gesetzt ist und es den Lauf wirklich gibt. Die übrigen Läufe rücken nach | `f81f8bc5` | |
| A23 | Ergebnis erst nach dem Beenden | Voreinstellung „nur beendete Läufe": ein vollständig gewerteter, aber nicht beendeter Lauf steht NICHT unter „Letztes Ergebnis" (auch nicht auf der Kiosk-Ansicht und der öffentlichen Ergebnisseite). Nach dem Beenden-Klick erscheint er dort | `7bd78c3e` | |
| A24 | Stufe „auch vollständig gewertete" | Veranstaltung auf „Auch vollständig gewertete Läufe" umgestellt: derselbe Lauf erscheint sofort unter „Letztes Ergebnis" — und steht dann bewusst gleichzeitig unter „Aktueller Lauf". Das ist das Verhalten von vor der Einstellung | `7bd78c3e` | |

## B — Zeitstrahl und Laufkette

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| B1 | Slot anlegen/ändern/löschen | Startzeit schreibt auf den Lauf durch; Besitz und Eindeutigkeit werden geprüft | `e3329e01`, `f3d3870b` | |
| B2 | Slot-Zustände | Wartend, gesetzt, gelaufen, abgesagt, verwaist werden im Tag-Agenda-Tab korrekt unterschieden | `a05e644a`, `18a6ebf2` | |
| B3 | Verschieben: plus/Uhrzeit/komprimieren | Vorschau vor dem Anwenden, Ergebnis entspricht der Vorschau | `9ae3cfe6`, `08f5c341` | |
| B4 | Verschieben über den Renntag hinaus | Wird abgelehnt, mit verständlicher Meldung | `4bfb2278` | |
| B5 | Lauf absagen | Nur mit Audit; ein Lauf, der schon unterwegs ist, lässt sich nicht absagen — „unterwegs" heißt bereits **aktiviert**, nicht erst mit Ist-Start aus der Zeitnahme. Der Zeitplan-Tab bietet die Aktion dann gar nicht erst an, ein direkter API-Aufruf antwortet 409. Ein Lauf darf nie abgesagt und laufend zugleich sein | `bf3e47cd`, `0b56ebf2`, `f81f8bc5` | |
| B6 | Wortwahl | Oberfläche spricht von „abgesagt", nicht von „übersprungen"; Umfang der Aktion ist benannt | `8bd94c1f`, `041bf03d` | |
| B7 | Excel-Import | Zeilenweise Vorschau, echte Excel-Zeilennummern in Fehlermeldungen, Namensabgleich | `8cc599eb`, `01863020`, `8bf3ef04` | |
| B8 | Kette: Fortschaltmodus | Drei Modi am Event: **Schiedsrichter** (Beenden und Kette über das Dashboard), **Regattabüro** (Beenden/Aktivieren nur über den Zeitplan — im Dashboard fehlt der Beenden-Knopf, die API antwortet dort 409), **Deaktiviert** (Beenden wirkt nur auf den Lauf) | `ed7160d1`, `27080b8b` | |
| B9 | Kette: Wartepunkt | Kette hält am wartenden Slot, ganze Startgruppe wird abgewartet | `6d6be623`, `cd83aed2` | |
| B10 | Kette nach Absage | Nach Absage des gewarteten Slots läuft die Kette weiter | `41b808e9` | |
| B11 | Läufe aus dem Zeitplan steuern | Büro kann Läufe direkt aus dem Zeitplan starten/beenden; beendete Läufe lassen sich nicht reaktivieren | `65406827`, `c9709773` | |
| B12 | Startzeit von Slot-Läufen | Manuelle Startzeit-Änderung am Lauf wird abgelehnt, solange ein Slot ihn führt | `c8441e8c` | |
| B13 | Zeitstrahl-Anzeige | Indikator „jetzt" im Plan-Tab und auf dem Schiedsrichter-Dashboard steht an der richtigen Stelle | `ba7e7bdb` | |
| B14 | Runde entfällt (Durchführung) | Aktion sitzt in der Durchführung, nicht im Zeitplan, und erscheint nur, wenn die Runde nichts zu fahren hat (jeder Lauf ein Freilos) | `3ebbc959` | |
| B15 | Runde entfällt: Schutzregel | Runde mit fahrbaren Läufen und noch nicht gesetzte Runde werden serverseitig mit 409 abgelehnt — auch bei direktem API-Aufruf | `748ade1c` | |
| B16 | Umfang der Absage | Tooltip, Text und Knopf benennen „nur diesen Lauf" bzw. „alle N Läufe"; bei einer Runde aus einem einzigen Slot fehlt die Runden-Aktion ganz | `041bf03d` | |
| B17 | Wartende Slots bearbeiten | Ein Slot ohne gesetzten Lauf lässt sich in Zeit und Dauer ändern | `19247b67` | |
| B18 | Vom Zeitplan zum Lauf | Verknüpfte Slots springen in die Durchführung des Wettkampfs | `a05e644a`, `19247b67` | |
| B19 | Stauchen unmöglich | Zu großer Verzug meldet die maximal aufholbare Zeit; der Wert kommt strukturiert aus der Antwort, nicht per Textanalyse | `0fb21aea` | |
| B20 | Beendete Läufe bei Import/Verschieben | Ihre Startzeit bleibt unverändert — Historie wird nicht überschrieben | `0fb21aea` | |
| B21 | Vorziehen (negativer Verzug) | Ein Shift, der den Vorgänger-Slot überholen würde, wird abgelehnt | `0fb21aea` | |
| B22 | Import ersetzt alles | Vorschau schreibt nichts; erst „Importieren" ersetzt alle Slots; eine doppelte Zeile blockiert den Import | `8cc599eb`, `8bf3ef04` | |
| B23 | Symbolspalten | Aktionssymbole stehen über alle Zeilen in festen Spalten untereinander | `bb39d7f7` | |
| B24 | Absage im Schiedsrichter-Dashboard | Ein abgesagter Lauf verschwindet dort NICHT, sondern steht durchgestrichen mit der Kennzeichnung „Abgesagt" und ohne Steuerknöpfe; der Live-Ausschnitt springt über ihn hinweg zum nächsten Lauf | `f81f8bc5` | |

## C — RaceClocker

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| C1 | Startlisten-Export | Export trägt die R2R-UUID in „Extra info"; ohne dieses Mapping findet der Pull keine Boote | `dd8d67b8` | |
| C2 | Wellenname | Wellenname enthält die Startzeit, Lauf wird im Feed gefunden | `09e6a642` | |
| C3 | Ergebnisse ziehen | Zeiten und Plätze landen am richtigen Boot, Plätze aus den Zeiten abgeleitet | `dd8d67b8` | |
| C4 | Teil-Pull | Ein Pull mit nur teilweise genommenen Zeiten ist wiederholbar, ohne die übrigen Boote zu beschädigen | `b1e2e238` | |
| C5 | Echter Start | Früheste gemessene Startzeit überschreibt `started_at`, auch gegen einen manuellen Stempel | `f86665ae`, `e4cb8753` | |
| C6 | Mitternacht als Platzhalter | `00:00:00.0` für Boote ohne Start setzt `started_at` nicht auf Mitternacht | `c0458d8d` | |
| C7 | Zeitstrafe aus dem Feed | `Penalty`/`Penalty note` landen als Ausweisung am Boot, die Zeit wird nicht zusätzlich verrechnet | `0c8e378c` | |
| C8 | Bahnen aus „Rank" | Eine Bahnvertauschung in RaceClocker schlägt auf die Bahnen durch, auch für Boote ohne Zeit; der Bib wandert nicht mit | `d64ae540` | |
| C9 | Doppelte Mannschaft | Zwei Zeilen für dasselbe Boot → Pull verweigert mit Namensliste, statt zu raten | `dd8d67b8` | |
| C10 | Keine Ergebnisse | Pull ohne gewertete Zeilen meldet das verständlich und ändert nichts | `dd8d67b8` | |
| C11 | Fehlerfälle URL | Fehlende, ungültige und nicht erreichbare URL werden unterschieden gemeldet | `dd8d67b8` | |
| C12 | Bahnen bei mehrfachem Pull | Wiederholte Pulls ohne Änderung in RaceClocker lassen die Bahnen unverändert; nichts wandert bei jedem Durchgang weiter | `d64ae540` | |
| C13 | Boot ohne Zeile im Feed | Behält eine eindeutige Nummer oberhalb der importierten und kollidiert nie mit einer echten Bahn | `d64ae540` | |
| C14 | xlsx-Import nach dem Bahn-Umbau | Startnummern kommen weiterhin aus der Datei — beide Wege teilen sich seit `d64ae540` eine Schreibroutine | `d64ae540` | |

### C15–C26 — Automatischer Abruf (Polling)

Neu am 07.08. Bis hierher kamen Ergebnisse nur, wenn jemand pro Lauf „Ergebnisse eintragen →
RaceClocker" klickte. Jetzt holt ein Hintergrund-Job sie selbst: ein aktiver Lauf alle 5 s, ein
bevorstehender einmal pro Minute, ein beendeter nie. **Vorbelegung ist `aus`** — Bestandsdaten
ändern sich nicht ungefragt, die Automatik muss im Zeitnahme-Tab der Veranstaltung erst
eingeschaltet werden.

Zwei Zusagen tragen den ganzen Block und gehören zuerst geprüft: der Abruf **beendet nie einen
Lauf** (C16), und eine **Handeingabe gewinnt** immer gegen die Automatik (C21). Entwurf:
`docs/superpowers/specs/2026-08-07-raceclocker-polling-design.md`.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| C15 | Automatik einschalten | Zeitnahme-Tab der **Veranstaltung**: Schalter „Ergebnisse automatisch abrufen" plus vier Zahlenfelder (Takt aktiv 5 s, Takt bevorstehend 60 s, Vorlauf 15 min, Nachlauf 120 min). Ohne Einschalten passiert nichts — eine bestehende Veranstaltung verhält sich exakt wie vorher | `a484883e` | |
| C16 | Start wird erkannt | Lauf steht an, in RaceClocker die Welle starten. Ohne einen Klick in ready2race springt der Lauf innerhalb eines Takts auf „läuft", mit dem Ist-Start aus dem Feed. Er wird dabei **nicht** beendet, auch wenn alle Zeiten schon da sind — Beenden bleibt Handarbeit | `605a2259` | |
| C17 | Ergebnisse laufen ein | Während der Lauf aktiv ist, Zeiten in RaceClocker nehmen: sie erscheinen ohne Klick in Durchführung, Schiedsrichter-Dashboard und Athleten-Anzeige. Bahnen, Plätze und Strafen wie beim Knopf — Knopf und Automatik teilen sich seit `351b8714` dieselbe Anwendungsroutine | `605a2259` | |
| C18 | Fenster um die Startzeit | Ein Lauf, dessen geplante Startzeit mehr als der Vorlauf entfernt ist, wird gar nicht beobachtet; einer jenseits des Nachlaufs fällt wieder heraus. Ein Lauf **ohne** Startzeit wird nie beobachtet — den aktiviert man von Hand, danach greift der schnelle Takt | `ece1598e` | |
| C19 | Statusanzeige je Lauf | Durchführungs-Tab zeigt je Lauf „zuletzt abgerufen HH:MM:SS", bei Problemen den Grund im Klartext | `ffbc158a` | |
| C20 | Wettkampf erbt RaceClocker | Der Wettkampf hat **kein** eigenes Zeitnahmesystem und erbt RaceClocker von der Veranstaltung — der Normalfall. Statusanzeige und „Automatik wieder aufnehmen" müssen trotzdem da sein. Sie hingen einmal an der eigenen Spalte und verschwanden genau dort, wo die Automatik läuft | `1c16f91f` | |
| C21 | Handeingabe pausiert | Ergebnis von Hand im Formular eintragen oder eine Datei hochladen, während die Automatik läuft: der Lauf wird für die Automatik pausiert, die Handeingabe wird **nicht** überschrieben. Der manuelle RaceClocker-Pull pausiert dagegen **nicht** — er ist derselbe Weg, nur ausgelöst | `af8464c4` | |
| C22 | Keine Vermerke ohne Automatik | Veranstaltung ohne Zeitnahmesystem oder mit ausgeschalteter Automatik: eine Handeingabe hinterlässt **keinen** Pausenvermerk. Sonst sammelte jede Regatta Vermerke ein, die sich auf nichts beziehen | `b012fb21` | |
| C23 | Wieder aufnehmen | Knopf „Automatik wieder aufnehmen" am pausierten Lauf: der nächste Takt schreibt wieder — sichtbar, nicht bloß im Vermerk. Der Fingerabdruck im Job muss dabei mit vergessen werden, sonst nimmt der Takt die Abkürzung „unverändert, nichts schreiben" und die Freigabe tut sichtbar nichts | `a83cfe4a` | |
| C24 | Fehler reißt nichts mit | Einen Lauf mit doppelten Crews in RaceClocker anlegen: nur dieser Lauf trägt den Fehler, die übrigen Läufe derselben Veranstaltung laufen weiter. Dasselbe mit einer unerreichbaren URL — dann tragen alle Läufe an dieser URL denselben Grund | `6f39d0f9` | |
| C25 | Kein Dauer-Alarm | Solange eine Welle in RaceClocker noch nicht angelegt ist oder jedes Boot `In race…` zeigt, erscheint **keine** Warnung — das ist der Normalzustand. Das Live-Dashboard warnt erst, wenn der Abruf wirklich hängt | `b1d42612`, `89f31d1e` | |
| C26 | Takt-Untergrenze und ETag | Takt auf `1` stellen: die Untergrenze greift, es geht kein Dauerfeuer an raceclocker.com raus, und das Formular sagt, was abgelehnt wurde. Parallel im Netzwerk-Tab: das Live-Dashboard liefert weiter `304`-Antworten — der Abrufzeitpunkt darf nicht in seinem ETag stecken | `5778a111`, `2628e50a` | |

## D — Schiedsrichter-Dashboard

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| D1 | Live- und Listen-Tab | Laufender Lauf oben, Liste vollständig; Badge nur bei Änderung im anderen Tab | `cda94db0`, `7abc060d` | |
| D2 | Takt und Countdown | Abfrage-Intervall wählbar, Countdown sichtbar, Auswahl bleibt am Gerät | `c4addcb3` | |
| D3 | Verbindungsverlust | Warnung „Stand veraltet" statt stiller Anzeige | `7fbdc4fe` | |
| D4 | Erststart | Ladeanzeige vor dem ersten Abruf, kein leeres Bild | `8931ef8f` | |
| D5 | Mannschafts-Dialog | Bleibt beim Nachladen aktuell; Auswahl wird geleert, wenn die Mannschaft aus der Antwort fällt | `e0cceada`, `1fe986ca` | |
| D6 | Auflagen | Erfüllt/fehlend/Zeitfenster mit einem Blick; Zeitfenster-Verstöße als Warnung | `11ac52e8`, `0f2cd691` | |
| D7 | Ersatzleute | Ersetzte Person und Grund werden im Dialog angezeigt | `5aeeca53`, `10beadd5` | |
| D8 | Lauf beenden mit offenen Ergebnissen | Rückfrage benennt die offenen Boote; nichts wird stillschweigend gewertet | `93176bb6`, `3cb1e410` | |
| D9 | Teilergebnisse | Ergebnisse einzelner Boote lassen sich erfassen, ohne den Lauf zu beenden | `b1e2e238` | |
| D10 | DNS/DNF/DSQ | Alle drei Zustände erfassbar und unterscheidbar dargestellt | `c1072f54` | |
| D11 | Zeitstrafe erfassen | Sekunden und Grund, Formular macht deutlich, dass die Zeit die Strafe enthält | `29d56efb`, `98b786bc` | |
| D12 | Schiedsrichter-Modus | Warnung, wenn das Büro den Modus überschreibt | `b061c470` | |
| D13 | Draußen lesbar | Große Schrift, hoher Kontrast, Vereinsnamen gekürzt, Karten als Spaltengitter | `211bbf4f`, `e45ce42f`, `95616793` | |
| D14 | Freie Slots | Programmpunkte erscheinen im Dashboard an ihrer Stelle | `42d6b9f7` | |
| D15 | Kein stiller Stopp | Vollständige Ergebnisse (Formular, Datei, RaceClocker) beenden den Lauf nicht; die Karte zeigt „Ergebnisse vollständig — wartet auf Beenden", steht weiter im Live-Tab und bietet „Lauf beenden" statt „Lauf aktivieren" | `6b0a0f7d`, `7bd78c3e` | |
| D16 | Geplant und echter Start | Karte zeigt „geplant HH:MM" und, sobald gestartet, „gestartet HH:MM"; „läuft seit" zählt ab dem echten Start | `e4cb8753` | |
| D17 | Kein Start-Knopf | Das Dashboard bietet kein manuelles Starten an — der Ist-Start kommt aus der Zeitnahme | `2cfa9bad` | |
| D18 | Wartende Läufe | Platzhalterkarte „Lauf noch nicht gesetzt" erscheint an ihrer Zeitposition und lässt sich von dort absagen | `e5744801`, `2cfa9bad` | |
| D19 | „Als Nächstes" ohne Altlast | Ein Platzhalter, dessen Startzeit über 30 min zurückliegt, steht nicht mehr unter „Als Nächstes"; im „Läufe"-Tab bleibt er sichtbar und absagbar. Ein überfälliger echter Lauf bleibt dagegen unter „Als Nächstes" — er ist der, der noch zu starten ist | `23e7c170` | |

## E — Betrieb

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| E1 | gzip | Antworten der öffentlichen Endpoints kommen komprimiert an | `9d96df1b` | |
| E2 | Zwischenspeicher | Viele gleichzeitige Zuschauer erzeugen höchstens einen Aufbau je 5 s und Veranstaltung | `77fca680` | |
| E3 | Notbremse | Rate-Limit der öffentlichen Info-Endpoints greift und meldet sauber | `1b752fbf` | |
| E4 | Rechte | `LIVE_DASHBOARD` steuert den Zugang zum Dashboard; die Athleten-Anzeige braucht keine Anmeldung | `6d6c6431`, `8eda6dc1` | |
| E5 | Nutzlast | Dashboard-Antwort enthält nur, was die Liste zeigt | `d189fe68` | |
| E6 | Zeitzone | Postgres läuft auf UTC, die Anwendung auf Europe/Berlin — angezeigte Zeiten stimmen mit dem Zeitplan überein | — | |
| E7 | Migration auf bestehender Datenbank | Der Moduswechsel (Boolean → drei Modi) läuft auf einer DB durch, die `event_view` schon trägt | `bb19b126` | |
| E8 | Migrationen außer der Reihe | Die aus `issue/94` nachgezogenen Migrationen blockieren den Start nicht (`outOfOrder`) | `41b808e9` | |
| E9 | QR-Code löschen | In der QR-Verwaltung einen Code löschen: verschwindet. Denselben Code ein zweites Mal löschen (Liste in einem zweiten Tab offen halten): die Oberfläche meldet, dass er nicht mehr existiert, statt Erfolg zu behaupten. Vorher war das ein stiller `KIO.fail` ohne `!` — der Fehlerzweig wurde gebaut und weggeworfen, die Antwort war immer 200. Die Scanner-Seite der QR-App bleibt bewusst gutmütig: rescannt ein Helfer ein Band, darf das keinen Fehler geben | `aa249792`, `c7f2ae32` | |

## F — Zeitnahme-Einstellungen

Der Tab ersetzt den RaceClocker-Dialog und beide Preset-Auswahldialoge. Weil die Auswahl jetzt am
Wettkampf hängt statt am Download, brauchen **Altdaten einmalig einen Eintrag**, bevor CSV-Startliste
und xlsx-Import wieder funktionieren — das ist gewollt und muss trotzdem geprüft werden.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| F1 | Tab vorhanden | „Zeitnahme" steht neben Setup, Durchführung, Platzierungen; nicht bei Challenge-Events | `890f7d48` | |
| F2 | System umschalten | Bei RaceClocker erscheint der URL-Block und zwei Startlisten-Felder, bei Webscorer eines ohne URLs, bei „nicht gesetzt" nur die Auswahl | `890f7d48`, `bb342341` | |
| F3 | Presets beim Systemwechsel | Wechsel auf „nicht gesetzt" räumt die Preset-Felder, statt eine tote Auswahl stehen zu lassen | `bb342341`, `12d673aa` | |
| F4 | Startliste ohne Dialog | CSV-Download ist eine direkte Aktion; der alte Auswahldialog erscheint nirgends mehr | `6075c8d2`, `915784c1` | |
| F5 | Rückfall Quali → Runden-Slot | Bei Webscorer und bei „nicht gesetzt" nutzt eine Qualifikationsrunde mit leerem Quali-Slot das Runden-Preset | `40783577` | |
| F6 | Kein Rückfall bei RaceClocker | Quali-Slot leer → Export bricht mit Fehlermeldung ab, statt das Läufe-Preset zu liefern (dessen Lauf-Spalte würde den Countdown am Start kosten) | `40783577` | |
| F7 | Beide Slots leer | Eigene Fehlermeldung mit Verweis auf den Zeitnahme-Tab, kein stiller Fehlschlag | `1c74900b`, `5498fa5f` | |
| F8 | Ergebnis-Import | Upload-Dialog hat nur noch die Dateiauswahl; das Preset kommt aus dem Wettkampf | `397bffd2`, `915784c1` | |
| F9 | Ergebnis-Menü | Bei Webscorer fehlt der RaceClocker-Eintrag; bei RaceClocker bleibt xlsx als Notausgang | `915784c1` | |
| F10 | Warnung bei Lücken | Unvollständige Konfiguration wird im Tab benannt und im Durchführungs-Tab mit Link dorthin gemeldet — nur bei gesetztem System | `cbb8495e` | |
| F11 | Preset gelöscht | Löschen in der globalen Verwaltung nimmt dem Wettkampf die Vorbelegung, ohne ihn zu beschädigen | `890f7d48` | |
| F12 | Unvollständig speichern | Speichern ohne URLs ist erlaubt — die Rennen entstehen erst kurz vor der Regatta | `890f7d48` | |
| F13 | Round-Trip RaceClocker | Presets hinterlegen, Startliste für Quali **und** eine Läufe-Runde laden, beide in RaceClocker importieren, Ergebnisse ziehen | `b13a7173` | |
| F14 | Round-Trip Webscorer | Ein Preset, CSV laden, xlsx ohne Dialog hochladen | `b13a7173` | |
| F15 | Sprachen | Tab, Fehlermeldungen und Preset-Namen auf de/en/da vollständig | `9756762b` | |

### F16–F22 — Benannte Rennen statt zweier fester Adressen

Neu am 10.08. Bis hierher hatte eine Veranstaltung genau **zwei** Adressfelder (Zeitfahren, Läufe),
und ein Wettkampf konnte sie mit eigenen Adressen überschreiben. Eine Regatta fährt aber mehr Rennen
als zwei — bei CRF sind es Timetrials, Langstrecke und Kurzstrecke. Ab jetzt legt die Veranstaltung
**benannte Rennen** an, und Veranstaltung wie Wettkampf **wählen** daraus aus.

Für den Bestand ändert sich nichts: Die Migration erzeugt aus den alten Adressen Rennen und setzt die
Anwahl so, dass jeder Wettkampf weiterhin dieselbe Adresse abfragt wie vorher.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| F16 | Rennen anlegen | Drei Rennen anlegen (Timetrials/Einzelstarts, Langstrecke/Läufe mit Rundenzeiten-Kennzeichen, Kurzstrecke/Läufe); alle drei erscheinen in der Liste mit Startart und Adresse | `a5d30cf3` | |
| F17 | Voreinstellung anwählen | Quali → Timetrials, übrige Runden → Kurzstrecke; ein Wettkampf ohne eigene Anwahl zeigt im Zeitnahme-Tab genau diese beiden Namen als „geerbt" | `a5d30cf3` | |
| F18 | Wettkampf weicht ab | Bei einem Wettkampf „Langstrecke" anwählen, speichern, Seite neu laden; im Event-Tab steht er unter „Wettkämpfe mit eigener Zeitnahme" **mit Rennennamen** | `a5d30cf3` | |
| F19 | Anwahl wieder leeren | Override-Schalter aus → der Wettkampf erbt wieder, und die Abweichungs-Liste nennt ihn nicht mehr | `a5d30cf3` | |
| F20 | Falsches Rennen angewählt | Absichtlich das falsche Rennen anwählen und Ergebnisse abrufen: Der Lauf wird über den Rückfall trotzdem gefunden, die Regatta läuft weiter | `a5d30cf3` | |
| F21 | Rennen löschen | Ein angewähltes Rennen löschen: Rückfrage erscheint; danach erben die betroffenen Wettkämpfe wieder die Voreinstellung, und der Abruf überspringt Läufe ohne jedes Rennen still | `a5d30cf3` | |
| F22 | Doppelte Adresse abgewiesen | Zwei Rennen mit derselben Ergebnis-Adresse anlegen → verständliche Meldung statt „Unerwarteter Fehler"; dasselbe für zwei gleiche Namen | `a5d30cf3` | |

**Was der Abruf dabei sparen soll (C15–C26 im Blick behalten):** Der Takt holt nur noch die Rennen,
die *gerade* gefahren werden, und den Rückfall erst, wenn ein Lauf im angewählten Rennen fehlt. Fährt
die Regatta nur Kurzstrecke, darf im Netzwerk-Protokoll pro Takt **eine** Anfrage stehen, nicht zwei.

## G — Urkunden

Neu auf dem Sammelbranch. Die Vorlagenpflege nutzt die bestehende Gap-Mechanik: PDF-Export der
DRV-PowerPoint hochladen, Platzhalter visuell setzen.

G25–G35 kamen mit `de0aac66` dazu: Vorlagen lassen sich als `.r2rtpl.zip` exportieren und
importieren, und der Editor zeigt Beispieltexte statt leerer Kästen, dazu Zahlenfelder und
Pfeiltasten für die Platzhalter. Für diesen Block zusätzlich vorbereiten: eine fertig eingerichtete
Siegerurkunden-Vorlage **mit** hochgeladener Schrift, eine Vorlage im **Querformat**, und eine
zweite Instanz (lokal neben dem Server), in die ein Paket importiert werden kann. Keiner dieser
Fälle wurde je in einer laufenden Anwendung gesehen — sie entstanden aus Code-Reviews, nicht aus
Bedienung.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| G1 | Vorlage anlegen | Typ „Siegerurkunde" wählbar, Platzhalter setzbar; angeboten werden nur die für den Typ erlaubten | `4b98dd09` | |
| G2 | Schrift-Upload | `.ttf`/`.otf` wird angenommen, eine unbrauchbare Datei fällt beim Anlegen auf und nicht erst beim Erzeugen | `4b98dd09` | |
| G3 | Ohne Schrift-Upload | Fallback Helvetica, Urkunde bleibt druckbar | `4b98dd09` | |
| G4 | Download je Wettkampf (PDF) | Plätze 1–3, sortiert nach Platz, Platzhalter gefüllt (Platz, Wettkampf, Verein, Renntage, Ort) | `4b98dd09` | |
| G5 | Pro Athlet / pro Boot | „pro Athlet" eine Seite je Person inklusive Steuermann, „pro Boot" eine Seite je Boot; Vor-/Nachname bleiben im Boot-Modus leer, `FULL_NAME` trägt | `4b98dd09` | |
| G6 | Renngemeinschaften | ~~RG-Boote zeigen die RG-Bezeichnung~~ — **seit dem Vereinsketten-Umbau überholt.** RG-Boote zeigen jetzt die vollen Vereinsnamen aller Beteiligten, ohne jede Kürzung. Siehe J10 | `4b98dd09`, überholt 09.08. | |
| G7 | Word-Download | Datei öffnet in Word, Rahmen sitzen an den Vorlagenkoordinaten, Text ist nachbearbeitbar, Seitenumbrüche stimmen | `4b98dd09` | |
| G8 | Veranstaltungsebene | Ein Download über alle Wettkämpfe, sortiert nach Wettkampf und Platz | `4b98dd09` | |
| G9 | Einzeldownload | Icon in der Ergebniszeile öffnet den Dialog und liefert danach genau diese Urkunde. Die Platzgrenze gilt hier **nicht** — ein Nachdruck für Platz 5 muss gehen, obwohl der Dialog auf 1–3 steht | `4b98dd09` | |
| G10 | „Design mitdrucken" | Standardmäßig aus (Amtspapier); eingeschaltet erscheint der Vorlagenhintergrund | `4b98dd09` | |
| G11 | Alle Plätze | Umschalten auf alle Platzierungen liefert entsprechend mehr Seiten | `4b98dd09` | |
| G12 | Ausgeschlossene Boote | Abgemeldete, ausgeschiedene und disqualifizierte Boote erscheinen nicht — und tragen in der Ergebniszeile auch kein Urkunden-Icon, weil es dort nur scheitern könnte | `4b98dd09` | |
| G13 | Plätze noch nicht berechnet | Verständliche Meldung statt leerer oder halb gefüllter Urkunden | `4b98dd09` | |
| G14 | Fehlende Vorlage | Dialog zeigt einen Hinweis mit Verweis auf die Konfiguration, kein Fehler beim Download | `4b98dd09` | |
| G15 | Teilnahmeurkunde als Word | `?format=docx` liefert .docx, der ZIP-Download enthält .docx-Einträge; der E-Mail-Versand bleibt PDF | `4b98dd09` | |
| G16 | Alte Teilnahme-Vorlage | Eine Vorlage ohne Schriftgröße rendert unverändert wie vorher | `4b98dd09` | |
| G17 | Rechte | Nur mit `ReadEventGlobal`; kein öffentlicher Zugriff auf Urkunden | `4b98dd09` | |
| G18 | Challenge-Event | Keine Siegerurkunde, die Teilnahmeurkunde bleibt | `4b98dd09` | |
| G19 | Serienlänge | „alle Plätze, pro Athlet" auf der größten Veranstaltung: Dauer und Seitenzahl notieren — daraus entscheidet sich, ob eine Obergrenze nötig wird | `4b98dd09` | |
| G20 | Ausländische Vereinsnamen | Ein Verein mit `ł`, `ř` oder Kyrillisch im Namen: der Download läuft durch und die Urkunde ist lesbar. Helvetica kann diese Zeichen nicht darstellen, sie werden ersetzt — **kein** Abbruch des ganzen Downloads | `4b98dd09` | |
| G21 | PDF und Word treffen dieselbe Stelle | Beide Formate derselben Urkunde aufs Amtspapier drucken und übereinanderlegen: die fünf Zeilen sitzen in denselben Feldern. Das ist die Kernzusage des Features | `4b98dd09` | |
| G22 | Mehrseitige Teilnahme-Vorlage als Word | Eine Vorlage mit zwei Seiten liefert ein zweiseitiges .docx; Platzhalter von Seite 2 fehlen nicht, und jede Seite behält ihr eigenes Format | `4b98dd09` | |
| G23 | Vorlage falsch zuweisen | Die Auswahl bietet nur Vorlagen des passenden Typs an; wird trotzdem eine fremde zugewiesen, lehnt der Server sichtbar ab statt stumm leere Urkunden zu erzeugen | `4b98dd09` | |
| G24 | Kaputte Vorlage | Eine unlesbare oder nullseitige PDF-Vorlage liefert eine verständliche Meldung — keine leere .docx und kein Serverfehler | `4b98dd09` | |
| G25 | Schrift-Vorschau an gespeicherter Vorlage | Eine **gespeicherte** Vorlage mit hochgeladener Schrift öffnen: die Beispieltexte im Editor stehen in dieser Schrift, nicht in der Standardschrift. Netzwerk-Tab: `GET /gapDocumentTemplate/{id}/font` antwortet 200. Der Fall ist der Kern der Vorschau — er schlug vorher still fehl, weil `.ttf`/`.otf` keinen ratbaren Content-Type haben und die Antwort mit 500 endete, ohne dass die Oberfläche etwas zeigte | `de0aac66` | |
| G26 | Dateiantwort mit unbekannter Endung | Regression zu G25 an einer **bestehenden** Stelle: ein hochgeladenes Veranstaltungsdokument ohne Dateiendung (oder mit exotischer) herunterladen. Muss ankommen statt 500 — der Fehler saß im gemeinsamen Datei-Responder, nicht im Urkunden-Code | `de0aac66` | |
| G27 | Vorlage exportieren und wieder importieren | ~~Rundlauf in einer Instanz~~ — seit `2ce88d59` durch `GapDocumentTemplateServiceTest` gegen echtes Postgres abgedeckt: Export → Import → Export ergibt dasselbe Paket, jedes Platzhalter-Feld inklusive Schriftgröße und festem Text übersteht die Runde. Von Hand bleibt nur der Blick auf die **Oberfläche**: Knopf vorhanden, Datei kommt an, Vorlage taucht in der Liste auf | `de0aac66` | |
| G28 | Paket in einer zweiten Instanz | Dasselbe Paket in eine andere Instanz importieren (lokal ↔ Server). Dort eine Urkunde erzeugen und mit dem Original vergleichen. Das ist der eigentliche Zweck des Formats und die einzige Prüfung, die den ganzen Weg abdeckt | `de0aac66` | |
| G29 | Import abgelehnt, verständlich | Dass der Server ablehnt, prüft seit `2ce88d59` der Service-Test (kaputtes ZIP, `formatVersion 2`, `template.pdf` das keines ist, Platzhaltertyp fremd zum Urkundentyp — jeweils mit eigenem Fehler, und die Datenbank bleibt leer). Von Hand bleibt: kommt im Dialog die **deutsche Meldung** an, oder das generische „Vorlage konnte nicht angelegt werden"? Mit einer umbenannten Nicht-ZIP-Datei und einem Paket mit `formatVersion 2` durchspielen | `de0aac66` | |
| G30 | Import derselben Datei erneut | Nach einem abgelehnten Import dieselbe Datei nochmals wählen. Bekannte Schwachstelle: das Dateifeld setzt sich nicht zurück, die zweite Auswahl löst womöglich nichts aus. Prüfen und notieren, ob es den Bedienenden trifft | `de0aac66` | |
| G31 | Nach dem Import | Erwartet ist **nicht**, dass sich der Bearbeiten-Dialog öffnet (bewusste Abweichung vom Entwurf): Erfolgsmeldung, Tabelle neu geladen, neue Vorlage in der Liste | `de0aac66` | |
| G32 | Schriftgröße im Editor gegen die Vorschau | Einen Platzhalter auf 20 pt setzen, speichern, Server-Vorschau daneben legen: die Textgröße muss übereinstimmen. **Einmal hochkant und einmal quer** prüfen — der Fehler, den das behebt, kehrte zwischen beiden Formaten das Vorzeichen um (zu klein im Hochformat, zu groß im Querformat) | `de0aac66` | |
| G33 | Koordinaten tippen | In den Feldern X/Y/Breite/Höhe: `44,7` mit Komma eintippen (muss ankommen, nicht zu 447 werden), `0` als Breite (muss auf die Mindestgröße gehen, kein unsichtbarer Kasten), `150` als X (muss auf 100 % begrenzt werden). Der Wert wird beim Verlassen des Feldes übernommen, nicht bei jedem Tastendruck | `de0aac66` | |
| G34 | Pfeiltasten | Platzhalter anklicken, Pfeiltasten bewegen ihn in kleinen Schritten, mit Shift in großen, und er bleibt auf der Seite. Danach in ein Zahlenfeld der Seitenleiste klicken und dort die Pfeiltasten drücken: der Kasten darf sich **nicht** bewegen | `de0aac66` | |
| G35 | Schrift entfernen | Bei geöffnetem Editor „Entfernen" drücken: die Beispieltexte wechseln sofort auf die Standardschrift, ohne Speichern. „Rückgängig" holt die Schrift zurück | `de0aac66` | |

## H — Lauf-Status in Durchführung, Zeitplan und öffentlicher Anzeige

Neu am 07.08. Bis dahin kannte die Durchführungsseite genau einen Zustand — die Checkbox „Aktuell
laufend". Ein beendeter, ein abgesagter und ein nie angefasster Lauf sahen dort identisch aus, und
der Zeitplan zeigte für einen verknüpften Lauf nur den *Slot*-Zustand „Verknüpft", also eine Aussage
über den Plan statt über den Lauf.

**Die Leitplanke ist der eigentliche Testgegenstand:** *nur* Anzeige. Kein neuer Zustandsübergang,
keine geänderte Aktivier-/Beenden-Logik, kein Eingriff in `ScheduleChain.decideNext` oder
`deriveMatchState`. Wer H durchgeht, prüft in erster Linie, dass die schon getestete Kette (B8–B11)
sich **nicht** anders verhält als vorher. Entwurf:
`docs/superpowers/specs/2026-08-07-lauf-status-anzeige-design.md`.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| H1 | Ein Vokabular, vier Oberflächen | Derselbe Lauf trägt in Durchführung, Zeitplan, Schiedsrichter-Dashboard und öffentlicher Anzeige dieselbe Aussage. Alle Chips stammen aus `matchStatusChip.ts` — sie können nicht auseinanderlaufen, aber sie können falsch abgeleitet sein | `31135e3b` | |
| H2 | Anstehend → Läuft → Beendet | Einen Lauf durch seinen normalen Weg schicken: grau „Anstehend", blau „Läuft · n min" (zählt ab dem **Ist**-Start, nicht ab der geplanten Zeit), grün „Beendet" | `31135e3b` | |
| H3 | Überfällig | Ein anstehender Lauf, dessen Startzeit über 5 min zurückliegt, wird rot „Überfällig · n min". Zwei Minuten Verzug sind Regattaalltag und dürfen **nicht** leuchten — sonst steht die halbe Liste in Rot | `31135e3b` | |
| H4 | Teilweise gewertet | Vier von sechs Booten werten, ohne den Lauf zu beenden: orange „Teilweise gewertet 4/6". Bei 0 gewerteten und bei allen gewerteten erscheint dieser Chip **nicht** — dann greifen „Anstehend" bzw. „Wartet auf Beenden" | `31135e3b` | |
| H5 | Wartet auf Beenden | Alle Ergebnisse vollständig, Lauf nicht beendet: orange „Wartet auf Beenden" — dieselbe Aussage wie D15 im Dashboard, jetzt auch im Büro sichtbar | `31135e3b` | |
| H6 | Abgesagt | Ein abgesagter Lauf steht durchgestrichen und grau da, statt wie vorher als „nicht aktiv" von einem noch nicht gestarteten ununterscheidbar zu sein | `31135e3b` | |
| H7 | Zeitplan zeigt den Lauf, nicht den Slot | In der Status-Spalte des Zeitplans trägt ein **verknüpfter** Slot (`matchId` gesetzt) den Lauf-Chip. Programmpunkte und noch nicht gesetzte Runden behalten ihren bisherigen Slot-Chip — dort gibt es keinen Lauf, über den man etwas sagen könnte | `31135e3b` | |
| H8 | Öffentlich bleibt grob | Athleten-Anzeige und Kiosk zeigen nur vier Zustände: Anstehend · Läuft · Ergebnisse da · Abgesagt. **Keine** Teilwertung und **kein** Wasserstand — Zuschauer würden ein Teilergebnis als Ergebnis lesen. Gegenprobe zu H4 auf `/board/{eventId}` | `31135e3b` | |
| H9 | Zählerleiste über der Runde | Über den Läufen einer Runde steht „1 läuft · 1 offen · 3 beendet · 1 abgesagt". Die Zahlen müssen mit den Chips darunter zusammenpassen; die Leiste erscheint erst ab zwei Läufen | `31135e3b` | |
| H10 | Wasser-Chip | Nur auf der Durchführungsseite, nur solange er etwas aussagt: „Wasser 4/6", wenn der Lauf ansteht oder läuft und noch nicht alle Crews ausgecheckt sind. Bei einem Wettkampf **ohne** Check-in/out (siehe I5) und bei vollständig ausgecheckten Crews erscheint er gar nicht — eine leere Hülle wäre schlimmer als nichts | `14fe10bb` | |

## I — Prüfungsschweregrad (Schiedsrichter-Dashboard)

Am 07.08. als Squash `93017cca` gelandet. Pro Wettkampf lässt sich einstellen, wie schwer eine
fehlende Prüfung wiegt: OK / Warnung / Kritisch. Backend und Frontend sind grün, **das Verhalten in
der laufenden Anwendung hat niemand gesehen** — der Serverstart scheiterte damals an der
Berechtigungsprüfung. Entwurf und Plan unter
`docs/superpowers/specs/2026-08-07-schiedsrichter-pruefungsschweregrad*`.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| I1 | Ohne Konfiguration wie vorher | **Die zentrale Zusage.** Eine Regatta ohne einen einzigen Konfigurationseintrag sieht aus wie vorher. Ampel je Boot mit dem alten Stand vergleichen, besonders ein Boot mit bezahlter Rechnung und ohne Teilnahmebedingungen: **grauer** Kreis, nicht grün | `93017cca` | |
| I2 | Verwaltungsdialog | Event-Seite, Knopf neben „Schiedsrichter-Dashboard öffnen": Sammelaktion je Zeile, speichern, Dialog neu öffnen — steht der Wert noch da? Wirkt er im Dashboard beim nächsten Poll? | `93017cca` | |
| I3 | Zurücksetzen | Alle Prüfungen auf Standard stellen und speichern. Das schickt eine **leere** Liste — der Fall hat einmal einen 500er erzeugt | `93017cca` | |
| I4 | Chip-Farben im Detail-Dialog | Bezahlt = grün, offen = nach eingestelltem Schweregrad, keine Rechnung = grau. Dasselbe für den Wasser-Chip | `93017cca` | |
| I5 | Beachsprint ohne Check-in/out | `checkInOutRequired` am Wettkampf abschalten: „auf dem Wasser" wird im Dashboard nicht mehr bewertet, in der QR-App bekommt die Team-Karte einen Hinweis-Chip, und hat die Person nur solche Meldungen, verschwindet der An-/Abmelden-Knopf | `93017cca` | |
| I6 | Ausgecheckt färbt nicht grün | Ein ausgechecktes Boot darf über `onWaterSeverity` nicht grün werden — es ist auf dem Wasser, das ist keine erfüllte Auflage | `b8848452` | |
| I7 | Vorübergehend abgemeldete Auflage | Eine Auflage abmelden und wieder anmelden: der eingestellte Schweregrad ist noch da und nicht stillschweigend auf Standard zurückgefallen | `c70f18d7`, `4d3c8a24` | |
| I8 | Zwei Bearbeiter gleichzeitig | **Bewusst offen gelassen, hier nur bestätigen:** `PUT /event/{eventId}/checkSeverity` hat kein optimistisches Sperren. Zwei gleichzeitig geöffnete Dialoge überschreiben sich kommentarlos. Vor der Regatta entscheiden, ob das reicht — im Zweifel heißt die Regel: einer pflegt | `93017cca` | |

---

## J — Vereinskette statt „Renngemeinschaft"

Am 09.08. gebaut. Bis dahin trugen mehrere Boote desselben Laufs die identische Zeile
„Renngemeinschaft" — im Produktivstand der CRF sind **42 von 100 Meldungen vereinsgemischt**, viele
mit vier oder fünf verschiedenen Vereinen. Angezeigt wird jetzt der Verein, den die Athleten
tragen; der meldende Verein taucht nirgends mehr auf. Entwurf:
`docs/superpowers/specs/2026-08-09-vereinskette-statt-renngemeinschaft-design.md`.

**Kein Agent hat die laufende Anwendung gesehen.** Alles unten ist compiliert und teils gegen echtes
Postgres geprüft, aber nichts davon wurde je gerendert.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| J1 | Reine Vereinsboote unverändert | **Die zentrale Zusage.** Die 58 nicht gemischten Boote sehen aus wie vorher — ein Vereinsname, keine Kette, kein Trenner. Vorher/Nachher an derselben Karte vergleichen | `ae25377f` | |
| J2 | Telefon: Kurzformen | Karte schmaler als 480 px: die Kette steht in Kurzformen und bricht auf zwei Zeilen um. Was dann nicht mehr passt, wird abgeschnitten — bewusst, nicht mit „+3" gekappt | `e8eb7559` | |
| J3 | Tablet-Spalte: volle Namen | Karte ab 480 px: dieselbe Kette in vollen Vereinsnamen. Das Fenster ist **nicht** entscheidend, die Karte ist es — auf dem Tablet stehen zwei schmale Spalten nebeneinander | `e8eb7559` | |
| J4 | Laptop: Crew je Person | Karte ab 700 px: zusätzlich je Person Nachname und Vereinskurzform. Rollenkürzel gegenlesen — „Sen." und „Ste." sind eine Setzung des Entwicklers, keine Vorgabe | `e8eb7559` | |
| J5 | Fenster verbreitern | Von schmal auf breit ziehen: die Crew fehlt bis zum nächsten Poll im Datensatz. Die Karte muss dann Stufe 2 zeigen, **keine leere Fläche** | `e8eb7559` | |
| J6 | Telefon bleibt sparsam | Netzwerk-Tab am Telefon: die Antwort enthält **kein** `crew`. Der Sekunden-Poll darf durch dieses Feature nicht schwerer werden | `8c63ee0b` | |
| J7 | Overlay | Zeile antippen: die Mannschaft steht mit dem Verein **jeder einzelnen Person**, nicht mit einem pauschalen Teamverein | `8c63ee0b` | |
| J8 | Crew-Reihenfolge stabil | Dieselbe Karte über mehrere Polls beobachten: die Kette darf ihre Reihenfolge nicht wechseln. Die Abfragen hatten vorher **gar keine** Sortierung — Postgres durfte bei jedem Poll anders liefern | `95d10153` | |
| J9 | Ummeldung ändert die Kette | Eine Person durch jemanden aus einem anderen Verein ersetzen: die Kette übernimmt das. Sonderfall dahinter: eine Ersatzperson kann Vereinsmitglied sein, ohne in der Veranstaltung gemeldet zu sein — die darf nicht ohne Verein in der Kette landen | `8c63ee0b` | |
| J10 | Urkunde mit fünf Vereinen | **Der wahrscheinlichste Überraschungspunkt.** Dieselbe Urkunde als PDF und als DOCX erzeugen: volle Vereinsnamen ohne jede Kürzung, Umbruch an den Vereinsgrenzen (nie mitten im Namen), keine Zeile über dem Rand, und beide Formate sehen gleich aus. Ohne den Umbruch lief der Text 1,7- bis 2,9-fach über die Seitenbreite | offen | |
| J11 | Vorlagen-Editor lügt nicht | Im Editor eine lange Kette einsetzen: die Vorschau zeigt, was gedruckt wird. Vorher schnitt sie an der Kastenkante ab und verschwieg den Überlauf | offen | |
| J12 | Kurzform pflegen | Auf der Pflegeseite eine Kurzform setzen: sie schlägt sofort die Automatik, auf dem Board und in der Anzeige. Feld leeren: zurück zur Automatik | `0bf3b74b` | |
| J13 | Schreibvarianten zusammenführen | `ARV Kiel` und `Akademischer Ruderverein Kiel e.V.` dieselbe Kurzform geben: zwei Boote desselben Vereins sehen danach gleich aus. Automatisch geht das nicht, das ist die Grenze der Normalisierung | `0bf3b74b` | |
| J14 | „auch:"-Zeilen | Bei einem zusammengefassten Eintrag stehen die weiteren Schreibweisen sichtbar darunter. Das ist die einzige Kontrolle dagegen, dass die Normalisierung zwei **verschiedene** Vereine verschmilzt — ohne diese Anzeige bliebe so ein Fehler unsichtbar | `0bf3b74b` | |
| J15 | Regeln, Reihenfolge | Ein Wortpaar anlegen und die Reihenfolge prüfen: `Ruderverein → RV` muss vor `Verein → V` greifen, sonst bleibt `Ruder-V` stehen | offen | |
| J16 | Regeln, Schalter | „Gründungsjahre entfernen" ausschalten: `Erster Kieler Ruder-Club von 1862` behält sein Jahr, überall. Wieder einschalten: weg | offen | |
| J17 | Frische Installation | Eine Installation ohne den Ruder-Seed kürzt Vereinstypen **nicht** — nur Rechtsform, Jahreszahlen und Klammerzusätze fallen weg. Das ist Absicht: `Ruderclub → RC` ist Sportart-Wissen und gehört nicht in den Produktkern | offen | |
| J18 | Meldeverein verschwunden | Auf Board, Athleten-Anzeige und Urkunde darf der meldende Verein nirgends mehr auftauchen, wenn er nicht zufällig auch ein getragener ist | `8c63ee0b`, `95d10153` | |

---

## Detailablauf A6/C7 — Zeitstrafe während der Lauf läuft

Der Kernfall: die Anzeige muss eine nachgetragene Strafe übernehmen, **bevor** der Lauf beendet ist.

1. Lauf starten, in RaceClocker die Zeiten aller Boote nehmen.
2. In ready2race „Ergebnisse aus RaceClocker holen". Lauf **nicht** beenden.
   → Anzeige zeigt im Mittelblock Platz und Zeit je Boot; notiere die Zeit eines Bootes.
3. In RaceClocker für dieses Boot 15 s Strafe mit Grund eintragen (RaceClocker rechnet die Strafe
   in `Result` ein).
4. Erneut „Ergebnisse aus RaceClocker holen". Lauf weiterhin **nicht** beenden.

**Erwartung:**
- Die Zeit des Bootes ist um 15 s höher als in Schritt 2, darunter steht „inkl. 15 s Zeitstrafe · Grund".
- Wirft die Strafe das Boot nach hinten, ändern sich die Plätze mit (Plätze werden beim Pull aus den
  Zeiten neu abgeleitet).
- Die Anzeige übernimmt das ohne Neuladen, spätestens nach ~15 s (Takt ≥ 10 s plus 5 s Cache).
- Der Lauf steht weiter unter „Aktueller Lauf".
- Nach dem Beenden (A8) sind Zeit, Platz und Strafhinweis im Ergebnisblock unverändert.

**Wichtig:** Die externe Zeitmessung ist die Quelle der Wahrheit. Ein Pull überschreibt eine im
Formular erfasste Strafe (A7). Beide Wege am selben Boot zu mischen ist kein unterstützter Ablauf.

## Detailablauf C8/C12/C13 — Bahnen aus „Rank"

Der Fall braucht einen eigenen Ablauf, weil er nur an einem echten RaceClocker-Rennen prüfbar ist:
die Bahn ergibt sich aus der Listenposition, und die lässt sich nur dort verschieben.

**Aufbau:** ein Lauf mit 6 Booten. Eines bekommt später **keine Zeit**, eines wird **gar nicht** nach
RaceClocker übernommen (Abmeldung). Ohne diese beiden testet man nur den einfachen Fall.

1. Startliste mit dem Preset „RaceClocker Laeufe" laden und in das Rennen importieren. **Die Spalte
   R2R-ID im Spaltenmapper auf „Extra info" ziehen** — nicht auf das Custom-Feld, sonst fehlt die
   UUID im Feed und der Pull ordnet überhaupt nichts zu (C1).
2. Feed mit `?json=1` abrufen und vor dem ersten Pull prüfen: ExtraInfo trägt die UUIDs, jede Zeile
   hat ein `Rank`.
3. Für einen Teil der Boote Zeiten nehmen, dann in ready2race die Ergebnisse ziehen.
   → **Erwartung:** Startnummern entsprechen der Reihenfolge in RaceClocker (1…n nach `Rank`); auch
   die ungetimten Boote stehen auf ihrer Bahn und nicht am Ende.
4. Zweimal hintereinander ziehen, ohne in RaceClocker etwas zu ändern.
   → **Erwartung:** die Bahnen bleiben identisch (C12).
5. Zwei getimte Einträge in RaceClocker verschieben, erneut ziehen.
   → **Erwartung:** die Startnummern folgen der neuen Reihenfolge, und **die Zeiten bleiben an ihrem
   Boot** — Bib und Zeit wandern mit dem Eintrag, nur die Position bleibt zurück.
6. Das noch ungetimte Boot verschieben, erneut ziehen.
   → **Erwartung:** es bekommt die neue Bahn und rutscht nicht ans Ende. Genau dafür kommen die
   Bahnen aus allen zugeordneten Zeilen und nicht nur aus den getimten.
7. Das abgemeldete Boot über alle Durchgänge beobachten.
   → **Erwartung:** eindeutige Nummer oberhalb der importierten, nie eine Kollision mit einer echten
   Bahn (C13).
8. Dashboard nach einem Tausch ansehen, auch auf dem Telefon.
   → **Erwartung:** die Startnummern-Spalte zeigt beim nächsten Takt die neue Bahn.
9. Zum Schluss einen Lauf per xlsx importieren (C14).
   → **Erwartung:** dort kommen die Startnummern weiterhin aus der Datei.

**Wichtig:** Der RaceClocker-Bib wird auf diesem Weg **nicht** mehr als Startnummer geschrieben. Er
wandert beim Verschieben mit und beschreibt damit nicht, wo ein Boot startet. Wer eine am Boot
bleibende Nummer erwartet, siehe den offenen Punkt „Bootsnummer" unten.

## Offene Punkte, die der Test entscheiden soll

- ~~**Lauf doppelt sichtbar.**~~ **Entschieden am 06.08.2026 (`7bd78c3e`), siehe A23/A24.** Nicht die
  Doppelsichtbarkeit war das Problem, sondern der Zeitpunkt: solange ein Lauf nicht beendet ist, kann
  noch eine Zeitstrafe kommen, und ein veröffentlichtes Ergebnis, das sich danach ändert, lässt sich
  nicht zurückholen. Die Veranstaltung entscheidet jetzt über `public_results_visibility`, ab welchem
  Zustand ein Lauf öffentlich als Ergebnis gilt — Vorgabe „nur beendete Läufe", wahlweise „auch
  vollständig gewertete" (das bisherige Verhalten). **Nebenwirkung für Altdaten:** Läufe, die
  vollständig gewertet, aber nie formal beendet wurden, fallen mit der Vorgabe aus den öffentlichen
  Ergebnissen heraus, bis sie beendet werden oder die Veranstaltung umgestellt wird.
- **Bootsnummer.** Es gibt keine Nummer, die am Boot bleibt: `start_number` ist die Bahn,
  `team_number` die n-te Mannschaft eines Vereins. Der RaceClocker-Bib wird seit `d64ae540` nicht
  mehr geschrieben. Falls die Athleten eine feste Bootsnummer erwarten, braucht das eine eigene
  Spalte samt Schreibweg — vor der Regatta entscheiden.
- **Ort/Strecke.** `placeName` in den Info-DTOs wird nie gefüllt; eine Tabelle für Orte existiert
  nicht. Entweder Feld entfernen oder Bedarf klären.
- **Sonderzeichen auf Urkunden (G20).** Ohne hochgeladene Schrift läuft das PDF auf Helvetica, das
  weder `ł` noch Kyrillisch kann; solche Zeichen werden ersetzt. Für eine Deutsche Meisterschaft mit
  ausländischen Crews ist das sichtbar. Entweder der DRV liefert die Schriftdatei (TheSansOffice, ist
  lizenzpflichtig und darf nicht im Repo liegen) und wir prüfen, ob sie die Zeichen enthält — oder wir
  liefern eine frei lizenzierte Schrift mit breiterem Zeichensatz als Fallback mit. Vor der Regatta
  entscheiden, sonst steht am Tag ein `?` auf der Urkunde.
- **Automatik am Renntag an oder aus? (C15).** Die Vorbelegung ist `aus`, die Umstellung ist ein
  Schalter. Wird sie eingeschaltet, hängt die Anzeige an einer fremden Cloud: ist raceclocker.com
  langsam oder weg, steht in jedem Lauf ein Fehler. Wird sie ausgelassen, bleibt es beim Klicken pro
  Lauf. Vorschlag: am Testtag mit eingeschalteter Automatik fahren und die Rückfallregel („Schalter
  aus, weiter wie bisher") einmal geübt haben, damit sie am 14.08. niemand suchen muss.
- **Wer pflegt den Prüfungsschweregrad? (I8).** Ohne optimistisches Sperren überschreiben zwei
  gleichzeitige Bearbeiter sich kommentarlos. Entweder es wird gesperrt oder es gilt organisatorisch:
  einer pflegt.

## Nicht in diesem Katalog

- Meldewesen, Rechnungen und die übrige Dokumentenerzeugung (eigene Stränge, teils eigene
  Worktrees). Urkunden stehen seit `4b98dd09` unter G, weil sie auf dem Sammelbranch liegen.
- Platzberechnung bei Zeitgleichheit (Rechenlogik, durch Unit-Tests gedeckt).
- Lastverhalten unter echter Zuschauerzahl — es gibt Cache, Takt-Untergrenze und Rate-Limit, aber
  keinen Messwert.
