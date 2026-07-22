const state = {
    page: 'overview',
    portals: [],
    portalStats: { total: 0, adminCount: 0, clientCount: 0 },
    bootstrap: null,
    adminSummary: null,
    adminUsers: [],
    clientMessages: [],
    editingPortal: null,
    crmConfig: null,
    crmWizard: { step: 1, processes: [], categories: [], stages: [] }
};

const els = {};

document.addEventListener('DOMContentLoaded', async () => {
    cacheElements();
    bindEvents();
    await loadAll();
});

function cacheElements() {
    els.syncIndicator = document.getElementById('syncIndicator');
    els.btnRefresh = document.getElementById('btnRefresh');
    els.portalNavList = document.getElementById('portalNavList');
    els.portalCards = document.getElementById('portalCards');
    els.portalNotice = document.getElementById('portalNotice');
    els.adminNotice = document.getElementById('adminNotice');
    els.adminPortalPanel = document.getElementById('adminPortalPanel');
    els.adminUsersPanel = document.getElementById('adminUsersPanel');
    els.messagesNotice = document.getElementById('messagesNotice');
    els.messagesList = document.getElementById('messagesList');
    els.backendStatePill = document.getElementById('backendStatePill');
    els.metricTotal = document.getElementById('metricTotal');
    els.metricAdmin = document.getElementById('metricAdmin');
    els.metricClients = document.getElementById('metricClients');
    els.bootstrapJson = document.getElementById('bootstrapJson');
    els.publicBaseUrl = document.getElementById('publicBaseUrl');

    els.portalModal = document.getElementById('portalModal');
    els.portalModalTitle = document.getElementById('portalModalTitle');
    els.portalId = document.getElementById('portalId');
    els.portalRole = document.getElementById('portalRole');
    els.portalClientCode = document.getElementById('portalClientCode');
    els.portalTitle = document.getElementById('portalTitle');
    els.portalDomain = document.getElementById('portalDomain');
    els.portalWebhookUrl = document.getElementById('portalWebhookUrl');
    els.portalClientPhoneGroup = document.getElementById('portalClientPhoneGroup');
    els.portalClientPhone = document.getElementById('portalClientPhone');
    els.portalMemberId = document.getElementById('portalMemberId');
    els.portalStatus = document.getElementById('portalStatus');
    els.portalForm = document.getElementById('portalForm');
    els.portalFormError = document.getElementById('portalFormError');
    els.btnDeletePortal = document.getElementById('btnDeletePortal');
    els.portalClientActions = document.getElementById('portalClientActions');
    els.btnModalClientTest = document.getElementById('btnModalClientTest');
    els.btnModalClientRegisterBot = document.getElementById('btnModalClientRegisterBot');

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
        button.addEventListener('click', () => setActivePage(button.dataset.page));
    });

    document.querySelectorAll('[data-switch-page]').forEach(button => {
        button.addEventListener('click', () => setActivePage(button.dataset.switchPage));
    });

    document.getElementById('btnAddPortal').addEventListener('click', () => openPortalModal({ role: 'CLIENT' }));
    document.getElementById('btnAddPortalTop').addEventListener('click', () => openPortalModal({ role: 'CLIENT' }));
    document.getElementById('btnAdminCreate').addEventListener('click', () => openPortalModal({ role: 'ADMIN', clientCode: 'admin', title: 'Умные продажи' }));
    document.querySelectorAll('[data-open-admin-create]').forEach(button => {
        button.addEventListener('click', () => openPortalModal({ role: 'ADMIN', clientCode: 'admin', title: 'Умные продажи' }));
    });

    document.getElementById('btnClosePortalModal').addEventListener('click', closePortalModal);
    document.getElementById('btnCancelPortal').addEventListener('click', closePortalModal);
    els.portalModal.addEventListener('click', event => {
        if (event.target === els.portalModal) closePortalModal();
    });
    els.portalForm.addEventListener('submit', savePortalFromForm);
    els.btnDeletePortal.addEventListener('click', deleteCurrentPortal);
    if (els.btnModalClientTest) {
        els.btnModalClientTest.addEventListener('click', async () => {
            const portalId = els.btnModalClientTest.dataset.portalId;
            if (!portalId) return;
            closePortalModal();
            await clientAction(portalId, 'test-connection', 'Клиентский портал проверен');
        });
    }
    if (els.btnModalClientRegisterBot) {
        els.btnModalClientRegisterBot.addEventListener('click', async () => {
            const portalId = els.btnModalClientRegisterBot.dataset.portalId;
            if (!portalId) return;
            closePortalModal();
            await clientAction(portalId, 'bot/register', 'Клиентский бот создан / проверен');
        });
    }

    els.portalRole.addEventListener('change', updatePortalRoleFields);
    document.getElementById('btnCloseCrmModal').addEventListener('click', closeCrmModal);
    els.crmModal.addEventListener('click', event => { if (event.target === els.crmModal) closeCrmModal(); });
    els.btnCrmBack.addEventListener('click', crmWizardBack);
    els.btnCrmNext.addEventListener('click', crmWizardNext);
    els.btnCrmSave.addEventListener('click', saveCrmConfiguration);

    els.btnRefresh.addEventListener('click', loadAll);

    els.portalNavList.addEventListener('click', event => {
        const item = event.target.closest('[data-portal-id]');
        if (!item) return;
        setActivePage('portals');
        const card = document.querySelector(`[data-portal-card-id="${CSS.escape(item.dataset.portalId)}"]`);
        if (card) card.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });

    els.portalCards.addEventListener('click', handlePortalCardClick);
    els.adminPortalPanel.addEventListener('click', handleAdminPanelClick);
    els.adminUsersPanel.addEventListener('click', handleAdminUsersClick);
}

