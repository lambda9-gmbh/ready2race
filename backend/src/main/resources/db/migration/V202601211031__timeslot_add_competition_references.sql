alter table timeslot
    add column if not exists competition_reference uuid references competition on delete set null,
    add column if not exists round_reference uuid references competition_setup_round on delete set null,
    add column if not exists match_reference uuid references competition_setup_match on delete set null;
