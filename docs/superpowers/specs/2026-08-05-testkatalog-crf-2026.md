# Testkatalog `feature/crf-2026`

**Stand:** 2026-08-10, Sammelbranch bei `87f85435` (Freilos-Anzeige M, Laufstatus N, Siegerehrungsbogen O, „Mein Event" P dazugekommen)
**Zweck:** Ein Katalog aller Fälle, die vor der Regatta am 14.08. auf **einem** gebauten Stand
durchlaufen werden. Er sammelt die Fälle über alle Arbeitsstränge (Athleten-Anzeige, Zeitstrahl,
RaceClocker, Schiedsrichter-Dashboard, Betrieb, Zeitnahme-Einstellungen, Urkunden, Lauf-Status,
Prüfungsschweregrad, Vereinskette, Wertungskategorien, Siegerehrungsbogen), damit der große Test in der Woche vom 10.08. nicht aus dem Gedächtnis
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
5. **Daten.** Für **L** zusätzlich: mindestens zwei Wertungskategorien, einem Lauf mit Booten aus
   beiden, einem Boot ganz ohne Kategorie, einem Gleichstand und einem Wettkampf ohne jede
   Kategorie (Regressionsfall L5).
   Veranstaltung mit Zeitplan, mindestens einem Wettkampf mit ≥ 4 Booten, davon eines
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
| C2 | Wellenname | Wellenname lautet `10:30 \| 12 JM4x \| AF1` (Startzeit, Rennnummer und Kürzel, Laufname), Lauf wird im Feed gefunden | `09e6a642` | |
| C3 | Ergebnisse ziehen | Zeiten und Plätze landen am richtigen Boot, Plätze aus den Zeiten abgeleitet | `dd8d67b8` | |
| C4 | Teil-Pull | Ein Pull mit nur teilweise genommenen Zeiten ist wiederholbar, ohne die übrigen Boote zu beschädigen | `b1e2e238` | |
| C5 | Echter Start | Früheste gemessene Startzeit überschreibt `started_at`, auch gegen einen manuellen Stempel | `f86665ae`, `e4cb8753` | |
| C6 | Mitternacht als Platzhalter | `00:00:00.0` für Boote ohne Start setzt `started_at` nicht auf Mitternacht | `c0458d8d` | |
| C7 | Zeitstrafe aus dem Feed | `Penalty`/`Penalty note` landen als Ausweisung am Boot, die Zeit wird nicht zusätzlich verrechnet | `0c8e378c` | |
| C8 | Bahnen aus „Rank" | Eine Bahnvertauschung in RaceClocker schlägt auf die Bahnen durch, auch für Boote ohne Zeit; der Bib wandert nicht mit | `d64ae540` | 10.08.: am CRF-Feed belegt (Finale A/B zogen im ersten Takt nach), nachdem `aa3d209a` den Riegel aus C40 entfernt hat |
| C9 | Doppelte Mannschaft | Zwei Zeilen für dasselbe Boot → Pull verweigert mit Namensliste, statt zu raten | `dd8d67b8` | |
| C10 | Keine Ergebnisse | Pull ohne gewertete Zeilen übernimmt **die Bahnen** und meldet Erfolg — seit `aa3d209a` kein Fehler mehr (Begründung in C40). Zeiten, Plätze und `started_at` bleiben unangetastet | `aa3d209a` | |
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

### C27–C34 — Neustart eines Rennens in RaceClocker

Neu am 09.08. (`e1742e0a`). Setzt der Zeitnehmer eine Welle zurück, weil ein Start ungültig war,
liefert der Feed danach `00:00:00.0` als Startzeit und `Not started` als Ergebnis — er behauptet
also, dieser Lauf sei nie gefahren. ready2race übernimmt diese Aussage jetzt und löscht Zeiten,
Plätze, Ausscheidungen, Strafzeiten und den Ist-Start. Vorher blieb der ungültige Lauf stehen,
während RaceClocker längst neu maß.

Der ganze Block hängt an **einer** Unterscheidung, und die ist der Grund, warum C28 direkt neben
C27 steht: „keine Zeile trägt ein Ergebnis" ist auch der Zustand jedes laufenden Rennens, solange
die Boote unterwegs sind. Nur die gemessene Startzeit trennt die beiden. Greift der Reset falsch,
nimmt er einem laufenden Lauf die schon eingelaufenen Boote weg — der teuerste Fehler in diesem
Block, und einer, den am Renntag niemand rückgängig machen kann.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| C27 | Neustart löscht | Gefahrener, gewerteter Lauf; in RaceClocker die Welle zurücksetzen. Innerhalb eines Takts sind Zeiten, Plätze, DNS/DNF/DQ und Strafzeiten der zugeordneten Boote weg und der Ist-Start ist leer. Der Lauf bleibt **aktiv** — die Automatik deaktiviert ihn nicht | `e1742e0a` | |
| C28 | Boote auf dem Wasser | Gegenprobe zu C27: Welle gestartet, noch keine Zielzeit. Es wird **nichts** gelöscht, und es erscheint auch keine Warnung (zusammen mit C25 prüfen). Der Unterschied zu C27 ist allein die gemessene Startzeit im Feed | `e1742e0a` | |
| C29 | Reset über den Knopf | „Ergebnisse eintragen → RaceClocker" auf einem zurückgesetzten Rennen löscht genauso wie die Automatik und meldet **Erfolg** — nicht mehr „keine Ergebnisse". Ungewohnt zu lesen, aber richtig: geschrieben wurde ja etwas | `e1742e0a` | |
| C30 | Bahnen überleben | Startnummern und Bahnen bleiben nach dem Reset unverändert; die Zeilen stehen weiter im Feed, nur ohne Zeiten | `e1742e0a` | |
| C31 | Zweiter Versuch | Nach dem Reset die Welle erneut starten: Aktivierung, Ist-Start und Zeiten kommen normal wieder. Besonders auf `started_at` achten — es war vorher gesetzt, wurde geleert und muss neu gestempelt werden | `e1742e0a` | |
| C32 | Boot außerhalb des Feeds | Ein Boot, das RaceClocker nicht kennt und dessen Ergebnis von Hand steht, behält es beim Reset — angefasst werden nur Boote mit einer Zeile im Feed | `e1742e0a` | |
| C33 | Gesperrte Runde | Ein Lauf, aus dessen Plätzen die nächste Runde bereits gesetzt ist, wird auch vom Reset nicht angefasst — weder über den Job noch über den Knopf. Dieselbe Sperre wie beim Schreiben; ohne sie würde ein Neustart in RaceClocker Plätze löschen, aus denen die Setzung längst abgeleitet ist | `e1742e0a` | |
| C34 | Handeingabe und Reset | Automatik läuft, Ergebnis von Hand eingetragen: der Lauf ist pausiert (C21) und ein Neustart in RaceClocker fasst ihn **nicht** an. Nach „Automatik wieder aufnehmen" (C23) schlägt der Reset dagegen durch und nimmt den Handeintrag mit. Das ist gewollt — aber einmal gesehen haben, bevor es am Renntag passiert | `e1742e0a` | |

### C35–C39 — Wellenname mit Wettkampf

Neu am 10.08. (`acd5004d`). Der Wellenname trägt jetzt auch Rennnummer und Kürzel:
`10:30 | 12 JM4x | AF1` statt `10:30 AF1`. Grund ist die Wellenliste in RaceClocker — sie hält
alle Wettkämpfe einer Veranstaltung nebeneinander, und „AF1" allein sagt dort nicht, um welches
Rennen es geht.

Belegt sind bisher nur die Backend-Tests (664 grün, darunter `WaveNameTest` und der
Datenbank-Test `RaceClockerPollRepoTest`). In der laufenden App und gegen echtes RaceClocker ist
davon nichts gesehen worden — deshalb dieser Block. C39 ist der einzige Fall mit echtem
Schadenspotenzial am Renntag; die übrigen vier sind Sichtprüfungen.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| C35 | Name im Export | Startliste eines Laufs exportieren: die Wellen-Spalte lautet `10:30 \| 12 JM4x \| AF1`. Nach dem Import in RaceClocker steht derselbe Name in der Wellenliste und auf dem Timer-Gerät | `acd5004d` | |
| C36 | Wettkampf ohne Kürzel | Ein Wettkampf ohne Kürzel ergibt `10:30 \| 12 \| AF1` — kein doppelter Trennstrich, kein hängendes Leerzeichen. Gegenprobe für einen Lauf ohne geplante Startzeit: `12 JM4x \| AF1` | `acd5004d` | |
| C37 | Liste bleibt chronologisch | Mehrere Wettkämpfe in ein RaceClocker-Rennen exportieren: die alphabetisch sortierte Wellenliste steht trotzdem in Startreihenfolge, weil die Uhrzeit vorn bleibt | `acd5004d` | |
| C38 | Ergebnis-Pull findet den Lauf | Knopf **und** Automatik ziehen die Ergebnisse einer unter dem neuen Namen angelegten Welle. Zusammen mit C2 zu lesen: Export und Pull leiten den Namen aus derselben Funktion ab, weichen sie voneinander ab, greift der Notnagel-Filter nicht mehr | `acd5004d` | |
| C39 | Altwelle aus früherem Export | Eine vor der Umstellung angelegte Welle heißt in RaceClocker weiter `10:30 AF1`. Der Pull findet die Boote trotzdem — über die Match-Team-ID in „Extra info" (C1). Nur eine Startliste **ohne** diese Spalte hängt am Wellennamen und findet dann nichts: in dem Fall die Startliste neu exportieren. Vor dem Renntag einmal geprüft haben, welche Wellen schon in RaceClocker stehen | `acd5004d` | |

### C40–C45 — Bahnen vor dem ersten Ergebnis

Neu am 10.08. (`aa3d209a`). Am Vorabend an der CRF-Testregatta aufgefallen: Ein Bahnentausch in
RaceClocker kam **nie** an, solange kein Boot des Laufs eine Zeit oder eine Ausscheidung trug.
`applyRaceClockerRows` brach mit `NoResults` ab, bevor die Bahnvergabe lief — und weil der Job
`NoResults` als Normalfall wertet (C25), blieb die Oberfläche stumm und meldete „Automatisch
abgerufen" bei unveränderten Bahnen. Getroffen hat das genau den Moment, in dem der Zeitnehmer die
Bahnen festlegt: Lauf am Start, jede Zeile auf `Not started`. Die Zusage aus C8 („auch für Boote
ohne Zeit") galt damit nur, solange **irgendein** Boot schon durchs Ziel war.

Zwei Änderungen tragen den Block: Die Bahnvergabe steht jetzt **vor** dem Ergebnis-Riegel, und ein
Lauf ohne Ergebnisse endet mit **Erfolg** statt mit einem Fehler. Letzteres ist keine Kosmetik —
der Job umschließt die Routine mit `transact()`, ein Fehler nähme die eben geschriebenen Bahnen
wieder zurück. Dieselbe Umdeutung wie in C29, jetzt auch für den Normalfall.

Der teuerste denkbare Fehler in diesem Block ist C43: Wenn die vorgezogene Bahnvergabe einen
bereits gewerteten Lauf umnummeriert, ohne dass jemand in RaceClocker etwas angefasst hat, wandern
Zeiten und Plätze optisch auf falsche Bahnen.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| C40 | Tausch vor dem Start | Lauf aktivieren („Am Start"), in RaceClocker zwei Boote der Welle vertauschen, **ohne** eine Zeit zu nehmen. Innerhalb eines Takts stehen die neuen Bahnen in Durchführung, Schiedsrichter-Dashboard und Athleten-Anzeige. Kein Fehler, keine Warnung | `aa3d209a` | 10.08. am CRF-Feed belegt (Finale A: Bahn 1 wechselte von Frankfurt auf Cassel, erster Takt nach dem Neustart) |
| C41 | Nicht aktivierter Lauf | Gegenprobe: derselbe Tausch an einem Lauf **ohne** „Am Start". Es passiert weiterhin nichts — ein Umsortieren vor der Aktivierung schlägt bewusst nicht durch. Der zweite Riegel bleibt bestehen und ist gewollt | `aa3d209a` | |
| C42 | Knopf statt Automatik | „Ergebnisse eintragen → RaceClocker" auf einem Lauf ohne jede Zeit meldet jetzt **Erfolg** statt „keine Ergebnisse", und die Bahnen sind übernommen. Wie C29 ungewohnt zu lesen und trotzdem richtig | `aa3d209a` | |
| C43 | Gewerteter Lauf bleibt heil | Lauf mit Zeiten und Plätzen, in RaceClocker **nichts** ändern, Takt abwarten: Bahnen, Zeiten und Plätze bleiben, wie sie sind. Prüft, dass die vorgezogene Bahnvergabe keinen fertigen Lauf umnummeriert | `aa3d209a` | |
| C44 | Handeingabe gewinnt weiter | Wie C21, aber am Lauf ohne Ergebnisse: Nach einer Handeingabe ist der Lauf pausiert, und der nächste Takt fasst auch die **Bahnen** nicht mehr an | `aa3d209a` | |
| C45 | Abgemeldetes Boot | Ein Boot ist abgemeldet und steht nicht mehr im Feed, der Lauf hat noch keine Zeiten: Es behält eine Nummer oberhalb der importierten Bahnen und kollidiert mit keiner echten Bahn (C13 für den ergebnislosen Fall) | `aa3d209a` | im DB-Test abgedeckt, in der App noch nicht gesehen |

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

## K — Manueller Check-in/-out je Athlet:in

Am 09.08. gebaut. Der QR-Scan am Steg bleibt der reguläre Weg; für den Fall, dass ein Boot ohne
Scan abgelegt hat und die Crew trotzdem auf dem Wasser ist, können Admin und Schiedsrichter
Einträge von Hand ergänzen und bestehende berichtigen. Jede Änderung verlangt eine Begründung und
hinterlässt Vorher-/Nachher-Stand samt Urheber in `participant_tracking_change`. Entwurf:
`docs/superpowers/specs/2026-08-09-manueller-checkin-checkout-design.md`. Migration
`V202608091600`.

**Kein Agent hat die laufende Anwendung gesehen.** Backend-Suite (632) und Frontend (584) sind
grün, ein HTTP-Test belegt die Rechteprüfung auf allen drei Endpunkten — gerendert wurde nichts
davon je. Der wunde Punkt ist **K13**: ob ein von Hand nachgetragener Eintrag im
Schiedsrichter-Dashboard dieselbe Wirkung hat wie ein Scan, ist nirgends geprüft.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| K1 | Rechte | Aktion und Dialog erscheinen **nur** für Admin und Schiedsrichter (`UPDATE LIVE_DASHBOARD` oder `UPDATE EVENT`). Mit einem Vereinsvertreter-Konto anmelden: weder Zeilenaktion noch Stift, und der direkte Aufruf von `GET /event/{eventId}/participant/{participantId}/tracking` liefert 403 — dort stehen Begründungen im Klartext | `f54cb98d` | |
| K2 | Der Anlassfall | Ein Boot hat ohne Scan abgelegt. Für eine Person Check-in **und** Check-out von Hand nachtragen, Uhrzeiten frei setzen. Beide Einträge stehen danach im Verlauf und tragen den Chip „manuell" | `f54cb98d` | |
| K3 | QR-Eintrag berichtigen | Eine Person regulär per QR einchecken, dann im Dialog die Uhrzeit korrigieren. Der Chip wechselt auf **„per QR, berichtigt"** — nicht auf „manuell": dass der Eintrag vom Scanner kam, wird durch die Korrektur nicht unwahr | `f54cb98d` | |
| K4 | Begründung ist Pflicht | Speichern ohne Begründung ist nicht möglich (Knopf bleibt gesperrt). Gegenprobe über die API mit leerem Grund: 422, nicht 500. Die Datenbank hält zusätzlich dagegen (`check (length(btrim(reason)) > 0)`) | `f54cb98d` | |
| K5 | Freie Zeitwahl | Ein Zeitpunkt Stunden in der Vergangenheit wird angenommen — das ist der Normalfall, nicht der Sonderfall. Auch ein Zeitpunkt in der Zukunft ist zugelassen; bewusst keine Sperre | `f54cb98d` | |
| K6 | Widersprüchliche Reihenfolge | Ein Check-out ohne vorherigen Check-in, oder ein Eintrag, der eine spätere Zeile umdreht: die Meldung muss **auf Deutsch erklären, was zu tun ist** („An- und Abmeldungen müssen sich abwechseln und mit einer Anmeldung beginnen"), nicht nach Speicherfehler klingen | `f54cb98d` | |
| K7 | Gleiche Sekunde | Zwei Einträge derselben Person auf exakt denselben Zeitpunkt werden abgelehnt. Sonst wäre nicht bestimmt, ob die Person am Ende auf dem Wasser ist | `f54cb98d` | |
| K8 | Änderungsverlauf lesbar | Unten im Dialog steht je Änderung: Zeitpunkt, Name der Person, Vorher → Nachher und die Begründung. Nach zwei Korrekturen desselben Eintrags stehen **beide** da — die zweite überschreibt die erste nicht | `f54cb98d` | |
| K9 | Schiedsrichter-Ansicht | Im Detail-Dialog einer Mannschaft trägt jede Person einen eigenen Steg-Chip („Drin 9:35" / „Draußen" / „kein Steg-Scan") und daneben den Stift. Bis hierher zeigte der Dialog gar nicht, an wem ein fehlender Scan hängt | `f54cb98d` | |
| K10 | Protokoll-Spalte | Event-Seite, Tab Teilnehmende, Tabelle „Status Protokoll": die Spalte „Erfassung" unterscheidet die drei Fälle. Über die Zeilenaktion öffnet sich derselbe Dialog | `f54cb98d` | |
| K11 | QR-App unverändert | **Regression.** Ein regulärer Scan in der QR-App muss weiterhin funktionieren und „per QR" erzeugen. Die alten Meldungen („ist bereits eingecheckt") dürfen sich nicht geändert haben | `f54cb98d` | |
| K12 | Nichts nach außen | Öffentliche Anzeige und Athleten-Ansicht zeigen weder Bearbeitung noch Begründungen. Auch die Tabelle, die ein Vereinsvertreter über `/participantTracking` erreichen kann, nennt **keine Namen** von Bearbeitern und keinen Grund — nur Herkunft und Anzahl der Korrekturen | `f54cb98d` | |
| K13 | Wirkung aufs Dashboard | **Der eigentliche Zweck, und der einzige Punkt ohne jeden Beleg.** Für die letzte fehlende Person eines Bootes den Check-in nachtragen: beim nächsten Poll muss die Mannschaft im Schiedsrichter-Dashboard „abgelegt um …" zeigen und der Arena-Chip grün werden — genau so, als wäre gescannt worden. Wenn irgendwo etwas klemmt, dann hier | `f54cb98d` | |
| K14 | Bestand aus der Zeit davor | Eine Regatta mit alten Scans öffnen: die Einträge tragen alle „per QR", niemand ist als manuell markiert. Die Migration setzt den Bestand auf `QR` — schlägt das fehl, sieht die ganze Historie nach Handarbeit aus | `f54cb98d` | |

---

## L — Ergebnisse nach Wertungskategorien

Am 09.08. gebaut. Bis dahin zeigte jede Ergebnisliste **eine** gemeinsame Rangliste; wer in der
Breitensportwertung Erster war, fand sich dort als Sechster wieder und musste selbst
zusammensuchen, welche der fünf Boote davor überhaupt in seiner Wertung fuhren. Öffentliche
Anzeige, Schiedsrichter-Dashboard, Athleten-Anzeige und Platzierungsansicht trennen jetzt in
Abschnitte je Kategorie und zählen in jedem Abschnitt ab 1. Entwurf:
`docs/superpowers/specs/2026-08-09-ergebnisse-nach-wertungskategorien-design.md`.

**Teilweise in der laufenden Anwendung gesehen** (09.08.2026, Agent im Browser, Dev-Stand auf
`:5127`): L2, L3, L4, L5, L7, L8, L12 und L20 sind dort grün gewesen — das ist **kein** Ersatz für den
Durchgang eines Menschen, aber die Abschnitte wurden gerendert und stimmten. Alles, was eine
Anmeldung braucht (L1, L9–L11, L13–L19, L21), ist weiterhin unbelegt: ein Agent darf keine
Zugangsdaten eingeben. L12 ging trotzdem, weil das Ergebnis-PDF auch ohne Anmeldung abrufbar ist
(`GET /api/results/event/{eventId}`).

**Zwei Befunde aus genau diesem Durchgang:**

1. **Kategorien ohne Zuordnung zur Veranstaltung sind der Normalfall, nicht der Ausnahmefall.** Im
   Bestand tragen 32 Boote „Internationale Wertung" und 55 „Deutsche Meisterschaft Wertung", ohne
   dass diese Kategorien je einer Veranstaltung zugeordnet wurden. Bis `9fbe99ed` sortierten sie
   auf Stelle 0 und drängten sich damit **vor** jede gepflegte Reihenfolge. Seither stehen sie
   hinten und untereinander alphabetisch — siehe L22.
2. **„gemeldet von … | undefined".** Ergebnisdialog und Platzierungsansicht hängten den
   Mannschaftsnamen ohne Prüfung an, und der ist bei einem Einer meistens leer — in **jeder** Zeile
   stand `undefined`. Älter als die Kategoriewertung, beim Durchgang aufgefallen und gleich
   mitbehoben.
3. **Ein Gleichstand im Lauf ist unmöglich.** `place_unique_in_match` (aus `V202507040930`) verbietet
   zwei Boote mit demselben Platz im selben Lauf. Der Gleichstandsfall gehört damit ausschließlich
   zu den Wettkampf-Platzierungen, wo `CompetitionSetupPlacesOption.EQUAL` mehrere Boote gleich
   wertet — L6 ist entsprechend umgeschrieben.

**Testdaten liegen bereit.** `docs/seeds/seed-block-l-wertungskategorien.sql` legt vier
Kategorien an, ordnet sie der „Coastal-Regatta Flensburg 2026" in einer bewusst **nicht**
alphabetischen Reihenfolge zu und verteilt sie auf die Boote des Wettkampfs 11. Am 09.08. war das
in der Dev-Datenbank bereits eingespielt; wer auf einer frischen Datenbank testet, spielt es
nach:
```
docker exec -i backend-db-1 psql -U developer -d ready2race < docs/seeds/seed-block-l-wertungskategorien.sql
```

**Voraussetzung für diesen Block:** eine Veranstaltung mit **mindestens zwei** zugeordneten
Wertungskategorien, einem Lauf, in dem Boote **beider** Kategorien starten, **einem Boot ganz ohne
Kategorie**, einem Gleichstand (zwei Boote mit demselben Platz) und einem abgemeldeten Boot.
Zusätzlich ein Wettkampf **ohne jede** Wertungskategorie für den Regressionsfall L5.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| L1 | Reihenfolge pflegen | Veranstaltung → Einstellungen → Wertungskategorien: Hoch/Runter verschiebt eine Kategorie. Nach dem Neuladen steht sie noch dort. Die Reihenfolge hängt an der **Veranstaltung**, nicht an der Kategorie — bei einer zweiten Regatta darf sie anders sein | `9fbe99ed` | |
| L2 | Abschnitte folgen der Reihenfolge | Öffentliche Ergebnisanzeige, Lauf öffnen: die Abschnitte stehen in genau der unter L1 gesetzten Folge — nicht alphabetisch. Zum Prüfen die Reihenfolge unter L1 absichtlich **gegen** das Alphabet stellen | `9fbe99ed` |  `AGENT 09.08.` |
| L3 | Zählung ab 1 je Abschnitt | Jeder Abschnitt beginnt bei 1. Ein Boot, das im Lauf Sechster ist, aber Erster seiner Kategorie, trägt die 1 | `9fbe99ed` |  `AGENT 09.08.` |
| L4 | Ohne Wertungskategorie | Das Boot ohne Kategorie steht in einem eigenen Abschnitt „Ohne Wertungskategorie" — **immer am Ende**, auch wenn eine echte Kategorie weiter hinten einsortiert ist | `9fbe99ed` |  `AGENT 09.08.` |
| L5 | Wettkampf ohne Kategorien unverändert | **Die zentrale Zusage.** Ein Wettkampf, in dem kein Boot eine Kategorie trägt, sieht aus wie vorher: eine durchgehende Liste, **keine** Überschrift „Ohne Wertungskategorie". Vorher/Nachher am selben Lauf vergleichen | `9fbe99ed` |  `AGENT 09.08.` |
| L6 | Gleichstand — **nur in den Platzierungen** | Im Lauf nicht herstellbar: `place_unique_in_match` verbietet zwei Boote mit demselben Platz. Zu prüfen ist die Wettkampf-Platzierung einer Runde mit Platzvergabe „gleich" (`EQUAL`): alle Boote dieser Runde tragen innerhalb ihrer Kategorie dieselbe Zahl, das nächste Boot lässt die Lücke (1, 1, 3 — kein Zweiter) | `9fbe99ed` | |
| L7 | Abgemeldet, DNF, DSQ | Solche Boote bekommen **keinen** Kategorieplatz und stehen am Ende **ihres eigenen** Abschnitts — sie verschwinden nicht. Eine Besatzung, die ihr Boot im Ergebnis nicht findet, hält das für einen Anzeigefehler | `9fbe99ed` |  `AGENT 09.08.` |
| L8 | Athleten-Anzeige gleicht der öffentlichen | `/board/{eventId}`: derselbe Lauf zeigt dieselben Abschnitte in derselben Folge und **dieselben Zahlen** wie die öffentliche Seite. Nebeneinander auf zwei Schirmen vergleichen | `9fbe99ed` |  `AGENT 09.08.` |
| L9 | Schiedsrichter: laufender Lauf bleibt Bahnliste | Solange kein Boot gewertet ist, zeigt die Karte **keine** Überschriften und sortiert nach Bahn. Am Steg wird sie gegen das Wasser gelesen — bewusste Abweichung von den anderen Ansichten | `9fbe99ed` | |
| L10 | Schiedsrichter: gewerteter Lauf | Sobald das erste Boot gewertet ist, erscheinen die Abschnitte, und die Zahl im Kreis ist der **Kategorie**platz — dieselbe Zahl wie öffentlich | `9fbe99ed` | |
| L11 | Platzierungsansicht | Wettkampf → Durchführung → Platzierungen: Abschnitte mit Überschrift, Kategorieplatz, ungewertete Boote mit „-" | `9fbe99ed` | |
| L12 | Ergebnis-PDF | Veranstaltungsergebnisse herunterladen: je Wettkampf eine Überschrift pro Kategorie, darunter die Kategorieplätze. Ein Wettkampf ohne Kategorien behält seine bisherige Form | `9fbe99ed` | `AGENT 09.08.` |
| L13 | Urkunde ohne die neue Option | **Regressionsfall.** Schalter „Wertungskategorie drucken" aus (Vorgabe): die Urkunde ist Zeichen für Zeichen die von vorher. Eine vor dem Update erzeugte danebenlegen | `9fbe99ed` | |
| L14 | Option an, Vorlage ohne Platzhalter | **Die wahrscheinlichste Enttäuschung.** Schalter an, aber die Vorlage trägt keinen `RATING_CATEGORY`-Platzhalter: es ändert sich **nichts**. Das ist so gebaut und keine Störung — wer die Zeile will, muss sie einmal in der Vorlage setzen | `9fbe99ed` | |
| L15 | Option an, Platzhalter gesetzt | Im Vorlagen-Editor einen `RATING_CATEGORY`-Platzhalter setzen, Urkunde mit Schalter erzeugen: die Kategorie steht als klar erkennbare eigene Zeile, PDF **und** DOCX | `9fbe99ed` | |
| L16 | Urkundenplatz bleibt wettkampfweit | **Ausdrückliche Entscheidung, kein Fehler.** Ein Boot, das in seiner Kategorie Erster, im Wettkampf aber Dritter ist, trägt auf der Urkunde „3. Platz" — auch bei eingeschalteter Option. Begründung: eine Urkunde hängt jahrelang neben älteren, auf denen „3. Platz" den Platz im Rennen meinte | `9fbe99ed` | |
| L17 | Platzgrenze der Urkunden | „Bis Platz 3" greift weiterhin auf den **wettkampfweiten** Platz. Es gibt also nicht je Kategorie drei Urkunden — beim Test bewusst gegenprüfen, ob das für die CRF so gewollt ist | `9fbe99ed` | |
| L18 | Vorlagen-Editor | Der neue Platzhaltertyp ist in der Auswahl, heißt auf Deutsch „Wertungskategorie", und die Vorschau zeigt den Beispieltext „Meisterschaften" an der gesetzten Stelle | `9fbe99ed` | |
| L19 | Neue Kategorie hängt hinten an | Einer Veranstaltung eine weitere Kategorie zuordnen: sie steht in Konfiguration und Ergebnisliste **am Ende**, nicht dazwischen | `9fbe99ed` | |
| L20 | Kategorie ohne Boote | Eine zugeordnete Kategorie, in der niemand gemeldet ist, erzeugt in **keiner** Ergebnisliste einen leeren Abschnitt | `9fbe99ed` |  `AGENT 09.08.` |
| L22 | Kategorie ohne Zuordnung zur Veranstaltung | Ein Boot trägt eine Kategorie, die der Veranstaltung nie zugeordnet wurde (im Bestand der Regelfall): ihr Abschnitt steht **hinter** allen konfigurierten, untereinander alphabetisch, und vor „Ohne Wertungskategorie". Am 09.08. im Browser bestätigt: „Deutsche Meisterschaft Wertung" vor „Internationale Wertung", beide hinter den gepflegten | `9fbe99ed` | `AGENT 09.08.` |
| L21 | Altdaten nach der Migration | Eine Veranstaltung, die vor dem Update Kategorien hatte: die Reihenfolge ist nach dem Update die bisher gezeigte alphabetische. Die Migration allein darf keine Anzeige verändert haben | `9fbe99ed` | |

**Aus dem Review (10.08., Fable):** keine Fehler gefunden. Zwei Punkte, die beim Handtest ein
zweiter Blick wert sind, weil sie im Code nicht abgesichert, sondern nur faktisch richtig sind:

- Die drei Lauf-Ansichten übernehmen den Platz **roh** aus der Datenbank, statt abgemeldete,
  ausgeschiedene und disqualifizierte Boote ausdrücklich als ungewertet zu behandeln (das tut nur
  die Platzierungsansicht). Heute folgenlos, weil ein solches Boot nie einen Platz trägt — beim
  Test also gezielt ein **gescheitertes Boot mit gesetztem Platz** suchen, falls es so eines je
  gibt. Es dürfte dann keinen Kategorieplatz bekommen.
- `getCompetitionPlaces` liefert seine flache Liste jetzt in **Abschnittsreihenfolge** statt streng
  nach Platz. Die Oberfläche gruppiert selbst neu, ist also unberührt; ein Fremdkonsument, der sich
  auf „Index = Platz − 1" verlässt, bräche. Ein solcher ist nicht bekannt.


---

## M — Freilos als Freilos erkennbar

Neu am 10.08. Ein Freilos sah in Zeitplan, Schiedsrichter-Dashboard und Durchführung aus wie ein
gewöhnlicher Lauf, für den noch Zeiten kommen: „Anstehend", nach fünf Minuten „Überfällig". Am Steg
war weder zu sehen, dass es dort nichts zu holen gibt, noch ob das Freilos schon quittiert ist.

**Die Leitplanke ist wieder die Anzeige:** kein neuer Zustand, kein geänderter Übergang, kein
Eingriff in Knöpfe, Rechte oder die Quittierung. Wer M durchgeht, prüft in erster Linie, dass H
(Lauf-Status) und B8–B11 (Kette) sich **nicht** anders verhalten als vorher. Die Freilos-Regel selbst
ist unverändert die des bisherigen Panels „Teams mit Freilos" — Runde nicht verpflichtend, genau eine
nicht als `out` mitgeführte Mannschaft. Entwurf:
`docs/superpowers/specs/2026-08-09-freilos-anzeige-design.md`.

Der Grund erscheint nur, wenn er belegt ist: „wegen Abmeldung" ausschließlich bei vorhandenem
`competition_deregistration`-Datensatz, der Freitext-Grund nur bei genau einer Abmeldung im Lauf.
Alles andere bekommt den neutralen Satz. **M5 und M6 sind deshalb die wichtigsten Fälle des Blocks** —
sie prüfen, dass die Anzeige nichts behauptet, was sie nicht weiß.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| M1 | Ein Vokabular, drei Oberflächen | Derselbe Freilos-Lauf trägt in Durchführung, Zeitplan und Schiedsrichter-Dashboard dieselbe Aussage. Alle drei lesen `status.bye` aus **einer** Ableitung (`MatchStatusLogic.deriveBye`) — sie können nicht auseinanderlaufen, aber sie können falsch abgeleitet sein | `87f85435` | |
| M2 | Strukturelles Freilos | Ein Lauf, in den von vornherein nur ein Boot gesetzt wurde: Chip „Freilos · offen", in Zeitplan und Dashboard darunter „Freilos – kein Gegner in dieser Runde". **Kein** „Anstehend", **kein** „Überfällig". In der Durchführung steht bewusst nur der Chip im Panel — dort keinen Erklärungssatz suchen | `87f85435` | |
| M3 | Freilos nach Abmeldung, mit Grund | Der Gegner ist mit gespeichertem Grund abgemeldet: „Freilos wegen Abmeldung – ⟨Verein⟩ (⟨Grund⟩)" in Zeitplan und Dashboard. Auch dann, wenn die Abmeldung in einer **früheren** Runde geschah und das Boot nur noch als `out`-Zeile mitläuft — genau dafür ist der Fall da | `87f85435` | |
| M4 | Freilos nach Abmeldung, ohne Grund | Abmeldung ohne Freitext: derselbe Satz ohne Klammer, der Verein steht trotzdem da | `87f85435` | |
| M5 | Zwei Abmeldungen im selben Lauf | Beide Vereine werden genannt, **der Grund entfällt** — die Zuordnung Name zu Grund wäre sonst geraten | `87f85435` | |
| M6 | Ausgeschiedener Gegner ist keine Abmeldung | Der Gegner ist `out`, weil er ausgeschieden ist (DSQ/DNF) oder den Platz nicht geschafft hat: neutraler Satz „kein Gegner in dieser Runde". Es darf **keine** Abmeldung behauptet werden | `87f85435` | |
| M7 | Noch zu quittieren | Solange niemand beendet hat: „Freilos · offen" (blau) | `87f85435` | |
| M8 | Quittiert | Nach „Lauf beenden": „Freilos · quittiert" (grün). Der Weg dorthin ist unverändert der bisherige Knopf | `87f85435` | |
| M9 | Entfallen | Runde abgesagt: „Freilos · entfallen", durchgestrichen — und der Lauf bleibt in Zeitplan und Dashboard **sichtbar**. Ein still verschwundener Lauf ist am Steg nicht von einem Anzeigefehler zu unterscheiden | `87f85435` | |
| M10 | Aktivierung schlägt das Freilos | Ein Freilos, das jemand an den Start ruft, zeigt „In Vorbereitung" bzw. „Läuft" — nicht „Freilos". Was tatsächlich passiert, schlägt weiterhin alles | `87f85435` | |
| M11 | Arena-Chip schweigt | Bei einem Freilos erscheint **kein** „Arena 0/1" — ein Boot, das nicht fährt, muss nicht draußen sein | `87f85435` | |
| M12 | Durchführung: Panel statt Karte | Das Freilos steht im Panel „Teams mit Freilos", jetzt mit Statuschip in einer dritten Spalte, und **nicht** zusätzlich als Lauf-Karte. Gegenprobe: kein Lauf taucht doppelt auf, keiner verschwindet ganz | `87f85435` | |
| M13 | Bedienung unverändert | Aktivier-Haken, „Ergebnisse eintragen", „Lauf beenden", „Runde entfällt", Startlisten und alle Rechte verhalten sich wie vor der Änderung — auch am Freilos | `87f85435` | |
| M14 | Board-Poll bleibt ruhig | Bei einem Lauf mit zwei Abmeldungen darf die Namensreihenfolge zwischen zwei Abrufen nicht wechseln (fester `ORDER BY`); sonst kippt der ETag des Dashboards bei jedem Poll und die volle Nutzlast fließt neu | `87f85435` | |
| M17 | Gegenprobe: kein Freilos, wo keins ist | Eine **verpflichtende** Runde mit einem einzigen Boot (Zeitrennen-Konstellation) trägt **keinen** Freilos-Chip und keinen Erklärungssatz — sie bleibt ein gewöhnlicher Lauf. Der billigste Handgriff, um eine falsch gespeiste Ableitung zu finden (etwa ein falsch gelesenes `round_required`) | `87f85435` | |

**Zwei bekannte Abweichungen, die der Test bestätigen soll — beide bestanden schon vorher und werden
durch die neue Anzeige nur sichtbar:**

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| M15 | Erste Runde, Freilos durch Abmeldung | `automaticFirstPlace` zählt in der **ersten** Runde Setzplätze und vergibt hier keinen Platz 1. Der Lauf steht deshalb auf „Freilos · offen" und braucht die Platzvergabe von Hand, bevor die nächste Runde gesetzt werden kann. Am Testtag entscheiden, ob das so bleiben soll | `87f85435` | |
| M16 | Ergebnissperre greift beim `out`-Gegner nicht | Ein Lauf mit einem fahrenden Boot und einem mitgeführten `out`-Gegner wird als Freilos angezeigt, `checkUpdateMatchResult` nimmt dort aber weiter Ergebnisse an (die Sperre prüft die **ungefilterte** Teamliste). Prüfen, ob das im Betrieb stört; wenn ja, ist das eine eigene Änderung an der Sperre, nicht an der Anzeige | `87f85435` | |

---

## N — Laufstatus in der öffentlichen Ergebnisanzeige

Am 09./10.08. gebaut. Vier Oberflächen zeigten den Laufzustand schon aus **einer** Ableitung
(`LiveDashboardLogic.deriveMatchState`): Durchführung, Schiedsrichter-Dashboard, Zeitplan und
Athleten-Anzeige. Die fünfte kannte ihn nicht — der Tab „Live" der öffentlichen Ergebnisanzeige
zeigte ausschließlich aktivierte Läufe, ohne jede Statusangabe, und lud genau einmal. Jetzt zeigt
er auch die anstehenden, jeden mit Chip, und aktualisiert sich alle 15 Sekunden von selbst.
Entwurf: `docs/superpowers/specs/2026-08-09-einheitlicher-laufstatus-oeffentlich-design.md`.

**Nichts davon ist in der laufenden Anwendung gesehen worden.** Der ganze Block ist unbelegt —
das ist der Grund, warum er hier steht. Backend (681) und Frontend (616) sind grün, aber
Testcontainers und Vitest sagen nichts darüber, was ein Zuschauer am Ufer sieht.

**Die drei Stellen, an denen ich mit einem Fehler rechne**, in dieser Reihenfolge:

1. **M4/M5** — der Takt. Er ist neu gebaut (`frontend/src/utils/polling.ts`), gegen Faketimer
   geprüft und noch nie gegen ein echtes Funkloch gelaufen.
2. **M9** — die Ergebnisfreigabe. Der Schutz ist strukturell (SQL plus ein DTO ohne
   Ergebnisfelder) und zweifach getestet, aber ein Leck hier wäre der einzige Fehler dieses
   Vorhabens, der einer Regatta wirklich schadet.
3. **M12** — der Zwischenspeicher. Fünf Sekunden Vorhaltezeit sind gesetzt; ob sich das im Feld
   wie „live" anfühlt oder wie ein Hänger, entscheidet erst der Blick.

**Voraussetzung für diesen Block:** eine Veranstaltung mit Zeitplan, mindestens drei Läufen
desselben Wettkampfs, einem abgesagten Slot, einem Programmpunkt (z.B. „Mittagspause", nur
sichtbar bei eingeschaltetem `showBreaksOnPublicBoards`) und einer Runde, die noch nicht erzeugt
ist. Der Förde-Seed bringt das mit. Zwei Geräte oder zwei Fenster nebeneinander: links das
Schiedsrichter-Dashboard oder die Durchführung, rechts `/results/{eventId}` im Tab „Live".

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| N1 | Anstehende Läufe erscheinen überhaupt | Tab „Live" öffnen, ohne dass irgendein Lauf aktiviert ist: die nächsten Läufe stehen da, jeder mit Chip „Anstehend". Vorher war der Tab in dieser Lage **leer** — genau das war der Anlass | `7f7d7398` | |
| N2 | Die Kette der Zustände an einem Lauf | Einen Lauf aktivieren → Chip springt auf „In Vorbereitung" (blau). „Läuft" drücken → „Läuft · n min", die Minutenzahl zählt hoch. Beenden → der Lauf verschwindet aus „Live" und taucht unter „Ergebnisse" auf | `7f7d7398` | |
| N3 | Dieselben Worte wie nebenan | Denselben Lauf gleichzeitig in Durchführung, Zeitplan, Dashboard und im Live-Tab ansehen: **gleicher Text, gleiche Farbe**. Das ist die eigentliche Zusage des Vorhabens — eine Abweichung hier ist ein Befund, auch wenn beide Seiten für sich plausibel aussehen | `7f7d7398` | |
| N4 | Wechsel ohne Neuladen | **Der Kernfall.** Den Live-Tab offen liegen lassen und im anderen Fenster aktivieren: der Chip wechselt binnen ~20 Sekunden von selbst. Nicht neu laden, nicht wegklicken — nur warten | `7f7d7398` | |
| N5 | Netz weg, Netz wieder da | WLAN am Gerät abschalten: die Karten **bleiben stehen**, darüber erscheint „Stand von hh:mm". WLAN wieder an: die Liste ist binnen Sekunden wieder frisch und die Zeile verschwindet. Eine leere Seite nach einem Funkloch wäre der schlechteste Ausgang | `c750e0fe` | |
| N6 | Erster Abruf scheitert | Backend anhalten, **dann** den Tab öffnen: „Die Läufe konnten nicht geladen werden." — und **nicht** „Zurzeit ist kein Lauf angesetzt." Der Unterschied ist der ganze Grund für das Feld `initialLoad` | `7f7d7398` | |
| N7 | Nichts angesetzt | Eine Veranstaltung ohne anstehende Läufe: „Zurzeit ist kein Lauf angesetzt." Nicht die Fehlermeldung aus N6 | `7f7d7398` | |
| N8 | Abgesagter Lauf | Einen Slot im Zeitplan absagen: der Lauf **bleibt** im Live-Tab stehen, durchgestrichen und abgeblendet, Chip „Abgesagt", nicht anklickbar. Eine Besatzung, die ihren Lauf sucht, muss ihn finden und daran ablesen, dass er nicht stattfindet | `248871b0` | |
| N9 | **Ergebnisfreigabe hält** | Veranstaltung auf „nur beendete Läufe" (`FINISHED_ONLY`) stellen. Einen Lauf vollständig werten, aber **nicht** beenden. Erwartung: er erscheint **weder** im Tab „Live" **noch** im Tab „Ergebnisse". Dann auf `RESULTS_COMPLETE` umstellen — jetzt steht er unter „Ergebnisse", weiterhin nicht unter „Live" | `d691ed6c` | |
| N10 | Teilergebnisse eines laufenden Laufs | Ein aktivierter Lauf, bei dem die Zeitnahme schon einzelne Boote gewertet hat: der Lauf bleibt „Läuft", und im Dialog stehen **keine** Plätze und Zeiten (der Dialog zeigt sie nur für beendete Ergebnisse). Das ist unverändertes Verhalten des alten Tabs — hier geht es darum, dass es unverändert **geblieben** ist | `7f7d7398` | |
| N11 | Wartende Runde und Programmpunkt | Eine noch nicht erzeugte Runde erscheint mit dem Hinweis „Aufstellung steht noch nicht fest" statt einer leeren Karte. Ein Programmpunkt („Mittagspause") erscheint **ohne** Chip — und wird auch eine halbe Stunde nach seiner Zeit **nicht** „Überfällig" genannt | `affc8ae5`, `248871b0` | |
| N12 | Last und Frische | Den Tab auf mehreren Geräten gleichzeitig offen halten. Der Zwischenspeicher hält die Antwort 5 Sekunden vor — ein Zustandswechsel darf sich dadurch um höchstens diese 5 Sekunden verspäten. Fühlt sich das nach „hängt" an, ist die Vorhaltezeit zu lang | `7ae3ae67` | |
| N13 | Hintergrund | Den Tab in den Hintergrund legen (anderer Browser-Tab), einige Minuten warten, zurückkommen: die Liste ist **sofort** frisch. Dazwischen wurde nicht getaktet — das lässt sich am Netzwerk-Reiter der Entwicklerwerkzeuge ablesen | `4d1cc3b9` | |
| N14 | Überfällig | Ein anstehender Lauf, dessen geplante Zeit mehr als 5 Minuten zurückliegt, ohne dass ihn jemand aktiviert hat: Chip „Überfällig · n min" in Rot. Bei 2 Minuten Verzug **noch nicht** — Regattaalltag soll nicht leuchten | `7f7d7398` | |
| N15 | Ohne Termin | Ein Lauf ohne geplante Startzeit trägt „Ungeplant" und wird **niemals** „Überfällig" | `7f7d7398` | |
| N16 | Ergebnis-Tab unberührt | **Regressionsfall.** Der Tab „Ergebnisse" daneben benutzt dieselbe Karte. Er muss aussehen und sich verhalten wie vor dem Update: kein Statuschip, Klickfläche vorhanden, Dialog wie gehabt | `7f7d7398` | |

---

## O — Siegerehrungsbogen

Am 09./10.08. gebaut. Bis dahin ging die Sprecherin mit dem Ergebnis-PDF der ganzen Veranstaltung
oder dem Platzierungs-Tab ans Pult — beides listet **alle** Boote, kennzeichnet den meldenden
Verein nicht und nennt den Heimatverein nur als Fußnote. Jetzt gibt es ein eigenes PDF: **eine
A4-Seite je Wertungskategorie**, darauf die Plätze 1–3 mit Namen, Vereinen, Zeiten und Lauf, zum
Vorlesen gesetzt. Auswahl über einen Dialog im Wettkämpfe-Tab und auf der Platzierungsseite.
Entwurf: `docs/superpowers/specs/2026-08-09-siegerehrungsbogen-design.md`.

> **Kein einziger Fall ist in der laufenden Anwendung gesehen worden, und der Bogen wurde nie
> ausgedruckt.** Abgesichert ist er ausschließlich durch Unit-Tests, PDF-Textextraktion und
> Code-Reviews. Hier zuerst hinsehen — und zwar mit einem echten Ausdruck, nicht am Bildschirm:
> die zentrale Zusage („passt auf ein Blatt, ist vorlesbar") lässt sich nur auf Papier prüfen.

Zwei Annahmen des Entwurfs sind beim Bau **widerlegt** worden und erklären, warum O4–O7 so genau
hinsehen: `page { }` im PDF-Baukasten ist keine harte Seitengrenze (der Renderer legt bei Überlauf
still eine **kopflose** Seite nach), und die Schriftgröße lässt sich nicht aus der Personenzahl
ableiten (Vereinsketten brechen um — drei *Vierer* einer Renngemeinschaft sprengten A4 bei zwölf
Personenzeilen, während sieben Personen zufällig passten). Heute wird die Seitenzahl gemessen.

| Fall | Titel | Was zu tun und zu sehen ist | testbar ab | Nachweis |
|---|---|---|---|---|
| O1 | Auswahl öffnen | Veranstaltung → Wettkämpfe: Knopf „Siegerehrungsbogen". Der Dialog listet die Ehrungen nach Rennnummer, je Wettkampf eine Zeile pro Wertungskategorie, mit der Zahl der geehrten Boote. Alles ist vorausgewählt | `f566309b` | |
| O2 | Auswahl vom einzelnen Rennen | Durchführung → Platzierungen eines Wettkampfs → „Siegerehrungsbogen": der Dialog zeigt **nur** dessen Ehrungen. Gegenprobe im Netzwerk-Tab: die Anfrage trägt `competitionId`, es wird nicht die ganze Regatta berechnet | `f566309b` | |
| O3 | Eine Seite je Wertungskategorie | **Die zentrale Zusage.** Ein Rennen mit zwei Kategorien ergibt zwei Seiten. Keine Kategorie steht auf dem Blatt einer anderen, jede Seite trägt Veranstaltung, Rennnummer, Wettkampfname und Wertung | `f566309b` | |
| O4 | **Ausdrucken und aus zwei Metern lesen** | Der wichtigste Fall. Ein Blatt auf Papier bringen und aus Pult-Entfernung vorlesen: Rangzahl (20 pt) und Vereinszeile (14 pt) müssen sicher greifbar sein, die Namenszeilen lesbar. Achtung: **jede Achter-Ehrung** landet auf der kleinsten Stufe (8,5 pt) — genau die ausdrucken, nicht einen Einer | `f566309b` | |
| O5 | Renngemeinschaft mit ausgeschriebenen Vereinsnamen | Einmal als Vierer, einmal als Achter: Titelzeile ist die **Vereinskette** in Bootsreihenfolge (nicht „Renngemeinschaft"), darunter „Meldender Verein: …", hinter den Namen nur die **abweichenden** Vereine. Hier zerbrach die erste Fassung | `f566309b` | |
| O6 | Fortsetzungsseite | Braucht ein sehr großes Feld (geteilter Rang mit vier Achtern als Renngemeinschaft) zwei Blätter, trägt **jedes** den vollständigen Kopf, und ab dem zweiten steht „Fortsetzung" darauf. Es darf **nie** ein Blatt ohne Kopf entstehen, und keine Mannschaft darf zerrissen werden | `f566309b` | |
| O7 | Kein leerer Platzhalter | Ein Boot ohne Zeit, ein Boot ohne Bootsnamen, ein Wettkampf ohne Wertungskategorie: die jeweilige Angabe **entfällt ganz**. Nirgends ein „—", ein hängender Trenner oder ein Doppelpunkt ins Leere | `f566309b` | |
| O8 | Reihenfolge der Kategorien | Die Seiten folgen der unter **L1** gepflegten Reihenfolge, nicht dem Alphabet — dieselbe Folge, in der auch geehrt wird. Zum Prüfen die Reihenfolge unter L1 absichtlich gegen das Alphabet stellen und beide Ausgaben nebeneinanderhalten | `f566309b` | |
| O9 | Zählung ab 1 je Kategorie | Ein Boot, das im Wettkampf Sechster, in seiner Kategorie aber Erster ist, steht auf dem Bogen als **1.** Gleiche Regel wie L3 — der Bogen rechnet seit `2caaf096` an derselben Stelle | `f566309b` | |
| O10 | Gleichstand ohne Bronze | Teilen sich zwei Boote den zweiten Platz, zeigt das Blatt 1., 2., 2. und **keinen** dritten Rang. Beide tragen den Vermerk „geteilt", die Rangzahl steht nur beim ersten. Herstellbar über eine Runde mit Platzvergabe „gleich" (`EQUAL`), siehe L6 | `f566309b` | |
| O11 | Ausgeschlossene Boote | Ein abgemeldetes, ein ausgeschiedenes und ein disqualifiziertes Boot stehen **nicht** auf dem Bogen, und die Ränge der übrigen rücken entsprechend auf | `f566309b` | |
| O12 | Zeit, Strafe, Lauf am richtigen Boot | Zeit rechts auf Höhe der Rangzahl, Zeitstrafe darunter („Zeitstrafe +10 s (Frühstart)") — beide beim Boot, zu dem sie gehören. Der Lauf steht im Kopf, wenn alle drei aus **demselben** stammen | `f566309b` | |
| O13 | Zwei Läufe in einer Wertung | Kommen die Ränge aus verschiedenen Läufen (A-/B-Finale, Zeitläufe), steht **keine** Lauf-Angabe im Kopf, dafür je Rangblock eine eigene. Ein Sieger ohne Lauf darf die Angaben der anderen nicht verschlucken | `f566309b` | |
| O14 | Meldender Verein verdichtet | Tragen alle Personen den meldenden Verein, steht er **einmal** als Titelzeile — keine Zeile „Meldender Verein", kein Vereinszusatz hinter den Namen. Startet die Crew für einen anderen Verein als den meldenden, stehen beide getrennt | `f566309b` | |
| O15 | Schreibvarianten desselben Vereins | Zwei Personen mit „Rostocker Ruderclub" und „Rostocker Ruder-Club von 1885 e.V.": das ist **ein** Verein, eine Titelzeile, kein Vereinszusatz. Ein Vereinsboot darf nicht wie eine Renngemeinschaft aussehen | `f566309b` | |
| O16 | Challenge-Veranstaltung | Bei einer Challenge-Veranstaltung erscheint der Knopf gar nicht erst; wird der Endpunkt direkt aufgerufen, kommt die Meldung „dort werden keine Läufe gefahren und keine Plätze vergeben" — nicht „keine Ergebnisse" | `f566309b` | |
| O17 | Noch keine Platzierungen | Vor dem ersten gewerteten Lauf: der Dialog sagt „Es gibt noch keine Ehrungen", statt eine leere Liste oder einen Fehler zu zeigen | `f566309b` | |
| O18 | Dialog zweimal öffnen | Zwischen zwei Öffnungen ein Ergebnis ändern: beim zweiten Öffnen stehen die neuen Zahlen da, die Vorauswahl ist wieder vollständig, und „Es gibt noch keine Ehrungen" blitzt nicht auf. Für diesen Fall gibt es hausüblich keinen Test | `f566309b` | |
| O19 | Dateiname | Der Download heißt `siegerehrung_<Veranstaltung>.pdf`. Einmal mit einer Veranstaltung prüfen, deren Name Umlaute trägt | `f566309b` | |
| O20 | Knopf gesperrt ohne Auswahl | Alle Häkchen entfernen: „Herunterladen" ist grau. Wichtig, weil eine leer verschickte Auswahl serverseitig „alle Ehrungen drucken" bedeutet | `f566309b` | |
| O21 | Berechtigung | Ohne Anmeldung ist der Endpunkt nicht erreichbar (401). Durch `AwardCeremonyHttpIT` abgedeckt — die Klasse läuft mangels Failsafe-Konfiguration aber **nicht** im normalen Testlauf mit und muss gezielt gestartet werden: `./mvnw test -Dtest=AwardCeremonyHttpIT -DfailIfNoSpecifiedTests=false` | `f566309b` | |
| O22 | Ganze Regatta in einem Rutsch | **Nie gemessen.** Den Dialog im Wettkämpfe-Tab auf der echten CRF-Datenmenge öffnen (~40 Rennen): das berechnet je Wettkampf die Platzierungen. Dann *alle* Ehrungen drucken — jeder Bogen wird bis zu fünfmal probeweise gesetzt. Dauer beider Schritte und Dateigröße notieren. Wird es zäh, ist die Auswahlliste der Hebel, nicht das Layout | `f566309b` | |
| O23 | Gleichstand **auf** Rang 3 | Spiegelfall zu O10: teilen sich zwei Boote den dritten Platz, stehen **beide** auf dem Blatt — vier Blöcke statt drei. Das Blatt wird damit voller als erwartet; prüfen, dass es trotzdem passt und nichts abgeschnitten ist | `f566309b` | |

**Offen, vom Test zu entscheiden:** Ob 8,5 pt als kleinste Stufe für ein Vorleseblatt trägt (O4).
Trägt sie nicht, ist der Boden höher zu setzen — dann bekommen mehr Ehrungen eine
Fortsetzungsseite, was der bewussten Abwägung „lieber zwei lesbare Blätter als eines mit
Kleingedrucktem" entspricht.

---

## P — „Mein Event": persönliches Dashboard über den QR-Code

Am 10.08. gebaut. Teilnehmende scannen den QR-Code auf ihrem Armband und sehen **ohne Anmeldung**
ihre eigenen Läufe, Ergebnisse und die freigegebenen Bedingungen. Entwurf:
`docs/superpowers/specs/2026-08-09-mein-event-design.md`.

**Vorbereitung, ohne die kein Fall dieses Blocks läuft:**

1. **Mindestens ein Teilnehmerband zuordnen.** QR-App → Zuordnung, oder direkt in `qr_codes` eine
   Zeile mit `participant` und `event`. Ohne zugeordneten Code zeigt der Reiter nur den Hinweistext.
2. **Mindestens eine Bedingung freigeben.** `participant_requirement.publicly_visible` steht nach
   der Migration auf „aus" — im Bedingungs-Editor das Häkchen „Für Teilnehmende sichtbar (Mein
   Event)" setzen. **Ohne dieses Häkchen ist der Bedingungsblock leer, und das ist kein Fehler.**
3. Zwei Bänder derselben Veranstaltung bereithalten (für P6–P8) und eines aus einer **anderen**
   Veranstaltung (P3).

**Am 10.08. bereits an einer laufenden Instanz belegt** (Agent, eigener Stand auf `:5131` gegen ein
Backend auf `:8131`, Seed `7e57` mit zwei Booten): P1, P2, P3, P4, P5, P6, P7, P9, P10, P12, P13,
P14 und P16. Das ersetzt den Durchgang eines Menschen nicht — insbesondere sind Aussehen und
Lesbarkeit auf einem echten Telefon nirgends geprüft.

| ID | Fall | Erwartung | testbar ab | Nachweis |
|---|---|---|---|---|
| P1 | Einstieg über den Code | `/results/{qrCode}` öffnet die Ergebnisseite mit aktivem Reiter „Mein Event". Die Adresszeile lautet danach `/results/event/{eventId}?tab=my-event` und enthält den Code **nicht** mehr | `f3439c15` | `AGENT 10.08.` |
| P2 | Code überlebt den Neustart | Browser schließen, `/results/event/{eventId}` direkt aufrufen, Reiter öffnen: die Daten sind ohne erneutes Scannen da | `f3439c15` | `AGENT 10.08.` |
| P3 | Fremde Veranstaltung | Ein Band aus Veranstaltung A an Veranstaltung B: 404 mit derselben Meldung wie ein unbekannter Code — die Antwort darf nicht verraten, dass es den Code gibt | `f3439c15` | `AGENT 10.08.` |
| P4 | Helferband | Ein Band, das an einem Helferkonto hängt, wird **nicht** gemerkt und führt ohne Reiter auf die Ergebnisseite. Kein hängengebliebenes „Mein Event", das nur Fehler zeigen kann | `f3439c15` | `AGENT 10.08.` (Endpunkt), Oberfläche offen |
| P5 | Ohne Anmeldung | Alles im privaten Fenster, ohne jede Anmeldung. Gegenprobe: der Aufruf mit `curl` ohne Cookie liefert dieselben Daten | `f3439c15` | `AGENT 10.08.` |
| P6 | Zweites Band auf demselben Gerät | Nach dem Scannen des zweiten Bandes steht **die zuletzt gescannte Person** oben — nicht die erste. Der Umschalter zeigt beide Namen | `f3439c15` | `AGENT 10.08.` |
| P7 | Umschalten | Über den Umschalter zur ersten Person wechseln: Name, Verein, Läufe und Bedingungen wechseln vollständig mit | `f3439c15` | `AGENT 10.08.` |
| P8 | Umschalter bleibt ruhig | Beim Öffnen darf der aktive Knopf nicht ~1 s später an eine andere Stelle springen (der Anzeigename wird nachgetragen). Auf dem Telefon tippt man sonst daneben | `f3439c15` | |
| P9 | Entfernen bei genau einem Eintrag | Auch mit nur **einem** gemerkten Band ist „Eintrag entfernen" erreichbar. Sonst bleibt auf einem geliehenen Telefon dauerhaft ein fremdes Dashboard stehen | `f3439c15` | `AGENT 10.08.` |
| P10 | Ohne gescannten Code | Ergebnisseite im privaten Fenster: der Reiter ist **da** und zeigt „Scanne den QR-Code auf deinem Band…" — er wird nicht ausgeblendet, sonst erfährt niemand von der Funktion | `f3439c15` | `AGENT 10.08.` |
| P11 | Challenge-Veranstaltung | Bei einer Challenge-Veranstaltung erscheint der Reiter **nicht** (dort gelten Verein/relativ/einzeln) | `f3439c15` | |
| P12 | Offene Pflichtbedingung | Ist eine freigegebene, nicht freiwillige Bedingung offen, steht ganz oben ein Band mit ihrem Namen und dem Verweis auf die Meldestelle | `f3439c15` | `AGENT 10.08.` |
| P13 | Bedingung abhaken | Nach dem Abhaken verschwindet das Band, die Liste bleibt weiter unten mit „erledigt" stehen | `f3439c15` | `AGENT 10.08.` |
| P14 | Nicht freigegebene Bedingung | Eine Bedingung **ohne** Häkchen taucht nirgends auf — weder im Band noch in der Liste | `f3439c15` | `AGENT 10.08.` |
| P15 | Notiz bleibt drin | Zu einer freigegebenen Bedingung in der Meldestelle eine Freitext-Notiz erfassen: sie erscheint **nirgends** im Dashboard. Gegenprobe im JSON der Antwort | `f3439c15` | |
| P16 | Abgemeldetes Boot | Eine Meldung für die Runde abmelden, **bevor** der Lauf beendet ist: die Karte zeigt „Abgemeldet · Grund", die Startzeit ist durchgestrichen, es läuft **kein** Countdown | `f3439c15` | `AGENT 10.08.` |
| P17 | Überfälliger Lauf | Ein eigener Lauf, der gefahren, aber nie beendet wurde: er darf die Karte „Dein nächster Lauf" **nicht** besetzen. Der echte nächste Lauf steht oben, der überfällige rutscht ans Ende — dieselbe 30-Minuten-Nachfrist wie A15 | `f3439c15` | |
| P18 | Ergebnis erscheint gleichzeitig | Lauf beenden: das Ergebnis erscheint im Dashboard und auf `/board` im selben Takt. Bei Voreinstellung „nur beendete Läufe" vorher in **keiner** der beiden Ansichten (vgl. A23) | `f3439c15` | |
| P19 | Gemeldet, noch kein Lauf | Vor der Auslosung steht die Meldung unter „Gemeldet, noch nicht terminiert" und verschwindet dort, sobald der Lauf gesetzt ist | `f3439c15` | `AGENT 10.08.` (beide Richtungen) |
| P20 | Zurückgezogene Meldung | Eine vor der Auslosung zurückgezogene Meldung erscheint **nicht** unter „Gemeldet, noch nicht terminiert" | `f3439c15` | |
| P21 | Nur die eigenen Läufe | Im Dashboard stehen ausschließlich Läufe, in deren Mannschaft die Person sitzt — keine fremden Boote desselben Laufs, auch nicht deren Zeiten | `f3439c15` | `AGENT 10.08.` |
| P22 | Rollengebundene Bedingung | Eine freigegebene Bedingung, die an eine Rolle gebunden ist, erscheint **nur** bei einer Person mit dieser Rolle — nicht bei allen als „offen" | `f3439c15` | |
| P23 | Keine Suchmaschine | Quelltext der Ergebnisseite enthält `<meta name="robots" content="noindex, nofollow">`, auch auf `/results/{qrCode}` und der Auswahlseite | `f3439c15` | `AGENT 10.08.` |
| P24 | Telefon-Layout | Auf einem echten Telefon: Blöcke untereinander, Namen brechen um, keine waagerechte Rolle, Countdown und „Abgemeldet" gut lesbar. **Der einzige Fall, den nur ein Mensch mit Gerät beantworten kann** | `f3439c15` | |
| P25 | Privater Modus von Safari | Im privaten Modus wirft `localStorage` schon beim Lesen. Die Ergebnisseite muss vollständig funktionieren, der Reiter zeigt dann dauerhaft den Hinweistext — nichts darf weiß bleiben | `f3439c15` | |
| P26 | Reiter-Unterstrich | Beim Einstieg über den Code steht der Unterstrich unter „Mein Event", nicht unter „Ergebnisse". In der kopflosen Browseransicht nicht messbar gewesen — **am Gerät nachsehen** | `f3439c15` | |
| P27 | Verbindungsverlust | Backend anhalten: der letzte gute Stand bleibt stehen, es erscheint nie fälschlich „kein Lauf eingetragen" | `f3439c15` | |

**Nicht in diesem Block:** Ersatzleute. Auf diesem Stand sieht eine für eine Runde eingewechselte
Person ihren Lauf nicht, und wer ausgewechselt wurde, sieht ihn weiterhin. Das wird auf dem Zweig
`claude/mein-event-ersatzleute` nachgereicht — sobald der hier liegt, gehören zwei Fälle dazu
(eingewechselt sieht den Lauf, ausgewechselt nicht mehr) und die Aufstellung in der Karte muss die
gefahrene sein, nicht die gemeldete.

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
- **Platzgrenze der Urkunden bei Kategoriewertung (L17).** Der gedruckte Platz und die Grenze
  „bis Platz 3" bleiben beide wettkampfweit — bewusst so entschieden. Für eine Regatta, in der die
  Kategorien getrennte Wertungen *sind*, kann das falsch wirken: die Breitensportwertung bekommt
  dann womöglich gar keine Urkunde, weil ihre besten Boote im Gesamtfeld hinter Platz 3 liegen. Vor
  der Regatta entscheiden, sonst fehlen am Tag Urkunden.
- **Laufender Lauf ohne Abschnitte (L9).** Die Schiedsrichter-Karte und der Live-Tab der
  öffentlichen Seite gruppieren erst, wenn gewertet wird. Falls die Schiedsrichter die Trennung
  schon während des Laufs erwarten, ist das eine Änderung — am Testtag ansehen und entscheiden.
- **Wer pflegt den Prüfungsschweregrad? (I8).** Ohne optimistisches Sperren überschreiben zwei
  gleichzeitige Bearbeiter sich kommentarlos. Entweder es wird gesperrt oder es gilt organisatorisch:
  einer pflegt.
- **Wer darf von Hand ein-/auschecken? (K1).** Die Funktion hängt an `UPDATE LIVE_DASHBOARD` **oder**
  `UPDATE EVENT` — bewusst an vorhandenen Rechten, damit in der laufenden Veranstaltung nichts
  nachkonfiguriert werden muss. Damit kann sie aber jeder, der das Dashboard bedient. Am Testtag
  gegenprüfen, ob das der gewünschte Kreis ist; wenn nicht, braucht es doch ein eigenes Privileg —
  und dann muss es der Schiedsrichter-Rolle **vor** dem 14.08. zugewiesen werden.
- **Reset gegen Handeintrag (C34).** Ein zurückgesetztes Rennen löscht die Ergebnisse der Boote,
  die im Feed stehen — auch die, die jemand von Hand eingetragen hat. Die Automatik schützt sich
  davor selbst (Handeingabe pausiert den Lauf), der Knopf und die wieder aufgenommene Automatik tun
  es nicht. Am Testtag entscheiden, ob das reicht oder ob ein zurückgesetztes Rennen vor dem
  Löschen nachfragen soll. Solange nichts nachfragt, gilt organisatorisch: nach einer Handeingabe
  die Automatik erst wieder aufnehmen, wenn RaceClocker den Lauf tatsächlich neu gefahren hat.
- **Löschen fehlt bewusst (K).** Ein falscher Eintrag wird korrigiert, nicht getilgt. Wenn am
  Testtag ein Fall auftaucht, in dem ein Eintrag ersatzlos weg muss (etwa eine komplett falsche
  Person), gibt es dafür heute keinen Weg außer SQL — vor der Regatta entscheiden, ob das reicht.
- **Codeformat der Armbänder (Block N).** Der Entwurf zu „Mein Event" setzt voraus, dass
  `qr_code_id` nicht erratbar ist. Das Feld wird aber nirgends erzeugt, sondern beim Zuordnen vom
  **aufgedruckten** Band übernommen — was drinsteht, entscheidet der Lieferant. Bis hierher war das
  folgenlos, weil ein anonymer Aufruf nur die Veranstaltungs-Kennung zurückgab; jetzt gibt derselbe
  Code Name, Verein, Läufe, Ergebnisse und Bedingungsstand heraus. Bei RKF ist es eine UUID (122 Bit,
  unproblematisch). **Vor dem ersten Einsatz nachsehen, was auf den Bändern der CRF steht.** Sind es
  kurze oder laufende Nummern, braucht der Pfad eine engere Rate-Limit-Gruppe als `publicInfo`
  (dort sind 500 Anfragen je 5 s und Gegenstelle erlaubt, bewusst hoch für hunderte Telefone hinter
  einer IP).

## Nicht in diesem Katalog

- Meldewesen, Rechnungen und die übrige Dokumentenerzeugung (eigene Stränge, teils eigene
  Worktrees). Urkunden stehen seit `4b98dd09` unter G, weil sie auf dem Sammelbranch liegen.
- Platzberechnung bei Zeitgleichheit (Rechenlogik, durch Unit-Tests gedeckt).
- Lastverhalten unter echter Zuschauerzahl — es gibt Cache, Takt-Untergrenze und Rate-Limit, aber
  keinen Messwert.
