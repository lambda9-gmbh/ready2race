set search_path to ready2race, pg_catalog, public;

drop view if exists caterer_transaction_view;
drop view if exists participant_qr_assignment_view;
drop view if exists team_status_with_participants;
drop view if exists startlist_view;
drop view if exists startlist_team;
drop view if exists invoice_download;
drop view if exists invoice_for_event_registration;
drop view if exists competition_setup_round_with_matches;
drop view if exists substitution_view;
drop view if exists competition_match_with_teams;
drop view if exists competition_match_team_with_registration;
drop view if exists participant_view;
drop view if exists work_shift_with_assigned_users;
drop view if exists task_with_responsible_users;
drop view if exists event_registration_for_invoice;
drop view if exists competition_registration_with_fees;
drop view if exists applied_fee;
drop view if exists document_template_assignment;
drop view if exists event_registration_result_view;
drop view if exists event_competition_registration;
drop view if exists competition_club_registration;
drop view if exists registered_competition_team;
drop view if exists registered_competition_team_participant;
drop view if exists event_registration_report_download;
drop view if exists event_registrations_view;
drop view if exists event_view;
drop view if exists event_public_view;
drop view if exists participant_for_event;
drop view if exists participant_id_for_event;
drop view if exists participant_requirement_for_event;
drop view if exists participant_requirement_named_participant;
drop view if exists event_document_download;
drop view if exists event_document_view;
drop view if exists app_user_registration_view;
drop view if exists app_user_invitation_with_roles;
drop view if exists competition_template_view;
drop view if exists competition_public_view;
drop view if exists competition_for_club_view;
drop view if exists competition_view;
drop view if exists fee_for_competition;
drop view if exists fee_for_competition_properties;
drop view if exists named_participant_for_competition_properties;
drop view if exists app_user_with_privileges;
drop view if exists app_user_with_qr_code_for_event;
drop view if exists app_user_with_roles;
drop view if exists every_app_user_with_roles;
drop view if exists role_with_privileges;
drop view if exists every_role_with_privileges;
drop view if exists app_user_name;

create view app_user_name as
select au.id,
       au.firstname,
       au.lastname
from app_user au;

create view every_role_with_privileges as
select r.id,
       r.name,
       r.description,
       r.static,
       coalesce(array_agg(p) filter ( where p.id is not null ), '{}') as privileges
from role r
         left join role_has_privilege rhp on r.id = rhp.role
         left join privilege p on rhp.privilege = p.id
group by r.id;

-- refactor this and similar views to use where-clause in API instead
create view role_with_privileges as
select r.id,
       r.name,
       r.description,
       r.privileges
from every_role_with_privileges r
where r.static is false;

create view every_app_user_with_roles as
select au.id,
       au.firstname,
       au.lastname,
       au.email,
       au.club,
       coalesce(array_agg(rwp) filter ( where rwp.id is not null ), '{}') as roles,
       qc.qr_code_id
from app_user au
         left join app_user_has_role auhr on au.id = auhr.app_user
         left join role_with_privileges rwp on auhr.role = rwp.id
         left join qr_codes qc on qc.app_user = au.id
    group by au.id, qc.id;

-- refactor this and similar views to use where-clause in API instead
create view app_user_with_roles as
select au.id,
       au.firstname,
       au.lastname,
       au.email,
       au.club,
       au.roles,
       au.qr_code_id
from every_app_user_with_roles au
where not exists(select *
                 from app_user_has_role auhr2
                 where auhr2.role = '00000000-0000-0000-0000-000000000000'
                   and auhr2.app_user = au.id)
;

create view app_user_with_qr_code_for_event as
select au.id,
       au.firstname,
       au.lastname,
       au.email,
       au.club,
       au.roles,
       qc.qr_code_id,
       qc.event as event_id,
       qc.created_at,
       qc.created_by
from every_app_user_with_roles au
         left join qr_codes qc on qc.app_user = au.id
;

