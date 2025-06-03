set search_path to ready2race, pg_catalog, public;

create table competition_setup
(
    competition_properties uuid primary key references competition_properties on delete cascade,
    created_at             timestamp not null,
    created_by             uuid      references app_user on delete set null,
    updated_at             timestamp not null,
    updated_by             uuid      references app_user on delete set null
);

create table competition_setup_template
(
    id          uuid primary key,
    name        text      not null,
    description text,
    created_at  timestamp not null,
    created_by  uuid      references app_user on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid      references app_user on delete set null
);

create table competition_setup_round
(
    id                         uuid primary key,
    competition_setup          uuid references competition_setup on delete cascade,
    competition_setup_template uuid references competition_setup_template on delete cascade,
    next_round                 uuid references competition_setup_round,
    name                       text    not null,
    required                   boolean not null,
    use_default_seeding        boolean not null,
    places_option              text    not null,
    constraint chk_either_competition_setup_or_competition_setup_template check (
        (competition_setup is null and competition_setup_template is not null) or
        (competition_setup is not null and competition_setup_template is null) )
);

create index on competition_setup_round (competition_setup);

create table competition_setup_group
(
    id        uuid primary key,
    weighting integer not null,
    teams     integer,
    name      text
);

create table competition_setup_group_statistic_evaluation
(
    competition_setup_round uuid references competition_setup_round on delete cascade,
    name                    text    not null,
    priority                integer not null,
    rank_by_biggest         boolean not null,
    ignore_biggest_values   integer not null,
    ignore_smallest_values  integer not null,
    as_average              boolean not null,
    primary key (competition_setup_round, name)
);

create unique index priority_unique_in_round on competition_setup_group_statistic_evaluation (competition_setup_round, priority);
create index on competition_setup_group_statistic_evaluation (competition_setup_round);

create table competition_setup_match
(
    id                      uuid primary key,
    competition_setup_round uuid    not null references competition_setup_round on delete cascade,
    competition_setup_group uuid references competition_setup_group on delete cascade,
    weighting               integer not null,
    teams                   integer,
    name                    text,
    execution_order         integer not null,
    start_time_offset       bigint
);

create index on competition_setup_match (competition_setup_round);
create index on competition_setup_match (competition_setup_group);

create table competition_setup_participant
(
    id                      uuid primary key,
    competition_setup_match uuid references competition_setup_match on delete cascade,
    competition_setup_group uuid references competition_setup_group on delete cascade,
    seed                    integer not null,
    ranking                 integer not null,
    constraint chk_either_competition_setup_match_or_competition_setup_group check (
        (competition_setup_match is null and competition_setup_group is not null) or
        (competition_setup_match is not null and competition_setup_group is null) )
);

create index on competition_setup_participant (competition_setup_match);
create index on competition_setup_participant (competition_setup_group);

create table competition_setup_place
(
    competition_setup_round uuid references competition_setup_round on delete cascade,
    round_outcome           integer not null,
    place                   integer not null,
    primary key (competition_setup_round, round_outcome)
);

create index on competition_setup_place (competition_setup_round);

insert into competition_setup_template (id, name, description, created_at, created_by, updated_at, updated_by)
values ('1bd64326-6229-447e-9740-fb627e7a3fae', 'Langstrecke', 'Massenstart mit optionaler Vorrunde', now(), null,
        now(), null),
       ('00522a61-e227-4eb6-9da8-a4590adcb967', 'Beachsprint', 'Zeitfahren mit anschließendem KO-Baum', now(), null,
        now(), null);

insert into competition_setup_round (id, competition_setup, competition_setup_template, next_round, name, required,
                                     use_default_seeding, places_option)
