-- Stable team identifier column for start list export / result import.
--
-- The start number (Webscorer "Bib") may be changed by the external timing tooling, so it can no
-- longer be the key for matching imported results back to teams. Instead, the competition
-- registration id (a stable UUID) is exported into a dedicated pass-through column and read back on
-- import. Webscorer only preserves a fixed set of fields through timing into the results export, so
-- this column must be mapped to one of those (typically "Info 1").
--
-- The header is configurable per config and required (the round-trip depends on it). Existing configs
-- (including the seeded "Webscorer ..." presets) are back-filled with "Info 1" so nothing is left
-- without an identifier column.

alter table startlist_export_config
    add column col_team_registration_id text;
update startlist_export_config
    set col_team_registration_id = 'Info 1'
    where col_team_registration_id is null;
alter table startlist_export_config
    alter column col_team_registration_id set not null;

alter table match_result_import_config
    add column col_team_registration_id text;
update match_result_import_config
    set col_team_registration_id = 'Info 1'
    where col_team_registration_id is null;
alter table match_result_import_config
    alter column col_team_registration_id set not null;
