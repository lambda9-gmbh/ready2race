alter table timeslot
    add column if not exists description_manual text,
    add column if not exists description_auto text;

update timeslot
set description_manual = description
where description_manual is null;

alter table timeslot
    drop constraint if exists timeslot_competition_reference_fkey,
    drop constraint if exists timeslot_round_reference_fkey,
    drop constraint if exists timeslot_match_reference_fkey;

alter table timeslot
    add constraint timeslot_competition_reference_fkey
        foreign key (competition_reference) references competition on delete cascade,
    add constraint timeslot_round_reference_fkey
        foreign key (round_reference) references competition_setup_round on delete cascade,
    add constraint timeslot_match_reference_fkey
        foreign key (match_reference) references competition_setup_match on delete cascade;