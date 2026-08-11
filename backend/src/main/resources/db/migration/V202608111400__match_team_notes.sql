set search_path to ready2race, pg_catalog, public;

-- Schiedsrichter-Notizen je Boot in einem Lauf ("Boje berührt"), geteilt über das Live-Dashboard
-- (Nutzerentscheidung vom 11.08.2026). Reine Kommunikation zwischen Schiedsrichtern - KEINE
-- Wertung: der Ausscheidungsgrund (failed_reason) und die Notizen der Teilnahmebedingungen
-- bleiben bewusst getrennt davon.
--
-- Append-only: Einträge sind unveränderlich, eine Korrektur ist Löschen + neu anlegen. So braucht
-- es kein Sperren - schreiben zwei Schiedsrichter gleichzeitig, entstehen schlicht zwei Einträge,
-- und keiner überschreibt den anderen. Deshalb auch kein updated_at/updated_by.
create table match_team_note
(
    id                     uuid primary key,
    -- Das Boot im Lauf - derselbe Anker wie bei den Zwischenzeiten (competition_match_team_lap).
    -- Fällt der Lauf oder das Boot weg, gehen die Notizen mit.
    competition_match_team uuid      not null references competition_match_team on delete cascade,
    note                   text      not null,
    created_at             timestamp not null,
    -- Bleibt lesbar, wenn das Konto der Autorin gelöscht wird - die Notiz gehört zum Lauf,
    -- nicht zum Konto.
    created_by             uuid      references app_user on delete set null,
    constraint chk_match_team_note_not_blank check (btrim(note) <> '')
);

create index on match_team_note (competition_match_team);
