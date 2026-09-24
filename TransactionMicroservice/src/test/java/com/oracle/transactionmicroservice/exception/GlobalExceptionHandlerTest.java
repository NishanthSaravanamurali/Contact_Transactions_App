package com.oracle.transactionmicroservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final String TRACE_ID =
            "31ac7274-1405-40a2-8a0f-8fb505f67dd5";

    @Test
    void returnsTheForbiddenMessageAsJson() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/test/forbidden")
                        .header(GlobalExceptionHandler.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(header().string(
                        GlobalExceptionHandler.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(jsonPath("$.errorCode")
                        .value("CONTACT_PHONE_MISMATCH"))
                .andExpect(jsonPath("$.message").value(
                        "This contact’s phone number no longer matches the registered user. "
                                + "Update the contact before transferring money."))
                .andExpect(jsonPath("$.path").value("/test/forbidden"));
    }

    @RestController
    private static class FailingController {

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenOperationException(
                    "CONTACT_PHONE_MISMATCH",
                    "This contact’s phone number no longer matches the registered user. "
                            + "Update the contact before transferring money."
            );
        }
    }
}
