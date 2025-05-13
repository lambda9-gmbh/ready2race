set search_path to ready2race, pg_catalog, public;

create table bank_account
(
    id uuid primary key,
    holder text not null,
    iban varchar(34) not null,
    bic varchar(11) not null,
    bank text not null,
    created_at timestamp not null,
    created_by uuid references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid references app_user on delete set null
);