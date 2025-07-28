set search_path to ready2race, pg_catalog, public;

create table if not exists qr_codes
(
    id          uuid primary key,
    qr_code_id  TEXT      NOT NULL,
    app_user    uuid      null references app_user on delete cascade,
    participant UUID      NULL references participant on delete cascade,
    event       UUID      NOT NULL references event on delete cascade,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    constraint chk_either_app_user_or_participant check (
        (app_user is null and participant is not null) or
        (app_user is not null and participant is null)
        ),
    constraint qr_code_unique UNIQUE (qr_code_id)
)