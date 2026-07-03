alter table portal_installation
    add column if not exists bot_event_webhook_url text,
    add column if not exists last_event_at timestamptz,
    add column if not exists last_client_message_at timestamptz;

create table if not exists support_message (
    id bigserial primary key,
    direction varchar(32) not null,
    client_installation_id bigint references portal_installation(id),
    client_dialog_id varchar(255),
    client_message_id varchar(255),
    admin_dialog_id varchar(255),
    admin_message_id varchar(255),
    reply_to_admin_message_id varchar(255),
    sender_user_id varchar(255),
    sender_name varchar(255),
    text text,
    status varchar(32) not null,
    raw_event_json text,
    created_at timestamptz not null default now()
);

create index if not exists idx_support_message_admin_message_id
    on support_message(admin_message_id);

create index if not exists idx_support_message_client_message
    on support_message(client_installation_id, client_message_id);

create index if not exists idx_support_message_created_at
    on support_message(created_at desc);
