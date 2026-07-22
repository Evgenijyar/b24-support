package ru.abs7.b24support.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.domain.CrmCompanyMatchStatus;
import ru.abs7.b24support.domain.CrmIntegrationConfig;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.repo.CrmIntegrationConfigRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import ru.abs7.b24support.repo.SupportMessageRepository;
import ru.abs7.b24support.repo.SupportTicketRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmTicketSyncServiceTest {

    @Mock
    private PortalInstallationRepository portalRepository;
    @Mock
    private CrmIntegrationConfigRepository configRepository;
    @Mock
    private SupportTicketRepository ticketRepository;
    @Mock
    private SupportMessageRepository messageRepository;
    @Mock
    private BitrixRestClient bitrixRestClient;

    private CrmTicketSyncService service;
    private ObjectMapper objectMapper;
    private PortalInstallation admin;
    private PortalInstallation client;
    private CrmIntegrationConfig config;

    @BeforeEach
    void setUp() {
        service = new CrmTicketSyncService(
                portalRepository,
                configRepository,
                ticketRepository,
                messageRepository,
                bitrixRestClient
        );
        objectMapper = new ObjectMapper();

        admin = new PortalInstallation(PortalRole.ADMIN, "admin", "Админский портал", "admin.bitrix24.ru");
        ReflectionTestUtils.setField(admin, "id", 1L);
        admin.setWebhookUrl("https://admin.bitrix24.ru/rest/1/secret/");

        client = new PortalInstallation(PortalRole.CLIENT, "client", "ООО Клиент", "client.bitrix24.ru");
        ReflectionTestUtils.setField(client, "id", 10L);
        client.setClientPhone("+79991234567");

        config = new CrmIntegrationConfig(admin);
        config.configure(
                1120,
                "Тикеты",
                68,
                "Общая",
                "DT1120_68:NEW",
                "В работе",
                "DT1120_68:SUCCESS",
                "Завершено",
                "216",
                "Евгений Молочников"
        );

        when(portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN)).thenReturn(Optional.of(admin));
        when(configRepository.findByAdminPortal_Id(1L)).thenReturn(Optional.of(config));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsSmartProcessItemWithContactFoundByPhone() throws Exception {
        SupportTicket ticket = ticket(13L);

        when(bitrixRestClient.callJson(eq(admin.getWebhookUrl()), anyString(), any()))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(1), invocation.getArgument(2), false));

        service.syncTicket(ticket);

        assertThat(ticket.getCrmItemId()).isEqualTo(30L);
        assertThat(ticket.getCrmContactId()).isEqualTo(55L);
        assertThat(ticket.getCrmCompanyId()).isNull();
        assertThat(ticket.getCrmCompanyMatchStatus()).isEqualTo(CrmCompanyMatchStatus.MATCHED);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(bitrixRestClient).callJson(eq(admin.getWebhookUrl()), eq("crm.item.add"), payload.capture());
        Map<?, ?> request = (Map<?, ?>) payload.getValue();
        Map<?, ?> fields = (Map<?, ?>) request.get("fields");
        assertThat(fields.get("contactId")).isEqualTo(55L);
        assertThat(fields.get("contactIds")).isEqualTo(List.of(55L));
        assertThat(fields).doesNotContainKey("companyId");
    }

    @Test
    void backfillsExistingSmartProcessItemWithContactAndLinkedCompany() throws Exception {
        SupportTicket ticket = ticket(14L);
        ticket.markCrmCreated(30L, 1120, 68, null, null, CrmCompanyMatchStatus.NOT_FOUND);
        ticket.markCrmPending("Повторный поиск клиента");

        when(bitrixRestClient.callJson(eq(admin.getWebhookUrl()), anyString(), any()))
                .thenAnswer(invocation -> responseFor(invocation.getArgument(1), invocation.getArgument(2), true));

        service.syncTicket(ticket);

        assertThat(ticket.getCrmContactId()).isEqualTo(55L);
        assertThat(ticket.getCrmCompanyId()).isEqualTo(77L);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(bitrixRestClient).callJson(eq(admin.getWebhookUrl()), eq("crm.item.update"), payload.capture());
        Map<?, ?> request = (Map<?, ?>) payload.getValue();
        Map<?, ?> fields = (Map<?, ?>) request.get("fields");
        assertThat(fields.get("contactId")).isEqualTo(55L);
        assertThat(fields.get("companyId")).isEqualTo(77L);
    }


    @Test
    void acceptsDirectArrayDuplicateSearchResponse() throws Exception {
        SupportTicket ticket = ticket(15L);

        when(bitrixRestClient.callJson(eq(admin.getWebhookUrl()), anyString(), any()))
                .thenAnswer(invocation -> {
                    String method = invocation.getArgument(1);
                    Map<?, ?> payload = invocation.getArgument(2);
                    if ("crm.duplicate.findbycomm".equals(method)) {
                        String entityType = String.valueOf(payload.get("entity_type"));
                        return "CONTACT".equals(entityType)
                                ? json("{\"result\":[55]}")
                                : json("{\"result\":[]}");
                    }
                    if ("crm.contact.company.items.get".equals(method)) {
                        return json("{\"result\":[]}");
                    }
                    if ("crm.item.add".equals(method)) {
                        return json("{\"result\":{\"item\":{\"id\":31}}}");
                    }
                    throw new IllegalArgumentException("Unexpected method: " + method);
                });

        service.syncTicket(ticket);

        assertThat(ticket.getCrmContactId()).isEqualTo(55L);
        assertThat(ticket.getCrmItemId()).isEqualTo(31L);
    }

    private SupportTicket ticket(Long id) {
        SupportTicket ticket = new SupportTicket(client, "1", "ООО Клиент, обращение №1", 1L);
        ReflectionTestUtils.setField(ticket, "id", id);
        ticket.markOpen("87074", "chat87074");
        return ticket;
    }

    private JsonNode responseFor(String method, Object rawPayload, boolean withCompany) throws Exception {
        Map<?, ?> payload = (Map<?, ?>) rawPayload;
        return switch (method) {
            case "crm.duplicate.findbycomm" -> {
                String entityType = String.valueOf(payload.get("entity_type"));
                if ("CONTACT".equals(entityType)) {
                    yield json("{\"result\":{\"CONTACT\":[55]}}");
                }
                yield json("{\"result\":{}}");
            }
            case "crm.contact.company.items.get" -> withCompany
                    ? json("{\"result\":[{\"COMPANY_ID\":77,\"IS_PRIMARY\":\"Y\"}]}")
                    : json("{\"result\":[]}");
            case "crm.item.add" -> json("{\"result\":{\"item\":{\"id\":30}}}");
            case "crm.item.update" -> json("{\"result\":{\"item\":{\"id\":30}}}");
            default -> throw new IllegalArgumentException("Unexpected method: " + method);
        };
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
