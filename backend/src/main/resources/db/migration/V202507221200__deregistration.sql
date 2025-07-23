set search_path to ready2race, pg_catalog, public;

create table competition_deregistration
(
    competition_registration uuid primary key references competition_registration,
    competition_setup_round  uuid references competition_setup_round on delete cascade,
    reason                   text,
    created_at               timestamp not null,
    created_by               uuid      references app_user on delete set null,
    updated_at               timestamp not null,
    updated_by               uuid      references app_user on delete set null
);

create unique index competition_registration_unique on competition_deregistration (competition_registration);