create view app_user_with_privileges as
select au.*,
       coalesce(array_agg(distinct p) filter ( where p.id is not null ), '{}') as privileges
from app_user au
         left join app_user_has_role auhr on au.id = auhr.app_user
         left join role_has_privilege rhp on auhr.role = rhp.role
         left join privilege p on rhp.privilege = p.id or auhr.role = '00000000-0000-0000-0000-000000000000'
group by au.id;

create view named_participant_for_competition_properties as
select cphnp.competition_properties,
       cphnp.count_males,
       cphnp.count_females,
       cphnp.count_non_binary,
       cphnp.count_mixed,
       np.id,
       np.name,
       np.description,
       cp.competition as competition_id
from competition_properties_has_named_participant cphnp
         join named_participant np on cphnp.named_participant = np.id
         join competition_properties cp on cphnp.competition_properties = cp.id
order by np.name, np.id;

create view fee_for_competition_properties as
select cphf.competition_properties,
       cphf.required,
       cphf.amount,
       f.id,
       f.name,
       f.description
from competition_properties_has_fee cphf
         left join fee f on cphf.fee = f.id;

create view fee_for_competition as
select f.id,
       f.name,
       f.description,
       cphf.amount,
       cphf.required,
       cp.competition as competition_id
from competition_properties_has_fee cphf
         join fee f on cphf.fee = f.id
         join competition_properties cp on cphf.competition_properties = cp.id;

create view competition_view as
select c.id,
       c.event,
       substring(cp.identifier for length(cp.identifier) -
                                   length(substring(cp.identifier from '\d*$'))) as identifier_prefix,
       cast(nullif(substring(cp.identifier from '\d*$'), '') as int)             as identifier_suffix,
       cp.name,
       cp.short_name,
       cp.description,
       nps.total_count                                                           as total_count,
       cc.id                                                                     as category_id,
       cc.name                                                                   as category_name,
       cc.description                                                            as category_description,
       coalesce(nps.named_participants, '{}')                                    as named_participants,
       coalesce(fs.fees, '{}')                                                   as fees,
       count(distinct cr.id)                                                     as registrations_count
from competition c
         left join competition_properties cp on c.id = cp.competition
         left join competition_category cc on cp.competition_category = cc.id
         left join (select npfcp.competition_properties,
                           (
                               coalesce(sum(npfcp.count_males), 0) +
                               coalesce(sum(npfcp.count_females), 0) +
                               coalesce(sum(npfcp.count_non_binary), 0) +
                               coalesce(sum(npfcp.count_mixed), 0)
                               )                                                    as total_count,
                           array_agg(npfcp)
                           filter (where npfcp.competition_properties is not null ) as named_participants
                    from named_participant_for_competition_properties npfcp
                    group by npfcp.competition_properties) nps on cp.id = nps.competition_properties
         left join (select ffcp.competition_properties,
                           array_agg(ffcp)
                           filter (where ffcp.competition_properties is not null ) as fees
                    from fee_for_competition_properties ffcp
                    group by ffcp.competition_properties) fs on cp.id = fs.competition_properties
         left join competition_registration cr on c.id = cr.competition
group by c.id, c.event, cp.identifier, cp.name, cp.short_name, cp.description, cc.id, cc.name,
         cc.description, nps.total_count, nps.named_participants, fs.fees
;

create view competition_for_club_view as
select c.id,
       c.event,
       cp.identifier,
       cp.name,
       cp.short_name,
       cp.description,
       nps.total_count                        as total_count,
       cc.id                                  as category_id,
       cc.name                                as category_name,
       cc.description                         as category_description,
       coalesce(nps.named_participants, '{}') as named_participants,
       coalesce(fs.fees, '{}')                as fees,
       count(distinct cr.id)                  as registrations_count,
       cb.id                                  as club
