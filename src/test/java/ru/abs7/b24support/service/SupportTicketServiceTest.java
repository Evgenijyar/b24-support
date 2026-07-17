package ru.abs7.b24support.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.abs7.b24support.api.dto.ticket.TicketActionResponse;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.domain.BitrixUser;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.domain.SupportTicketStatus;
import ru.abs7.b24support.repo.BitrixUserRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import ru.abs7.b24support.repo.SupportMessageRepository;
import ru.abs7.b24support.repo.SupportTicketRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;
    @Mock
    private PortalInstallationRepository portalRepository;
    @Mock
    private BitrixUserRepository bitrixUserRepository;
    @Mock
    private SupportMessageRepository supportMessageRepository;
    @Mock
    private SupportSettingsService settingsService;
    @Mock
    private BitrixRestClient bitrixRestClient;

    private SupportTicketService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = new SupportTicketService(
                ticketRepository,
                portalRepository,
                bitrixUserRepository,
                supportMessageRepository,
                settingsService,
                bitrixRestClient
        );
        objectMapper = new ObjectMapper();
    }

    @Test
    void createsNewChatWithSelectedActiveOperators() throws Exception {
        PortalInstallation client = clientPortal();
        PortalInstallation admin = adminPortal();
        BitrixUser first = supportUser(admin, 11L, "101", true);
        BitrixUser second = supportUser(admin, 12L, "102", true);
        BitrixUser inactive = supportUser(admin, 13L, "103", false);

        when(ticketRepository.findFirstByClientInstallation_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.empty());
        when(portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN)).thenReturn(Optional.of(admin));
        when(bitrixUserRepository.findAllByPortalInstallationIdAndSupportMemberTrueOrderByLastNameAscFirstNameAscIdAsc(1L))
                .thenReturn(List.of(first, second, inactive));
        when(ticketRepository.saveAndFlush(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", 501L);
            return ticket;
        });
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bitrixRestClient.callJson(eq(admin.getWebhookUrl()), eq("imbot.v2.Chat.add"), any()))
                .thenReturn(json("""
                        {"result":{"chat":{"id":77,"dialogId":"chat77"}}}
                        """));

        SupportTicketService.TicketResolution resolution = service.resolveOrCreateOpenTicket(client, "chat900");

        assertThat(resolution.created()).isTrue();
        assertThat(resolution.ticket().getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(resolution.ticket().getAdminChatId()).isEqualTo("77");
        assertThat(resolution.ticket().getAdminDialogId()).isEqualTo("chat77");
        assertThat(resolution.ticket().getChatTitle()).isEqualTo("ООО Ромашка");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bitrixRestClient).callJson(eq(admin.getWebhookUrl()), eq("imbot.v2.Chat.add"), payloadCaptor.capture());
        Map<?, ?> payload = (Map<?, ?>) payloadCaptor.getValue();
        Map<?, ?> fields = (Map<?, ?>) payload.get("fields");
        assertThat(fields.get("title")).isEqualTo("ООО Ромашка");
        assertThat(fields.get("userIds")).isEqualTo(List.of(101, 102));
    }

    @Test
    void reusesExistingOpenTicketWithoutCreatingAnotherChat() {
        PortalInstallation client = clientPortal();
        SupportTicket existing = openTicket(client, 501L, "chat77");

        when(ticketRepository.findFirstByClientInstallation_IdAndStatusInOrderByIdDesc(eq(10L), any()))
                .thenReturn(Optional.of(existing));
        when(ticketRepository.save(existing)).thenReturn(existing);

        SupportTicketService.TicketResolution resolution = service.resolveOrCreateOpenTicket(client, "chat901");

        assertThat(resolution.created()).isFalse();
        assertThat(resolution.ticket()).isSameAs(existing);
        assertThat(existing.getClientDialogId()).isEqualTo("chat901");
        verify(bitrixRestClient, never()).callJson(anyString(), anyString(), any());
    }

    @Test
    void closesTicketNotifiesClientAndSchedulesDeletion() throws Exception {
        PortalInstallation client = clientPortal();
        SupportTicket ticket = openTicket(client, 501L, "chat77");
        OffsetDateTime before = OffsetDateTime.now().plusDays(4).minusMinutes(1);

        when(ticketRepository.findById(501L)).thenReturn(Optional.of(ticket));
        when(settingsService.retentionDays()).thenReturn(4);
        when(bitrixRestClient.callJson(eq(client.getWebhookUrl()), eq("imbot.v2.Chat.Message.send"), any()))
                .thenReturn(json("{" + "\"result\":{\"id\":909}}"));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(supportMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketActionResponse result = service.close(501L, "101", "Иван Иванов");

        assertThat(result.success()).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.CLOSED);
        assertThat(ticket.getClosedByUserId()).isEqualTo("101");
        assertThat(ticket.getClosedByUserName()).isEqualTo("Иван Иванов");
        assertThat(ticket.getDeleteAfter()).isAfter(before);
        verify(bitrixRestClient).callJson(eq(client.getWebhookUrl()), eq("imbot.v2.Chat.Message.send"), any());
        verify(supportMessageRepository).save(any());
    }

    @Test
    void deletesClosedChatByRemovingHumansAndMakingBotLeave() throws Exception {
        PortalInstallation client = clientPortal();
        PortalInstallation admin = adminPortal();
        SupportTicket ticket = openTicket(client, 501L, "chat77");
        ticket.markClosed("101", "Иван Иванов", OffsetDateTime.now().plusDays(7));

        when(ticketRepository.findById(501L)).thenReturn(Optional.of(ticket));
        when(portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN)).thenReturn(Optional.of(admin));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(bitrixRestClient.callJson(eq(admin.getWebhookUrl()), eq("imbot.v2.Chat.User.list"), any()))
                .thenReturn(json("""
                        {"result":[
                          {"id":101,"bot":false},
                          {"id":102,"bot":false},
                          {"id":501,"bot":true}
                        ]}
                        """));
        when(bitrixRestClient.callJson(eq(admin.getWebhookUrl()), eq("imbot.v2.Chat.User.delete"), any()))
                .thenReturn(json("{\"result\":true}"));
        when(bitrixRestClient.callJson(eq(admin.getWebhookUrl()), eq("imbot.v2.Chat.leave"), any()))
                .thenReturn(json("{\"result\":true}"));

        TicketActionResponse result = service.deleteChat(501L);

        assertThat(result.success()).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.DELETED);
        assertThat(ticket.getDeletionAttempts()).isEqualTo(1);
        verify(bitrixRestClient, times(2))
                .callJson(eq(admin.getWebhookUrl()), eq("imbot.v2.Chat.User.delete"), any());
        verify(bitrixRestClient)
                .callJson(eq(admin.getWebhookUrl()), eq("imbot.v2.Chat.leave"), any());
    }

    private PortalInstallation adminPortal() {
        PortalInstallation portal = new PortalInstallation(PortalRole.ADMIN, "admin", "Админский портал", "admin.bitrix24.ru");
        ReflectionTestUtils.setField(portal, "id", 1L);
        portal.setWebhookUrl("https://admin.bitrix24.ru/rest/1/secret/");
        portal.setBotId("501");
        portal.setBotToken("admin-token");
        return portal;
    }

    private PortalInstallation clientPortal() {
        PortalInstallation portal = new PortalInstallation(PortalRole.CLIENT, "romashka", "ООО Ромашка", "romashka.bitrix24.ru");
        ReflectionTestUtils.setField(portal, "id", 10L);
        portal.setWebhookUrl("https://romashka.bitrix24.ru/rest/2/secret/");
        portal.setBotId("601");
        portal.setBotToken("client-token");
        return portal;
    }

    private BitrixUser supportUser(PortalInstallation admin, Long id, String bitrixUserId, boolean active) {
        BitrixUser user = new BitrixUser(admin, bitrixUserId);
        ReflectionTestUtils.setField(user, "id", id);
        user.setActive(active);
        user.setSupportMember(true);
        return user;
    }

    private SupportTicket openTicket(PortalInstallation client, Long id, String adminDialogId) {
        SupportTicket ticket = new SupportTicket(client, "chat900", client.getTitle());
        ReflectionTestUtils.setField(ticket, "id", id);
        ticket.markOpen(adminDialogId.substring("chat".length()), adminDialogId);
        return ticket;
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
