# Backlog: Persönliche Daten hinter einer Anmeldung

**Stand:** 2026-08-10
**Status:** Idee, bewusst zurückgestellt
**Auslöser:** Frage beim Abschluss von „Mein Event"
(`2026-08-09-mein-event-design.md`): Sollten die persönlichen Informationen hinter eine
Anmeldung wandern, mit Registrierung für Athleten?

## Entscheidung für den Moment: nein

Was „Mein Event" heute zeigt, ist zu weiten Teilen ohnehin öffentlich: Name, Verein, Lauf,
Startposition, Startzeit, Platz und Zeit stehen in Startliste, Meldeergebnis und auf der
Athleten-Anzeige. Eine Anmeldung würde die Reibung genau dort einbauen, wo das Konzept lebt —
Band scannen, hinsehen, weiterrudern — und dafür kaum etwas zusätzlich schützen. Die einzige
echte Aussage *über* eine Person, der Bedingungsstatus, hängt bereits an
`participant_requirement.publicly_visible`, das der Veranstalter je Bedingung umlegen muss.

## Was es schon gibt

Mehr als erwartet:

- Vollständiges Konto-System: Registrierung mit E-Mail-Bestätigung, Anmeldung, Passwort
  zurücksetzen, Einladungen, Rollen und Privilegien.
- **Selbstanmeldung für Athleten**, pro Veranstaltung über `event.participant_self_registration`
  freischaltbar. Wer sich so anmeldet, kann im selben Zug Rennen melden
  (`app_user_registration_competition_registration`), braucht die Freigabe eines
  Vereinsvertreters (`app_user_club_representative_approval`), und es entsteht dabei automatisch
  eine Teilnehmerzeile — `AppUserService.kt:496`.
- `global_configurations.create_club_on_registration` steuert, ob dabei ein Verein entsteht.

## Was fehlt: das Bindeglied

`participant` hat **keine** Spalte für ein Konto (`V202502140000__event_registration.sql`). Selbst
bei der Selbstanmeldung bleibt die Verbindung zufällig: `created_by` und eine übereinstimmende
E-Mail.

Das ist die eigentliche Hürde, und sie ist größer als sie aussieht: Bei einer Regatta meldet **der
Verein**. Die Meldestelle tippt Namen in die Meldung; diese Menschen haben kein Konto und werden
auch keines anlegen. Eine reine Anmeldelösung sperrt damit den Großteil der Athleten aus, statt
sie zu schützen.

## Wann es richtig wird

Sobald wirklich Privates dazukommt oder etwas geschrieben werden soll:

- Rechnungen und Gebühren, Kontaktdaten, hochgeladene Dokumente, Check-in-Historie
- sich selbst abmelden, Anwesenheit bestätigen, eine Bahnänderung quittieren

Dafür reicht ein Bändchen-Code nicht: Er kann weitergegeben werden, und niemand kann ihn
widerrufen.

## Vorgeschlagener Weg (zweistufig)

Der QR-Code bleibt der Einstieg und zeigt weiter das, was auch auf der Anzeige steht. Wer mehr
sehen oder etwas tun will, meldet sich an — und **der QR-Code ist der Beweis, dass die
Teilnehmerzeile ihm gehört**: „Das bin ich" nach dem Scan schreibt die Zuordnung. Damit löst der
Code genau das Problem, an dem eine Selbstzuordnung sonst scheitert: woher das System weiß,
welche der drei „M. Müller" die richtige ist.

Nötig wären:

- Spalte `app_user` an `participant` samt Eindeutigkeitsregel je Veranstaltung
- ein Zuordnungsendpunkt („Code beanspruchen"), der nur mit gültigem, noch nicht beanspruchtem
  Code greift
- die Entscheidung, welche Inhalte nur angemeldet sichtbar sind

## Vor der Umsetzung zu klären

- Was genau soll hinter die Anmeldung, was bleibt offen?
- Was passiert, wenn zwei Menschen denselben Code beanspruchen — gewinnt der erste, oder muss die
  Meldestelle entscheiden?
- Jugendliche ohne eigene E-Mail-Adresse: Eltern-Konto, Vereins-Konto, oder gar nicht?
- Was passiert mit der Zuordnung nach der Veranstaltung — bleibt sie über Regatten hinweg bestehen?
