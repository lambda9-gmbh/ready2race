set search_path to ready2race, pg_catalog, public;

drop view if exists app_user_registration_view;
drop view if exists app_user_invitation_with_roles;
drop view if exists race_template_to_properties_with_named_participants;
drop view if exists race_to_properties_with_named_participants;
drop view if exists named_participant_for_race_properties;
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

create view named_participant_for_race_properties as
select rphnp.race_properties,
       rphnp.required,
       rphnp.count_males,
       rphnp.count_females,
       rphnp.count_non_binary,
       rphnp.count_mixed,
       np.id,
       np.name,
       np.description
from race_properties_has_named_participant rphnp
         left join named_participant np on rphnp.named_participant = np.id;


create view race_to_properties_with_named_participants as
select r.id,
       r.event,
       r.template,
       rp.identifier,
       rp.name,
       rp.short_name,
       rp.description,
       rp.count_males,
       rp.count_females,
       rp.count_non_binary,
       rp.count_mixed,
       rp.participation_fee,
       rp.rental_fee,
       rc.id                                                                               as category_id,
       rc.name                                                                             as category_name,
       rc.description                                                                      as category_description,
       coalesce(array_agg(npfrp) filter ( where npfrp.race_properties is not null ), '{}') as named_participants
from race r
         left join race_properties rp on r.id = rp.race
         left join named_participant_for_race_properties npfrp on rp.id = npfrp.race_properties
         left join race_category rc on rp.race_category = rc.id
group by r.id, rp.id, rc.id;


create view race_template_to_properties_with_named_participants as
select rt.id,
       rp.identifier,
       rp.name,
       rp.short_name,
       rp.description,
       rp.count_males,
       rp.count_females,
       rp.count_non_binary,
       rp.count_mixed,
       rp.participation_fee,
       rp.rental_fee,
       rc.id                                                                               as category_id,
       rc.name                                                                             as category_name,
       rc.description                                                                      as category_description,
       coalesce(array_agg(npfrp) filter ( where npfrp.race_properties is not null ), '{}') as named_participants
from race_template rt
         left join race_properties rp on rt.id = rp.race_template
         left join named_participant_for_race_properties npfrp on rp.id = npfrp.race_properties
         left join race_category rc on rp.race_category = rc.id
group by rt.id, rp.id, rc.id;

create view app_user_invitation_with_roles as
select aui.id,
       aui.token,
       aui.email,
       aui.firstname,
       aui.lastname,
       aui.language,
       aui.expires_at,
       aui.created_at,
       e                                                                             as email_entity,
       coalesce(array_agg(rwp) filter ( where rwp.id is not null ), '{}') as roles,
       cb                                                                            as created_by
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