create table support_settings (
    id bigint primary key,
    closed_chat_retention_days integer not null default 7,
    updated_at timestamptz not null default now(),
    constraint ck_support_settings_singleton check (id = 1),
    constraint ck_support_settings_retention_days check (closed_chat_retention_days between 1 and 3650)
);

insert into support_settings (id, closed_chat_retention_days)
values (1, 7)
on conflict (id) do nothing;

create table support_ticket (
    id bigserial primary key,
    client_installation_id bigint not null references portal_installation(id) on delete cascade,
    status varchar(32) not null,
    client_dialog_id varchar(255) not null,
    admin_chat_id varchar(64),
    admin_dialog_id varchar(255),
    chat_title varchar(255) not null,
    opened_at timestamptz not null,
    closed_at timestamptz,
    delete_after timestamptz,
    deleted_at timestamptz,
    closed_by_user_id varchar(255),
    closed_by_user_name varchar(255),
    deletion_attempts integer not null default 0,
    last_error text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index uq_support_ticket_active_client
    on support_ticket(client_installation_id)
    where status in ('OPENING', 'OPEN');

create unique index uq_support_ticket_admin_dialog
    on support_ticket(admin_dialog_id)
    where admin_dialog_id is not null;

create index idx_support_ticket_status_delete_after
    on support_ticket(status, delete_after);

create index idx_support_ticket_client_opened_at
    on support_ticket(client_installation_id, opened_at desc);

alter table support_message
    add column if not exists support_ticket_id bigint references support_ticket(id) on delete set null;

create index if not exists idx_support_message_ticket_created_at
    on support_message(support_ticket_id, created_at);
