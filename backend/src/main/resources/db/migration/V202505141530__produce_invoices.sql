set search_path to ready2race, pg_catalog, public;

drop table invoice_document_data;
drop table invoice_document;
drop table invoice_position;
drop table invoice;

create table invoice
(
    id                     uuid primary key,
    invoice_number         text      not null,
    filename               text      not null,
    billed_to_name         text      not null,
    billed_to_organization text,
    payment_due_by         timestamp not null,
    payee_holder           text      not null,
    payee_iban             text      not null,
    payee_bic              text      not null,
    payee_bank             text      not null,
    created_at             timestamp not null,
    created_by             uuid      references app_user on delete set null
);

create unique index on invoice (invoice_number);

create table invoice_position
(
    invoice     uuid           not null references invoice on delete cascade,
    position    int            not null,
    item        text           not null,
    description text,
    quantity    decimal(10, 2) not null,
    unit_price  decimal(10, 2) not null,
    primary key (invoice, position)
);

create index on invoice_position (invoice);

create table invoice_document_data
(
    invoice uuid primary key references invoice on delete cascade,
    data    bytea not null
);

create table contact_information
(
    id uuid primary key,
    name text not null,
    address_zip text not null,
    address_city text not null,
    address_street text not null,
    email text not null,
    created_at timestamp not null,
    created_by uuid references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid references app_user on delete set null
);

create table contact_information_usage
(
    contact_information uuid not null references contact_information on delete cascade,
    event uuid references event on delete cascade,
    assigned_at timestamp not null,
    assigned_by uuid references app_user on delete set null
);

create unique index on contact_information_usage (event) nulls not distinct;

create table produce_invoice_for_registration
(
    event_registration uuid primary key references event_registration,
    contact            uuid      not null references contact_information,
    payee              uuid      not null references bank_account,
    last_error_at      timestamp,
    last_error         text,
    created_at         timestamp not null,
    created_by         uuid references app_user
);

alter table event
    add column invoices_produced timestamp
;

create table event_registration_invoice
(
    event_registration uuid not null references event_registration on delete cascade,
    invoice            uuid not null references invoice on delete cascade
);

create unique index only_one_registration_per_invoice on event_registration_invoice (invoice);