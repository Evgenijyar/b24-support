const state = {
    page: 'portals',
    portals: [],
    portalStats: { total: 0, adminCount: 0, clientCount: 0 },
    bootstrap: null,
    adminSummary: null,
    adminUsers: [],
    crmConfig: null,
    conversations: [],
    selectedConversationId: null,
    conversationMessages: [],
    pendingDeletePortalId: null,
    selectedPortalId: null,
    portalWizard: createEmptyPortalWizard(),
    crmWizard: { step: 1, processes: [], categories: [], stages: [] }
};

const els = {};

document.addEventListener('DOMContentLoaded', async () => {
    cacheElements();
    bindEvents();
    await loadAll();
});

function createEmptyPortalWizard() {
    return {
        step: 1,
        portalId: null,
        role: null,
        title: '',
        webhookUrl: '',
        clientPhone: '',
        selectedUserIds: [],
        processes: [],
        adminReady: false,
        clientReady: false,
        running: false
    };
}

function cacheElements() {
    els.syncIndicator = document.getElementById('syncIndicator');
    els.btnRefresh = document.getElementById('btnRefresh');
    els.portalNavList = document.getElementById('portalNavList');
    els.portalWorkspace = document.getElementById('portalWorkspace');
    els.messagesNotice = document.getElementById('messagesNotice');
    els.messagesPortalTitle = document.getElementById('messagesPortalTitle');
    els.messagesPortalMeta = document.getElementById('messagesPortalMeta');
    els.conversationCount = document.getElementById('conversationCount');
    els.conversationList = document.getElementById('conversationList');
    els.conversationThread = document.getElementById('conversationThread');
    els.publicBaseUrl = document.getElementById('publicBaseUrl');

    els.portalModal = document.getElementById('portalModal');
    els.portalModalStep = document.getElementById('portalModalStep');
    els.portalModalTitle = document.getElementById('portalModalTitle');
    els.portalWizardError = document.getElementById('portalWizardError');
    els.portalWizardStep1 = document.getElementById('portalWizardStep1');
    els.portalWizardAdminUsers = document.getElementById('portalWizardAdminUsers');
    els.portalWizardAdminFinalize = document.getElementById('portalWizardAdminFinalize');
    els.portalWizardClientFinalize = document.getElementById('portalWizardClientFinalize');
    els.wizardPortalTitle = document.getElementById('wizardPortalTitle');
    els.wizardWebhookUrl = document.getElementById('wizardWebhookUrl');
    els.wizardClientPhoneGroup = document.getElementById('wizardClientPhoneGroup');
    els.wizardClientPhone = document.getElementById('wizardClientPhone');
    els.wizardConnectionState = document.getElementById('wizardConnectionState');
    els.wizardUsersList = document.getElementById('wizardUsersList');
    els.adminBotCheck = document.getElementById('adminBotCheck');
    els.adminRoutingCheck = document.getElementById('adminRoutingCheck');
    els.clientWebhookCheck = document.getElementById('clientWebhookCheck');
    els.clientBotCheck = document.getElementById('clientBotCheck');
    els.clientRoutingCheck = document.getElementById('clientRoutingCheck');
    els.wizardConnectCrm = document.getElementById('wizardConnectCrm');
    els.wizardCrmSelectGroup = document.getElementById('wizardCrmSelectGroup');
    els.wizardCrmProcessSelect = document.getElementById('wizardCrmProcessSelect');
    els.wizardCrmHint = document.getElementById('wizardCrmHint');
    els.btnPortalWizardCancel = document.getElementById('btnPortalWizardCancel');
    els.btnPortalWizardBack = document.getElementById('btnPortalWizardBack');
    els.btnPortalWizardNext = document.getElementById('btnPortalWizardNext');
    els.btnPortalWizardFinish = document.getElementById('btnPortalWizardFinish');

    els.deletePortalModal = document.getElementById('deletePortalModal');
    els.deletePortalName = document.getElementById('deletePortalName');
    els.deletePortalError = document.getElementById('deletePortalError');
    els.btnCloseDeletePortalModal = document.getElementById('btnCloseDeletePortalModal');
    els.btnCancelDeletePortal = document.getElementById('btnCancelDeletePortal');
    els.btnConfirmDeletePortal = document.getElementById('btnConfirmDeletePortal');

    els.crmModal = document.getElementById('crmModal');
    els.crmStepLabel = document.getElementById('crmStepLabel');
    els.crmWizardLoading = document.getElementById('crmWizardLoading');
    els.crmWizardError = document.getElementById('crmWizardError');
    els.crmStepProcess = document.getElementById('crmStepProcess');
    els.crmStepCategory = document.getElementById('crmStepCategory');
    els.crmStepMapping = document.getElementById('crmStepMapping');
    els.crmProcessSelect = document.getElementById('crmProcessSelect');
    els.crmCategorySelect = document.getElementById('crmCategorySelect');
    els.crmOpenStageSelect = document.getElementById('crmOpenStageSelect');
    els.crmClosedStageSelect = document.getElementById('crmClosedStageSelect');
    els.crmResponsibleSelect = document.getElementById('crmResponsibleSelect');
    els.btnCrmBack = document.getElementById('btnCrmBack');
    els.btnCrmNext = document.getElementById('btnCrmNext');
    els.btnCrmSave = document.getElementById('btnCrmSave');
}

function bindEvents() {
    document.querySelectorAll('[data-page]').forEach(button => {
        button.addEventListener('click', async () => {
            setActivePage(button.dataset.page);
            if (button.dataset.page === 'messages') {
                await loadConversationContext();
                renderMessages();
            }
        });
    });

    document.getElementById('btnAddPortal').addEventListener('click', openPortalWizard);
    document.getElementById('btnClosePortalModal').addEventListener('click', closePortalWizard);
    els.btnPortalWizardCancel.addEventListener('click', closePortalWizard);
    els.btnPortalWizardBack.addEventListener('click', portalWizardBack);
    els.btnPortalWizardNext.addEventListener('click', portalWizardNext);
    els.btnPortalWizardFinish.addEventListener('click', portalWizardFinish);
    els.portalModal.addEventListener('click', event => {
        if (event.target === els.portalModal) closePortalWizard();
    });
    document.querySelectorAll('input[name="portalRoleChoice"]').forEach(input => {
        input.addEventListener('change', updateWizardRole);
    });
    els.wizardConnectCrm.addEventListener('change', handleWizardCrmToggle);

    els.btnRefresh.addEventListener('click', loadAll);
    els.portalNavList.addEventListener('click', async event => {
        const deleteButton = event.target.closest('[data-nav-delete-portal]');
        if (deleteButton) {
            event.stopPropagation();
            openDeletePortalModal(Number(deleteButton.dataset.navDeletePortal));
            return;
        }
        const item = event.target.closest('[data-portal-id]');
        if (!item) return;
        state.selectedPortalId = Number(item.dataset.portalId);
        state.selectedConversationId = null;
        await Promise.all([loadSelectedPortalContext(), loadConversationContext()]);
        renderPortalNav();
        renderPortalWorkspace();
        renderMessages();
    });
    els.conversationList.addEventListener('click', async event => {
        const item = event.target.closest('[data-conversation-id]');
        if (!item) return;
        state.selectedConversationId = Number(item.dataset.conversationId);
        await loadConversationMessages();
        renderMessages();
    });
    els.portalWorkspace.addEventListener('click', handleWorkspaceClick);
    els.portalWorkspace.addEventListener('submit', handleWorkspaceSubmit);

    els.btnCloseDeletePortalModal.addEventListener('click', closeDeletePortalModal);
    els.btnCancelDeletePortal.addEventListener('click', closeDeletePortalModal);
    els.btnConfirmDeletePortal.addEventListener('click', confirmDeletePortal);
    els.deletePortalModal.addEventListener('click', event => {
        if (event.target === els.deletePortalModal) closeDeletePortalModal();
    });

    document.getElementById('btnCloseCrmModal').addEventListener('click', closeCrmModal);
    els.crmModal.addEventListener('click', event => { if (event.target === els.crmModal) closeCrmModal(); });
    els.btnCrmBack.addEventListener('click', crmWizardBack);
    els.btnCrmNext.addEventListener('click', crmWizardNext);
    els.btnCrmSave.addEventListener('click', saveCrmConfiguration);
}

