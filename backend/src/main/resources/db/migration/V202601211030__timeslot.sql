create table timeslot
(
    id          uuid primary key,
    event_day   uuid      not null references event_day,
    name        text      not null,
    description text,
    start_time  time      not null,
    end_time    time      not null,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

alter table competition_properties
    add match_duration      integer,
    add match_gaps_duration integer;