from competition c
         left join competition_properties cp on c.id = cp.competition
         left join competition_category cc on cp.competition_category = cc.id
         left join (select npfcp.competition_properties,
                           (
                               coalesce(sum(npfcp.count_males), 0) +
                               coalesce(sum(npfcp.count_females), 0) +
                               coalesce(sum(npfcp.count_non_binary), 0) +
                               coalesce(sum(npfcp.count_mixed), 0)
                               )                                                    as total_count,
                           array_agg(npfcp)
                           filter (where npfcp.competition_properties is not null ) as named_participants
                    from named_participant_for_competition_properties npfcp
                    group by npfcp.competition_properties) nps on cp.id = nps.competition_properties
         left join (select ffcp.competition_properties,
                           array_agg(ffcp)
                           filter (where ffcp.competition_properties is not null ) as fees
                    from fee_for_competition_properties ffcp
                    group by ffcp.competition_properties) fs on cp.id = fs.competition_properties
         cross join club cb
         left join competition_registration cr on c.id = cr.competition and cb.id = cr.club
group by c.id, c.event, cp.identifier, cp.name, cp.short_name, cp.description, cc.id, cc.name,
         cc.description, nps.total_count, nps.named_participants, fs.fees, cb.id;

create view competition_public_view as
select c.id,
       c.event,
       cp.identifier,
       cp.name,
       cp.short_name,
       cp.description,
       nps.total_count                        as total_count,
       cc.id                                  as category_id,
       cc.name                                as category_name,
       cc.description                         as category_description,
       coalesce(nps.named_participants, '{}') as named_participants,
       coalesce(fs.fees, '{}')                as fees
from competition c
         join event e on c.event = e.id
         left join competition_properties cp on c.id = cp.competition
         left join competition_category cc on cp.competition_category = cc.id
         left join (select npfcp.competition_properties,
                           (
                               coalesce(sum(npfcp.count_males), 0) +
                               coalesce(sum(npfcp.count_females), 0) +
                               coalesce(sum(npfcp.count_non_binary), 0) +
                               coalesce(sum(npfcp.count_mixed), 0)
                               )                                                    as total_count,
                           array_agg(npfcp)
                           filter (where npfcp.competition_properties is not null ) as named_participants
                    from named_participant_for_competition_properties npfcp
                    group by npfcp.competition_properties) nps on cp.id = nps.competition_properties
         left join (select ffcp.competition_properties,
                           array_agg(ffcp)
                           filter (where ffcp.competition_properties is not null ) as fees
                    from fee_for_competition_properties ffcp
                    group by ffcp.competition_properties) fs on cp.id = fs.competition_properties
where e.published is true
group by c.id, c.event, cp.identifier, cp.name, cp.short_name, cp.description, cc.id, cc.name,
         cc.description, nps.total_count, nps.named_participants, fs.fees;

create view competition_template_view as
select ct.id,
       cp.identifier,
       cp.name,
       cp.short_name,
       cp.description,
       cc.id                                  as category_id,
       cc.name                                as category_name,
       cc.description                         as category_description,
       coalesce(nps.named_participants, '{}') as named_participants,
       coalesce(fs.fees, '{}')                as fees,
       cst.id                                 as setup_template_id,
       cst.name                               as setup_template_name,
       cst.description                        as setup_template_description
from competition_template ct
         left join competition_properties cp on ct.id = cp.competition_template
         left join competition_category cc on cp.competition_category = cc.id
         left join (select npfcp.competition_properties,
                           array_agg(npfcp)
                           filter (where npfcp.competition_properties is not null ) as named_participants
                    from named_participant_for_competition_properties npfcp
                    group by npfcp.competition_properties) nps on cp.id = nps.competition_properties
         left join (select ffcp.competition_properties,
                           array_agg(ffcp)
                           filter (where ffcp.competition_properties is not null ) as fees
                    from fee_for_competition_properties ffcp
                    group by ffcp.competition_properties) fs on cp.id = fs.competition_properties
         left join competition_setup_template cst on ct.competition_setup_template = cst.id;

