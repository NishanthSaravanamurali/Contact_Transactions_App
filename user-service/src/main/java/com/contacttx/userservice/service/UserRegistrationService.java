package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.RegisterUserRequest;
import com.contacttx.userservice.dto.response.RegistrationResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.OutboxEvent;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.DuplicateEmailException;
import com.contacttx.userservice.mapper.UserMapper;
import com.contacttx.userservice.messaging.OutboxEventFactory;
import com.contacttx.userservice.repository.AppUserRepository;
import com.contacttx.userservice.repository.OutboxEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserRegistrationService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "UQ_APP_USER_EMAIL";

    private final AppUserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final OutboxEventFactory outboxEventFactory;

    public UserRegistrationService(
            AppUserRepository userRepository,
            OutboxEventRepository outboxEventRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            OutboxEventFactory outboxEventFactory) {
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.outboxEventFactory = outboxEventFactory;
    }

    @Transactional
    public RegistrationResponse register(RegisterUserRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        AppUser user = new AppUser(
                request.getName(),
                passwordEncoder.encode(request.getPassword()),
                normalizedEmail,
                Long.valueOf(request.getMobileNo()),
                request.getDateOfBirth(),
                UserStatus.ACTIVE);

        try {
            AppUser savedUser = userRepository.saveAndFlush(user);
            OutboxEvent outboxEvent =
                    outboxEventFactory.createUserRegistered(savedUser.getUserId());
            outboxEventRepository.save(outboxEvent);
            return userMapper.toRegistrationResponse(savedUser);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmailViolation(exception)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isDuplicateEmailViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String uppercaseMessage = message.toUpperCase(Locale.ROOT);
                if (uppercaseMessage.contains(EMAIL_UNIQUE_CONSTRAINT)
                        || uppercaseMessage.contains("ORA-00001")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
