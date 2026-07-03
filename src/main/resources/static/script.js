const state = {
    page: 'overview',
    portals: [],
    portalStats: { total: 0, adminCount: 0, clientCount: 0 },
    bootstrap: null,
    adminSummary: null,
    adminUsers: [],
    editingPortal: null
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
    els.portalMemberId = document.getElementById('portalMemberId');
    els.portalStatus = document.getElementById('portalStatus');
    els.portalForm = document.getElementById('portalForm');
    els.portalFormError = document.getElementById('portalFormError');
    els.btnDeletePortal = document.getElementById('btnDeletePortal');
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
        const [bootstrap, portals, adminSummary] = await Promise.all([
            api('/api/bootstrap/status'),
            api('/api/portals'),
            api('/api/admin-portal/summary')
        ]);

        state.bootstrap = bootstrap;
        state.portals = portals.items || [];
        state.portalStats = portals;
        state.adminSummary = adminSummary;

        renderAll();
        await loadAdminUsersIfPossible(false);
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

            <div class="portal-meta-grid">
                <div><span>Код</span><b>${escapeHtml(portal.clientCode)}</b></div>
                <div><span>Webhook</span><b>${portal.webhookConfigured ? 'Заполнен' : 'Не указан'}</b></div>
                <div><span>Bot ID</span><b>${escapeHtml(portal.botId || '—')}</b></div>
                <div><span>Bot token</span><b>${escapeHtml(portal.botTokenMasked || '—')}</b></div>
                <div><span>Chat ID</span><b>${escapeHtml(portal.supportChatId || '—')}</b></div>
                <div><span>Dialog ID</span><b>${escapeHtml(portal.supportDialogId || '—')}</b></div>
            </div>

            ${portal.lastError ? `<div class="error-panel mt-3">${escapeHtml(portal.lastError)}</div>` : ''}

            <div class="portal-actions">
                <button class="btn btn-flat btn-sm" type="button" data-edit-portal="${portal.id}">Редактировать</button>
                ${portal.role === 'ADMIN' ? `<button class="btn btn-flat btn-sm" type="button" data-admin-test="${portal.id}">Проверить</button><button class="btn btn-save btn-sm" type="button" data-admin-load-users="${portal.id}">Загрузить сотрудников</button>` : ''}
            </div>
        </article>
    `).join('');
}

function renderAdminPanel() {
    const summary = state.adminSummary;
    const portal = summary?.adminPortal;

    if (!portal) {
        els.adminPortalPanel.innerHTML = `
            <div class="info-card text-start">
                <div class="eyebrow">Админский портал не подключён</div>
                <h2>Добавь Bitrix24 своей компании</h2>
                <p>После подключения можно будет загрузить сотрудников и выбрать операторов для общего чата «Техподдержка админ».</p>
                <button class="btn btn-save" type="button" onclick="openPortalModal({ role: 'ADMIN', clientCode: 'admin', title: 'Умные продажи' })">Добавить админский портал</button>
            </div>
        `;
        els.adminUsersPanel.innerHTML = '';
        return;
    }

    const botCreated = !!portal.botId;
    const chatCreated = !!portal.supportDialogId;
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
                <div class="admin-step ${chatCreated ? 'is-ok' : ''}"><b>5</b><span>Админский чат</span><small>${chatCreated ? escapeHtml(portal.supportDialogId) : 'не создан'}</small></div>
            </div>

            <div class="portal-meta-grid mt-3">
                <div><span>Bot code</span><b>${escapeHtml(portal.botCode || '—')}</b></div>
                <div><span>Bot type</span><b>${escapeHtml(portal.botType || '—')}</b></div>
                <div><span>Bot token</span><b>${escapeHtml(portal.botTokenMasked || '—')}</b></div>
                <div><span>Chat ID</span><b>${escapeHtml(portal.supportChatId || '—')}</b></div>
                <div><span>Dialog ID</span><b>${escapeHtml(portal.supportDialogId || '—')}</b></div>
            </div>

            ${portal.lastError ? `<div class="error-panel mt-3">${escapeHtml(portal.lastError)}</div>` : ''}

            <div class="portal-actions">
                <button class="btn btn-flat" type="button" data-edit-portal="${portal.id}">Редактировать портал</button>
                <button class="btn btn-flat" type="button" data-admin-test="${portal.id}">Проверить подключение</button>
                <button class="btn btn-save" type="button" data-admin-load-users="${portal.id}">Загрузить сотрудников</button>
            </div>

            <div class="portal-actions admin-bot-actions">
                <button class="btn btn-save" type="button" data-admin-register-bot="${portal.id}">Создать / проверить бота</button>
                <button class="btn btn-save" type="button" data-admin-create-chat="${portal.id}" ${(!botCreated || supportMembers === 0) ? 'disabled' : ''}>Создать админский чат</button>
                <button class="btn btn-flat" type="button" data-admin-add-chat-users="${portal.id}" ${(!botCreated || !chatCreated || supportMembers === 0) ? 'disabled' : ''}>Добавить операторов в чат</button>
                <button class="btn btn-flat" type="button" data-admin-test-message="${portal.id}" ${(!botCreated || !chatCreated) ? 'disabled' : ''}>Отправить тест</button>
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
                    <h3>Выбери сотрудников для общего чата</h3>
                    <p>Эти сотрудники будут добавлены в общий чат «Техподдержка админ». После изменения списка нажми «Сохранить выбранных», затем «Добавить операторов в чат».</p>
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

    const registerBotId = event.target.closest('[data-admin-register-bot]')?.dataset.adminRegisterBot;
    if (registerBotId) {
        await adminAction(registerBotId, 'bot/register', 'Бот создан / проверен');
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
    els.portalMemberId.value = portal.memberId || '';
    els.portalStatus.value = portal.status || 'DRAFT';

    els.portalModal.classList.remove('d-none');
    els.portalModal.setAttribute('aria-hidden', 'false');
    setTimeout(() => els.portalTitle.focus(), 0);
}

function closePortalModal() {
    els.portalModal.classList.add('d-none');
    els.portalModal.setAttribute('aria-hidden', 'true');
    state.editingPortal = null;
}

async function savePortalFromForm(event) {
    event.preventDefault();
    clearFormError();
    setLoading(true);

    const id = els.portalId.value;
    const payload = {
        role: els.portalRole.value,
        clientCode: valueOrNull(els.portalClientCode.value),
        title: els.portalTitle.value.trim(),
        domain: els.portalDomain.value.trim(),
        webhookUrl: valueOrNull(els.portalWebhookUrl.value),
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

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}
