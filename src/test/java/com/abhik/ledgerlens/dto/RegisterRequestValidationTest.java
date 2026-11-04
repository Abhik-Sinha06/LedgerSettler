package com.abhik.ledgerlens.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private static Validator validator;
    private static AutoCloseable validatorFactory;

    @BeforeAll
    static void setUp() {
        var factory = Validation.buildDefaultValidatorFactory();
        validatorFactory = factory::close;
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() throws Exception {
        validatorFactory.close();
    }

    @Test
    void acceptsPasswordWithRequiredLengthNumberAndSpecialCharacter() {
        RegisterRequest request = requestWithPassword("abc1!d");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsPasswordShorterThanSixCharacters() {
        Set<String> messages = messagesFor(requestWithPassword("a1!bc"));

        assertTrue(messages.contains("Password must be at least 6 characters long."));
    }

    @Test
    void rejectsPasswordWithoutNumberOrSpecialCharacter() {
        String message = "Password must contain at least one number and one special character.";
        assertTrue(messagesFor(requestWithPassword("abcdef!")).contains(message));
        assertTrue(messagesFor(requestWithPassword("abcdef1")).contains(message));
    }

    private static RegisterRequest requestWithPassword(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user");
        request.setEmail("user@example.com");
        request.setPassword(password);
        return request;
    }

    private static Set<String> messagesFor(RegisterRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }
}
