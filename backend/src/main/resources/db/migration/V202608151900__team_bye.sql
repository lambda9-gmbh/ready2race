set search_path to ready2race, pg_catalog, public;

-- Freilos für ein einzelnes Boot (Regattatag 15.08.2026): Ein Boot verpasst seinen Vorlauf, die
-- Schiedsrichter setzen es trotzdem in die Folgerunde. Bis hierher ging das nur, indem man ihm im
-- Vorlauf von Hand einen Platz eintrug, den es nie gefahren ist -- das verschob alle anderen Boote
-- um eine Position und stand so im Ergebnis.
--
-- Das Flag hängt am Boot IM LAUF (competition_match_team), nicht an der Meldung: Es ist eine
-- Entscheidung über diesen einen Vorlauf, und beim Löschen und Neu-Auslosen der Runde soll sie mit
-- verschwinden -- dieselbe Begründung wie bei competition_match.bye_must_race (V202608111800).
--
-- Nicht zu verwechseln mit dem strukturellen Freilos: das entsteht aus der Besetzung (ein Boot
-- allein in seinem Lauf einer nicht verpflichtenden Runde) und trägt seinen Namen am Lauf
-- (competition_match.bye_name). Dieses hier wird ausdrücklich vergeben.
--
-- Wirkung: Das Boot bleibt ohne Platz und ohne Zeit -- es hat nichts gefahren, was zu werten wäre --,
-- steigt beim Erzeugen der Folgerunde aber vor den platzierten Booten auf. Wer die Bahnverteilung
-- der Folgerunde danach anders haben will, tauscht sie dort (Bahnentausch).
alter table competition_match_team
    add column bye boolean not null default false;