create view app_user_invitation_with_roles as
select aui.id,
       aui.token,
       aui.email,
       aui.firstname,
       aui.lastname,
       aui.language,
       aui.expires_at,
       aui.created_at,
       e                                                                  as email_entity,
       coalesce(array_agg(rwp) filter ( where rwp.id is not null ), '{}') as roles,
       cb                                                                 as created_by
from app_user_invitation aui
         left join app_user_invitation_to_email auite on aui.id = auite.app_user_invitation
         left join email e on auite.email = e.id
         left join app_user_invitation_has_role auihr on aui.id = auihr.app_user_invitation
         left join every_role_with_privileges rwp on auihr.role = rwp.id
         left join app_user_name cb on aui.created_by = cb.id
group by aui.id, e, cb;

create view app_user_registration_view as
select aur.id,
       aur.email,
       aur.firstname,
       aur.lastname,
       aur.language,
       aur.expires_at,
       aur.created_at,
       e as email_entity
from app_user_registration aur
         left join app_user_registration_to_email aurte on aur.id = aurte.app_user_registration
         left join email e on aurte.email = e.id;

create view event_document_view as
select ed.id,
       ed.event,
       edt as document_type,
       ed.name,
       ed.created_at,
       cb  as created_by,
       ed.updated_at,
       ub  as updated_by
from event_document ed
         left join event_document_type edt on ed.event_document_type = edt.id
         left join app_user_name cb on ed.created_by = cb.id
         left join app_user_name ub on ed.updated_by = ub.id;

create view event_document_download as
select ed.id,
       ed.event,
       ed.name,
       edd.data
from event_document ed
         join event_document_data edd on ed.id = edd.event_document;

-- Helper view to convert requirements to typed records
create or replace view participant_requirement_named_participant as
select
    ehpr.event,
    ehpr.participant_requirement,
    ehpr.named_participant as id,
    np.name,
    ehpr.qr_code_required
from event_has_participant_requirement ehpr
left join named_participant np on ehpr.named_participant = np.id;

create or replace view participant_requirement_for_event as
select
    pr.*,
    e.id as event,
    bool_or(ehpr.event is not null) as active,
    coalesce(array_agg(distinct prnp) filter (where prnp is not null), '{}') as requirements
from participant_requirement pr
         cross join event e
         left join event_has_participant_requirement ehpr
                   on pr.id = ehpr.participant_requirement and e.id = ehpr.event
         left join participant_requirement_named_participant prnp
                   on ehpr.event = prnp.event
                   and ehpr.participant_requirement = prnp.participant_requirement
group by pr.id, e.id;

create view participant_id_for_event as
select er.event as event_id,
       p.id     as participant_id
from event_registration er
         join competition_registration cr on er.id = cr.event_registration
         join competition_registration_named_participant crnp on cr.id = crnp.competition_registration
         join participant p on crnp.participant = p.id
group by er.event, p.id;

create view participant_for_event as
select er.event                                                                  as event_id,
       c.id                                                                      as club_id,
       c.name                                                                    as club_name,
       p.id                                                                      as id,
       p.firstname,
       p.lastname,
       p.year,
       p.gender,
       p.external,
       p.external_club_name,
       coalesce(array_agg(distinct pr) filter ( where pr.id is not null ), '{}') as participant_requirements_checked,
       qc.qr_code_id,
       array_agg(distinct crnp.named_participant) as named_participant_ids
from event_registration er
         join club c on er.club = c.id
         join competition_registration cr on er.id = cr.event_registration
         join competition_registration_named_participant crnp on cr.id = crnp.competition_registration
         join participant p on crnp.participant = p.id
         left join participant_has_requirement_for_event phrfe on p.id = phrfe.participant and phrfe.event = er.event
         left join participant_requirement pr on phrfe.participant_requirement = pr.id
         left join qr_codes qc on qc.participant = p.id
group by er.event, c.id, c.name, p.id, p.firstname, p.lastname, p.year, p.gender, p.external, p.external_club_name, qc.id
order by c.name, p.firstname, p.lastname;

