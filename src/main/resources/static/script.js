const state = {
    activePage: 'overview',
    portals: [],
    bootstrap: null,
    editingPortal: null
};

const els = {};

document.addEventListener('DOMContentLoaded', () => {
    cacheElements();
    bindEvents();
    loadAll();
});

function cacheElements() {
    els.syncIndicator = document.getElementById('syncIndicator');
    els.btnRefresh = document.getElementById('btnRefresh');
    els.navButtons = [...document.querySelectorAll('[data-page]')];
    els.pageSections = [...document.querySelectorAll('.page-section')];
    els.portalNavList = document.getElementById('portalNavList');
    els.portalCards = document.getElementById('portalCards');
    els.portalNotice = document.getElementById('portalNotice');
    els.bootstrapJson = document.getElementById('bootstrapJson');
    els.backendStatePill = document.getElementById('backendStatePill');
    els.metricTotal = document.getElementById('metricTotal');
    els.metricAdmin = document.getElementById('metricAdmin');
    els.metricClients = document.getElementById('metricClients');
    els.publicBaseUrl = document.getElementById('publicBaseUrl');

    els.portalModal = document.getElementById('portalModal');
    els.portalForm = document.getElementById('portalForm');
    els.portalModalTitle = document.getElementById('portalModalTitle');
    els.portalModalStep = document.getElementById('portalModalStep');
    els.portalFormError = document.getElementById('portalFormError');
    els.btnDeletePortal = document.getElementById('btnDeletePortal');

    els.portalId = document.getElementById('portalId');
    els.portalRole = document.getElementById('portalRole');
    els.portalClientCode = document.getElementById('portalClientCode');
    els.portalTitle = document.getElementById('portalTitle');
    els.portalDomain = document.getElementById('portalDomain');
    els.portalWebhookUrl = document.getElementById('portalWebhookUrl');
    els.portalMemberId = document.getElementById('portalMemberId');
    els.portalStatus = document.getElementById('portalStatus');
}

function bindEvents() {
    els.btnRefresh.addEventListener('click', loadAll);
    document.getElementById('btnAddPortal').addEventListener('click', () => openPortalModal());
    document.getElementById('btnAddPortalTop').addEventListener('click', () => openPortalModal());
    document.querySelector('[data-open-admin-create]').addEventListener('click', () => openPortalModal({ role: 'ADMIN' }));

    els.navButtons.forEach(button => {
        button.addEventListener('click', () => setActivePage(button.dataset.page));
    });

    document.getElementById('btnClosePortalModal').addEventListener('click', closePortalModal);
    document.querySelectorAll('[data-close-modal]').forEach(button => button.addEventListener('click', closePortalModal));
    els.portalModal.addEventListener('click', event => {
        if (event.target === els.portalModal) closePortalModal();
    });

    els.portalForm.addEventListener('submit', savePortalFromForm);
    els.btnDeletePortal.addEventListener('click', deleteCurrentPortal);
}

async function loadAll() {
    setLoading(true);
    try {
        const [bootstrap, portals] = await Promise.all([
            api('/api/bootstrap/status'),
            api('/api/portals')
        ]);
        state.bootstrap = bootstrap;
        state.portals = portals.items || [];
        renderAll();
    } catch (error) {
        showNotice(els.portalNotice, error.message || 'Ошибка загрузки данных', true);
        renderBackendError(error);
    } finally {
        setLoading(false);
    }
}

function renderAll() {
    renderBackendStatus();
    renderMetrics();
    renderPortalNav();
    renderPortalCards();
}

function renderBackendStatus() {
    els.backendStatePill.textContent = 'Backend OK';
    els.backendStatePill.className = 'status-pill active';
    els.bootstrapJson.textContent = JSON.stringify(state.bootstrap || {}, null, 2);
    els.publicBaseUrl.textContent = state.bootstrap?.publicBaseUrl || '—';
}

function renderBackendError(error) {
    els.backendStatePill.textContent = 'Ошибка';
    els.backendStatePill.className = 'status-pill error';
    els.bootstrapJson.textContent = String(error);
}

function renderMetrics() {
    const adminCount = state.portals.filter(item => item.role === 'ADMIN').length;
    const clientCount = state.portals.filter(item => item.role === 'CLIENT').length;
    els.metricTotal.textContent = String(state.portals.length);
    els.metricAdmin.textContent = adminCount ? 'Есть' : 'Нет';
    els.metricClients.textContent = String(clientCount);
}

