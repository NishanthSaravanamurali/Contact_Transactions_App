package com.oracle.transactionmicroservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_PUBLIC_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "INTERNAL_SERVICE_TOKEN", matches = ".+")
class TransactionMicroserviceApplicationTests {

    @Test
    void contextLoads() {
    }

}
