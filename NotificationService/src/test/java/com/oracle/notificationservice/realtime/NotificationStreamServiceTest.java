package com.oracle.notificationservice.realtime;

import com.oracle.notificationservice.dto.response.NotificationResponse;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class NotificationStreamServiceTest {
    private NotificationStreamService streams;

    @BeforeEach void setup() { streams = new NotificationStreamService(300000, 2, 10); }
    @AfterEach void cleanup() { streams.shutdown(); }

    @RestController
    static class StreamController {
        private final NotificationStreamService streams;
        StreamController(NotificationStreamService streams) { this.streams = streams; }
        @GetMapping(value = "/stream/{user}", produces = "text/event-stream")
        SseEmitter connect(@PathVariable Long user) {
            return streams.subscribe(user, Instant.now().plusSeconds(60));
        }
    }

    @Test
    void pushesOnlyToReceiverAndAnnouncesReadUpdates() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StreamController(streams)).build();
        MvcResult receiver = mvc.perform(get("/stream/2")).andReturn();
        MvcResult other = mvc.perform(get("/stream/3")).andReturn();
        assertThat(receiver.getRequest().isAsyncStarted()).isTrue();
        assertThat(receiver.getResponse().getContentAsString()).contains("event:ready");
        streams.notificationCreated(2L, new NotificationResponse(1L, 10L, "Payment received",
                OffsetDateTime.now(), null));
        streams.notificationsRead(2L);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(receiver.getResponse().getContentAsString())
                        .contains("event:notification-created", "Payment received", "event:notifications-read"));
        assertThat(other.getResponse().getContentAsString()).doesNotContain("Payment received", "notifications-read");
        streams.heartbeat();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(other.getResponse().getContentAsString()).contains(":keepalive"));
    }

    @Test
    void lifetimeIsCappedByJwtExpiryAndExpiredTokensCannotSubscribe() {
        SseEmitter emitter = streams.subscribe(2L, Instant.now().plusSeconds(30));
        assertThat(emitter.getTimeout()).isBetween(1L, 30000L);
        assertThatThrownBy(() -> streams.subscribe(2L, Instant.now().minusSeconds(1)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> streams.subscribe(2L, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void perUserLimitDoesNotBlockOtherUsers() {
        streams.subscribe(2L, Instant.now().plusSeconds(60));
        streams.subscribe(2L, Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> streams.subscribe(2L, Instant.now().plusSeconds(60)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(streams.subscribe(3L, Instant.now().plusSeconds(60))).isNotNull();
    }

    @Test
    void completedConnectionsAreRemoved() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StreamController(streams)).build();
        MvcResult result = mvc.perform(get("/stream/2")).andReturn();
        result.getRequest().getAsyncContext().complete();
        streams.subscribe(2L, Instant.now().plusSeconds(60));
        assertThat(streams.subscribe(2L, Instant.now().plusSeconds(60))).isNotNull();
    }
}
