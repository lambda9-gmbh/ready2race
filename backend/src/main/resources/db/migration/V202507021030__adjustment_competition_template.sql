alter table competition_template
    add column competition_setup_template uuid references competition_setup_template on delete set null;

drop view if exists competition_view;
drop view if exists competition_for_club_view;
drop view if exists competition_public_view;

alter table competition
    drop column template;