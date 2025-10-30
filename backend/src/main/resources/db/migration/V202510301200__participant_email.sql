set search_path to ready2race, pg_catalog, public;

alter table participant
    add column email text
;

create table event_participant
(
    event uuid not null references event,
    participant uuid not null references participant,
    access_token char(30) not null
);

create unique index on event_participant (access_token);