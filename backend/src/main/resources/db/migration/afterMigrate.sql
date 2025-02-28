set search_path to ready2race, pg_catalog, public;

drop view if exists app_user_registration_view;
drop view if exists app_user_invitation_with_roles;
drop view if exists competition_template_view;
drop view if exists competition_view;
drop view if exists fee_for_competition_properties;
drop view if exists named_participant_for_competition_properties;
drop view if exists app_user_with_privileges;
drop view if exists app_user_with_roles;
drop view if exists role_with_privileges;
drop view if exists created_by;

create view created_by as
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
       cphnp.required,
       cphnp.count_males,
       cphnp.count_females,
       cphnp.count_non_binary,
       cphnp.count_mixed,
       np.id,
       np.name,
       np.description
from competition_properties_has_named_participant cphnp
         left join named_participant np on cphnp.named_participant = np.id;

create view fee_for_competition_properties as
select cphf.competition_properties,
       cphf.required,
       cphf.amount,
       f.id,
       f.name,
       f.description
from competition_properties_has_fee cphf
         left join fee f on cphf.fee = f.id;


create view competition_view as
select c.id,
       c.event,
       c.template,
       cp.identifier,
       cp.name,
       cp.short_name,
       cp.description,
       cp.count_males,
       cp.count_females,
       cp.count_non_binary,
       cp.count_mixed,
       cc.id                                                                                      as category_id,
       cc.name                                                                                    as category_name,
       cc.description                                                                             as category_description,
       coalesce(array_agg(npfrp) filter ( where npfrp.competition_properties is not null ), '{}') as named_participants,
       coalesce(array_agg(ffrp) filter ( where ffrp.competition_properties is not null ), '{}')   as fees
from competition c
         left join competition_properties cp on c.id = cp.competition
         left join competition_category cc on cp.competition_category = cc.id
         left join named_participant_for_competition_properties npfrp on cp.id = npfrp.competition_properties
         left join fee_for_competition_properties ffrp on cp.id = ffrp.competition_properties
group by c.id, cp.id, cc.id;


create view competition_template_view as
select ct.id,
       cp.identifier,
       cp.name,
       cp.short_name,
       cp.description,
       cp.count_males,
       cp.count_females,
       cp.count_non_binary,
       cp.count_mixed,
       cc.id                                                                                      as category_id,
       cc.name                                                                                    as category_name,
       cc.description                                                                             as category_description,
       coalesce(array_agg(npfrp) filter ( where npfrp.competition_properties is not null ), '{}') as named_participants,
       coalesce(array_agg(ffrp) filter ( where ffrp.competition_properties is not null ), '{}')   as fees
from competition_template ct
         left join competition_properties cp on ct.id = cp.competition_template
         left join competition_category cc on cp.competition_category = cc.id
         left join named_participant_for_competition_properties npfrp on cp.id = npfrp.competition_properties
         left join fee_for_competition_properties ffrp on cp.id = ffrp.competition_properties
group by ct.id, cp.id, cc.id;

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
         left join created_by cb on aui.created_by = cb.id
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