async function loadAll() {
    setLoading(true);
    try {
        const [bootstrap, portals, adminSummary] = await Promise.all([
            api('/api/bootstrap/status'),
            api('/api/portals'),
            api('/api/admin-portal/summary')
        ]);
        state.bootstrap = bootstrap;
        state.portals = sortPortals(portals.items || []);
        state.portalStats = portals;
        state.adminSummary = adminSummary;

        if (!state.selectedPortalId || !state.portals.some(item => item.id === state.selectedPortalId)) {
            state.selectedPortalId = state.portals[0]?.id || null;
        }
        await Promise.all([loadSelectedPortalContext(), loadConversationContext()]);
        renderAll();
    } catch (error) {
        renderFatalWorkspace(error.message || 'Не удалось загрузить данные');
    } finally {
        setLoading(false);
    }
}

function sortPortals(portals) {
    return [...portals].sort((a, b) => {
        if (a.role !== b.role) return a.role === 'ADMIN' ? -1 : 1;
        return String(a.title || '').localeCompare(String(b.title || ''), 'ru');
    });
}

async function loadSelectedPortalContext() {
    const portal = selectedPortal();
    state.adminUsers = [];
    state.crmConfig = null;
    if (!portal || portal.role !== 'ADMIN') return;

    try {
        const [users, crmConfig] = await Promise.all([
            api(`/api/admin-portal/${portal.id}/users`).catch(() => ({ users: [] })),
            api(`/api/admin-portal/${portal.id}/crm/config`).catch(() => null)
        ]);
        state.adminUsers = users.users || [];
        state.crmConfig = crmConfig;
    } catch (_) {
        state.adminUsers = [];
        state.crmConfig = null;
    }
}

async function loadConversationContext() {
    const portal = selectedPortal();
    state.conversations = [];
    state.conversationMessages = [];
    if (!portal) {
        state.selectedConversationId = null;
        return;
    }

    try {
        const result = await api(`/api/support/conversations?portalId=${portal.id}`);
        state.conversations = result.items || [];
        if (!state.selectedConversationId || !state.conversations.some(item => item.ticketId === state.selectedConversationId)) {
            state.selectedConversationId = state.conversations[0]?.ticketId || null;
        }
        await loadConversationMessages();
    } catch (error) {
        state.conversations = [];
        state.conversationMessages = [];
        state.selectedConversationId = null;
        showMessagesNotice(error.message || 'Не удалось загрузить историю обращений', true);
    }
}

async function loadConversationMessages() {
    if (!state.selectedConversationId) {
        state.conversationMessages = [];
        return;
    }
    const result = await api(`/api/support/conversations/${state.selectedConversationId}/messages`);
    state.conversationMessages = result.items || [];
}


function renderAll() {
    renderPortalNav();
    renderPortalWorkspace();
    renderMessages();
    els.publicBaseUrl.textContent = state.bootstrap?.publicBaseUrl || '—';
}

function renderPortalNav() {
    if (!state.portals.length) {
        els.portalNavList.innerHTML = '<div class="history-empty">Порталы пока не добавлены.</div>';
        return;
    }
    els.portalNavList.innerHTML = state.portals.map(portal => `
        <div class="portal-nav-row ${portal.id === state.selectedPortalId ? 'is-active' : ''}">
            <button class="history-item" type="button" data-portal-id="${portal.id}">
                <span class="history-item-title">${escapeHtml(portal.title)}</span>
                <span class="history-item-meta">${escapeHtml(portal.domain || 'Домен не определён')}</span>
                <span class="${portal.role === 'ADMIN' ? 'history-item-admin' : 'history-item-client'}">${roleLabel(portal.role)} · ${statusLabel(portal.status)}</span>
            </button>
            <button class="portal-nav-delete" type="button" data-nav-delete-portal="${portal.id}" aria-label="Удалить ${escapeAttribute(portal.title)}" title="Удалить портал">✕</button>
        </div>
    `).join('');
}

function renderPortalWorkspace() {
    const portal = selectedPortal();
    if (!portal) {
        els.portalWorkspace.innerHTML = `
            <div class="empty-state text-center">
                <div class="eyebrow">Порталы Bitrix24</div>
                <h2 class="fw-semibold text-white mb-2">Добавь первый портал</h2>
                <p class="text-secondary mb-3">Подключение начинается с админского или клиентского Bitrix24.</p>
                <button class="btn btn-save" type="button" data-open-add-wizard>Добавить портал</button>
            </div>`;
        return;
    }

    els.portalWorkspace.innerHTML = `
        <div class="workspace-heading portal-detail-heading">
            <div>
                <div class="eyebrow">${roleLabel(portal.role)}</div>
                <h1 class="workspace-title">${escapeHtml(portal.title)}</h1>
                <div class="workspace-meta">${escapeHtml(portal.domain || 'Домен определяется из webhook')}</div>
            </div>
            <div class="workspace-actions">
                <span class="status-pill ${statusClass(portal.status)}">${statusLabel(portal.status)}</span>
                <button class="btn btn-danger-soft" type="button" data-delete-portal="${portal.id}">Удалить</button>
            </div>
        </div>
        <div id="workspaceNotice" class="bitrix-notice"></div>
        ${portal.role === 'ADMIN' ? renderAdminPortalWorkspace(portal) : renderClientPortalWorkspace(portal)}
    `;
}

