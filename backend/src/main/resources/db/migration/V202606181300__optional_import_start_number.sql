-- The start number is no longer required for matching imported results back to teams (the team
-- identifier / registration id is now the source of truth). The start number column in the result
-- import config therefore becomes optional: if configured, the imported start numbers are written
-- back to competition_match_team.start_number; if not, the existing numbers are kept.

alter table match_result_import_config
    alter column col_team_start_number drop not null;
