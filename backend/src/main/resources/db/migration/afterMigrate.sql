set search_path to ready2race, pg_catalog, public;

drop view if exists race_with_properties;
drop view if exists race_properties_with_named_participant;
drop view if exists app_user_with_privileges;
drop view if exists app_user_with_roles;
drop view if exists role_with_privileges;

create view role_with_privileges as
select r.id,
       r.name,
       r.description,
       r.static,
       coalesce(array_agg(p.name) filter ( where p.name is not null ), '{}')             as privileges_global,
       coalesce(array_agg(p_bound.name) filter ( where p_bound.name is not null ), '{}') as privileges_bound
from role r
         left join role_has_privilege rhp on r.id = rhp.role
         left join privilege p on rhp.privilege = p.name and rhp.association_bound = false
         left join privilege p_bound on rhp.privilege = p.name and rhp.association_bound = true
where r.assignable is true
group by r.id;

create view app_user_with_roles as
select au.id,
       au.firstname,
       au.lastname,
       au.email,
       coalesce(array_agg(auhr.role) filter ( where auhr.role is not null ), '{}') as roles
from app_user au
         left join app_user_has_role auhr on au.id = auhr.app_user
where not exists(select *
                 from app_user_has_role auhr2
                 where auhr2.role = '00000000-0000-0000-0000-000000000000'
                   and auhr2.app_user = au.id)
group by au.id;

create view app_user_with_privileges as
select au.*,
       coalesce(array_agg(distinct p.name) filter ( where p.name is not null ), '{}')             as privileges_global,
       coalesce(array_agg(distinct p_bound.name) filter ( where p_bound.name is not null ), '{}') as privileges_bound
from app_user au
         left join app_user_has_role auhr on au.id = auhr.app_user
         left join role_has_privilege rhp on auhr.role = rhp.role
         left join privilege p on rhp.privilege = p.name and rhp.association_bound = false or
                                  auhr.role = '00000000-0000-0000-0000-000000000000'
         left join privilege p_bound on rhp.privilege = p.name and rhp.association_bound = true
group by au.id;


create view race_properties_with_named_participant as
select rphnp.race_properties,
       rphnp.count_males,
       rphnp.count_females,
       rphnp.count_non_binary,
       rphnp.count_mixed,
       np.name,
       np.description,
       np.required
from race_properties_has_named_participant rphnp
         left join named_participant np on rphnp.named_participant = np.name;


create view race_with_properties as
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
       rp.race_category,
       coalesce(array_agg(rpwnp), '{}') as named_participant_list
from race r
         left join race_properties rp on r.id = rp.race
         left join race_properties_with_named_participant rpwnp on rp.id = rpwnp.race_properties
group by r.id, rp.id;
