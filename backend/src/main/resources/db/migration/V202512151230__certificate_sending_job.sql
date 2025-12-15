create table certificate_of_event_participation_sending_job
(
    id uuid primary key,
    event uuid not null references event,
    participant uuid not null references participant
);