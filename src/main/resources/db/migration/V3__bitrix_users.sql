create table if not exists bitrix_user (
    id bigserial primary key,
    portal_installation_id bigint not null references portal_installation(id) on delete cascade,
    bitrix_user_id varchar(64) not null,
    active boolean not null default true,
    support_member boolean not null default false,
    first_name varchar(255),
    last_name varchar(255),
    second_name varchar(255),
    display_name varchar(512),
    email varchar(512),
    work_position varchar(512),
    personal_photo text,
    raw_json text,
    loaded_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_bitrix_user_portal_user unique (portal_installation_id, bitrix_user_id)
);

create index if not exists idx_bitrix_user_portal_support
    on bitrix_user(portal_installation_id, support_member);

create index if not exists idx_bitrix_user_portal_name
    on bitrix_user(portal_installation_id, last_name, first_name, id);
