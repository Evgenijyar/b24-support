alter table incoming_bitrix_event
    drop constraint if exists incoming_bitrix_event_portal_installation_id_fkey;

alter table incoming_bitrix_event
    add constraint incoming_bitrix_event_portal_installation_id_fkey
        foreign key (portal_installation_id) references portal_installation(id) on delete cascade;

alter table support_message
    drop constraint if exists support_message_client_installation_id_fkey;

alter table support_message
    add constraint support_message_client_installation_id_fkey
        foreign key (client_installation_id) references portal_installation(id) on delete cascade;

alter table message_route_mapping
    drop constraint if exists message_route_mapping_client_installation_id_fkey;

alter table message_route_mapping
    add constraint message_route_mapping_client_installation_id_fkey
        foreign key (client_installation_id) references portal_installation(id) on delete cascade;
