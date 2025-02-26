set search_path to ready2race, pg_catalog, public;

create table sequence
(
    consumer text primary key,
    value    bigint not null,
    step     int    not null
);

create table invoice
(
    id             uuid primary key,
    invoice_number text      not null,
    billed_to      text      not null,
    payment_due_by timestamp not null,
    created_at     timestamp not null,
    created_by     uuid      references app_user on delete set null
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

create table invoice_document
(
    invoice    uuid primary key references invoice on delete cascade,
    name       text      not null,
    created_at timestamp not null
);

create index on invoice_document (invoice);

create table invoice_document_data
(
    invoice_document uuid primary key references invoice_document on delete cascade,
    data             bytea not null
);

create index on invoice_document_data (invoice_document);