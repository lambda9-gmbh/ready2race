set search_path to ready2race, pg_catalog, public;

create table race_category
(
    name text primary key
);

create table participant_count
(
    id               uuid primary key default gen_random_uuid(),
    count_males      integer not null default 0,
    count_females    integer not null default 0,
    count_non_binary integer not null default 0,
    count_mixed      integer not null default 0,
    constraint chk_count_sum_greater_0 check (count_males + count_females + count_non_binary + count_mixed > 0)
);

create table race_properties
(
    id                uuid primary key default gen_random_uuid(),
    identifier        text           not null,
    name              text           not null,
    short_name        text,
    description       text,
    participants      uuid references participant_count on delete cascade on update cascade,
    participation_fee decimal(10, 2) not null default 0,
    rental_fee        decimal(10, 2) not null default 0,
    race_category     text references race_category on delete set null on update cascade
);

create table named_participant_role
(
    name        text primary key,
    description text,
    required    boolean not null
);

create table race_properties_named_participant_role
(
    properties        uuid not null references race_properties on delete cascade on update cascade,
    role              text not null references named_participant_role on delete cascade on update cascade,
    participant_count uuid not null references participant_count on delete cascade on update cascade,
    primary key (properties, role)
);

create table race_template
(
    id         uuid primary key default gen_random_uuid(),
    note       text,
    properties uuid      not null references race_properties on delete cascade on update cascade,
    created_at timestamp not null default now(),
    created_by uuid references app_user on update cascade,
    updated_at timestamp not null default now(),
    updated_by uuid references app_user on update cascade
);
create index on race_template (properties);

create table event
(
    id                          uuid primary key default gen_random_uuid(),
    name                        text      not null,
    description                 text,
    location                    text,
    registration_available_from timestamp,
    registration_available_to   timestamp,
    payment_due_date            timestamp,
    invoice_prefix              text,
    created_at                  timestamp not null default now(),
    created_by                  uuid references app_user on update cascade,
    updated_at                  timestamp not null default now(),
    updated_by                  uuid references app_user on update cascade
);

create table event_day
(
    id          uuid primary key default gen_random_uuid(),
    event       uuid      not null references event on delete cascade on update cascade,
    date        date      not null,
    name        text,
    description text,
    created_at  timestamp not null default now(),
    created_by  uuid references app_user on update cascade,
    updated_at  timestamp not null default now(),
    updated_by  uuid references app_user on update cascade
);
create index on event_day (event);

create table race_registration_offer
(
    id         uuid primary key default gen_random_uuid(),
    event      uuid not null references event on delete cascade on update cascade,
    properties uuid not null references race_properties on delete cascade on update cascade,
    template   uuid references race_template on delete set null on update set null
);
create index on race_registration_offer (event);

create table event_day_race_registration_offer
(
    event_day          uuid not null references event_day on delete cascade on update cascade,
    registration_offer uuid not null references race_registration_offer on delete cascade on update cascade,
    primary key (event_day, registration_offer)
);