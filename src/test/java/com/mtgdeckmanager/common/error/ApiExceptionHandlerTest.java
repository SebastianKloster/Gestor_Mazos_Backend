package com.mtgdeckmanager.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void validationReturnsFieldErrorsWithoutRejectedValues() throws Exception {
        mvc.perform(post("/validation").contentType("application/json").content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/validation").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void domainErrorsUseTheirStatus() throws Exception {
        for (int status : new int[]{404, 409, 422}) {
            mvc.perform(get("/domain/" + status)).andExpect(status().is(status))
                    .andExpect(jsonPath("$.detail").value("Domain rule violated"));
        }
    }

    @Test
    void staleWritesReturnConflictWithoutInternalDetails() throws Exception {
        mvc.perform(get("/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The operation conflicts with the current state"));
    }

    @RestController
    static class TestController {
        @PostMapping("/validation") void validate(@Valid @RequestBody Input input) { }
        @GetMapping("/domain/{code}") void domain(@PathVariable int code) {
            throw new DomainException(HttpStatus.valueOf(code), "Domain rule violated");
        }
        @GetMapping("/conflict") void conflict() {
            throw new OptimisticLockingFailureException("Sensitive internal detail");
        }
    }
    record Input(@Positive int quantity) { }
}