function renderGeneralSettings(portal) {
    return `
        <article class="settings-card">
            <div class="settings-card-head">
                <div><div class="eyebrow">Основные параметры</div><h3>Подключение к Bitrix24</h3></div>
            </div>
            <form data-portal-settings-form="${portal.id}">
                <label class="form-label">Название организации</label>
                <input name="title" class="form-control custom-input" value="${escapeAttribute(portal.title || '')}" required>
                ${portal.role === 'CLIENT' ? `
                    <label class="form-label mt-3">Телефон клиента</label>
                    <input name="clientPhone" class="form-control custom-input" type="tel" value="${escapeAttribute(portal.clientPhone || '')}" required>
                ` : ''}
                <label class="form-label mt-3">Webhook / REST URL</label>
                <input name="webhookUrl" class="form-control custom-input" value="${escapeAttribute(portal.webhookUrl || '')}" required>
                <div class="field-hint">Домен определяется автоматически из webhook. Внутренний код клиента скрыт и управляется backend.</div>
                <div class="portal-meta-grid mt-3">
                    <div><span>Домен</span><b>${escapeHtml(portal.domain || '—')}</b></div>
                    <div><span>Bot ID</span><b>${escapeHtml(portal.botId || '—')}</b></div>
                    <div><span>Последнее событие</span><b>${formatDateTime(portal.lastEventAt) || '—'}</b></div>
                    <div><span>Статус</span><b>${statusLabel(portal.status)}</b></div>
                </div>
                ${portal.lastError ? `<div class="error-panel mt-3">${escapeHtml(portal.lastError)}</div>` : ''}
                <div class="settings-actions">
                    <button class="btn btn-save" type="submit">Сохранить изменения</button>
                </div>
            </form>
        </article>`;
}

function renderAdminPortalWorkspace(portal) {
    const selectedCount = state.adminUsers.filter(item => item.supportMember).length;
    const crm = state.crmConfig;
    return `
        <div class="portal-settings-grid">
            ${renderGeneralSettings(portal)}
            <article class="settings-card">
                <div class="settings-card-head">
                    <div><div class="eyebrow">Техническая настройка</div><h3>Админский бот и маршрутизация</h3></div>
                </div>
                <div class="setup-status-grid">
                    ${miniStatus('Webhook', portal.webhookConfigured && portal.status === 'ACTIVE', portal.webhookConfigured ? 'Указан' : 'Не указан')}
                    ${miniStatus('Бот', !!portal.botId, portal.botId ? `ID ${escapeHtml(portal.botId)}` : 'Не создан')}
                    ${miniStatus('Маршрутизация', !!portal.botEventWebhookUrl, portal.botEventWebhookUrl ? 'Настроена' : 'Не проверена')}
                </div>
                <div class="button-row mt-3">
                    <button class="btn btn-flat" type="button" data-admin-action="test-connection">Проверить webhook</button>
                    <button class="btn btn-save" type="button" data-admin-action="bot/register">Создать / проверить бота</button>
                    <button class="btn btn-flat" type="button" data-admin-action="routing/repair" ${portal.botId ? '' : 'disabled'}>Проверить маршрутизацию</button>
                    <button class="btn btn-flat" type="button" data-resume-setup="${portal.id}">Мастер настройки</button>
                </div>
            </article>
        </div>

        <article class="settings-card mt-3">
            <div class="settings-card-head">
                <div>
                    <div class="eyebrow">Сотрудники</div>
                    <h3>Участники новых чатов</h3>
                    <p>Выбрано: ${selectedCount}. Эти сотрудники автоматически добавляются в каждый новый чат обращения.</p>
                </div>
                <button class="btn btn-flat" type="button" data-admin-load-users>Обновить список</button>
            </div>
            <div class="users-list portal-users-list">
                ${state.adminUsers.length ? state.adminUsers.map(renderUserRow).join('') : '<div class="history-empty p-3">Сотрудники ещё не загружены.</div>'}
            </div>
            <div class="settings-actions">
                <button class="btn btn-save" type="button" data-save-support-users>Сохранить сотрудников</button>
            </div>
        </article>

        <article class="settings-card mt-3">
            <div class="settings-card-head">
                <div>
                    <div class="eyebrow">CRM</div>
                    <h3>${crm?.configured ? escapeHtml(crm.processTitle) : 'Смарт-процесс не подключён'}</h3>
                    ${crm?.configured ? `<p>Воронка: ${escapeHtml(crm.categoryTitle)} · В работе: ${escapeHtml(crm.openStageTitle)} · Завершено: ${escapeHtml(crm.closedStageTitle)} · Ответственный: ${escapeHtml(crm.responsibleUserName)}</p>` : '<p>Подключение можно выполнить сейчас или в любой момент позже.</p>'}
                </div>
            </div>
            ${crm?.lastError ? `<div class="error-panel mb-3">${escapeHtml(crm.lastError)}</div>` : ''}
            <div class="button-row">
                <button class="btn btn-save" type="button" data-crm-setup="${portal.id}">${crm?.configured ? 'Изменить настройки' : 'Подключить смарт-процесс'}</button>
                ${crm?.configured ? `<button class="btn btn-flat" type="button" data-crm-validate="${portal.id}">Проверить интеграцию</button>` : ''}
            </div>
        </article>`;
}

function renderClientPortalWorkspace(portal) {
    return `
        <div class="portal-settings-grid">
            ${renderGeneralSettings(portal)}
            <article class="settings-card">
                <div class="settings-card-head">
                    <div><div class="eyebrow">Клиентский портал</div><h3>Бот и маршрутизация</h3></div>
                </div>
                <div class="setup-status-grid">
                    ${miniStatus('Webhook', portal.webhookConfigured && portal.status === 'ACTIVE', portal.webhookConfigured ? 'Указан' : 'Не указан')}
                    ${miniStatus('Клиентский бот', !!portal.botId, portal.botId ? `ID ${escapeHtml(portal.botId)}` : 'Не создан')}
                    ${miniStatus('Маршрутизация', !!portal.botEventWebhookUrl, portal.botEventWebhookUrl ? 'Настроена' : 'Не проверена')}
                </div>
                <div class="button-row mt-3">
                    <button class="btn btn-flat" type="button" data-client-action="test-connection">Проверить webhook</button>
                    <button class="btn btn-save" type="button" data-client-action="bot/register">Создать / проверить бота</button>
                    <button class="btn btn-flat" type="button" data-client-action="routing/repair" ${portal.botId ? '' : 'disabled'}>Проверить маршрутизацию</button>
                    <button class="btn btn-flat" type="button" data-resume-setup="${portal.id}">Мастер настройки</button>
                </div>
            </article>
        </div>`;
}

function miniStatus(title, ok, detail) {
    return `<div class="mini-status ${ok ? 'is-ok' : ''}"><span>${ok ? '✓' : '•'}</span><div><b>${title}</b><small>${detail}</small></div></div>`;
}

function renderUserRow(user) {
    const initials = [user.firstName, user.lastName].filter(Boolean).map(part => part[0]).join('').slice(0, 2).toUpperCase() || 'U';
    return `
        <label class="user-row ${user.supportMember ? 'is-selected' : ''}">
            <input type="checkbox" data-support-user-id="${user.id}" ${user.supportMember ? 'checked' : ''} ${user.active ? '' : 'disabled'}>
            <span class="user-avatar">${escapeHtml(initials)}</span>
            <span class="user-main"><b>${escapeHtml(user.displayName || 'Без имени')}</b><small>${escapeHtml([user.email, user.workPosition].filter(Boolean).join(' · ') || 'ID ' + user.bitrixUserId)}</small></span>
            <span class="user-id">B24 ID ${escapeHtml(user.bitrixUserId)}</span>
        </label>`;
}