async function loadAll() {
    setLoading(true);
    try {
        const [bootstrap, portals, adminSummary, messages] = await Promise.all([
            api('/api/bootstrap/status'),
            api('/api/portals'),
            api('/api/admin-portal/summary'),
            api('/api/client-portals/messages')
        ]);

        state.bootstrap = bootstrap;
        state.portals = portals.items || [];
        state.portalStats = portals;
        state.adminSummary = adminSummary;
        state.clientMessages = messages.items || [];

        renderAll();
        await loadAdminUsersIfPossible(false);
        await loadCrmConfigIfPossible(false);
    } catch (error) {
        showNotice(els.portalNotice, error.message || 'Ошибка загрузки данных', true);
    } finally {
        setLoading(false);
    }
}

function renderAll() {
    renderMetrics();
    renderPortalNav();
    renderPortalCards();
    renderAdminPanel();
    renderMessages();
    renderSettings();
}

function renderMetrics() {
    els.metricTotal.textContent = state.portalStats.total ?? state.portals.length;
    els.metricAdmin.textContent = state.portalStats.adminCount ?? state.portals.filter(item => item.role === 'ADMIN').length;
    els.metricClients.textContent = state.portalStats.clientCount ?? state.portals.filter(item => item.role === 'CLIENT').length;
    els.backendStatePill.textContent = state.bootstrap?.state || 'OK';
    els.backendStatePill.className = 'status-pill active';
    els.bootstrapJson.textContent = JSON.stringify(state.bootstrap || {}, null, 2);
}

function renderSettings() {
    els.publicBaseUrl.textContent = state.bootstrap?.publicBaseUrl || '—';
}

function renderPortalNav() {
    if (!state.portals.length) {
        els.portalNavList.innerHTML = '<div class="history-empty">Порталы пока не добавлены.</div>';
        return;
    }

    els.portalNavList.innerHTML = state.portals.map(portal => `
        <button class="history-item" type="button" data-portal-id="${portal.id}">
            <span class="history-item-title">${escapeHtml(portal.title)}</span>
            <span class="history-item-meta">${escapeHtml(portal.domain)}</span>
            <span class="${portal.role === 'ADMIN' ? 'history-item-admin' : 'history-item-client'}">${roleLabel(portal.role)} · ${statusLabel(portal.status)}</span>
        </button>
    `).join('');
}

function renderPortalCards() {
    if (!state.portals.length) {
        els.portalCards.innerHTML = `
            <div class="info-card text-center">
                <h2 class="mb-2">Порталы не добавлены</h2>
                <p class="text-secondary mb-3">Начни с админского Bitrix24 своей компании.</p>
                <button class="btn btn-save" type="button" onclick="openPortalModal({ role: 'ADMIN', clientCode: 'admin', title: 'Умные продажи' })">Добавить админский портал</button>
            </div>
        `;
        return;
    }

    els.portalCards.innerHTML = state.portals.map(portal => `
        <article class="portal-card" data-portal-card-id="${portal.id}">
            <div class="portal-card-head">
                <div>
                    <div class="eyebrow">${roleLabel(portal.role)}</div>
                    <h3>${escapeHtml(portal.title)}</h3>
                    <div class="portal-domain">${escapeHtml(portal.domain)}</div>
                </div>
                <span class="status-pill ${statusClass(portal.status)}">${statusLabel(portal.status)}</span>
            </div>

            ${portal.role === 'CLIENT' ? `
                <div class="client-action-strip">
                    <div>
                        <div class="eyebrow mb-1">Настройка клиентского бота</div>
                        <div class="field-hint m-0">Проверь webhook клиента и зарегистрируй бота для маршрута клиент → отдельный чат обращения.</div>
                    </div>
                    <div class="button-row">
                        <button class="btn btn-flat btn-sm" type="button" data-client-test="${portal.id}">Проверить webhook</button>
                        <button class="btn btn-save btn-sm" type="button" data-client-register-bot="${portal.id}">Создать клиентского бота</button>
                        <button class="btn btn-flat btn-sm" type="button" data-client-repair-routing="${portal.id}" ${!portal.botId ? 'disabled' : ''}>Проверить маршрутизацию</button>
                    </div>
                </div>` : ''}

            <div class="portal-meta-grid">
                <div><span>Код</span><b>${escapeHtml(portal.clientCode)}</b></div>
                <div><span>Webhook</span><b>${portal.webhookConfigured ? 'Заполнен' : 'Не указан'}</b></div>
                ${portal.role === 'CLIENT' ? `<div><span>Телефон клиента</span><b>${escapeHtml(portal.clientPhone || '—')}</b></div>` : ''}
                <div><span>Bot ID</span><b>${escapeHtml(portal.botId || '—')}</b></div>
                <div><span>Bot token</span><b>${escapeHtml(portal.botTokenMasked || '—')}</b></div>
                <div><span>Chat ID</span><b>${escapeHtml(portal.supportChatId || '—')}</b></div>
                <div><span>Dialog ID</span><b>${escapeHtml(portal.supportDialogId || '—')}</b></div>
                <div><span>Event URL</span><b>${portal.botEventWebhookUrl ? 'Готов' : '—'}</b></div>
                <div><span>Последнее событие</span><b>${formatDateTime(portal.lastEventAt) || '—'}</b></div>
            </div>

            ${portal.lastError ? `<div class="error-panel mt-3">${escapeHtml(portal.lastError)}</div>` : ''}

            <div class="portal-actions">
                <button class="btn btn-flat btn-sm" type="button" data-edit-portal="${portal.id}">Редактировать</button>
                ${portal.role === 'ADMIN' ? `<button class="btn btn-flat btn-sm" type="button" data-admin-test="${portal.id}">Проверить</button><button class="btn btn-save btn-sm" type="button" data-admin-load-users="${portal.id}">Загрузить сотрудников</button>` : ''}
                ${portal.role === 'CLIENT' ? `<button class="btn btn-flat btn-sm" type="button" data-client-test="${portal.id}">Проверить</button><button class="btn btn-save btn-sm" type="button" data-client-register-bot="${portal.id}">Создать клиентского бота</button><button class="btn btn-flat btn-sm" type="button" data-client-repair-routing="${portal.id}" ${!portal.botId ? 'disabled' : ''}>Проверить маршрутизацию</button>` : ''}
            </div>
        </article>
    `).join('');
}

