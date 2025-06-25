set search_path to ready2race, pg_catalog, public;

create table work_type
(
    id          uuid primary key,
    name        text      not null,
    description text,
    color       text,
    min_user    integer   not null,
    max_user    integer,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table work_shift
(
    id         uuid primary key,
    work_type  uuid      not null references work_type on delete cascade,
    event       uuid      not null references event on delete cascade,
    time_from  timestamp not null,
    time_to    timestamp not null,
    min_user   integer   not null,
    max_user   integer,
    remark     text,
    created_at timestamp not null,
    created_by uuid      references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid      references app_user on delete set null
);

create table work_shift_has_user
(
    work_shift uuid not null references work_shift on delete cascade,
    app_user   uuid not null references app_user on delete cascade,
    primary key (work_shift, app_user)
);