create view event_public_view as
select e.id,
       e.name,
       e.description,
       e.location,
       e.registration_available_from,
       e.registration_available_to,
       e.created_at,
       count(c.id)  as competition_count,
       min(ed.date) as event_from,
       max(ed.date) as event_to
from event e
         left join competition c on e.id = c.event
         left join event_day ed on e.id = ed.event
where e.published = true
group by e.id, e.name, e.description, e.location, e.registration_available_from, e.registration_available_to,
         e.created_at
having max(ed.date) is null
    or max(ed.date) >= current_date
;

create view event_view as
select e.id,
       e.name,
       e.description,
       e.location,
       e.registration_available_from,
       e.registration_available_to,
       e.invoice_prefix,
       e.published,
       e.invoices_produced,
       e.payment_due_by,
       coalesce(array_agg(distinct er.club) filter ( where er.club is not null ), '{}') as registered_clubs,
       err.event is not null                                                            as registrations_finalized
from event e
         left join competition c on e.id = c.event
         left join event_day ed on e.id = ed.event
         left join event_registration er on e.id = er.event
         left join event_registration_report err on e.id = err.event
group by e.id, e.name, e.description, e.location, e.registration_available_from, e.registration_available_to,
         e.created_at, err.event;

create view event_registrations_view as
select er.id,
       er.created_at,
       er.message,
       er.updated_at,
       e.id                             as event_id,
       e.name                           as event_name,
       c.id                             as club_id,
       c.name                           as club_name,
       count(distinct cr.id)            as competition_registration_count,
       count(distinct crnp.participant) as participant_count
from event_registration er
         left join event e on er.event = e.id
         left join club c on er.club = c.id
         left join competition_registration cr on er.id = cr.event_registration
         left join competition_registration_named_participant crnp on cr.id = crnp.competition_registration
group by er.id, er.created_at, er.message, er.updated_at, e.id, e.name, c.id, c.name;

create view event_registration_report_download as
select err.event,
       err.name,
       errd.data
from event_registration_report err
         join event_registration_report_data errd on err.event = errd.result_document;

create view registered_competition_team_participant as
select crnp.competition_registration as team_id,
       np.id                         as role_id,
       np.name                       as role,
       p.id                          as participant_id,
       p.firstname,
       p.lastname,
       p.year,
       p.gender,
       p.external,
       p.external_club_name
from competition_registration_named_participant crnp
         join named_participant np on crnp.named_participant = np.id
         join participant p on crnp.participant = p.id
;

create view registered_competition_team as
select cr.id,
       cr.competition,
       cr.club,
       cr.name                                                                   as team_name,
       cr.team_number,
       coalesce(array_agg(rctp) filter ( where rctp.team_id is not null ), '{}') as participants
from competition_registration cr
         left join registered_competition_team_participant rctp on cr.id = rctp.team_id
group by cr.id;

create view competition_club_registration as
select rct.competition,
       c.name,
       coalesce(array_agg(rct), '{}') as teams
from registered_competition_team rct
         join club c on rct.club = c.id
group by rct.competition, c.id;

create view event_competition_registration as
select c.id,
       c.event,
       cp.identifier,
       cp.name,
       cp.short_name,
       cc.name                                                                     as category_name,
       coalesce(array_agg(ccr) filter ( where ccr.competition is not null ), '{}') as club_registrations
from competition c
         join competition_properties cp on c.id = cp.competition
         left join competition_category cc on cp.competition_category = cc.id
         left join competition_club_registration ccr on c.id = ccr.competition
group by c.id, cp.id, cc.id;

create view event_registration_result_view as
select e.id,
       e.name                                                             as event_name,
       coalesce(array_agg(ecr) filter ( where ecr.id is not null ), '{}') as competitions
from event e
         left join event_competition_registration ecr on e.id = ecr.event
group by e.id;

create view document_template_assignment as
select dt.page_padding_top,
       dt.page_padding_left,
       dt.page_padding_right,
       dt.page_padding_bottom,
       dtd.data,
       usage.document_type,
       usage.event
