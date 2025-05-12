set search_path to ready2race, pg_catalog, public;

create type task_state as enum ('OPEN','IN_PROGRESS', 'DONE', 'CANCELED');

create table task
(
    id          uuid primary key,
    event       uuid       not null references event on delete cascade,
    name        text       not null,
    due_date    timestamp,
    description text,
    remark      text,
    state       task_state not null default 'OPEN',
    created_at  timestamp  not null,
    created_by  uuid       references app_user on delete set null,
    updated_at  timestamp  not null,
    updated_by  uuid       references app_user on delete set null
);

create table task_has_responsible_user
(
    task       uuid      not null references task on delete cascade,
    app_user   uuid      not null references app_user on delete cascade,
    primary key (task, app_user)
);
