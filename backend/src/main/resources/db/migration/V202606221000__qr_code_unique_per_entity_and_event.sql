set search_path to ready2race, pg_catalog, public;

-- A qr code is scoped to an event (qr_codes.event). The app layer assumes at most
-- one code per entity (app_user or participant) per event, but until now nothing
-- enforced it. Multiple codes for the same entity+event produced duplicate rows in
-- the qr-joined views and could make single-row fetches (fetchOne) fail.
--
-- The chk_either_app_user_or_participant constraint guarantees exactly one of
-- app_user / participant is set, so two partial unique indexes cover both cases.

-- De-dup any pre-existing violations before adding the indexes, otherwise the
-- index creation would fail on legacy data. We keep the most recently created
-- code per (entity, event) and drop the older ones.
delete from qr_codes qc
    using (select id,
                  row_number() over (partition by app_user, event order by created_at desc, id) as rn
           from qr_codes
           where app_user is not null) dup
where qc.id = dup.id
  and dup.rn > 1;

delete from qr_codes qc
    using (select id,
                  row_number() over (partition by participant, event order by created_at desc, id) as rn
           from qr_codes
           where participant is not null) dup
where qc.id = dup.id
  and dup.rn > 1;

create unique index if not exists qr_codes_app_user_event_unique
    on qr_codes (app_user, event)
    where app_user is not null;

create unique index if not exists qr_codes_participant_event_unique
    on qr_codes (participant, event)
    where participant is not null;