function renderPortalNav() {
    if (!state.portals.length) {
        els.portalNavList.innerHTML = '<div class="history-item"><div class="history-item-title">Порталы ещё не добавлены</div><div class="history-item-meta">Начни с админского Bitrix24</div></div>';
        return;
    }

    els.portalNavList.innerHTML = state.portals.map(portal => `
        <button class="history-item" type="button" data-edit-portal-id="${portal.id}">
            <div class="history-item-title">${escapeHtml(portal.title)}</div>
            <div class="history-item-meta">${escapeHtml(portal.domain)}</div>
            <div class="${portal.role === 'ADMIN' ? 'history-item-admin' : 'history-item-client'}">${roleLabel(portal.role)} · ${escapeHtml(portal.clientCode || 'без кода')}</div>
        </button>
    `).join('');

    els.portalNavList.querySelectorAll('[data-edit-portal-id]').forEach(button => {
        button.addEventListener('click', () => {
            const portal = findPortal(button.dataset.editPortalId);
            openPortalModal(portal);
            setActivePage('portals');
        });
    });
}

function renderPortalCards() {
    if (!state.portals.length) {
        els.portalCards.innerHTML = `
            <div class="empty-state text-center">
                <h2 class="fw-semibold text-white mb-3">Пока пусто</h2>
                <p class="text-secondary m-0">Добавь админский Bitrix24, затем начнём подключать клиентские порталы.</p>
            </div>
        `;
        return;
    }

    els.portalCards.innerHTML = state.portals.map(portal => `
        <article class="portal-card">
            <div>
                <div class="portal-title-row">
                    <h3 class="portal-title">${escapeHtml(portal.title)}</h3>
                    <span class="status-pill ${portal.role === 'ADMIN' ? 'admin' : 'client'}">${roleLabel(portal.role)}</span>
                    <span class="status-pill status-${String(portal.status || 'DRAFT').toLowerCase()}">${statusLabel(portal.status)}</span>
                </div>
                <div class="portal-meta">
                    <span><b>Домен:</b> ${escapeHtml(portal.domain)}</span>
                    <span><b>Код:</b> ${escapeHtml(portal.clientCode || '—')}</span>
                    <span><b>Webhook:</b> ${portal.webhookConfigured ? 'заполнен' : 'не задан'}</span>
                    ${portal.memberId ? `<span><b>member_id:</b> ${escapeHtml(portal.memberId)}</span>` : ''}
                    ${portal.supportDialogId ? `<span><b>dialog:</b> ${escapeHtml(portal.supportDialogId)}</span>` : ''}
                </div>
            </div>
            <div class="portal-actions">
                <button class="btn btn-flat" type="button" data-edit-portal-id="${portal.id}">Редактировать</button>
            </div>
        </article>
    `).join('');

    els.portalCards.querySelectorAll('[data-edit-portal-id]').forEach(button => {
        button.addEventListener('click', () => openPortalModal(findPortal(button.dataset.editPortalId)));
    });
}

function setActivePage(page) {
    state.activePage = page;
    els.navButtons.forEach(button => button.classList.toggle('active', button.dataset.page === page));
    els.pageSections.forEach(section => section.classList.add('d-none'));
    const pageEl = document.getElementById(page + 'Page');
    if (pageEl) pageEl.classList.remove('d-none');
}

function openPortalModal(portal = {}) {
    state.editingPortal = portal.id ? portal : null;
    clearFormError();

    els.portalModalTitle.textContent = portal.id ? 'Редактировать портал' : 'Добавить портал';
    els.portalModalStep.textContent = portal.id ? `ID ${portal.id}` : 'Портал Bitrix24';
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
        setActivePage('portals');
        showNotice(els.portalNotice, 'Портал сохранён', false);
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
        headers: {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        },
        ...options
    });

    if (response.status === 204) return null;

    const text = await response.text();
    let data = null;
    try {
        data = text ? JSON.parse(text) : null;
    } catch (e) {
        data = text;
    }

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
    const labels = {
        DRAFT: 'Черновик',
        ACTIVE: 'Активен',
        ERROR: 'Ошибка',
        DISABLED: 'Отключён'
    };
    return labels[status] || status || 'Черновик';
}

function valueOrNull(value) {
    const cleaned = String(value || '').trim();
    return cleaned ? cleaned : null;
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function showNotice(element, text, isError) {
    element.textContent = text || '';
    element.classList.toggle('is-error', !!isError);
    element.classList.toggle('is-ok', !!text && !isError);
    if (text && !isError) {
        setTimeout(() => {
            element.textContent = '';
            element.classList.remove('is-ok');
        }, 3500);
    }
}

function showFormError(text) {
    els.portalFormError.textContent = text;
    els.portalFormError.classList.remove('d-none');
}

function clearFormError() {
    els.portalFormError.textContent = '';
    els.portalFormError.classList.add('d-none');
}