values ('10832c01-eb84-40ad-a88f-50304ea13b4a', null, '1bd64326-6229-447e-9740-fb627e7a3fae', null, 'Hauptrunde', true,
        true,
        'ASCENDING'),
       ('822f8bc2-f6e5-4b41-b735-47342676a8e9', null, '1bd64326-6229-447e-9740-fb627e7a3fae',
        '10832c01-eb84-40ad-a88f-50304ea13b4a', 'Vorrunde', false, true, 'EQUAL'),
       ('23220b76-189f-4270-842f-4ae7e47e0eb2', null, '00522a61-e227-4eb6-9da8-a4590adcb967', null, 'Finalrunde', true,
        false, 'CUSTOM'),
       ('90462ab5-8adb-4532-9476-52ab1466bff9', null, '00522a61-e227-4eb6-9da8-a4590adcb967',
        '23220b76-189f-4270-842f-4ae7e47e0eb2', 'Halbfinale', false, true, 'EQUAL'),
       ('11b3f543-1b1e-4974-8741-81c8e21a8df6', null, '00522a61-e227-4eb6-9da8-a4590adcb967',
        '90462ab5-8adb-4532-9476-52ab1466bff9', 'Viertelfinale', false, true, 'EQUAL'),
       ('ec01426d-c104-4a43-ac87-16160a53ee19', null, '00522a61-e227-4eb6-9da8-a4590adcb967',
        '11b3f543-1b1e-4974-8741-81c8e21a8df6', 'Achtelfinale', false, true, 'EQUAL'),
       ('9b6b61a2-4e82-4c07-88eb-d74d213a2de1', null, '00522a61-e227-4eb6-9da8-a4590adcb967',
        'ec01426d-c104-4a43-ac87-16160a53ee19', 'Zeitfahren', true, true, 'ASCENDING');

insert into competition_setup_match (id, competition_setup_round, competition_setup_group, weighting, teams, name,
                                     execution_order, start_time_offset)
values ('a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', '10832c01-eb84-40ad-a88f-50304ea13b4a', null, 1, 8, 'Massenstart', 1,
        null),
       ('0a1766f8-b9e6-4075-93c9-97a84f31e126', '822f8bc2-f6e5-4b41-b735-47342676a8e9', null, 1, null, 'Vorrunde 1', 1,
        null),
       ('8f320ef8-c465-4961-b98c-8851b1569e33', '822f8bc2-f6e5-4b41-b735-47342676a8e9', null, 2, null, 'Vorrunde 2', 2,
        null),
       ('32335470-d1e0-4ef4-9f03-a7e0f6c57732', '23220b76-189f-4270-842f-4ae7e47e0eb2', null, 1, 2, 'Finale A', 1, null),
       ('3de79972-f366-4914-80c2-8d365b802e9c', '23220b76-189f-4270-842f-4ae7e47e0eb2', null, 2, 2, 'Finale B',
        2, null),
       ('c3b257e2-b614-41d8-becd-e1d590b9b428', '90462ab5-8adb-4532-9476-52ab1466bff9', null, 1, 2, 'HF-1', 1, null),
       ('51d13186-dbf0-444c-86e9-87fd0d047843', '90462ab5-8adb-4532-9476-52ab1466bff9', null, 2, 2, 'HF-2', 2, null),
       ('b46fdaf0-c0ba-45fa-814f-414a2781c0f1', '11b3f543-1b1e-4974-8741-81c8e21a8df6', null, 1, 2, 'VF-1', 1, null),
       ('e67f0eb6-8658-4cd1-a1a3-51cb44e10414', '11b3f543-1b1e-4974-8741-81c8e21a8df6', null, 2, 2, 'VF-4', 4, null),
       ('76c2675b-264e-45b5-9716-60aa45796848', '11b3f543-1b1e-4974-8741-81c8e21a8df6', null, 3, 2, 'VF-3', 3, null),
       ('2c2b4a47-a0a4-408a-9900-97fd5044cbe3', '11b3f543-1b1e-4974-8741-81c8e21a8df6', null, 4, 2, 'VF-2', 2, null),
       ('c4a3b360-175a-42d0-a93c-3862cecaa7fa', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 1, 2, 'AF-1', 1, null),
       ('f5da51b0-5459-4bfe-af89-032d93361cc2', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 2, 2, 'AF-8', 8, null),
       ('efb111ed-cb0d-46bb-943e-ce9d93b3cec3', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 3, 2, 'AF-5', 5, null),
       ('eefe45e6-ada2-444c-9c44-e0ea451c0887', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 4, 2, 'AF-4', 4, null),
       ('abd5ba78-22dd-44d6-881f-ab2faabf953b', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 5, 2, 'AF-3', 3, null),
       ('daf60666-74c5-4c28-91f7-2454495db4e5', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 6, 2, 'AF-6', 6, null),
       ('efd37a94-3312-4fd0-937d-e65180504b74', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 7, 2, 'AF-7', 7, null),
       ('2c6bc26b-92c3-4b3b-9b4a-2155f53632e1', 'ec01426d-c104-4a43-ac87-16160a53ee19', null, 8, 2, 'AF-2', 2, null),
       ('77a9c40d-0c76-459a-adb6-667ccb91f207', '9b6b61a2-4e82-4c07-88eb-d74d213a2de1', null, 1, null, 'Zeitfahren', 1,
        60);

