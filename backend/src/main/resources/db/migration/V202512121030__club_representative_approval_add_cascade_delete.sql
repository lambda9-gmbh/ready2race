-- Drop existing foreign key constraint
alter table app_user_club_representative_approval
    drop constraint app_user_club_representative_approval_app_user_fkey;

-- Re-add with cascade delete
alter table app_user_club_representative_approval
    add constraint app_user_club_representative_approval_app_user_fkey
        foreign key (app_user) references app_user on delete cascade;