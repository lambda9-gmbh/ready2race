set search_path to ready2race, pg_catalog, public;

-- Die Regeln, nach denen ein Vereinsname gekürzt wird (Nachtrag zum Entwurf 2026-08-09).
--
-- Bis hierher standen sie als zwei Listen im Kotlin-Code. "Ruderclub -> RC" ist aber Wissen über
-- eine Sportart, und ready2race ist keine Rudersoftware: eine Segel- oder Leichtathletik-Regatta
-- braucht andere Kürzel und darf sie nicht als Codeänderung beantragen müssen.
--
-- Die Regeln wirken ausschließlich auf die *angezeigte* Kurzform. Der Schlüssel, unter dem die
-- gepflegten Kurzformen hängen (club_short_name.name_key, siehe ClubNameKey), bleibt fest
-- einkompiliert: verschöbe ihn eine Regeländerung, verlöre jeder gepflegte Eintrag still seine
-- Zuordnung.
create table club_name_rule
(
    id          uuid      not null primary key,
    -- ABBREVIATION: term wird durch replacement ersetzt (Ruderverein -> RV)
    -- REMOVE_TERM:  term faellt weg (e.V.)
    -- REMOVE_YEARS / REMOVE_BRACKETED: strukturell, nicht als Wort ausdrückbar - deshalb ohne
    -- term. Eine solche Zeile ist ein Schalter: vorhanden heißt aktiv.
    kind        text      not null check (kind in ('ABBREVIATION', 'REMOVE_TERM', 'REMOVE_YEARS', 'REMOVE_BRACKETED')),
    term        text,
    replacement text,
    -- Die Reihenfolge ist inhaltlich, nicht kosmetisch: stünde "Verein" vor "Ruderverein", bliebe
    -- aus "Ruder-Verein" ein "Ruder-V" stehen.
    sort_order  integer   not null,
    created_at  timestamp not null,
    created_by  uuid references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid references app_user on delete set null,

    constraint chk_club_name_rule_term check (
        (kind in ('ABBREVIATION', 'REMOVE_TERM') and term is not null)
            or (kind in ('REMOVE_YEARS', 'REMOVE_BRACKETED') and term is null)
        ),
    constraint chk_club_name_rule_replacement check (
        (kind = 'ABBREVIATION' and replacement is not null) or (kind <> 'ABBREVIATION' and replacement is null)
        )
);

-- Ein Schalter kann nicht zweimal an sein.
create unique index club_name_rule_switch_unique on club_name_rule (kind) where term is null;

-- Ausgeliefert wird nur, was für jede Sportart gilt: die Rechtsformen und die beiden Schalter.
-- Die Vereinstyp-Kürzel des Rudersports liegen als Seed unter docs/seeds/ für Installationen, die
-- sie bisher aus dem Code bekommen haben.
insert into club_name_rule (id, kind, term, replacement, sort_order, created_at, updated_at)
values (gen_random_uuid(), 'REMOVE_TERM', 'e.V.', null, 10, now(), now()),
       (gen_random_uuid(), 'REMOVE_TERM', 'e. V.', null, 20, now(), now()),
       (gen_random_uuid(), 'REMOVE_TERM', 'eV', null, 30, now(), now()),
       (gen_random_uuid(), 'REMOVE_BRACKETED', null, null, 40, now(), now()),
       (gen_random_uuid(), 'REMOVE_YEARS', null, null, 50, now(), now());
