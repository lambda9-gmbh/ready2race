alter table competition_template
    add column competition_setup_template uuid references competition_setup_template on delete set null;