function renderMessages() {
    if (!els.messagesList) return;

    if (!state.clientMessages.length) {
        els.messagesList.innerHTML = `
            <div class="empty-state text-center">
                <h2 class="fw-semibold text-white mb-3">Журнал сообщений пуст</h2>
                <p class="text-secondary m-0">После регистрации клиентского бота напиши ему в клиентском Bitrix24. Сообщение должно появиться здесь и в админском чате.</p>
            </div>
        `;
        return;
    }

    els.messagesList.innerHTML = `
        <div class="messages-list">
            ${state.clientMessages.map(message => renderMessageCard(message)).join('')}
        </div>
    `;
}

function renderMessageCard(message) {
    return `
        <article class="message-card">
            <div class="message-card-head">
                <div>
                    <div class="eyebrow">${escapeHtml(message.clientPortalTitle || 'Клиентский портал')} · #${escapeHtml(message.clientCode || '—')}</div>
                    <h3>${escapeHtml(message.senderName || 'Клиент')}</h3>
                    <div class="portal-domain">${formatDateTime(message.createdAt) || '—'} · клиентский диалог ${escapeHtml(message.clientDialogId || '—')}</div>
                </div>
                <span class="status-pill ${message.status === 'FORWARDED' ? 'active' : message.status === 'ERROR' ? 'error' : ''}">${escapeHtml(message.status || 'NEW')}</span>
            </div>
            <div class="message-text">${escapeHtml(message.text || '')}</div>
            <div class="portal-meta-grid mt-3">
                <div><span>Client msg</span><b>${escapeHtml(message.clientMessageId || '—')}</b></div>
                <div><span>Admin msg</span><b>${escapeHtml(message.adminMessageId || '—')}</b></div>
                <div><span>Admin dialog</span><b>${escapeHtml(message.adminDialogId || '—')}</b></div>
            </div>
        </article>
    `;
}

