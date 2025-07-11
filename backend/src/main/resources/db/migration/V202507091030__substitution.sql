set search_path to ready2race, pg_catalog, public;

create table substitution
(
    id                       uuid primary key,
    competition_registration uuid references competition_registration,
    competition_setup_round  uuid references competition_setup_round on delete cascade,
    participant_out          uuid references participant,
    participant_in           uuid references participant,
    reason                   text,
    order_for_round          bigint    not null,
    named_participant        uuid references named_participant,
    created_at               timestamp not null,
    created_by               uuid      references app_user on delete set null,
    updated_at               timestamp not null,
    updated_by               uuid      references app_user on delete set null
);

create unique index order_for_round_unique_in_round on substitution (competition_setup_round, order_for_round);