function renderMessages() {
    const portal = selectedPortal();
    if (!portal) {
        els.messagesPortalTitle.textContent = 'Выберите портал';
        els.messagesPortalMeta.textContent = 'Слева выберите портал, чтобы открыть его обращения.';
        els.conversationCount.textContent = '0';
        els.conversationList.innerHTML = '<div class="conversation-empty">Портал не выбран.</div>';
        els.conversationThread.innerHTML = '<div class="thread-empty"><b>Нет выбранного портала</b><span>Выберите портал в левой колонке.</span></div>';
        return;
    }

    els.messagesPortalTitle.textContent = portal.title;
    els.messagesPortalMeta.textContent = portal.role === 'ADMIN'
        ? `${portal.domain || ''} · все обращения клиентских порталов`
        : `${portal.domain || ''} · история обращений этого клиента`;
    els.conversationCount.textContent = String(state.conversations.length);

    if (!state.conversations.length) {
        els.conversationList.innerHTML = '<div class="conversation-empty">Обращений пока нет.</div>';
        els.conversationThread.innerHTML = '<div class="thread-empty"><b>История обращений пуста</b><span>Переписка появится после первого сообщения клиента.</span></div>';
        return;
    }

    els.conversationList.innerHTML = state.conversations.map(conversation => `
        <button class="conversation-item ${conversation.ticketId === state.selectedConversationId ? 'is-active' : ''}" type="button" data-conversation-id="${conversation.ticketId}">
            <span class="conversation-item-top"><b>Обращение №${escapeHtml(conversation.sequenceNumber || conversation.ticketId)}</b><small>${formatDateTime(conversation.lastMessageAt) || '—'}</small></span>
            <span class="conversation-requester">${escapeHtml(conversation.requesterName || 'Клиент')}</span>
            ${portal.role === 'ADMIN' ? `<span class="conversation-client">${escapeHtml(conversation.clientTitle || 'Клиентский портал')}</span>` : ''}
            <span class="conversation-preview">${escapeHtml(conversation.lastMessagePreview || '')}</span>
        </button>
    `).join('');

    const selected = state.conversations.find(item => item.ticketId === state.selectedConversationId);
    if (!selected) {
        els.conversationThread.innerHTML = '<div class="thread-empty"><b>Выберите обращение</b><span>Справа будет показана вся переписка.</span></div>';
        return;
    }

    const messages = state.conversationMessages.map(message => renderConversationMessage(message)).join('');
    els.conversationThread.innerHTML = `
        <div class="thread-head">
            <div><div class="eyebrow">${escapeHtml(selected.clientTitle || portal.title)}</div><h3>Обращение №${escapeHtml(selected.sequenceNumber || selected.ticketId)}</h3><span>${escapeHtml(selected.requesterName || 'Клиент')} · открыто ${formatDateTime(selected.openedAt) || '—'}</span></div>
            <span class="status-pill ${conversationStatusClass(selected.status)}">${conversationStatusLabel(selected.status)}</span>
        </div>
        <div class="thread-messages">${messages || '<div class="thread-empty compact"><b>Сообщений пока нет</b></div>'}</div>`;
    requestAnimationFrame(() => {
        const container = els.conversationThread.querySelector('.thread-messages');
        if (container) container.scrollTop = container.scrollHeight;
    });
}

function renderConversationMessage(message) {
    const direction = String(message.direction || 'CLIENT_TO_ADMIN');
    const kind = direction === 'ADMIN_TO_CLIENT' ? 'support' : direction === 'SYSTEM_TO_CLIENT' ? 'system' : 'client';
    const role = kind === 'support' ? 'Техподдержка' : kind === 'system' ? 'Система' : 'Клиент';
    return `
        <article class="chat-message is-${kind}">
            <div class="chat-message-meta"><b>${escapeHtml(message.senderName || role)}</b><span>${role} · ${formatDateTime(message.createdAt) || '—'}</span></div>
            <div class="chat-message-bubble">${formatMessageBody(message.text || '')}</div>
        </article>`;
}

function formatMessageBody(value) {
    return escapeHtml(value).replaceAll('\n', '<br>');
}

function conversationStatusLabel(status) {
    return ({ OPENING: 'Создаётся', OPEN: 'В работе', CLOSED: 'Закрыто', DELETING: 'Удаляется', DELETED: 'Чат удалён', ERROR: 'Ошибка' })[status] || status || '—';
}

function conversationStatusClass(status) {
    return status === 'OPEN' ? 'active' : status === 'ERROR' ? 'error' : status === 'DELETED' ? 'disabled' : '';
}

function showMessagesNotice(message, isError) {
    if (!els.messagesNotice) return;
    els.messagesNotice.textContent = message;
    els.messagesNotice.className = `bitrix-notice is-visible ${isError ? 'is-error' : ''}`;
}

function renderFatalWorkspace(message) {
    els.portalWorkspace.innerHTML = `<div class="error-panel">${escapeHtml(message)}</div>`;
}

async function handleWorkspaceClick(event) {
    const portal = selectedPortal();
    if (!portal) {
        if (event.target.closest('[data-open-add-wizard]')) openPortalWizard();
        return;
    }

    const adminActionName = event.target.closest('[data-admin-action]')?.dataset.adminAction;
    if (adminActionName) return runPortalAction(portal, `/api/admin-portal/${portal.id}/${adminActionName}`);
    const clientActionName = event.target.closest('[data-client-action]')?.dataset.clientAction;
    if (clientActionName) return runPortalAction(portal, `/api/client-portals/${portal.id}/${clientActionName}`);
    if (event.target.closest('[data-admin-load-users]')) return loadAdminUsers(portal.id, true);
    if (event.target.closest('[data-save-support-users]')) return saveSupportUsersFromWorkspace(portal.id);
    if (event.target.closest('[data-delete-portal]')) return openDeletePortalModal(portal.id);
    if (event.target.closest('[data-resume-setup]')) return openPortalWizard(portal);

    const setupId = event.target.closest('[data-crm-setup]')?.dataset.crmSetup;
    if (setupId) return openCrmModal(Number(setupId));
    const validateId = event.target.closest('[data-crm-validate]')?.dataset.crmValidate;
    if (validateId) return validateCrmConfiguration(Number(validateId));

    if (event.target.matches('[data-support-user-id]')) {
        event.target.closest('.user-row')?.classList.toggle('is-selected', event.target.checked);
    }
}

async function handleWorkspaceSubmit(event) {
    const form = event.target.closest('[data-portal-settings-form]');
    if (!form) return;
    event.preventDefault();
    const portal = selectedPortal();
    if (!portal) return;

    const data = new FormData(form);
    const payload = {
        role: portal.role,
        clientCode: portal.clientCode,
        title: String(data.get('title') || '').trim(),
        domain: null,
        memberId: portal.memberId,
        clientPhone: portal.role === 'CLIENT' ? String(data.get('clientPhone') || '').trim() : null,
        webhookUrl: String(data.get('webhookUrl') || '').trim(),
        botId: portal.botId,
        supportDialogId: portal.supportDialogId,
        status: portal.status
    };
    await performWorkspaceOperation(async () => {
        await api(`/api/portals/${portal.id}`, { method: 'PUT', body: JSON.stringify(payload) });
        await loadAll();
        showWorkspaceNotice('Настройки портала сохранены', false);
    });
}

async function runPortalAction(portal, url) {
    await performWorkspaceOperation(async () => {
        const result = await api(url, { method: 'POST' });
        await loadAll();
        showWorkspaceNotice(result.message || 'Операция выполнена', !result.success);
    });
}

