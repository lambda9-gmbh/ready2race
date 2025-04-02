set search_path to ready2race, pg_catalog, public;

drop view if exists document_template_assignment;
drop view if exists event_registration_result_view;
drop view if exists event_competition_registration;
drop view if exists competition_club_registration;
drop view if exists registered_competition_team;
drop view if exists registered_competition_team_participant;
drop view if exists event_registration_report_download;
drop view if exists event_document_download;
drop view if exists event_document_view;
drop view if exists app_user_registration_view;
drop view if exists app_user_invitation_with_roles;
drop view if exists competition_template_view;
drop view if exists competition_view;
drop view if exists fee_for_competition_properties;
drop view if exists named_participant_for_competition_properties;
drop view if exists app_user_with_privileges;
drop view if exists app_user_with_roles;
drop view if exists role_with_privileges;
drop view if exists app_user_name;
drop view if exists fee_for_competition;

create view app_user_name as
select au.id,
       au.firstname,
       au.lastname
from app_user au;

create view role_with_privileges as
select r.id,
       r.name,
       r.description,
       coalesce(array_agg(p) filter ( where p.id is not null ), '{}') as privileges
from role r
         left join role_has_privilege rhp on r.id = rhp.role
         left join privilege p on rhp.privilege = p.id
where r.static is false
group by r.id;

create view app_user_with_roles as
select au.id,
       au.firstname,
       au.lastname,
       au.email,
       coalesce(array_agg(rwp) filter ( where rwp.id is not null ), '{}') as roles
from app_user au
         left join app_user_has_role auhr on au.id = auhr.app_user
         left join role_with_privileges rwp on auhr.role = rwp.id
where not exists(select *
                 from app_user_has_role auhr2
                 where auhr2.role = '00000000-0000-0000-0000-000000000000'
                   and auhr2.app_user = au.id)
group by au.id;

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
         join competition_properties cp on cphnp.competition_properties = cp.id;

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
       c.template,
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
                    group by ffcp.competition_properties) fs on cp.id = fs.competition_properties;


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
       coalesce(fs.fees, '{}')                as fees
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
                    group by ffcp.competition_properties) fs on cp.id = fs.competition_properties;

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
         left join role_with_privileges rwp on auihr.role = rwp.id
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
       ed.name,
       edd.data
from event_document ed
         join event_document_data edd on ed.id = edd.event_document;

create view event_registration_report_download as
select errd.event,
       errd.name,
       errdd.data
from event_registration_report errd
         join event_registration_report_data errdd on errd.event = errdd.result_document;

create view registered_competition_team_participant as
select crnp.competition_registration as team_id,
       np.name                       as role,
       p.firstname,
       p.lastname,
       p.year,
       p.gender,
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
         join competition_category cc on cp.competition_category = cc.id
         left join competition_club_registration ccr on c.id = ccr.competition
group by c.id, cp.id, cc.id;

create view event_registration_result_view as
select e.id,
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
               from document_template_usage dtu
               ) usage on dt.id = usage.template
;