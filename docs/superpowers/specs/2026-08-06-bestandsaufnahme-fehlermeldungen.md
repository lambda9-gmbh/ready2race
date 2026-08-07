# Bestandsaufnahme: Fehlermeldungen ohne ErrorCode

**Stand:** 06.08.2026, **fortgeschrieben** nach der Überarbeitung von Zeitplan-Rest, Urkunden,
Durchführung und Live-Anzeigen.
**Zweck:** Entscheidungsgrundlage für Thomas — welche Bereiche der Fehlerkommunikation sind noch
offen? Die ursprüngliche Fassung war eine reine Liste; erledigte Bereiche sind seither mit ✅
markiert, das Mengengerüst ist nachgezogen.

**Was seither gemacht wurde** (vier Commits auf `feature/crf-2026`, Schwerpunkt auf den neuen
Features dieses Branches):

1. Zeitplan-Rest — die acht verbleibenden Zweige aus `EventScheduleError.kt`
2. Urkunden — `CertificateError`, `AwardCertificateError`, `GapDocumentTemplateError` (ohne WebDAV)
3. Durchführung — `SubstitutionError`, `CompetitionExecutionChallengeError` und vier gezielte Fälle
   aus `CompetitionExecutionError`
4. Live-Anzeigen — `LiveDashboardError`, `QrCodeError`, `ParticipantTrackingError`

Nicht angefasst und weiterhin offen: Stammdaten & Konfiguration, Benutzer/Rollen/Anmeldung,
Meldungen/Registrierung, WebDAV, Rechnungen, der Rest von `CompetitionExecutionError`.

## Das Muster, um das es geht

Ein Backend-Fehler trägt einen `ErrorCode`
(`backend/.../calls/responses/ErrorCode.kt`), das Frontend bildet ihn auf einen übersetzten Text ab,
mit einem Fallback für Unbekanntes. Vorbild:
`frontend/src/components/event/competition/registration/deregistrationError.ts`.

Fehlt der Code, passiert im Frontend eines von zwei Dingen:

1. Der rohe **englische Backend-Text** steht in der Oberfläche (z. B. beim Urkunden-Download).
2. Häufiger: die Meldung verschwindet in einem **Sammeltopf** — `common.error.unexpected`,
   `entity.add/edit/delete.error` oder ein fester Dialogtext. Der Nutzer erfährt, *dass* etwas nicht
   ging, nie *warum*. Das ist der eigentliche Verlust; er fällt weniger auf als englischer Text,
   kostet am Renntag aber genauso viel Zeit.

51 `*Error.kt`-Dateien unter `backend/src/main/kotlin/de/lambda9/ready2race/backend/app/` geprüft.
404er auf IDs, die die Oberfläche gar nicht anbietet, sind durchgehend ausgeklammert — die kann ein
Nutzer über die normale Bedienung nicht auslösen.

## Überblick

| Bereich | Zweige gesamt | nutzerauslösbar | ohne ErrorCode (vorher) | ohne ErrorCode (jetzt) |
| --- | ---: | ---: | ---: | ---: |
| Zeitplan (eventSchedule) | 22 | 19 | 8 | **0** ✅ |
| Durchführung/Ergebnisse | 62 | 60 | 36 | **18** |
| Meldungen/Registrierung | 39 | 33 | 18 | **18** |
| Urkunden & Dokumente | 27 | 20 | 19 | **6** (nur noch WebDAV) |
| Stammdaten & Konfiguration | 49 | 32 | 32 | **32** (0 % Abdeckung) |
| Rechnungen | 5 | 5 | 1 | **1** |
| Benutzer/Rollen/Anmeldung | 21 | 16 | 16 | **16** |
| Live-Anzeigen | 9 | 9 | 9 | **0** ✅ |
| **Summe** | **234** | **195** | **≈139** | **≈91** |

Zwei Zahlen haben sich gegenüber der ersten Fassung geändert, nicht nur durch die Arbeit:

- **Urkunden & Dokumente** waren mit 16 nutzerauslösbaren Zweigen zu niedrig angesetzt. Nachgezählt
  sind es 19 (Teilnahmeurkunde 5, Siegerurkunde 4, Dokumentvorlage 4 statt der einen namentlich
  genannten `InvalidFont`, WebDAV 6) — die reinen 404er aus `DocumentTemplateError`,
  `EventDocumentError` und `EventDocumentTypeError` bleiben wie gehabt draußen, `CertificateJobError`
  ebenso (Hintergrundjob, kein API-Fehler). Die Gesamtsumme steigt dadurch von ≈136 auf ≈139.