from document_template dt
         join document_template_data dtd on dt.id = dtd.template
         join (select edtu.document_type,
                      edtu.template,
                      edtu.event
               from event_document_template_usage edtu
               union all
               select dtu.document_type,
                      dtu.template,
                      null
               from document_template_usage dtu) usage on dt.id = usage.template
;

create view applied_fee as
select cphf.id,
       cr.id as competition_registration,
       f.name,
       cphf.amount
from competition_registration cr
         join competition_properties cp on cr.competition = cp.competition
         left join competition_properties_has_fee cphf on cp.id = cphf.competition_properties
         left join competition_registration_optional_fee crof on cr.id = crof.competition_registration
         left join fee f on cphf.fee = f.id
where cphf.required is true
   or crof.fee = f.id
;

create view competition_registration_with_fees as
select cr.event_registration,
       cp.id                                                            as properties_id,
       cp.identifier,
       cp.name,
       cp.short_name,
       coalesce(array_agg(af) filter ( where af.id is not null ), '{}') as applied_fees
from competition_registration cr
         join competition_properties cp on cr.competition = cp.competition
         left join applied_fee af on cr.id = af.competition_registration
group by cr.id, cp.id
;

create view event_registration_for_invoice as
select er.id,
       er.event,
       c.name                                                                               as club_name,
       au                                                                                   as recipient,
       coalesce(array_agg(crwf) filter ( where crwf.event_registration is not null ), '{}') as competitions
from event_registration er
         join club c on er.club = c.id
         left join app_user au on c.id = au.club
         left join competition_registration_with_fees crwf on er.id = crwf.event_registration
group by er.id, c.id, au.id
;

create view task_with_responsible_users as
select t.id,
       t.event,
       e.name                                                         as event_name,
       t.name,
       t.due_date,
       t.description,
       t.remark,
       t.state,
       t.created_at,
       t.created_by,
       t.updated_at,
       t.updated_by,
       coalesce(array_agg(u) filter ( where u.id is not null ), '{}') as responsible_user
from task t
         left join event e on t.event = e.id
         left join task_has_responsible_user ru on t.id = ru.task
         left join app_user u on ru.app_user = u.id
group by t.id, t.event, e.name, t.name, t.due_date, t.description, t.remark, t.state, t.created_at, t.created_by,
         t.updated_at,
         t.updated_by;

create view work_shift_with_assigned_users as
select ws.id,
       ws.event,
       ws.time_from,
       ws.time_to,
       ws.remark,
       e.name                                                         as event_name,
       ws.work_type,
       wt.name                                                        as work_type_name,
       ws.min_user,
       ws.max_user,
       ws.created_at,
       ws.created_by,
       ws.updated_at,
       ws.updated_by,
       coalesce(string_agg(u.firstname || ' ' || u.lastname, ', ' order by u.firstname, u.lastname)
                filter ( where u.id is not null ), '')                as title,
       coalesce(array_agg(u) filter ( where u.id is not null ), '{}') as assigned_user
from work_shift ws
         left join work_type wt on ws.work_type = wt.id
         left join event e on ws.event = e.id
         left join work_shift_has_user wu on ws.id = wu.work_shift
         left join app_user u on wu.app_user = u.id
group by ws.id, ws.event, ws.time_from, ws.time_to, ws.remark, e.name, ws.work_type, wt.name, ws.min_user, ws.max_user,
         ws.created_at, ws.created_by, ws.updated_at, ws.updated_by;

create view participant_view as
select p.*,
       exists(select * from competition_registration_named_participant where participant = p.id) as used_in_registration
from participant p;

create view competition_match_team_with_registration as
select cmt.id,
       cmt.competition_match,
       cmt.start_number,
       cmt.place,
       cmt.competition_registration,
       cr.club                                                                 as club_id,
       c.name                                                                  as club_name,
       cr.name                                                                 as registration_name,
       cr.team_number,
       coalesce(array_agg(rctp) filter (where rctp.team_id is not null), '{}') as participants
