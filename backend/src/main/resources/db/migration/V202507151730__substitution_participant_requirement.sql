set search_path to ready2race, pg_catalog, public;

create table substitution_has_participant_requirement
(
    substitution            uuid references substitution,
    participant_requirement uuid      not null references participant_requirement,
    requirement_approved    boolean   not null,
    primary key (substitution, participant_requirement)
);
