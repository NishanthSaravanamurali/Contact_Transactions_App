package com.contacttx.contactservice;

import com.contacttx.contactservice.repository.ContactRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "contact-service.user-service.internal-token=test-internal-token",
        "contact-service.internal.service-token=test-internal-token"
})
class ContactServiceApplicationTests {

    @MockitoBean
    private ContactRepository contactRepository;

    @MockitoBean
    private EntityManager entityManager;

    @Test
    void contextLoads() {
    }
}
