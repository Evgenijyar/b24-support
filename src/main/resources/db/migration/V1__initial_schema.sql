create table portal_installation (
    id bigserial primary key,
    role varchar(24) not null,
    client_code varchar(64) not null unique,
    title varchar(255) not null,
    domain varchar(255) not null unique,
    member_id varchar(255),
    status varchar(24) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table incoming_bitrix_event (
    id bigserial primary key,
    portal_installation_id bigint references portal_installation(id),
    event_name varchar(128),
    event_id varchar(255),
    status varchar(32) not null default 'NEW',
    raw_payload text not null,
    error_message text,
    created_at timestamptz not null default now(),
    processed_at timestamptz
);

create index idx_incoming_bitrix_event_status_created_at
    on incoming_bitrix_event(status, created_at);

create table support_message (
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

create index idx_support_message_admin_message_id
    on support_message(admin_message_id);

create index idx_support_message_client_message
    on support_message(client_installation_id, client_message_id);

create table message_route_mapping (
    id bigserial primary key,
    admin_message_id varchar(255) not null unique,
    client_installation_id bigint not null references portal_installation(id),
    client_dialog_id varchar(255) not null,
    client_original_message_id varchar(255),
    created_at timestamptz not null default now()
);