function renderAdminPanel() {
    const summary = state.adminSummary;
    const portal = summary?.adminPortal;

    if (!portal) {
        els.adminPortalPanel.innerHTML = `
            <div class="info-card text-start">
                <div class="eyebrow">Админский портал не подключён</div>
                <h2>Добавь Bitrix24 своей компании</h2>
                <p>После подключения можно будет загрузить сотрудников, выбрать специалистов новых чатов и настроить смарт-процесс.</p>
                <button class="btn btn-save" type="button" onclick="openPortalModal({ role: 'ADMIN', clientCode: 'admin', title: 'Умные продажи' })">Добавить админский портал</button>
            </div>
        `;
        els.adminUsersPanel.innerHTML = '';
        return;
    }

    const botCreated = !!portal.botId;
    const supportMembers = summary.supportMembers || 0;

    els.adminPortalPanel.innerHTML = `
        <article class="portal-card admin-main-card">
            <div class="portal-card-head">
                <div>
                    <div class="eyebrow">Админский Bitrix24</div>
                    <h3>${escapeHtml(portal.title)}</h3>
                    <div class="portal-domain">${escapeHtml(portal.domain)}</div>
                </div>
                <span class="status-pill ${statusClass(portal.status)}">${statusLabel(portal.status)}</span>
            </div>

            <div class="admin-steps-grid">
                <div class="admin-step ${portal.webhookConfigured ? 'is-ok' : ''}"><b>1</b><span>Webhook URL</span><small>${portal.webhookConfigured ? 'заполнен' : 'не заполнен'}</small></div>
                <div class="admin-step ${(summary.loadedUsers || 0) > 0 ? 'is-ok' : ''}"><b>2</b><span>Сотрудники</span><small>${summary.loadedUsers || 0} загружено</small></div>
                <div class="admin-step ${supportMembers > 0 ? 'is-ok' : ''}"><b>3</b><span>Операторы</span><small>${supportMembers} выбрано</small></div>
                <div class="admin-step ${botCreated ? 'is-ok' : ''}"><b>4</b><span>Бот</span><small>${botCreated ? 'ID ' + escapeHtml(portal.botId) : 'не создан'}</small></div>
                <div class="admin-step ${state.crmConfig?.configured ? 'is-ok' : ''}"><b>5</b><span>Смарт-процесс</span><small>${state.crmConfig?.configured ? escapeHtml(state.crmConfig.processTitle) : 'не настроен'}</small></div>
            </div>

            <div class="portal-meta-grid mt-3">
                <div><span>Bot code</span><b>${escapeHtml(portal.botCode || '—')}</b></div>
                <div><span>Bot type</span><b>${escapeHtml(portal.botType || '—')}</b></div>
                <div><span>Bot token</span><b>${escapeHtml(portal.botTokenMasked || '—')}</b></div>
                <div><span>Chat ID</span><b>${escapeHtml(portal.supportChatId || '—')}</b></div>
                <div><span>Dialog ID</span><b>${escapeHtml(portal.supportDialogId || '—')}</b></div>
                <div><span>Event URL</span><b>${portal.botEventWebhookUrl ? 'Готов' : '—'}</b></div>
                <div><span>Последнее событие</span><b>${formatDateTime(portal.lastEventAt) || '—'}</b></div>
            </div>

            ${portal.lastError ? `<div class="error-panel mt-3">${escapeHtml(portal.lastError)}</div>` : ''}

            <div class="portal-actions">
                <button class="btn btn-flat" type="button" data-edit-portal="${portal.id}">Редактировать портал</button>
                <button class="btn btn-flat" type="button" data-admin-test="${portal.id}">Проверить подключение</button>
                <button class="btn btn-save" type="button" data-admin-load-users="${portal.id}">Загрузить сотрудников</button>
            </div>

            <div class="portal-actions admin-bot-actions">
                <button class="btn btn-save" type="button" data-admin-register-bot="${portal.id}">Создать / проверить бота</button>
                <button class="btn btn-flat" type="button" data-admin-repair-routing="${portal.id}" ${!botCreated ? 'disabled' : ''}>Проверить маршрутизацию</button>
            </div>
            <div class="crm-integration-card">
                <div>
                    <div class="eyebrow">Смарт-процесс обращений</div>
                    ${state.crmConfig?.configured ? `
                        <h3>${escapeHtml(state.crmConfig.processTitle)}</h3>
                        <div class="crm-config-summary">
                            <span>Воронка: <b>${escapeHtml(state.crmConfig.categoryTitle)}</b></span>
                            <span>В работе: <b>${escapeHtml(state.crmConfig.openStageTitle)}</b></span>
                            <span>Завершено: <b>${escapeHtml(state.crmConfig.closedStageTitle)}</b></span>
                            <span>Ответственный: <b>${escapeHtml(state.crmConfig.responsibleUserName)}</b></span>
                        </div>
                        ${state.crmConfig.lastError ? `<div class="error-panel mt-2">${escapeHtml(state.crmConfig.lastError)}</div>` : ''}
                    ` : `<p>Выбери смарт-процесс, воронку, стадии и ответственного. После этого каждое обращение будет автоматически создавать CRM-тикет.</p>`}
                </div>
                <div class="button-row">
                    <button class="btn btn-save" type="button" data-crm-setup="${portal.id}">${state.crmConfig?.configured ? 'Изменить настройки' : 'Настроить смарт-процесс'}</button>
                    ${state.crmConfig?.configured ? `<button class="btn btn-flat" type="button" data-crm-validate="${portal.id}">Проверить интеграцию</button>` : ''}
                </div>
            </div>
        </article>
    `;

    renderAdminUsers();
}

function renderAdminUsers() {
    const portal = state.adminSummary?.adminPortal;
    if (!portal) {
        els.adminUsersPanel.innerHTML = '';
        return;
    }

    if (!state.adminUsers.length) {
        els.adminUsersPanel.innerHTML = `
            <div class="info-card text-start">
                <div class="eyebrow">Сотрудники</div>
                <h2>Список пока пуст</h2>
                <p>Нажми «Загрузить сотрудников», чтобы получить пользователей из админского Bitrix24 через REST API.</p>
            </div>
        `;
        return;
    }

    els.adminUsersPanel.innerHTML = `
        <div class="users-panel">
            <div class="users-panel-head">
                <div>
                    <div class="eyebrow">Операторы поддержки</div>
                    <h3>Выбери сотрудников для новых чатов обращений</h3>
                    <p>Все выбранные сотрудники автоматически добавляются в каждый новый чат клиентского обращения.</p>
                </div>
                <button id="btnSaveSupportUsers" class="btn btn-save" type="button" data-save-support-users="${portal.id}">Сохранить выбранных</button>
            </div>
            <div class="users-list">
                ${state.adminUsers.map(user => renderUserRow(user)).join('')}
            </div>
        </div>
    `;
}

