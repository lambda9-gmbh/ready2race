set
search_path to ready2race, pg_catalog, public;

create table participant_requirement
(
    id          uuid primary key,
    name        text      not null unique,
    description text,
    optional    boolean   not null,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table event_has_participant_requirement
(
    event                   uuid      not null references event on delete cascade,
    participant_requirement uuid      not null references participant_requirement on delete cascade,
    created_at              timestamp not null,
    created_by              uuid      references app_user on delete set null,
    primary key (event, participant_requirement)
);

create table participant_has_requirement_for_event
(
    participant             uuid      not null references participant on delete cascade,
    event                   uuid      not null references event on delete cascade,
    participant_requirement uuid      not null references participant_requirement on delete cascade,
    created_at              timestamp not null,
    created_by              uuid      references app_user on delete set null,
    primary key (participant, event, participant_requirement)
);
