create table caterer_transaction
(
    id          uuid primary key,
    caterer_id  uuid not null references app_user(id),
    app_user_id uuid not null references app_user(id),
    price       decimal(10,2) not null,
    event_id    uuid not null references event(id),
    created_at  timestamp not null,
    created_by  uuid references app_user(id) on delete set null,
    updated_at  timestamp not null,
    updated_by  uuid references app_user(id) on delete set null
);

create index on caterer_transaction (event_id, caterer_id, created_at);
create index on caterer_transaction (app_user_id, event_id);

alter table caterer_transaction add constraint chk_caterer_transaction_price_non_negative 
    check (price >= 0);