async function loadAdminUsers(portalId, showResult) {
    await performWorkspaceOperation(async () => {
        const result = await api(`/api/admin-portal/${portalId}/load-users`, { method: 'POST' });
        const users = await api(`/api/admin-portal/${portalId}/users`);
        state.adminUsers = users.users || [];
        await loadAll();
        if (showResult) showWorkspaceNotice(result.message || 'Сотрудники загружены', !result.success);
    });
}

async function saveSupportUsersFromWorkspace(portalId) {
    const ids = [...els.portalWorkspace.querySelectorAll('[data-support-user-id]:checked')].map(input => Number(input.dataset.supportUserId)).filter(Number.isFinite);
    await performWorkspaceOperation(async () => {
        const result = await api(`/api/admin-portal/${portalId}/support-users`, { method: 'PUT', body: JSON.stringify({ userIds: ids }) });
        state.adminUsers = result.users || [];
        await loadAll();
        showWorkspaceNotice('Список сотрудников сохранён', false);
    });
}

function openDeletePortalModal(portalId) {
    const portal = state.portals.find(item => item.id === portalId);
    if (!portal) return;
    state.pendingDeletePortalId = portalId;
    els.deletePortalName.textContent = portal.title;
    els.deletePortalError.textContent = '';
    els.deletePortalError.classList.add('d-none');
    els.deletePortalModal.classList.remove('d-none');
    els.deletePortalModal.setAttribute('aria-hidden', 'false');
}

function closeDeletePortalModal() {
    if (els.btnConfirmDeletePortal.disabled) return;
    state.pendingDeletePortalId = null;
    els.deletePortalModal.classList.add('d-none');
    els.deletePortalModal.setAttribute('aria-hidden', 'true');
}

async function confirmDeletePortal() {
    const portalId = state.pendingDeletePortalId;
    if (!portalId) return;
    els.btnConfirmDeletePortal.disabled = true;
    els.btnCancelDeletePortal.disabled = true;
    els.btnConfirmDeletePortal.textContent = 'Удаляю…';
    try {
        await api(`/api/portals/${portalId}`, { method: 'DELETE' });
        state.pendingDeletePortalId = null;
        state.selectedPortalId = null;
        state.selectedConversationId = null;
        els.deletePortalModal.classList.add('d-none');
        els.deletePortalModal.setAttribute('aria-hidden', 'true');
        await loadAll();
        showWorkspaceNotice('Портал и его локальная история удалены', false);
    } catch (error) {
        els.deletePortalError.textContent = error.message || 'Не удалось удалить портал';
        els.deletePortalError.classList.remove('d-none');
    } finally {
        els.btnConfirmDeletePortal.disabled = false;
        els.btnCancelDeletePortal.disabled = false;
        els.btnConfirmDeletePortal.textContent = 'Удалить';
    }
}

async function performWorkspaceOperation(operation) {
    setLoading(true);
    try { await operation(); } catch (error) { showWorkspaceNotice(error.message || 'Операция не выполнена', true); } finally { setLoading(false); }
}

function showWorkspaceNotice(message, isError) {
    const notice = document.getElementById('workspaceNotice');
    if (!notice) return;
    notice.textContent = message;
    notice.className = `bitrix-notice is-visible ${isError ? 'is-error' : ''}`;
}

function openPortalWizard(existingPortal = null) {
    state.portalWizard = createEmptyPortalWizard();
    if (existingPortal) {
        state.portalWizard.portalId = existingPortal.id;
        state.portalWizard.role = existingPortal.role;
        state.portalWizard.title = existingPortal.title || '';
        state.portalWizard.webhookUrl = existingPortal.webhookUrl || '';
        state.portalWizard.clientPhone = existingPortal.clientPhone || '';
    }
    clearPortalWizardError();
    els.portalModal.classList.remove('d-none');
    els.portalModal.setAttribute('aria-hidden', 'false');
    document.querySelectorAll('input[name="portalRoleChoice"]').forEach(input => {
        input.checked = input.value === state.portalWizard.role;
        input.disabled = Boolean(existingPortal);
    });
    els.wizardPortalTitle.value = state.portalWizard.title;
    els.wizardWebhookUrl.value = state.portalWizard.webhookUrl;
    els.wizardClientPhone.value = state.portalWizard.clientPhone;
    updateWizardRole();
    renderPortalWizardStep();
}

function closePortalWizard() {
    if (state.portalWizard.running) return;
    els.portalModal.classList.add('d-none');
    els.portalModal.setAttribute('aria-hidden', 'true');
    state.portalWizard = createEmptyPortalWizard();
}

function updateWizardRole() {
    const checked = document.querySelector('input[name="portalRoleChoice"]:checked');
    state.portalWizard.role = checked?.value || state.portalWizard.role;
    const isClient = state.portalWizard.role === 'CLIENT';
    els.wizardClientPhoneGroup.classList.toggle('d-none', !isClient);
}

async function portalWizardNext() {
    clearPortalWizardError();
    if (state.portalWizard.step === 1) {
        await saveWizardPortalAndContinue();
        return;
    }
    if (state.portalWizard.role === 'ADMIN' && state.portalWizard.step === 2) {
        const selected = [...els.wizardUsersList.querySelectorAll('[data-wizard-user-id]:checked')].map(input => Number(input.dataset.wizardUserId)).filter(Number.isFinite);
        if (!selected.length) return showPortalWizardError('Выбери хотя бы одного сотрудника техподдержки');
        state.portalWizard.selectedUserIds = selected;
        try {
            setPortalWizardRunning(true);
            await api(`/api/admin-portal/${state.portalWizard.portalId}/support-users`, { method: 'PUT', body: JSON.stringify({ userIds: selected }) });
            state.portalWizard.step = 3;
            renderPortalWizardStep();
            await runAdminFinalization();
        } catch (error) {
            showPortalWizardError(error.message || 'Не удалось сохранить сотрудников');
        } finally {
            setPortalWizardRunning(false);
        }
    }
}

async function saveWizardPortalAndContinue() {
    const role = state.portalWizard.role;
    const title = els.wizardPortalTitle.value.trim();
    const webhookUrl = els.wizardWebhookUrl.value.trim();
    const clientPhone = els.wizardClientPhone.value.trim();
    if (!role) return showPortalWizardError('Выбери тип портала');
    if (!title) return showPortalWizardError('Укажи название организации');
    if (!webhookUrl) return showPortalWizardError('Укажи Webhook / REST URL');
    if (role === 'CLIENT' && !clientPhone) return showPortalWizardError('Для клиентского портала укажи телефон клиента');

    const existing = state.portals.find(item => item.id === state.portalWizard.portalId);
    const payload = {
        role,
        clientCode: existing?.clientCode || null,
        title,
        domain: null,
        memberId: existing?.memberId || null,
        clientPhone: role === 'CLIENT' ? clientPhone : null,
        webhookUrl,
        botId: existing?.botId || null,
        supportDialogId: existing?.supportDialogId || null,
        status: existing?.status || 'DRAFT'
    };

    try {
        setPortalWizardRunning(true);
        const portal = await api(state.portalWizard.portalId ? `/api/portals/${state.portalWizard.portalId}` : '/api/portals', {
            method: state.portalWizard.portalId ? 'PUT' : 'POST',
            body: JSON.stringify(payload)
        });
        state.portalWizard.portalId = portal.id;
        state.portalWizard.title = portal.title;
        state.portalWizard.webhookUrl = portal.webhookUrl;
        state.portalWizard.clientPhone = portal.clientPhone || '';
        state.portalWizard.step = 2;
        renderPortalWizardStep();
        if (role === 'ADMIN') await runAdminConnectionAndLoadUsers();
        else await runClientFinalization();
    } catch (error) {
        showPortalWizardError(error.message || 'Не удалось сохранить портал');
    } finally {
        setPortalWizardRunning(false);
    }
}

