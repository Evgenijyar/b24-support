alter table portal_installation
    add column if not exists bot_code varchar(128),
    add column if not exists bot_token varchar(64),
    add column if not exists bot_type varchar(32),
    add column if not exists support_chat_id varchar(64),
    add column if not exists bot_registered_at timestamptz,
    add column if not exists support_chat_created_at timestamptz;