from competition_match_team cmt
         left join competition_registration cr on cr.id = cmt.competition_registration
         left join club c on c.id = cr.club
         left join registered_competition_team_participant rctp on cr.id = rctp.team_id
group by cmt.id, cmt.competition_match, cmt.start_number, cmt.place, cmt.competition_registration, cr.club, c.name,
         cr.name, cr.team_number
;

create view competition_match_with_teams as
select cm.competition_setup_match,
       cm.start_time,
       coalesce(array_agg(cmtwr) filter (where cmtwr.id is not null), '{}') as teams
from competition_match cm
         left join competition_match_team_with_registration cmtwr
                   on cm.competition_setup_match = cmtwr.competition_match
group by cm.competition_setup_match
;

create view substitution_view as
select s.id,
       s.reason,
       s.order_for_round,
       np.id    as named_participant_id,
       np.name  as named_participant_name,
       csr.id   as competition_setup_round_id,
       csr.name as competition_setup_round_name,
       cr.id    as competition_registration_id,
       cr.name  as competition_registration_name,
       c.id     as club_id,
       c.name   as club_name,
       p_out    as participant_out,
       p_in     as participant_in
from substitution s
         left join named_participant np on s.named_participant = np.id
         left join competition_setup_round csr on s.competition_setup_round = csr.id
         left join competition_registration cr on cr.id = s.competition_registration
         left join club c on c.id = cr.club
         join participant p_out on s.participant_out = p_out.id
         join participant p_in on s.participant_in = p_in.id
;

create view competition_setup_round_with_matches as
select sr.id                                                                                            as setup_round_id,
       sr.competition_setup,
       sr.next_round,
       sr.name                                                                                          as setup_round_name,
       sr.required,
       sr.places_option,
       coalesce(array_agg(distinct csp) filter ( where csp.competition_setup_round is not null ), '{}') as places,
       coalesce(array_agg(distinct sm) filter (where sm.id is not null),
                '{}')                                                                                   as setup_matches,
       coalesce(array_agg(distinct mwt) filter (where mwt.competition_setup_match is not null), '{}')   as matches,
       coalesce(array_agg(distinct sv) filter ( where sv.id is not null ), '{}')                        as substitutions
from competition_setup_round sr
         left join competition_setup_place csp on sr.id = csp.competition_setup_round
         left join competition_setup_match sm on sr.id = sm.competition_setup_round
         left join competition_match_with_teams mwt on sm.id = mwt.competition_setup_match
         left join substitution_view sv on sr.id = sv.competition_setup_round_id
group by sr.id
;

create view invoice_for_event_registration as
select i.*,
       substring(i.invoice_number for length(i.invoice_number) -
                                      length(substring(i.invoice_number from '\d*$'))) as invoice_number_prefix,
       cast(nullif(substring(i.invoice_number from '\d*$'), '') as int)                as invoice_number_suffix,
       round(coalesce(sum(ip.unit_price * ip.quantity), 0), 2)                         as total_amount,
       eri.event_registration,
       er.club,
       er.event
from invoice i
         join event_registration_invoice eri on i.id = eri.invoice
         join event_registration er on eri.event_registration = er.id
         left join invoice_position ip on i.id = ip.invoice
group by i.id, eri.event_registration, er.id;

create view invoice_download as
select i.id,
       i.filename,
       idd.data
from invoice i
         join invoice_document_data idd on i.id = idd.invoice;


create view startlist_team as
select cmt.competition_match,
       cmt.start_number,
       cr.id as team_id,
       cr.name as team_name,
       c.id as club_id,
       c.name as club_name,
       coalesce(array_agg(distinct rctp) filter (where rctp.team_id is not null), '{}') as participants,
       coalesce(array_agg(distinct sv) filter (where sv.id is not null), '{}') as substitutions