function renderUserRow(user) {
    const initials = buildInitials(user.displayName || user.email || user.bitrixUserId);
    return `
        <label class="user-row ${user.supportMember ? 'is-selected' : ''}">
            <input type="checkbox" data-support-user-id="${user.id}" ${user.supportMember ? 'checked' : ''}>
            <span class="user-avatar">${escapeHtml(initials)}</span>
            <span class="user-main">
                <b>${escapeHtml(user.displayName || 'Без имени')}</b>
                <small>${escapeHtml([user.email, user.workPosition].filter(Boolean).join(' · ') || 'ID ' + user.bitrixUserId)}</small>
            </span>
            <span class="user-id">B24 ID ${escapeHtml(user.bitrixUserId)}</span>
        </label>
    `;
}

async function handlePortalCardClick(event) {
    const editId = event.target.closest('[data-edit-portal]')?.dataset.editPortal;
    if (editId) {
        const portal = findPortal(editId);
        if (portal) openPortalModal(portal);
        return;
    }

    const testId = event.target.closest('[data-admin-test]')?.dataset.adminTest;
    if (testId) {
        await testAdminPortal(testId);
        return;
    }

    const loadId = event.target.closest('[data-admin-load-users]')?.dataset.adminLoadUsers;
    if (loadId) {
        await loadAdminUsers(loadId);
        return;
    }

    const clientTestId = event.target.closest('[data-client-test]')?.dataset.clientTest;
    if (clientTestId) {
        await clientAction(clientTestId, 'test-connection', 'Клиентский портал проверен');
        return;
    }

    const clientRegisterBotId = event.target.closest('[data-client-register-bot]')?.dataset.clientRegisterBot;
    if (clientRegisterBotId) {
        await clientAction(clientRegisterBotId, 'bot/register', 'Клиентский бот создан / проверен');
        return;
    }

    const clientRepairRoutingId = event.target.closest('[data-client-repair-routing]')?.dataset.clientRepairRouting;
    if (clientRepairRoutingId) {
        await clientAction(clientRepairRoutingId, 'routing/repair', 'Маршрутизация клиентского бота проверена');
        return;
    }

    const registerBotId = event.target.closest('[data-admin-register-bot]')?.dataset.adminRegisterBot;
    if (registerBotId) {
        await adminAction(registerBotId, 'bot/register', 'Бот создан / проверен');
        return;
    }

    const repairRoutingId = event.target.closest('[data-admin-repair-routing]')?.dataset.adminRepairRouting;
    if (repairRoutingId) {
        await adminAction(repairRoutingId, 'routing/repair', 'Маршрутизация админского бота проверена');
        return;
    }

    const createChatId = event.target.closest('[data-admin-create-chat]')?.dataset.adminCreateChat;
    if (createChatId) {
        await adminAction(createChatId, 'chat/create', 'Админский чат создан');
        return;
    }

    const addChatUsersId = event.target.closest('[data-admin-add-chat-users]')?.dataset.adminAddChatUsers;
    if (addChatUsersId) {
        await adminAction(addChatUsersId, 'chat/add-users', 'Операторы добавлены в чат');
        return;
    }

    const testMessageId = event.target.closest('[data-admin-test-message]')?.dataset.adminTestMessage;
    if (testMessageId) {
        await adminAction(testMessageId, 'chat/test-message', 'Тестовое сообщение отправлено');
    }
}

async function handleAdminPanelClick(event) {
    const setupId = event.target.closest('[data-crm-setup]')?.dataset.crmSetup;
    if (setupId) {
        await openCrmModal(setupId);
        return;
    }
    const validateId = event.target.closest('[data-crm-validate]')?.dataset.crmValidate;
    if (validateId) {
        await validateCrmConfiguration(validateId);
        return;
    }
    await handlePortalCardClick(event);
}

async function handleAdminUsersClick(event) {
    if (event.target.matches('[data-support-user-id]')) {
        const row = event.target.closest('.user-row');
        if (row) row.classList.toggle('is-selected', event.target.checked);
        return;
    }

    const saveId = event.target.closest('[data-save-support-users]')?.dataset.saveSupportUsers;
    if (saveId) {
        await saveSupportUsers(saveId);
    }
}

async function testAdminPortal(portalId) {
    setLoading(true);
    clearNotices();
    try {
        const result = await api(`/api/admin-portal/${portalId}/test-connection`, { method: 'POST' });
        await loadAll();
        setActivePage('admin');
        showNotice(els.adminNotice, result.message || 'Проверка выполнена', !result.success);
    } catch (error) {
        showNotice(els.adminNotice, error.message || 'Не удалось проверить подключение', true);
    } finally {
        setLoading(false);
    }
}

async function loadAdminUsers(portalId) {
    setLoading(true);
    clearNotices();
    try {
        const result = await api(`/api/admin-portal/${portalId}/load-users`, { method: 'POST' });
        await loadAll();
        await loadAdminUsersIfPossible(true);
        setActivePage('admin');
        showNotice(els.adminNotice, result.message || 'Сотрудники загружены', !result.success);
    } catch (error) {
        showNotice(els.adminNotice, error.message || 'Не удалось загрузить сотрудников', true);
    } finally {
        setLoading(false);
    }
}

