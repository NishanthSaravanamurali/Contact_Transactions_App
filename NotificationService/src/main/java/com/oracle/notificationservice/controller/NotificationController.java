package com.oracle.notificationservice.controller;

import com.oracle.notificationservice.dto.response.*;
import com.oracle.notificationservice.realtime.NotificationStreamService;
import com.oracle.notificationservice.service.abstractions.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import org.springframework.data.web.PagedModel;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationQueryService queries;
    private final NotificationCommandService commands;
    private final NotificationStreamService streams;
    private final UserIdResolver userIdResolver;

    public NotificationController(NotificationQueryService queries, NotificationCommandService commands,
            NotificationStreamService streams, UserIdResolver userIdResolver) {
        this.queries = queries;
        this.commands = commands;
        this.streams = streams;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    public PagedModel<NotificationResponse> getAll(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(queries.getAll(userIdResolver.resolve(jwt), filter, page, size));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(queries.unreadCount(userIdResolver.resolve(jwt)));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) Long id) {
        commands.markRead(userIdResolver.resolve(jwt), id);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        commands.markAllRead(userIdResolver.resolve(jwt));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        Long userId = userIdResolver.resolve(jwt);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return streams.subscribe(userId, jwt.getExpiresAt());
    }
}
