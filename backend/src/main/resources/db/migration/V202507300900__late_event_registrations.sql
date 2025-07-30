alter table event
    add column late_registration_available_to timestamp,
    add column late_invoices_produced timestamp,
    add column late_payment_due_by date
;

alter table competition_properties
    add column late_registration_allowed boolean not null default false
;

alter table competition_registration
    add column is_late boolean not null default false
;

alter table produce_invoice_for_registration
    drop constraint produce_invoice_for_registration_pkey
;

alter table produce_invoice_for_registration
    alter column event_registration set not null
;

alter table produce_invoice_for_registration
    add column id uuid not null default (gen_random_uuid()),
    add column mode text not null default 'REGULAR'
;

alter table produce_invoice_for_registration
    add primary key (id)
;

alter table competition_properties_has_fee
    add column lateAmount decimal(10,2)
;