insert into competition_setup_participant (id, competition_setup_match, competition_setup_group, seed, ranking)
values ('2fd06eca-2b05-4532-9d7c-0095a7059b17', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 1, 1),
       ('41669ede-0d6e-4485-a5eb-f0352a2c72da', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 2, 2),
       ('1070c317-a691-4a11-83d3-08e9b1afa68c', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 3, 3),
       ('6f11cecb-de95-40b2-9553-03d520fc160e', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 4, 4),
       ('a76773dd-2c5c-4760-a73b-3c5cac91a7ca', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 5, 5),
       ('64d9e4cb-8d3b-4afc-b040-b14bb23a2572', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 6, 6),
       ('8682a5e6-9ba5-41ec-a011-57c8979b8119', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 7, 7),
       ('cc81d0cf-a092-4d7e-9d3e-39a896cba312', 'a9e32fe1-58ee-4dbd-bbd5-c77dd2d539fe', null, 8, 8),
       ('a0718892-708f-43fb-94ab-26260731e0c9', '32335470-d1e0-4ef4-9f03-a7e0f6c57732', null, 1, 1),
       ('fa13a471-68a5-4346-90dc-f40d80e316ea', '32335470-d1e0-4ef4-9f03-a7e0f6c57732', null, 2, 2),
       ('f337c1dc-d05f-432d-ada2-53bb0ab3b5e1', '3de79972-f366-4914-80c2-8d365b802e9c', null, 3, 1),
       ('2378ec5e-16d7-40ec-8ede-288c5afecaca', '3de79972-f366-4914-80c2-8d365b802e9c', null, 4, 2),
       ('90be11c7-8294-4f0c-a6e6-43e12201e06d', 'c3b257e2-b614-41d8-becd-e1d590b9b428', null, 1, 1),
       ('42992cd6-5c69-43de-ba08-6e1d2748f005', 'c3b257e2-b614-41d8-becd-e1d590b9b428', null, 4, 2),
       ('b46d2576-dcb6-406d-918b-e77bb3e62d30', '51d13186-dbf0-444c-86e9-87fd0d047843', null, 2, 1),
       ('715d53b6-376c-4e35-812c-f15ed2db1d1f', '51d13186-dbf0-444c-86e9-87fd0d047843', null, 3, 2),
       ('d4e05bc2-498b-4759-bb2d-a7ce4b71ad6e', 'b46fdaf0-c0ba-45fa-814f-414a2781c0f1', null, 1, 1),
       ('f52cb273-665f-46d2-a58f-61e863bcec21', 'b46fdaf0-c0ba-45fa-814f-414a2781c0f1', null, 8, 2),
       ('628871f3-3d05-4e1e-bbfe-b18c03e71b84', 'e67f0eb6-8658-4cd1-a1a3-51cb44e10414', null, 2, 1),
       ('a762756f-5f4f-4fa3-9540-34ae42d51cf3', 'e67f0eb6-8658-4cd1-a1a3-51cb44e10414', null, 7, 2),
       ('82da4283-f6a0-4f93-b1c3-d2d101aba359', '76c2675b-264e-45b5-9716-60aa45796848', null, 3, 1),
       ('0c04c573-bc77-4770-bb28-e6e5e13ea8c9', '76c2675b-264e-45b5-9716-60aa45796848', null, 6, 2),
       ('191a00d6-dda1-4539-af6c-6239e40387db', '2c2b4a47-a0a4-408a-9900-97fd5044cbe3', null, 4, 1),
       ('19bf7e30-c4f4-49c3-9ec8-703eecdd6385', '2c2b4a47-a0a4-408a-9900-97fd5044cbe3', null, 5, 2),
       ('07103dea-8a08-421d-b2fb-5be7bbfe9d54', 'c4a3b360-175a-42d0-a93c-3862cecaa7fa', null, 1, 1),
       ('2a40251c-50d4-49d1-8f3b-5609433dc4ba', 'c4a3b360-175a-42d0-a93c-3862cecaa7fa', null, 16, 2),
       ('a3fae3cc-4825-451f-852f-d3c51cca64e5', 'f5da51b0-5459-4bfe-af89-032d93361cc2', null, 2, 1),
       ('75111148-2569-4718-a8f5-d4ad21dcaa7f', 'f5da51b0-5459-4bfe-af89-032d93361cc2', null, 15, 2),
       ('60e157a5-24cd-4c81-ad38-393e02dac654', 'efb111ed-cb0d-46bb-943e-ce9d93b3cec3', null, 3, 1),
       ('15aa21d2-a893-4c10-b3fc-b17dd812bfe8', 'efb111ed-cb0d-46bb-943e-ce9d93b3cec3', null, 14, 2),
       ('95188133-06f2-4a51-b327-ef23b2bbd3fb', 'eefe45e6-ada2-444c-9c44-e0ea451c0887', null, 4, 1),
       ('80f38b71-9c78-46ad-81a6-9623ea9d77b7', 'eefe45e6-ada2-444c-9c44-e0ea451c0887', null, 13, 2),
       ('10991f62-5236-4e58-a685-72ec6d81fc24', 'abd5ba78-22dd-44d6-881f-ab2faabf953b', null, 5, 1),
       ('273e1ea3-4ba0-45aa-83e9-80e180823cfe', 'abd5ba78-22dd-44d6-881f-ab2faabf953b', null, 12, 2),
       ('4623a3d9-0c50-4358-a513-dd678ed906cc', 'daf60666-74c5-4c28-91f7-2454495db4e5', null, 6, 1),
       ('4dbdcd1b-2b69-4973-827f-ea1ccc43fb43', 'daf60666-74c5-4c28-91f7-2454495db4e5', null, 11, 2),
       ('7dcaee89-a2b6-4108-8d42-f044a48fc1b1', 'efd37a94-3312-4fd0-937d-e65180504b74', null, 7, 1),
       ('13a1cf5a-6b42-433b-ab84-3c21127c5746', 'efd37a94-3312-4fd0-937d-e65180504b74', null, 10, 2),
       ('6fa0e277-ff7e-4de4-a2af-542dd08b1f7e', '2c6bc26b-92c3-4b3b-9b4a-2155f53632e1', null, 8, 1),
       ('7ad80084-5b67-4726-be52-af63ffaa563a', '2c6bc26b-92c3-4b3b-9b4a-2155f53632e1', null, 9, 2);

insert into competition_setup_place (competition_setup_round, round_outcome, place)
values ('23220b76-189f-4270-842f-4ae7e47e0eb2', 1, 1),
       ('23220b76-189f-4270-842f-4ae7e47e0eb2', 2, 3),
       ('23220b76-189f-4270-842f-4ae7e47e0eb2', 3, 4),
       ('23220b76-189f-4270-842f-4ae7e47e0eb2', 4, 2);
