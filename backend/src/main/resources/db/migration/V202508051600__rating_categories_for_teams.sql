create table rating_category
(
    id uuid primary key,
    name text not null,
    created_at timestamp not null,
    created_by uuid references app_user on delete set null,
    updated_at timestamp not null,
    updated_by uuid references app_user on delete set null
);

alter table competition_registration
    add column rating_category uuid references rating_category;