- **Zwei Zweige sind neu** und deshalb in „gesamt" mitgezählt: `AwardCertificateError.IsChallengeEvent`
  und `CompetitionExecutionError.MatchIsBye` (beide unten begründet). Beide tragen von Anfang an
  einen Code.

Vorbildlich sind jetzt: **Zeitplan** (19 von 19), **Live-Anzeigen** (9 von 9), **RaceClocker**
(8 von 8), **Abmeldung/CompetitionDeregistration** (5 von 5) und die **Urkunden** ohne WebDAV
(14 von 14). Bei **Rechnungen** fehlt weiterhin nur `NotFound`.

## Nach Bereich

### Zeitplan (eventSchedule) — ✅ erledigt

Verschieben und Excel-Import waren schon erledigt (`SCHEDULE_SHIFT_*`,
`SCHEDULE_IMPORT_DUPLICATE_ROWS`, `SPREADSHEET_*`, `FILE_ERROR`). Die acht verbliebenen Zweige
derselben Datei sind jetzt nachgezogen — `SCHEDULE_SLOT_*`, `SCHEDULE_SETUP_MATCH_ALREADY_PLANNED`,
`SCHEDULE_ROUND_*`, `SCHEDULE_COMPRESSION_IMPOSSIBLE`. Angezeigt über `scheduleError.ts`
(`slotActionErrorText`, `roundSkipErrorText`) in `EventSchedule.tsx`, `ScheduleSlotDialog.tsx` und
`CompetitionExecutionRound.tsx`.

Zwei Dinge sind dabei über das reine Anhängen von Codes hinausgegangen:

- `RoundNotMaterialized` / `RoundHasRunsToRace` teilten sich den Text `cancelRound.error`, obwohl
  der eine Fall „setz die Runde erst" heißt und der andere „diese Läufe müssen gefahren werden".
  `cancelRound.error` ist deshalb vom String zum Objekt geworden.
- `RoundHasRunsToRace` schickt jetzt `raceableMatchCount` in `details`; die Meldung sagt, wie viele
  Läufe noch anstehen, statt nur dass noch etwas offen ist.

`CompressionImpossible` behält zusätzlich den bisherigen Regex-Fallback auf den Freitext, damit eine
ältere Backend-Version weiterhin verständlich bleibt.

### Durchführung/Ergebnisse — teilweise erledigt, größter verbleibender Block

**Erledigt** (die geschlossenen Gruppen und die vier häufigsten Einzelfälle):

- ✅ `SubstitutionError.kt` — alle sieben Gründe (`SUBSTITUTION_*`). Hier war die Anzeige doppelt
  kaputt: in **de und da sind `add.error` und `delete.error` vertauscht** (add trägt ein Objekt mit
  `conflict`/`unexpected`, also den Texten des Löschens; delete einen String), während
  `Substitutions.tsx` `add.error` als String und `delete.error.conflict` als Objekt benutzte. In
  beiden Fällen stand deshalb der **rohe i18n-Schlüssel** in der Oberfläche. Beide Schlüssel sind
  jetzt wieder schlichte Sammelmeldungen, die konkreten Gründe liegen unter `substitution.error.*`.
- ✅ `CompetitionExecutionChallengeError.kt` — alle sieben Gründe (`CHALLENGE_*`). Nebenbefund
  behoben: `verifyChallengeTeamResult` und `deleteChallengeTeamResult` verwarfen ihren Fehler in
  `CompetitionRegistrationTeamTable.tsx` stillschweigend.
- ✅ `CompetitionExecutionError.kt` — die vier benannten Fälle `TeamsNotMatching`,
  `MatchResultsLocked`, `PlacesNotContinuous` (mit erwartetem und eingetragenem Platz in `details`)
  und `StartTimeManagedBySchedule`.
