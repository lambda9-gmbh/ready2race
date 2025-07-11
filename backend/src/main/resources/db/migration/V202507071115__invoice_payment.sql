alter table invoice
    add column paid_at timestamp;

drop table produce_invoice_for_registration;

create table produce_invoice_for_registration
(
    event_registration uuid primary key references event_registration,
    contact_name text not null,
    contact_address_zip    text not null,
    contact_address_city   text not null,
    contact_address_street text not null,
    contact_email          text not null,
    payee_holder text not null,
    payee_iban varchar(34) not null,
    payee_bic varchar(11) not null,
    payee_bank text not null,
    last_error_at      timestamp,
    last_error         text,
    created_at         timestamp not null,
    created_by         uuid references app_user
);
