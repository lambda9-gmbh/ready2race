set search_path to ready2race, pg_catalog, public;

-- Die Export-Mappe einer Veranstaltung (Wunsch Regattabüro, 12.08.2026): eine sortierte Liste
-- aus hochgeladenen Veranstaltungs-Dokumenten und GENAU EINEM Platzhalter für die generierten
-- Startlisten. Der PDF-Sammelexport am Zeitplan hängt die Einträge in dieser Reihenfolge zu
-- einer Gesamtmappe zusammen - wie das handgebaute Meldeergebnis-Dokument der Vorjahre
-- (Hinweise, Regelwerk, Zeitplan vorn, Startlisten an der Platzhalter-Position).
create table event_export_bundle_item
(
    id         uuid primary key,
    event      uuid      not null references event on delete cascade,
    -- Sortierschlüssel innerhalb der Veranstaltung; geschrieben wird immer die ganze Liste
    -- (Zehnerschritte wie club_name_rule.sort_order), Eindeutigkeit erzwingt deshalb niemand.
    position   int       not null,
    kind       text      not null check (kind in ('DOCUMENT', 'GENERATED_STARTLISTS')),
    -- on delete cascade: ein gelöschtes Veranstaltungs-Dokument verschwindet auch aus der
    -- Mappe - ein Eintrag ohne Datei wäre beim Zusammenbau nur ein Loch.
    document   uuid      references event_document on delete cascade,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null,
    -- Genau die Dokument-Einträge tragen ein Dokument, der Platzhalter nie.
    constraint event_export_bundle_item_document_iff_kind
        check ((kind = 'DOCUMENT') = (document is not null))
);

-- Höchstens EIN Startlisten-Platzhalter je Veranstaltung - er markiert die Stelle der
-- generierten Startlisten, zwei Stellen gibt es nicht.
create unique index event_export_bundle_item_placeholder_unique
    on event_export_bundle_item (event)
    where kind = 'GENERATED_STARTLISTS';

-- Ein Dokument steht höchstens einmal in der Mappe (die Veranstaltung steckt im Dokument selbst).
create unique index event_export_bundle_item_document_unique
    on event_export_bundle_item (document)
    where document is not null;