async function adminAction(portalId, action, fallbackMessage) {
    setLoading(true);
    clearNotices();
    try {
        const result = await api(`/api/admin-portal/${portalId}/${action}`, { method: 'POST' });
        await loadAll();
        await loadAdminUsersIfPossible(true);
        setActivePage('admin');
        showNotice(els.adminNotice, result.message || fallbackMessage, !result.success);
    } catch (error) {
        showNotice(els.adminNotice, error.message || fallbackMessage || 'Операция не выполнена', true);
    } finally {
        setLoading(false);
    }
}

async function clientAction(portalId, action, fallbackMessage) {
    setLoading(true);
    clearNotices();
    try {
        const result = await api(`/api/client-portals/${portalId}/${action}`, { method: 'POST' });
        await loadAll();
        setActivePage('portals');
        showNotice(els.portalNotice, result.message || fallbackMessage, !result.success);
    } catch (error) {
        showNotice(els.portalNotice, error.message || fallbackMessage || 'Операция не выполнена', true);
    } finally {
        setLoading(false);
    }
}

async function loadAdminUsersIfPossible(forceRender) {
    const portal = state.adminSummary?.adminPortal;
    if (!portal) {
        state.adminUsers = [];
        if (forceRender) renderAdminUsers();
        return;
    }

    try {
        const result = await api(`/api/admin-portal/${portal.id}/users`);
        state.adminUsers = result.users || [];
        if (forceRender || state.page === 'admin') renderAdminUsers();
    } catch (error) {
        state.adminUsers = [];
        if (forceRender) showNotice(els.adminNotice, error.message || 'Не удалось прочитать сотрудников', true);
    }
}

async function saveSupportUsers(portalId) {
    const checked = [...document.querySelectorAll('[data-support-user-id]:checked')]
        .map(input => Number(input.dataset.supportUserId))
        .filter(Number.isFinite);

    setLoading(true);
    clearNotices();
    try {
        const result = await api(`/api/admin-portal/${portalId}/support-users`, {
            method: 'PUT',
            body: JSON.stringify({ userIds: checked })
        });
        state.adminUsers = result.users || [];
        await loadAll();
        setActivePage('admin');
        showNotice(els.adminNotice, 'Список операторов поддержки сохранён', false);
    } catch (error) {
        showNotice(els.adminNotice, error.message || 'Не удалось сохранить сотрудников', true);
    } finally {
        setLoading(false);
    }
}


async function loadCrmConfigIfPossible(forceRender) {
    const portal = state.adminSummary?.adminPortal;
    if (!portal) {
        state.crmConfig = null;
        return;
    }
    try {
        state.crmConfig = await api(`/api/admin-portal/${portal.id}/crm/config`);
        if (forceRender || state.page === 'admin') renderAdminPanel();
    } catch (error) {
        state.crmConfig = null;
        if (forceRender) showNotice(els.adminNotice, error.message || 'Не удалось прочитать CRM-настройки', true);
    }
}

function updatePortalRoleFields() {
    const isClient = els.portalRole.value === 'CLIENT';
    els.portalClientPhoneGroup.classList.toggle('d-none', !isClient);
    els.portalClientPhone.required = isClient;
}

async function openCrmModal(portalId) {
    state.crmWizard = { portalId, step: 1, processes: [], categories: [], stages: [] };
    clearCrmWizardError();
    els.crmModal.classList.remove('d-none');
    els.crmModal.setAttribute('aria-hidden', 'false');
    setCrmWizardLoading(true);
    try {
        const processes = await api(`/api/admin-portal/${portalId}/crm/processes`);
        state.crmWizard.processes = processes || [];
        const eligible = state.crmWizard.processes.filter(item => item.eligible);
        if (!eligible.length) throw new Error('Нет доступных смарт-процессов со стадиями и поддержкой клиентов');
        els.crmProcessSelect.innerHTML = eligible.map(item => `<option value="${item.entityTypeId}">${escapeHtml(item.title)}</option>`).join('');
        if (state.crmConfig?.configured) els.crmProcessSelect.value = String(state.crmConfig.entityTypeId);
        renderCrmWizardStep();
    } catch (error) {
        showCrmWizardError(error.message || 'Не удалось загрузить смарт-процессы');
    } finally {
        setCrmWizardLoading(false);
    }
}

function closeCrmModal() {
    els.crmModal.classList.add('d-none');
    els.crmModal.setAttribute('aria-hidden', 'true');
}