function portalWizardBack() {
    if (state.portalWizard.running) return;
    if (state.portalWizard.step > 1) state.portalWizard.step -= 1;
    clearPortalWizardError();
    renderPortalWizardStep();
}

function renderPortalWizardStep() {
    const wizard = state.portalWizard;
    const adminStep = wizard.role === 'ADMIN';
    els.portalWizardStep1.classList.toggle('d-none', wizard.step !== 1);
    els.portalWizardAdminUsers.classList.toggle('d-none', !(adminStep && wizard.step === 2));
    els.portalWizardAdminFinalize.classList.toggle('d-none', !(adminStep && wizard.step === 3));
    els.portalWizardClientFinalize.classList.toggle('d-none', !(!adminStep && wizard.step === 2));

    const totalSteps = adminStep ? 3 : 2;
    els.portalModalStep.textContent = `Шаг ${wizard.step} из ${totalSteps}`;
    els.portalModalTitle.textContent = wizard.portalId ? 'Настройка портала' : 'Добавить портал';
    els.btnPortalWizardBack.classList.toggle('d-none', wizard.step === 1);
    els.btnPortalWizardNext.classList.toggle('d-none', (adminStep && wizard.step === 3) || (!adminStep && wizard.step === 2));
    els.btnPortalWizardFinish.classList.toggle('d-none', !((adminStep && wizard.step === 3) || (!adminStep && wizard.step === 2)));
    els.btnPortalWizardFinish.disabled = adminStep ? !wizard.adminReady : !wizard.clientReady;
    els.btnPortalWizardFinish.textContent = 'Готово';
}

async function runAdminConnectionAndLoadUsers() {
    setWizardBanner('Проверяю подключение к порталу…', 'loading');
    try {
        const connection = await api(`/api/admin-portal/${state.portalWizard.portalId}/test-connection`, { method: 'POST' });
        if (!connection.success) throw new Error(connection.message || 'Webhook не прошёл проверку');
        setWizardBanner('Подключение к порталу установлено', 'success');

        const load = await api(`/api/admin-portal/${state.portalWizard.portalId}/load-users`, { method: 'POST' });
        if (!load.success) throw new Error(load.message || 'Не удалось загрузить сотрудников');
        const users = await api(`/api/admin-portal/${state.portalWizard.portalId}/users`);
        state.adminUsers = users.users || [];
        state.portalWizard.selectedUserIds = state.adminUsers.filter(item => item.supportMember).map(item => item.id);
        renderWizardUsers();
    } catch (error) {
        setWizardBanner('Подключение не установлено', 'error');
        showPortalWizardError(`${error.message || 'Webhook не работает'}. Вернись назад и проверь URL.`);
    }
}

function renderWizardUsers() {
    if (!state.adminUsers.length) {
        els.wizardUsersList.innerHTML = '<div class="history-empty p-3">Сотрудники не найдены.</div>';
        return;
    }
    els.wizardUsersList.innerHTML = state.adminUsers.filter(item => item.active).map(user => {
        const initials = [user.firstName, user.lastName].filter(Boolean).map(part => part[0]).join('').slice(0, 2).toUpperCase() || 'U';
        const selected = state.portalWizard.selectedUserIds.includes(user.id);
        return `<label class="user-row ${selected ? 'is-selected' : ''}"><input type="checkbox" data-wizard-user-id="${user.id}" ${selected ? 'checked' : ''}><span class="user-avatar">${escapeHtml(initials)}</span><span class="user-main"><b>${escapeHtml(user.displayName || 'Без имени')}</b><small>${escapeHtml([user.email, user.workPosition].filter(Boolean).join(' · ') || 'ID ' + user.bitrixUserId)}</small></span><span class="user-id">B24 ID ${escapeHtml(user.bitrixUserId)}</span></label>`;
    }).join('');
    els.wizardUsersList.querySelectorAll('[data-wizard-user-id]').forEach(input => input.addEventListener('change', () => input.closest('.user-row')?.classList.toggle('is-selected', input.checked)));
}

async function runAdminFinalization() {
    state.portalWizard.adminReady = false;
    renderPortalWizardStep();
    resetSetupCheck(els.adminBotCheck, 'Создаю админского бота…');
    resetSetupCheck(els.adminRoutingCheck, 'Ожидание создания бота');
    try {
        setSetupCheck(els.adminBotCheck, 'loading', 'Создаю админского бота…');
        const bot = await api(`/api/admin-portal/${state.portalWizard.portalId}/bot/register`, { method: 'POST' });
        if (!bot.success) throw new Error(bot.message || 'Не удалось создать бота');
        setSetupCheck(els.adminBotCheck, 'success', 'Бот создан');

        setSetupCheck(els.adminRoutingCheck, 'loading', 'Проверяю маршрутизацию…');
        const routing = await api(`/api/admin-portal/${state.portalWizard.portalId}/routing/repair`, { method: 'POST' });
        if (!routing.success) throw new Error(routing.message || 'Маршрутизация не прошла проверку');
        setSetupCheck(els.adminRoutingCheck, 'success', 'Маршрутизация проверена');
        state.portalWizard.adminReady = true;
        renderPortalWizardStep();
    } catch (error) {
        const target = els.adminBotCheck.classList.contains('is-success') ? els.adminRoutingCheck : els.adminBotCheck;
        setSetupCheck(target, 'error', error.message || 'Ошибка настройки');
        showPortalWizardError(error.message || 'Не удалось завершить техническую настройку');
    }
}

async function runClientFinalization() {
    state.portalWizard.clientReady = false;
    renderPortalWizardStep();
    [els.clientWebhookCheck, els.clientBotCheck, els.clientRoutingCheck].forEach(item => resetSetupCheck(item, 'Ожидание запуска'));
    try {
        setSetupCheck(els.clientWebhookCheck, 'loading', 'Проверяю webhook…');
        const connection = await api(`/api/client-portals/${state.portalWizard.portalId}/test-connection`, { method: 'POST' });
        if (!connection.success) throw new Error(connection.message || 'Webhook не прошёл проверку');
        setSetupCheck(els.clientWebhookCheck, 'success', 'Webhook проверен');

        setSetupCheck(els.clientBotCheck, 'loading', 'Создаю клиентского бота…');
        const bot = await api(`/api/client-portals/${state.portalWizard.portalId}/bot/register`, { method: 'POST' });
        if (!bot.success) throw new Error(bot.message || 'Не удалось создать клиентского бота');
        setSetupCheck(els.clientBotCheck, 'success', 'Клиентский бот создан');

        setSetupCheck(els.clientRoutingCheck, 'loading', 'Проверяю маршрутизацию…');
        const routing = await api(`/api/client-portals/${state.portalWizard.portalId}/routing/repair`, { method: 'POST' });
        if (!routing.success) throw new Error(routing.message || 'Маршрутизация не прошла проверку');
        setSetupCheck(els.clientRoutingCheck, 'success', 'Маршрутизация проверена');
        state.portalWizard.clientReady = true;
        renderPortalWizardStep();
    } catch (error) {
        const targets = [els.clientWebhookCheck, els.clientBotCheck, els.clientRoutingCheck];
        const target = targets.find(item => !item.classList.contains('is-success')) || els.clientRoutingCheck;
        setSetupCheck(target, 'error', error.message || 'Ошибка настройки');
        showPortalWizardError(error.message || 'Не удалось завершить настройку клиентского портала');
    }
}

