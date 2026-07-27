package com.lingXi.ai.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigRegressionTest {

    private static final Pattern AI_PERMIT_ALL = Pattern.compile(
            "\\.antMatchers\\s*\\(\\s*\"/api/ai/\\*\\*\"\\s*\\)"
                    + "\\s*\\.permitAll\\s*\\(");
    private static final Pattern INTERNAL_TOOL_PERMIT_ALL = Pattern.compile(
            "\\.antMatchers\\s*\\(\\s*\"/internal/ai/tools/\\*\\*\"\\s*\\)"
                    + "\\s*\\.permitAll\\s*\\(");

    @Test
    void aiApiIsNotInAnonymousSecurityWhitelist() throws IOException {
        String source = Files.readString(findSecurityConfig(), StandardCharsets.UTF_8);

        assertFalse(AI_PERMIT_ALL.matcher(source).find(),
                "/api/ai/** must require authentication");
        assertTrue(source.contains(".anyRequest().authenticated()"),
                "the authenticated fallback must remain enabled");
    }

    @Test
    void internalToolGatewayUsesItsOwnBearerBoundary() throws IOException {
        String source = Files.readString(findSecurityConfig(), StandardCharsets.UTF_8);

        assertTrue(INTERNAL_TOOL_PERMIT_ALL.matcher(source).find(),
                "the internal gateway must bypass browser JWT authentication");
        assertTrue(source.contains(".anyRequest().authenticated()"),
                "other endpoints must keep the authenticated fallback");
    }

    private static Path findSecurityConfig() {
        Path workingDirectory = Paths.get(System.getProperty("user.dir"))
                .toAbsolutePath().normalize();
        String relativePath = "src/main/java/com/dkd/framework/config/SecurityConfig.java";
        List<Path> candidates = Arrays.asList(
                workingDirectory.resolve("../lingXi-framework").resolve(relativePath),
                workingDirectory.resolve("lingXi-framework").resolve(relativePath),
                workingDirectory.resolve("dkd-parent/lingXi-framework").resolve(relativePath));
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw new AssertionError("SecurityConfig.java not found from " + workingDirectory);
    }
}