async function crmWizardNext() {
    clearCrmWizardError();
    const portalId = state.crmWizard.portalId;
    setCrmWizardLoading(true);
    try {
        if (state.crmWizard.step === 1) {
            const entityTypeId = Number(els.crmProcessSelect.value);
            state.crmWizard.entityTypeId = entityTypeId;
            state.crmWizard.categories = await api(`/api/admin-portal/${portalId}/crm/processes/${entityTypeId}/categories`);
            if (!state.crmWizard.categories.length) throw new Error('У выбранного смарт-процесса нет доступных воронок');
            els.crmCategorySelect.innerHTML = state.crmWizard.categories.map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join('');
            if (state.crmConfig?.configured && Number(state.crmConfig.entityTypeId) === entityTypeId) els.crmCategorySelect.value = String(state.crmConfig.categoryId);
            state.crmWizard.step = 2;
        } else if (state.crmWizard.step === 2) {
            const categoryId = Number(els.crmCategorySelect.value);
            state.crmWizard.categoryId = categoryId;
            state.crmWizard.stages = await api(`/api/admin-portal/${portalId}/crm/processes/${state.crmWizard.entityTypeId}/categories/${categoryId}/stages`);
            if (!state.crmWizard.stages.length) throw new Error('В выбранной воронке не найдены стадии');
            const processStages = state.crmWizard.stages.filter(item => item.semantics === 'PROCESS');
            const successStages = state.crmWizard.stages.filter(item => item.semantics === 'SUCCESS');
            const openOptions = processStages.length ? processStages : state.crmWizard.stages;
            const closeOptions = successStages.length ? successStages : state.crmWizard.stages;
            els.crmOpenStageSelect.innerHTML = openOptions.map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join('');
            els.crmClosedStageSelect.innerHTML = closeOptions.map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join('');
            els.crmResponsibleSelect.innerHTML = state.adminUsers.filter(item => item.active).map(item => `<option value="${escapeHtml(item.bitrixUserId)}">${escapeHtml(item.displayName || 'ID ' + item.bitrixUserId)}</option>`).join('');
            if (!els.crmResponsibleSelect.options.length) throw new Error('Сначала загрузи сотрудников админского портала');
            if (state.crmConfig?.configured && Number(state.crmConfig.categoryId) === categoryId) {
                els.crmOpenStageSelect.value = state.crmConfig.openStageId;
                els.crmClosedStageSelect.value = state.crmConfig.closedStageId;
                els.crmResponsibleSelect.value = state.crmConfig.responsibleUserId;
            }
            state.crmWizard.step = 3;
        }
        renderCrmWizardStep();
    } catch (error) {
        showCrmWizardError(error.message || 'Не удалось перейти к следующему шагу');
    } finally {
        setCrmWizardLoading(false);
    }
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
        const payload = {
            entityTypeId: state.crmWizard.entityTypeId,
            categoryId: state.crmWizard.categoryId,
            openStageId: els.crmOpenStageSelect.value,
            closedStageId: els.crmClosedStageSelect.value,
            responsibleUserId: els.crmResponsibleSelect.value
        };
        state.crmConfig = await api(`/api/admin-portal/${state.crmWizard.portalId}/crm/config`, { method: 'PUT', body: JSON.stringify(payload) });
        closeCrmModal();
        renderAdminPanel();
        showNotice(els.adminNotice, 'Интеграция со смарт-процессом сохранена', false);
    } catch (error) {
        showCrmWizardError(error.message || 'Не удалось сохранить CRM-интеграцию');
    } finally {
        setCrmWizardLoading(false);
    }
}

async function validateCrmConfiguration(portalId) {
    setLoading(true);
    try {
        const result = await api(`/api/admin-portal/${portalId}/crm/config/validate`, { method: 'POST' });
        state.crmConfig = result.config;
        renderAdminPanel();
        showNotice(els.adminNotice, result.message, !result.success);
    } catch (error) {
        showNotice(els.adminNotice, error.message || 'Не удалось проверить CRM-интеграцию', true);
    } finally {
        setLoading(false);
    }
}

function setCrmWizardLoading(loading) {
    els.crmWizardLoading.classList.toggle('d-none', !loading);
    [els.btnCrmBack, els.btnCrmNext, els.btnCrmSave].forEach(button => button.disabled = loading);
}

function showCrmWizardError(message) {
    els.crmWizardError.textContent = message;
    els.crmWizardError.classList.remove('d-none');
}

function clearCrmWizardError() {
    els.crmWizardError.textContent = '';
    els.crmWizardError.classList.add('d-none');
}

function setActivePage(page) {
    state.page = page;
    document.querySelectorAll('.page-section').forEach(section => section.classList.add('d-none'));
    const target = document.getElementById(`${page}Page`);
    if (target) target.classList.remove('d-none');

    document.querySelectorAll('.top-nav-link').forEach(button => {
        button.classList.toggle('active', button.dataset.page === page);
    });

    if (page === 'admin') renderAdminUsers();
}