async function handleWizardCrmToggle() {
    const checked = els.wizardConnectCrm.checked;
    els.wizardCrmSelectGroup.classList.toggle('d-none', !checked);
    if (!checked || state.portalWizard.processes.length) return;
    els.wizardCrmProcessSelect.innerHTML = '<option>Загрузка…</option>';
    els.wizardCrmProcessSelect.disabled = true;
    try {
        const processes = await api(`/api/admin-portal/${state.portalWizard.portalId}/crm/processes`);
        state.portalWizard.processes = (processes || []).filter(item => item.eligible);
        if (!state.portalWizard.processes.length) throw new Error('Нет доступных смарт-процессов со стадиями и клиентами');
        els.wizardCrmProcessSelect.innerHTML = state.portalWizard.processes.map(item => `<option value="${item.entityTypeId}">${escapeHtml(item.title)}</option>`).join('');
        els.wizardCrmProcessSelect.disabled = false;
    } catch (error) {
        els.wizardCrmProcessSelect.innerHTML = '<option value="">Не удалось загрузить</option>';
        els.wizardCrmHint.textContent = error.message || 'Не удалось загрузить смарт-процессы';
        showPortalWizardError(error.message || 'Не удалось загрузить смарт-процессы');
    }
}

async function portalWizardFinish() {
    if (state.portalWizard.running) return;
    if (state.portalWizard.role === 'ADMIN' && !state.portalWizard.adminReady) return;
    if (state.portalWizard.role === 'CLIENT' && !state.portalWizard.clientReady) return;

    try {
        setPortalWizardRunning(true);
        if (state.portalWizard.role === 'ADMIN' && els.wizardConnectCrm.checked) {
            const entityTypeId = Number(els.wizardCrmProcessSelect.value);
            if (!Number.isFinite(entityTypeId)) throw new Error('Выбери смарт-процесс');
            await autoConfigureCrm(state.portalWizard.portalId, entityTypeId);
        }
        const portalId = state.portalWizard.portalId;
        closePortalWizardForce();
        state.selectedPortalId = portalId;
        await loadAll();
        setActivePage('portals');
        showWorkspaceNotice('Портал полностью настроен', false);
    } catch (error) {
        showPortalWizardError(error.message || 'Не удалось завершить настройку');
    } finally {
        setPortalWizardRunning(false);
    }
}

async function autoConfigureCrm(portalId, entityTypeId) {
    const categories = await api(`/api/admin-portal/${portalId}/crm/processes/${entityTypeId}/categories`);
    if (!categories?.length) throw new Error('У смарт-процесса нет доступной воронки');
    const category = categories.find(item => item.defaultCategory) || categories[0];
    const stages = await api(`/api/admin-portal/${portalId}/crm/processes/${entityTypeId}/categories/${category.id}/stages`);
    if (!stages?.length) throw new Error('У выбранного смарт-процесса нет стадий');
    const open = pickOpenStage(stages);
    const closed = pickClosedStage(stages);
    if (!open || !closed) throw new Error('Не удалось автоматически определить рабочую и завершающую стадии');

    const selectedDbId = state.portalWizard.selectedUserIds[0];
    const responsible = state.adminUsers.find(item => item.id === selectedDbId) || state.adminUsers.find(item => item.supportMember) || state.adminUsers.find(item => item.active);
    if (!responsible?.bitrixUserId) throw new Error('Не удалось определить ответственного сотрудника');

    await api(`/api/admin-portal/${portalId}/crm/config`, {
        method: 'PUT',
        body: JSON.stringify({
            entityTypeId,
            categoryId: category.id,
            openStageId: open.id,
            closedStageId: closed.id,
            responsibleUserId: responsible.bitrixUserId
        })
    });
}

function pickOpenStage(stages) {
    const process = stages.filter(item => String(item.semantics).toUpperCase() === 'PROCESS');
    return process.find(item => normalizeText(item.name).includes('в работе')) || process[0] || stages[0];
}

function pickClosedStage(stages) {
    const success = stages.filter(item => String(item.semantics).toUpperCase() === 'SUCCESS');
    return success.find(item => normalizeText(item.name).includes('заверш')) || success[0] || stages[stages.length - 1];
}

function setWizardBanner(text, type) {
    els.wizardConnectionState.textContent = text;
    els.wizardConnectionState.className = `wizard-banner is-${type}`;
}

function resetSetupCheck(element, text) { setSetupCheck(element, 'pending', text); }
function setSetupCheck(element, stateName, text) {
    element.className = `setup-check is-${stateName}`;
    element.querySelector('.setup-check-icon').textContent = stateName === 'success' ? '✓' : stateName === 'error' ? '!' : stateName === 'loading' ? '…' : '•';
    element.querySelector('small').textContent = text;
}

function setPortalWizardRunning(running) {
    state.portalWizard.running = running;
    [els.btnPortalWizardCancel, els.btnPortalWizardBack, els.btnPortalWizardNext, els.btnPortalWizardFinish].forEach(button => button.disabled = running);
}

function closePortalWizardForce() {
    state.portalWizard.running = false;
    els.portalModal.classList.add('d-none');
    els.portalModal.setAttribute('aria-hidden', 'true');
    state.portalWizard = createEmptyPortalWizard();
}

function showPortalWizardError(message) {
    els.portalWizardError.textContent = message;
    els.portalWizardError.classList.remove('d-none');
}
function clearPortalWizardError() {
    els.portalWizardError.textContent = '';
    els.portalWizardError.classList.add('d-none');
}

async function openCrmModal(portalId) {
    state.crmWizard = { portalId, step: 1, processes: [], categories: [], stages: [] };
    clearCrmWizardError();
    els.crmModal.classList.remove('d-none');
    els.crmModal.setAttribute('aria-hidden', 'false');
    renderCrmWizardStep();
    setCrmWizardLoading(true);
    try {
        const processes = await api(`/api/admin-portal/${portalId}/crm/processes`);
        state.crmWizard.processes = processes || [];
        const eligible = state.crmWizard.processes.filter(item => item.eligible);
        if (!eligible.length) throw new Error('Нет доступных смарт-процессов');
        els.crmProcessSelect.innerHTML = eligible.map(item => `<option value="${item.entityTypeId}">${escapeHtml(item.title)}</option>`).join('');
        if (state.crmConfig?.configured) els.crmProcessSelect.value = String(state.crmConfig.entityTypeId);
    } catch (error) {
        showCrmWizardError(error.message || 'Не удалось загрузить смарт-процессы');
    } finally { setCrmWizardLoading(false); }
}