- ✅ **Neu abgespalten:** `MatchIsBye`. `MatchResultsLocked` trug zwei Bedeutungen — „Lauf gehört zu
  einer früheren Runde" und „Freilos, es gibt nichts einzutragen". Der zweite Fall las sich damit
  als „nur die aktuelle Runde ist bearbeitbar" und schickte den Nutzer in die falsche Richtung.
  RaceClocker trennt die beiden längst (`RACECLOCKER_MATCH_IS_BYE`); der Standardwert von
  `checkUpdateMatchResult(byeError = …)` zeigt jetzt ebenfalls auf den eigenen Fall.

**Offen bleiben ~18 Zweige**, alle aus `CompetitionExecutionError.kt` (Rundensetzung, Setup- und
Zustandsprüfungen wie `NoRoundsInSetup`, `AllRoundsCreated`, `RegistrationsNotFinalized`,
`NotEnoughTeamSpace`, `TeamWasPreviouslyDeregistered`, `NotInChallengeTimespan`, …), dazu je einer
aus `MatchResultImportConfigError` und `StartListConfigError`. `RaceClockerError.kt` ist
vollständig.

- Nebenfund, weiterhin offen: `PlaceAndTimeBothNull` scheint toter Code, wird nirgends mehr
  geworfen. Nur gemeldet, nicht gelöscht.

### Meldungen/Registrierung

- `CompetitionRegistrationError.kt` — 8 Zweige, **kein einziger Code**. Darunter die
  Kernvalidierungen `RatingCategoryMissing` (Z. 44) und `ParticipantOutOfAgeRestriction` (Z. 49),
  die in `entity.add.error` verschwinden.
- `EventRegistrationError.kt` — 10 von 19 sind schon sauber gemappt
  (`EventRegistrationCreatePage.tsx`); offen u. a. `SelfRegistrationNotAllowed` (Z. 121) und
  `DocumentsAlreadyAccepted` (Z. 136).
- `EventParticipantError.kt:21` `NoEmail` — trägt im Quelltext schon ein `//TODO: error-code`.
- ✅ Erledigt: der rohe englische Text in `ParticipantForEventTable.tsx` — die Stelle gehörte
  inhaltlich zum Urkunden-Download und ist dort behoben.

### Urkunden & Dokumente — ✅ erledigt bis auf WebDAV

- ✅ `CertificateError.kt` — alle fünf Gründe (`CERTIFICATE_*`). Das war die einzige Stelle mit
  **rohem englischem Text** in der Oberfläche: `ParticipantForEventTable.tsx` gab
  `feedback.error(error.message)` aus, also z. B. „No results in this event for this participant"
  mitten in der deutschen Anzeige.
- ✅ `AwardCertificateError.kt` — alle Gründe (`AWARD_CERTIFICATE_*`). Der Dialog unterschied vorher
  nur nach HTTP-Status; 409 hieß pauschal „keine nutzbare Vorlage", egal ob gar keine zugewiesen
  oder die zugewiesene kaputt ist. Der Verweis in die Konfiguration erscheint jetzt nur noch, wenn
  dort auch etwas zu tun ist.
