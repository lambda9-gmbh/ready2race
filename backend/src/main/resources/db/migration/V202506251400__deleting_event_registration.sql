alter table event_registration_invoice
    drop constraint event_registration_invoice_event_registration_fkey;

alter table event_registration_invoice
    add foreign key (event_registration) references event_registration;

alter table event_registration_invoice
    drop constraint event_registration_invoice_invoice_fkey;

alter table event_registration_invoice
    add foreign key (invoice) references invoice;