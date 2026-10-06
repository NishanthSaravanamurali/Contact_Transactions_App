package com.oracle.notificationservice.realtime;

import com.oracle.notificationservice.dto.response.NotificationResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;

@Service
@EnableScheduling
public class NotificationStreamService {
    private record Subscription(Long userId, Instant expiresAt) {}
    private final ConcurrentMap<SseEmitter, Subscription> connections = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor writer;
    private final long timeoutMs;
    private final int maxPerUser;
    private final int maxConnections;

    public NotificationStreamService(
            @Value("${notifications.sse.timeout-ms:300000}") long timeoutMs,
            @Value("${notifications.sse.max-per-user:5}") int maxPerUser,
            @Value("${notifications.sse.max-connections:1000}") int maxConnections) {
        if (timeoutMs <= 0 || maxPerUser <= 0 || maxConnections <= 0) {
            throw new IllegalArgumentException("SSE limits must be positive.");
        }
        this.timeoutMs = timeoutMs;
        this.maxPerUser = maxPerUser;
        this.maxConnections = maxConnections;
        this.writer = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(128), runnable -> {
                    Thread thread = new Thread(runnable, "notification-sse-writer");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized SseEmitter subscribe(Long userId, Instant tokenExpiry) {
        if (userId == null || userId <= 0 || tokenExpiry == null || !tokenExpiry.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid unexpired token is required.");
        }
        long userConnections = connections.values().stream().filter(s -> s.userId().equals(userId)).count();
        if (userConnections >= maxPerUser || connections.size() >= maxConnections) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many live connections.");
        }
        long lifetime = Math.max(1, Math.min(timeoutMs, Duration.between(Instant.now(), tokenExpiry).toMillis()));
        SseEmitter emitter = new SseEmitter(lifetime);
        connections.put(emitter, new Subscription(userId, tokenExpiry));
        emitter.onCompletion(() -> connections.remove(emitter));
        emitter.onTimeout(() -> close(emitter));
        emitter.onError(error -> connections.remove(emitter));
        // On every connection/reconnection the client must reload persisted state.
        send(emitter, SseEmitter.event().name("ready").reconnectTime(3000)
                .data(Map.of("refresh", true)));
        return emitter;
    }

    public void notificationCreated(Long userId, NotificationResponse notification) {
        publish(userId, "notification-created", notification);
    }

    public void notificationsRead(Long userId) {
        publish(userId, "notifications-read", Map.of("refresh", true));
    }

    private void publish(Long userId, String event, Object data) {
        connections.forEach((emitter, subscription) -> {
            if (subscription.userId().equals(userId)) {
                enqueue(emitter, SseEmitter.event().name(event).data(data));
            }
        });
    }

    @Scheduled(fixedDelayString = "${notifications.sse.heartbeat-ms:15000}")
    public void heartbeat() {
        connections.forEach((emitter, subscription) -> {
            if (!subscription.expiresAt().isAfter(Instant.now())) close(emitter);
            else enqueue(emitter, SseEmitter.event().comment("keepalive"));
        });
    }

    private void enqueue(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { writer.execute(() -> send(emitter, event)); }
        catch (RejectedExecutionException exception) {
            // Disconnect a slow client; its next connection reloads state from Oracle.
            close(emitter);
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        Subscription subscription = connections.get(emitter);
        if (subscription == null) return;
        if (!subscription.expiresAt().isAfter(Instant.now())) {
            close(emitter);
            return;
        }
        try { emitter.send(event); }
        catch (IOException | RuntimeException exception) { close(emitter); }
    }

    private void close(SseEmitter emitter) {
        connections.remove(emitter);
        try { emitter.complete(); }
        catch (RuntimeException ignored) { /* Already disconnected. */ }
    }

    @PreDestroy
    public void shutdown() {
        connections.keySet().forEach(this::close);
        writer.shutdownNow();
    }
}