- ✅ **Neu:** `AwardCertificateError.IsChallengeEvent` samt Prüfung in
  `AwardCertificateService.entriesForEvent` (Testfall **G18**). Ein Siegerurkunden-Download auf
  einem Challenge-Event antwortete bislang mit `NoResults` („No placed teams for these
  certificates") — das Ergebnis stimmte zufällig, die Begründung nicht. Ein Challenge-Event fährt
  keine Läufe und vergibt keine Plätze, dort gibt es grundsätzlich keine Siegerurkunden; wer „keine
  platzierten Teams" liest, wartet dagegen auf Ergebnisse, die nie kommen. Die Prüfung sitzt in
  `entriesForEvent` und deckt damit alle drei Granularitäten ab (Veranstaltung, Wettkampf,
  Einzelnachdruck).
- ✅ `GapDocumentTemplateError.kt` — die vier nutzerauslösbaren Gründe (`DOCUMENT_TEMPLATE_*`).
  `NotFound` bleibt bewusst ohne Code (404 auf eine ID, die die Oberfläche nicht anbietet).
- **Offen:** `WebDAVError.kt` — 10 Zweige, 6 klar nutzerauslösbar (`ConfigIncomplete` Z. 78,
  `ManifestNotFound` Z. 62), alle in einer Pauschalmeldung. Bewusst nicht angefasst.

`CertificateError.NoResults` war bis zu einem parallelen Fix **unerreichbar**:
`getByEventIdAndParticipantId` liefert über `database.Extensions.select` eine `List`, und
`.onNullFail` feuert auf einer nicht-nullbaren Liste nie — ein Teilnehmer ohne Ergebnis bekam HTTP
200 und eine Urkunde mit „0 m". Die Erreichbarkeit ist außerhalb dieser Arbeit behoben worden; der
Zweig hat hier seinen ErrorCode und seinen übersetzten Text bekommen.

### Stammdaten & Konfiguration — 0 % Abdeckung

Für diese Domänen enthält das `ErrorCode`-Enum bislang überhaupt nichts.

- `ParticipantError.kt:17` `ParticipantInUse` — Löschen blockiert, Grund unsichtbar.
- `CompetitionSetupError.kt:28/33` `CreatedRoundDeleted` / `CreatedRoundOrderChanged` — reguläre
  Bedienfehler beim Umbau eines Setups.
- `CompetitionCategoryError.kt:21`, `FeeError.kt:20` (`…InUse`) — liefern in `details` sogar die
  Liste der betroffenen Wettkämpfe mit; angezeigt wird sie nicht.
- `EventDayError.kt:22` `CompetitionsNotFound` — ebenso mit ungenutzter `details`-Liste.
- `CatererError.kt:13` `InvalidPrice`. Nebenfund: die zugehörige Validierung in
  `CatererTransactionRequest.kt:13` ist toter Code (`Valid` fest verdrahtet).
- ✅ Erledigt: `ParticipantError.kt:19` `UnknownGenderValue`. Der Grund war nicht der Switch allein
  — `PARTICIPANT_IMPORT_UNKNOWN_GENDER_VALUE` stand **gar nicht in `documentation.yaml`** und fehlte
  deshalb auch im generierten `ErrorCode`-Typ des Frontends; der Fall war dort schlicht nicht
  benennbar. Nachgetragen an allen drei Stellen, dazu reist der beanstandete Wert jetzt in `details`
  mit (sonst sucht man ihn in einer Datei mit hunderten Zeilen von Hand).

### Benutzer/Rollen/Anmeldung

`AuthError` (4), `RoleError` (2), `TaskError` (2), `WorkShiftError` (2), `WorkTypeError` (1),
`AppUserError` (4 von 7) ohne Code.

- `TaskError.kt:17`, `WorkShiftError.kt:17` `AssignedAClubRepresentative` → „Löschen fehlgeschlagen".
- `RoleError.kt:32` `Static` — dass die Rolle systemseitig fixiert ist, erfährt niemand.
- `AuthError.kt:15` `CredentialsIncorrect` funktioniert nur, weil zufällig kein zweiter 401-Fall
  existiert: die Zuordnung hängt am Status, nicht an einem Code.

### Live-Anzeigen — ✅ erledigt

Die Zuordnung liegt als eigenes Modul `liveDashboardError.ts` neben den Dashboard-Komponenten; in
`LiveDashboardPage.tsx` ist bewusst nur die eine Zeile angefasst, die die Meldung zeigt (am
Schiedsrichter-Dashboard wurde parallel gearbeitet).

- ✅ `LiveDashboardError.FinishReservedForOffice` (`LIVE_DASHBOARD_FINISH_RESERVED_FOR_OFFICE`) —
  der häufigste Fall am Steg und **gar keine Störung**: die Veranstaltung steht auf
  `chainProgressionMode = REGATTABUERO`, dort beendet das Büro über den Zeitplan. „Der Lauf konnte
  nicht geändert werden" las sich wie ein Defekt, also probierte man es erneut.
- ✅ `QrCodeError.QrCodeAlreadyInUse` (`QR_CODE_ALREADY_IN_USE`) — die Meldung klang nach Scanfehler
  und schickte die Helfer dazu, noch einmal zu scannen; gebraucht wird ein anderes Bändchen.
- ✅ `ParticipantTrackingError` — alle vier Gründe (`TRACKING_*`). „Ist schon eingecheckt" ist dabei
  kein Fehler, sondern die Auskunft, dass nichts mehr zu tun ist.
- Nebenfund, weiterhin offen: `QrCodeError.QrCodeNotFound` ist toter Code (Aufruf in
  `QrCodeAppService.kt:25` auskommentiert) und bleibt deshalb bewusst ohne ErrorCode. Nur gemeldet,
  nicht gelöscht.

### Rechnungen

Fast fertig: nur `InvoiceError.NotFound` (Z. 22) ohne Code, die vier Hauptfälle sind gemappt.
`ProduceInvoiceError` ist kein API-Fehler — `MissingRecipient` / `NoPositions` erreichen den Nutzer
nie, der Hintergrundjob hängt still fest und `EventInvoicesInfoDto` meldet weiter „läuft noch". Das
ist keine ErrorCode-Lücke, sondern eine Sichtbarkeitslücke.

## Wo der Hebel am größten wäre

Die ersten vier Punkte der ursprünglichen Reihenfolge sind abgearbeitet — Live-Anzeigen,
Zeitplan-Rest, die geschlossenen Gruppen der Durchführung und die Urkunden ohne WebDAV. Was bliebe,
in dieser Reihenfolge:

1. **Rest von `CompetitionExecutionError`** (~18 Fälle) — Rundensetzung und Setup-Prüfungen. Weniger
   renntagskritisch als die Ergebniserfassung, weil vieles davon vor dem Renntag passiert.
2. **Meldungen/Registrierung** (18 Fälle) — `CompetitionRegistrationError.kt` hat weiterhin keinen
   einzigen Code, darunter die Kernvalidierungen `RatingCategoryMissing` und
   `ParticipantOutOfAgeRestriction`.
3. **WebDAV** (6 Fälle) — die Restmenge der Urkunden & Dokumente.
4. **Benutzer/Rollen/Anmeldung** (16 Fälle) — hier ist vor allem `AuthError.CredentialsIncorrect`
   auffällig: die Zuordnung hängt am Status, nicht an einem Code, und funktioniert nur, solange
   zufällig kein zweiter 401-Fall existiert.
5. **Stammdaten** (~32 Fälle) — die meisten Fälle, aber nicht renntagskritisch; hier ist der
   Sonderfall interessant, dass mehrere Fehler ihre `details`-Liste schon mitschicken und sie nur
   niemand anzeigt.

Drei Fundstellen toten Codes bleiben stehen — **nur gemeldet, nicht gelöscht**:
`CompetitionExecutionError.PlaceAndTimeBothNull` (wird nirgends mehr geworfen),
`QrCodeError.QrCodeNotFound` (Aufruf in `QrCodeAppService.kt:25` auskommentiert) und
`CatererTransactionRequest.kt:13` (`Valid` fest verdrahtet).

## Was das Muster inzwischen umfasst

Für neue Fälle gibt es vier Frontend-Module nach demselben Muster (Code → i18n-Key, Fallback für
Unbekanntes, Test daneben, der Zuordnung **und** Existenz aller Keys in de/en/da prüft):

| Modul | deckt ab |
| --- | --- |
| `components/event/competition/registration/deregistrationError.ts` | Abmeldung |
| `components/event/schedule/scheduleError.ts` | Verschieben, Import, Slot-Aktionen, Runde entfällt |
| `components/certificate/certificateError.ts` | Teilnahme- und Siegerurkunden, Dokumentvorlagen |
| `components/event/competition/excecution/executionError.ts` | Ummeldungen, Challenge, Ergebniserfassung |
| `components/event/liveDashboard/liveDashboardError.ts` | Dashboard, Bändchen-Ausgabe, Check-in |

Zwei Fallstricke, die dabei aufgefallen sind und beim nächsten Block Zeit sparen:

- **Ein neuer `ErrorCode` muss an drei Stellen stehen:** `ErrorCode.kt`, der `ErrorCode`-Enum in
  `backend/src/main/resources/openapi/documentation.yaml` **und** `frontend/src/api/types.gen.ts`.
  Fehlt die zweite, ist der Fall im Frontend nicht einmal benennbar — genau das war beim
  Teilnehmer-Import passiert.
- **Aus einem i18n-String ein Objekt zu machen (oder umgekehrt) bricht still.** Wo Code und
  Übersetzung auseinanderlaufen, zeigt die Oberfläche den rohen Schlüssel; bei den Ummeldungen ist
  das über zwei Sprachen und zwei Meldungen unbemerkt geblieben. Der Existenz-Teil der Modultests
  fängt genau das ab.