function closeCrmModal() {
    els.crmModal.classList.add('d-none');
    els.crmModal.setAttribute('aria-hidden', 'true');
}

async function crmWizardNext() {
    clearCrmWizardError();
    setCrmWizardLoading(true);
    try {
        if (state.crmWizard.step === 1) {
            const entityTypeId = Number(els.crmProcessSelect.value);
            state.crmWizard.entityTypeId = entityTypeId;
            state.crmWizard.categories = await api(`/api/admin-portal/${state.crmWizard.portalId}/crm/processes/${entityTypeId}/categories`);
            if (!state.crmWizard.categories.length) throw new Error('Нет доступных воронок');
            els.crmCategorySelect.innerHTML = state.crmWizard.categories.map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join('');
            if (state.crmConfig?.configured && Number(state.crmConfig.entityTypeId) === entityTypeId) els.crmCategorySelect.value = String(state.crmConfig.categoryId);
            state.crmWizard.step = 2;
        } else if (state.crmWizard.step === 2) {
            const categoryId = Number(els.crmCategorySelect.value);
            state.crmWizard.categoryId = categoryId;
            state.crmWizard.stages = await api(`/api/admin-portal/${state.crmWizard.portalId}/crm/processes/${state.crmWizard.entityTypeId}/categories/${categoryId}/stages`);
            const processStages = state.crmWizard.stages.filter(item => String(item.semantics).toUpperCase() === 'PROCESS');
            const successStages = state.crmWizard.stages.filter(item => String(item.semantics).toUpperCase() === 'SUCCESS');
            els.crmOpenStageSelect.innerHTML = (processStages.length ? processStages : state.crmWizard.stages).map(item => `<option value="${escapeAttribute(item.id)}">${escapeHtml(item.name)}</option>`).join('');
            els.crmClosedStageSelect.innerHTML = (successStages.length ? successStages : state.crmWizard.stages).map(item => `<option value="${escapeAttribute(item.id)}">${escapeHtml(item.name)}</option>`).join('');
            els.crmResponsibleSelect.innerHTML = state.adminUsers.filter(item => item.active).map(item => `<option value="${escapeAttribute(item.bitrixUserId)}">${escapeHtml(item.displayName || 'ID ' + item.bitrixUserId)}</option>`).join('');
            if (!els.crmResponsibleSelect.options.length) throw new Error('Сначала загрузи сотрудников');
            if (state.crmConfig?.configured && Number(state.crmConfig.categoryId) === categoryId) {
                els.crmOpenStageSelect.value = state.crmConfig.openStageId;
                els.crmClosedStageSelect.value = state.crmConfig.closedStageId;
                els.crmResponsibleSelect.value = state.crmConfig.responsibleUserId;
            }
            state.crmWizard.step = 3;
        }
        renderCrmWizardStep();
    } catch (error) { showCrmWizardError(error.message || 'Не удалось перейти к следующему шагу'); }
    finally { setCrmWizardLoading(false); }
}

function crmWizardBack() {
    if (state.crmWizard.step > 1) state.crmWizard.step -= 1;
    clearCrmWizardError();
    renderCrmWizardStep();
}

function renderCrmWizardStep() {
    const step = state.crmWizard.step;
    els.crmStepLabel.textContent = `Шаг ${step} из 3`;
    els.crmStepProcess.classList.toggle('d-none', step !== 1);
    els.crmStepCategory.classList.toggle('d-none', step !== 2);
    els.crmStepMapping.classList.toggle('d-none', step !== 3);
    els.btnCrmBack.classList.toggle('d-none', step === 1);
    els.btnCrmNext.classList.toggle('d-none', step === 3);
    els.btnCrmSave.classList.toggle('d-none', step !== 3);
}

async function saveCrmConfiguration() {
    clearCrmWizardError();
    setCrmWizardLoading(true);
    try {
        state.crmConfig = await api(`/api/admin-portal/${state.crmWizard.portalId}/crm/config`, {
            method: 'PUT',
            body: JSON.stringify({
                entityTypeId: state.crmWizard.entityTypeId,
                categoryId: state.crmWizard.categoryId,
                openStageId: els.crmOpenStageSelect.value,
                closedStageId: els.crmClosedStageSelect.value,
                responsibleUserId: els.crmResponsibleSelect.value
            })
        });
        closeCrmModal();
        renderPortalWorkspace();
        showWorkspaceNotice('Интеграция со смарт-процессом сохранена', false);
    } catch (error) { showCrmWizardError(error.message || 'Не удалось сохранить CRM-интеграцию'); }
    finally { setCrmWizardLoading(false); }
}

async function validateCrmConfiguration(portalId) {
    await performWorkspaceOperation(async () => {
        const result = await api(`/api/admin-portal/${portalId}/crm/config/validate`, { method: 'POST' });
        state.crmConfig = result.config;
        renderPortalWorkspace();
        showWorkspaceNotice(result.message, !result.success);
    });
}

function setCrmWizardLoading(loading) {
    els.crmWizardLoading.classList.toggle('d-none', !loading);
    [els.btnCrmBack, els.btnCrmNext, els.btnCrmSave].forEach(button => button.disabled = loading);
}
function showCrmWizardError(message) { els.crmWizardError.textContent = message; els.crmWizardError.classList.remove('d-none'); }
function clearCrmWizardError() { els.crmWizardError.textContent = ''; els.crmWizardError.classList.add('d-none'); }

function setActivePage(page) {
    state.page = page;
    document.querySelectorAll('.page-section').forEach(section => section.classList.add('d-none'));
    document.getElementById(`${page}Page`)?.classList.remove('d-none');
    document.querySelectorAll('.top-nav-link').forEach(button => button.classList.toggle('active', button.dataset.page === page));
}

function selectedPortal() { return state.portals.find(item => item.id === state.selectedPortalId) || null; }
function roleLabel(role) { return role === 'ADMIN' ? 'Админский портал' : 'Клиентский портал'; }
function statusLabel(status) { return ({ DRAFT: 'Черновик', ACTIVE: 'Активен', ERROR: 'Ошибка', DISABLED: 'Отключён' })[status] || status || '—'; }
function statusClass(status) { return status === 'ACTIVE' ? 'active' : status === 'ERROR' ? 'error' : status === 'DISABLED' ? 'disabled' : ''; }
function normalizeText(value) { return String(value || '').trim().toLocaleLowerCase('ru-RU'); }

function formatDateTime(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

function setLoading(loading) {
    els.syncIndicator.classList.toggle('d-none', !loading);
    els.btnRefresh.disabled = loading;
}

async function api(url, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    const response = await fetch(url, { ...options, headers });
    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try {
            const data = await response.json();
            message = data.detail || data.message || data.error || message;
        } catch (_) {
            const text = await response.text();
            if (text) message = text;
        }
        throw new Error(message);
    }
    if (response.status === 204) return null;
    return response.json();
}

function escapeHtml(value) {
    return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}
function escapeAttribute(value) { return escapeHtml(value); }
