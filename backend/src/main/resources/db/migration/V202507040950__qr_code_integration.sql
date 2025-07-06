set search_path to ready2race, pg_catalog, public;

alter table participant
    add column qr_code_id TEXT NULL,
    add constraint participant_qr_code_unique UNIQUE (qr_code_id, firstname, lastname);

alter table app_user
    add column qr_code_id TEXT NULL,
    add constraint app_user_qr_code_unique UNIQUE (qr_code_id);