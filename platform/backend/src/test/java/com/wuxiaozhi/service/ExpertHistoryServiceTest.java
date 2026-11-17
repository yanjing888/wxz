package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.entity.ExpertConversation;
import com.wuxiaozhi.repository.ExpertConversationRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpertHistoryServiceTest {
    @Test void readsActualMessagesFromUpstreamAndChecksOwnerBeforeRequest() throws Exception {
        String id = UUID.randomUUID().toString();
        ExpertConversation link = new ExpertConversation();
        link.setId(id); link.setUserId(1L); link.setExpertId("expert"); link.setMode("ask");
        link.setThreadId("thr_test");
        ExpertConversationRepository repository = mock(ExpertConversationRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(link));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/history/thr_test", exchange -> {
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"messages\":[{\"role\":\"ai\",\"text\":\"from Octop\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var service = new ExpertHistoryService(repository, new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            var messages = (com.fasterxml.jackson.databind.JsonNode) service.get(id, 1L, "expert").get("messages");
            assertEquals("from Octop", messages.get(0).path("text").asText());
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(id, 2L, "expert")).getStatusCode().value());
            assertThrows(ResponseStatusException.class, () -> service.get(id, 1L, "other"));
            assertThrows(ResponseStatusException.class, () -> service.begin(id, 2L, "expert", "overwrite", "ask"));
        } finally { server.stop(0); }
    }

    @Test void storesRoutingMetadataAndPreservesTitleAcrossFollowUps() {
        ExpertConversationRepository repository = mock(ExpertConversationRepository.class);
        Map<String, ExpertConversation> records = new HashMap<>();
        when(repository.findById(anyString())).thenAnswer(c -> Optional.ofNullable(records.get(c.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(c -> {
            ExpertConversation link = c.getArgument(0); records.put(link.getId(), link); return link;
        });
        var service = new ExpertHistoryService(repository, new ObjectMapper(), "", "");
        String id = UUID.randomUUID().toString();
        ExpertConversation link = new ExpertConversation();
        link.setId(id); link.setUserId(1L); link.setExpertId("expert"); link.setTitle("first question"); link.setThreadId("thr_existing");
        records.put(id, link);
        assertEquals("thr_existing", service.begin(id, 1L, "expert", "follow up", "plan"));
        assertEquals("first question", records.get(id).getTitle());
        assertEquals("plan", records.get(id).getMode());
        assertEquals(1, records.size());
    }
}