from competition_match_team cmt
         join competition_registration cr on cmt.competition_registration = cr.id
         join club c on cr.club = c.id
         join competition_setup_match csm on cmt.competition_match = csm.id
         left join registered_competition_team_participant rctp on cmt.competition_registration = rctp.team_id
         left join substitution_view sv on cr.id = sv.competition_registration_id and csm.competition_setup_round = sv.competition_setup_round_id
group by cmt.competition_match, cmt.start_number, cr.id, cr.name, c.id, c.name;

create view startlist_view as
select csm.id,
       csm.name,
       csm.execution_order,
       csm.start_time_offset,
       csr.name                                                                        as round_name,
       cm.start_time,
       cp.identifier                                                                   as competition_identifier,
       cp.name                                                                         as competition_name,
       cp.short_name                                                                   as competition_short_name,
       cc.name                                                                         as competition_category,
       c.event,
       coalesce(array_agg(st) filter ( where st.competition_match is not null ), '{}') as teams
from competition_setup_match csm
         join competition_setup_round csr on csm.competition_setup_round = csr.id
         join competition_match cm on csm.id = cm.competition_setup_match
         join competition_properties cp on csr.competition_setup = cp.id
         join competition c on cp.competition = c.id
         left join competition_category cc on cp.competition_category = cc.id
         left join startlist_team st on csm.id = st.competition_match
group by csm.id, csr.id, cm.competition_setup_match, cp.id, cc.id, c.event;


create view team_status_with_participants as
select
    cr.id as competition_registration_id,
    cr.event_registration,
    cr.competition,
    c.name as club,
    cr.name as team_name,
    p.id as participant_id,
    p.firstname,
    p.lastname,
    p.year,
    p.gender,
    np.name as named_pariticpant_name,
    tt.scan_type as current_status,
    tt.scanned_at as last_scan_at,
    tt.scanned_by,
    coalesce(array_agg(distinct sv) filter (where sv.id is not null), '{}') as substitutions_for_registration
from competition_registration cr
join competition_registration_named_participant crnp on crnp.competition_registration = cr.id
join participant p on p.id = crnp.participant
    join named_participant np on crnp.named_participant = np.id
join club c on cr.club = c.id
left join lateral (
    select scan_type, scanned_at, scanned_by
    from team_tracking
    where competition_registration_id = cr.id
    order by scanned_at desc
    limit 1
) tt on true
left join substitution_view sv on cr.id = sv.competition_registration_id
group by cr.id, cr.event_registration, cr.competition, c.name, cr.name, p.id, p.firstname, p.lastname, p.year, p.gender, np.name, tt.scan_type, tt.scanned_at, tt.scanned_by
;

create view participant_qr_assignment_view as
SELECT 
    p.id AS participant_id,
    p.firstname,
    p.lastname,
    qc.qr_code_id AS qr_code_value,
    np.name AS named_participant,
    crnp.competition_registration,
    cp.name AS competition_name,
    er.event AS event_id,
    er.club AS club_id
FROM participant p
INNER JOIN competition_registration_named_participant crnp 
    ON p.id = crnp.participant
INNER JOIN named_participant np
    ON crnp.named_participant = np.id
INNER JOIN competition_registration cr
    ON crnp.competition_registration = cr.id
INNER JOIN competition c
    ON cr.competition = c.id
INNER JOIN competition_properties cp
    ON c.id = cp.competition
INNER JOIN event_registration er
    ON cr.event_registration = er.id
LEFT JOIN qr_codes qc
    ON p.id = qc.participant 
    AND qc.event = er.event
ORDER BY crnp.competition_registration, p.lastname, p.firstname;

create view caterer_transaction_view as
SELECT 
    ct.id,
    ct.caterer_id,
    caterer.firstname AS caterer_firstname,
    caterer.lastname AS caterer_lastname,
    ct.app_user_id,
    app_user.firstname AS user_firstname,
    app_user.lastname AS user_lastname,
    ct.event_id,
    ct.price,
    ct.created_at
FROM caterer_transaction ct
INNER JOIN app_user caterer ON ct.caterer_id = caterer.id
INNER JOIN app_user app_user ON ct.app_user_id = app_user.id;