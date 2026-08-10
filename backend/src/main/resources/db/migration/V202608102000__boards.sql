set search_path to ready2race, pg_catalog, public;

-- Boards ersetzen die Info-View-Konfiguration: ein Board je Bildschirm, die gesamte
-- Struktur (Layout, Kacheln, Elemente) als ein JSON-Dokument. Kein Fremdschlüssel zeigt
-- aus der Konfiguration heraus; geschrieben wird sie immer im Ganzen (siehe Design-Doku
-- docs/superpowers/specs/2026-08-10-boards-design.md).
create table board
(
    id         uuid primary key,
    event_id   uuid      not null references event (id) on delete cascade,
    name       text      not null,
    config     jsonb     not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_board_event_id on board (event_id);

-- Für jedes Event mit aktiver Athleten-Anzeige ein Default-Board, das die bisherige
-- Bühne reproduziert (drei Spalten: Lauf 0 / Lauf +1 / Lauf −1). showCountdown und der
-- Poll-Takt werden übernommen; montierte Bildschirme laufen über die alte URL weiter
-- (das Frontend leitet auf das erste Board des Events um).
insert into board (id, event_id, name, config, created_at, updated_at)
select gen_random_uuid(),
       ivc.event_id,
       'Athleten-Anzeige',
       jsonb_build_object(
           'layout', 'THREE_COLUMNS',
           'refreshIntervalSeconds', greatest(coalesce(ivc.display_duration_seconds, 15), 10),
           'tiles', jsonb_build_array(
               jsonb_build_object('rotationIntervalSeconds', 10, 'elements', jsonb_build_array(
                   jsonb_build_object('type', 'MATCH', 'offset', 0,
                                      'showCrew', true,
                                      'showCountdown', coalesce((ivc.filters ->> 'showCountdown')::boolean, true),
                                      'showTimes', true, 'contrastColors', true, 'autoFit', true))),
               jsonb_build_object('rotationIntervalSeconds', 10, 'elements', jsonb_build_array(
                   jsonb_build_object('type', 'MATCH', 'offset', 1,
                                      'showCrew', true,
                                      'showCountdown', coalesce((ivc.filters ->> 'showCountdown')::boolean, true),
                                      'showTimes', true, 'contrastColors', true, 'autoFit', true))),
               jsonb_build_object('rotationIntervalSeconds', 10, 'elements', jsonb_build_array(
                   jsonb_build_object('type', 'MATCH', 'offset', -1,
                                      'showCrew', true,
                                      'showCountdown', coalesce((ivc.filters ->> 'showCountdown')::boolean, true),
                                      'showTimes', true, 'contrastColors', true, 'autoFit', true)))
           )
       ),
       now(),
       now()
from (select distinct on (event_id) *
      from info_view_configuration
      where view_type = 'ATHLETE_BOARD'
        and is_active
      order by event_id, sort_order) ivc;

drop table info_view_configuration;
drop type info_view_type;
