set search_path to ready2race, pg_catalog, public;

create table event_rating_category
(
    event                 uuid      not null references event on delete cascade,
    rating_category       uuid      not null references rating_category on delete cascade,
    year_restriction_from int       null,
    year_restriction_to   int       null,
    created_at            timestamp not null,
    created_by            uuid      references app_user on delete set null,
    updated_at            timestamp not null,
    updated_by            uuid      references app_user on delete set null,
    primary key (event, rating_category)
);

create index on event_rating_category (event, rating_category);