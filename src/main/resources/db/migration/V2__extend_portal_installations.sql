alter table portal_installation
    add column if not exists webhook_url text,
    add column if not exists bot_id varchar(255),
    add column if not exists support_dialog_id varchar(255),
    add column if not exists last_error text,
    add column if not exists connected_at timestamptz;

create index if not exists idx_portal_installation_role_title
    on portal_installation(role, title);
