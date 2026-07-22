alter table support_ticket
    add column if not exists crm_contact_id bigint;

-- Повторно ставим в очередь уже созданные CRM-тикеты без клиента.
-- После обновления поиск выполняется и по контактам, а не только по компаниям.
update support_ticket
set crm_sync_status = 'PENDING',
    crm_company_match_status = 'NOT_ATTEMPTED',
    crm_last_error = 'Повторный поиск клиента по телефону после обновления алгоритма'
where crm_item_id is not null
  and crm_company_id is null
  and crm_contact_id is null;