function openPortalModal(portal = {}) {
    clearFormError();
    state.editingPortal = portal.id ? portal : null;
    els.portalModalTitle.textContent = portal.id ? 'Редактировать портал' : 'Добавить портал';
    els.btnDeletePortal.classList.toggle('d-none', !portal.id);

    els.portalId.value = portal.id || '';
    els.portalRole.value = portal.role || 'CLIENT';
    els.portalClientCode.value = portal.clientCode || '';
    els.portalTitle.value = portal.title || '';
    els.portalDomain.value = portal.domain || '';
    els.portalWebhookUrl.value = portal.webhookUrl || '';
    els.portalClientPhone.value = portal.clientPhone || '';
    els.portalMemberId.value = portal.memberId || '';
    els.portalStatus.value = portal.status || 'DRAFT';
    updatePortalRoleFields();

    const showClientActions = Boolean(portal.id && portal.role === 'CLIENT');
    if (els.portalClientActions) {
        els.portalClientActions.classList.toggle('d-none', !showClientActions);
    }
    if (els.btnModalClientTest) {
        els.btnModalClientTest.dataset.portalId = showClientActions ? portal.id : '';
    }
    if (els.btnModalClientRegisterBot) {
        els.btnModalClientRegisterBot.dataset.portalId = showClientActions ? portal.id : '';
    }

    els.portalModal.classList.remove('d-none');
    els.portalModal.setAttribute('aria-hidden', 'false');
    setTimeout(() => els.portalTitle.focus(), 0);
}

function closePortalModal() {
    els.portalModal.classList.add('d-none');
    els.portalModal.setAttribute('aria-hidden', 'true');
    if (els.portalClientActions) els.portalClientActions.classList.add('d-none');
    if (els.btnModalClientTest) els.btnModalClientTest.dataset.portalId = '';
    if (els.btnModalClientRegisterBot) els.btnModalClientRegisterBot.dataset.portalId = '';
    state.editingPortal = null;
}

async function savePortalFromForm(event) {
    event.preventDefault();
    clearFormError();
    setLoading(true);

    const id = els.portalId.value;
    if (els.portalRole.value === 'CLIENT' && !els.portalClientPhone.value.trim()) {
        showFormError('Для клиентского портала обязательно укажи телефон клиента');
        setLoading(false);
        return;
    }
    const payload = {
        role: els.portalRole.value,
        clientCode: valueOrNull(els.portalClientCode.value),
        title: els.portalTitle.value.trim(),
        domain: els.portalDomain.value.trim(),
        webhookUrl: valueOrNull(els.portalWebhookUrl.value),
        clientPhone: valueOrNull(els.portalClientPhone.value),
        memberId: valueOrNull(els.portalMemberId.value),
        status: els.portalStatus.value
    };

    try {
        await api(id ? `/api/portals/${id}` : '/api/portals', {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify(payload)
        });
        closePortalModal();
        await loadAll();
        setActivePage(payload.role === 'ADMIN' ? 'admin' : 'portals');
        showNotice(payload.role === 'ADMIN' ? els.adminNotice : els.portalNotice, 'Портал сохранён', false);
    } catch (error) {
        showFormError(error.message || 'Не удалось сохранить портал');
    } finally {
        setLoading(false);
    }
}

async function deleteCurrentPortal() {
    const id = els.portalId.value;
    if (!id) return;
    if (!confirm('Удалить этот портал из реестра?')) return;

    clearFormError();
    setLoading(true);
    try {
        await api(`/api/portals/${id}`, { method: 'DELETE' });
        closePortalModal();
        await loadAll();
        showNotice(els.portalNotice, 'Портал удалён', false);
    } catch (error) {
        showFormError(error.message || 'Не удалось удалить портал');
    } finally {
        setLoading(false);
    }
}

async function api(path, options = {}) {
    const response = await fetch(path, {
        cache: 'no-store',
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        ...options
    });

    if (response.status === 204) return null;

    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }

    if (!response.ok) {
        const message = data?.detail || data?.message || data?.error || `HTTP ${response.status}`;
        throw new Error(message);
    }

    return data;
}

function setLoading(isLoading) {
    els.syncIndicator.classList.toggle('d-none', !isLoading);
    els.btnRefresh.disabled = isLoading;
}

function findPortal(id) {
    return state.portals.find(item => String(item.id) === String(id));
}

function roleLabel(role) {
    return role === 'ADMIN' ? 'Админский' : 'Клиентский';
}

function statusLabel(status) {
    const labels = { DRAFT: 'Черновик', ACTIVE: 'Активен', ERROR: 'Ошибка', DISABLED: 'Отключён' };
    return labels[status] || status || 'Черновик';
}

function statusClass(status) {
    if (status === 'ACTIVE') return 'active';
    if (status === 'ERROR') return 'error';
    if (status === 'DISABLED') return 'disabled';
    return '';
}

function valueOrNull(value) {
    const cleaned = String(value || '').trim();
    return cleaned ? cleaned : null;
}

function buildInitials(value) {
    const parts = String(value || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return 'U';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
}

function clearNotices() {
    showNotice(els.portalNotice, '', false);
    showNotice(els.adminNotice, '', false);
    showNotice(els.messagesNotice, '', false);
}

function showNotice(element, message, isError) {
    if (!element) return;
    element.textContent = message || '';
    element.classList.toggle('is-visible', !!message);
    element.classList.toggle('is-error', !!isError);
}

function showFormError(message) {
    els.portalFormError.textContent = message;
    els.portalFormError.classList.remove('d-none');
}

function clearFormError() {
    els.portalFormError.textContent = '';
    els.portalFormError.classList.add('d-none');
}

function formatDateTime(value) {
    if (!value) return '';
    try {
        return new Intl.DateTimeFormat('ru-RU', {
            day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
        }).format(new Date(value));
    } catch (e) {
        return String(value);
    }
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}
