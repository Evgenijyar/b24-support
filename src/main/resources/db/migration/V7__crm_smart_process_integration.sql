alter table portal_installation
    add column if not exists client_phone varchar(64);

create table crm_integration_config (
    id bigserial primary key,
    admin_portal_id bigint not null references portal_installation(id) on delete cascade,
    enabled boolean not null default true,
    entity_type_id integer not null,
    process_title varchar(255) not null,
    category_id integer not null,
    category_title varchar(255) not null,
    open_stage_id varchar(255) not null,
    open_stage_title varchar(255) not null,
    closed_stage_id varchar(255) not null,
    closed_stage_title varchar(255) not null,
    responsible_user_id varchar(64) not null,
    responsible_user_name varchar(512) not null,
    configured_at timestamptz not null,
    last_validated_at timestamptz,
    last_error text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_crm_integration_admin_portal unique (admin_portal_id)
);

alter table support_ticket
    add column if not exists client_sequence_number bigint;

with ranked as (
    select id,
           row_number() over (partition by client_installation_id order by id) as sequence_number
    from support_ticket
)
update support_ticket ticket
set client_sequence_number = ranked.sequence_number
from ranked
where ticket.id = ranked.id
  and ticket.client_sequence_number is null;

alter table support_ticket
    alter column client_sequence_number set not null;

create unique index if not exists uq_support_ticket_client_sequence
    on support_ticket(client_installation_id, client_sequence_number);

alter table support_ticket
    add column if not exists crm_item_id bigint,
    add column if not exists crm_entity_type_id integer,
    add column if not exists crm_category_id integer,
    add column if not exists crm_company_id bigint,
    add column if not exists crm_company_match_status varchar(32),
    add column if not exists crm_sync_status varchar(32) not null default 'PENDING',
    add column if not exists crm_last_error text,
    add column if not exists crm_created_at timestamptz,
    add column if not exists crm_closed_at timestamptz;

create index if not exists idx_support_ticket_crm_sync
    on support_ticket(crm_sync_status, id);

alter table support_message
    add column if not exists crm_timeline_comment_id bigint,
    add column if not exists crm_sync_status varchar(32) not null default 'PENDING',
    add column if not exists crm_last_error text,
    add column if not exists crm_synced_at timestamptz;

update support_message
set crm_sync_status = 'NOT_CONFIGURED'
where support_ticket_id is null;

create index if not exists idx_support_message_crm_sync
    on support_message(crm_sync_status, id);
