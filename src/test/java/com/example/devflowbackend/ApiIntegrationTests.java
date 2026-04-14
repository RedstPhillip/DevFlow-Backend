package com.example.devflowbackend;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM dm_pairs");
        jdbcTemplate.update("DELETE FROM chat_participants");
        jdbcTemplate.update("DELETE FROM chats");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void authFlowWorks() throws Exception {
        HttpResult register = postJson("/api/auth/register", """
                {"username":"alice","password":"password123"}
                """);
        assertEquals(201, register.statusCode);
        assertTrue(register.body.contains("\"accessToken\""));
        assertTrue(register.body.contains("\"refreshToken\""));

        String accessToken = JsonPath.read(register.body, "$.accessToken");
        String refreshToken = JsonPath.read(register.body, "$.refreshToken");

        HttpResult me = get("/api/users/me", accessToken);
        assertEquals(200, me.statusCode);
        assertEquals("alice", JsonPath.read(me.body, "$.username"));

        HttpResult login = postJson("/api/auth/login", """
                {"username":"alice","password":"password123"}
                """);
        assertEquals(200, login.statusCode);
        assertEquals("alice", JsonPath.read(login.body, "$.user.username"));

        HttpResult refresh = postJson("/api/auth/refresh", "{" + "\"refreshToken\":\"" + refreshToken + "\"}");
        assertEquals(200, refresh.statusCode);
        assertTrue(refresh.body.contains("\"accessToken\""));
    }

    @Test
    void duplicateUsernameReturnsConflict() throws Exception {
        HttpResult first = postJson("/api/auth/register", """
                {"username":"bob","password":"password123"}
                """);
        assertEquals(201, first.statusCode);

        HttpResult second = postJson("/api/auth/register", """
                {"username":"bob","password":"password123"}
                """);
        assertEquals(409, second.statusCode);
        assertEquals("Username already taken", JsonPath.read(second.body, "$.error"));
    }

    @Test
    void dmAndMessagesFlowWorks() throws Exception {
        RegisteredUser alice = registerUser("alice");
        RegisteredUser bob = registerUser("bob");
        RegisteredUser charlie = registerUser("charlie");

        HttpResult createdDm = postJsonAuth("/api/chats/dm", "{" + "\"otherUserId\":" + bob.id + "}", alice.accessToken);
        assertEquals(201, createdDm.statusCode);
        int chatId = ((Number) JsonPath.read(createdDm.body, "$.id")).intValue();

        HttpResult existingDm = postJsonAuth("/api/chats/dm", "{" + "\"otherUserId\":" + bob.id + "}", alice.accessToken);
        assertEquals(200, existingDm.statusCode);

        HttpResult createdMsg = postJsonAuth("/api/chats/" + chatId + "/messages", """
                {"content":"Hey, how are you?"}
                """, bob.accessToken);
        assertEquals(201, createdMsg.statusCode);
        int messageId = ((Number) JsonPath.read(createdMsg.body, "$.id")).intValue();

        HttpResult messages = getAuth("/api/chats/" + chatId + "/messages?afterId=0&limit=50", alice.accessToken);
        assertEquals(200, messages.statusCode);
        assertEquals(messageId, ((Number) JsonPath.read(messages.body, "$[0].id")).intValue());

        HttpResult forbidden = getAuth("/api/chats/" + chatId + "/messages", charlie.accessToken);
        assertEquals(403, forbidden.statusCode);
        assertEquals("Not a participant of this chat", JsonPath.read(forbidden.body, "$.error"));

        HttpResult emptyMessage = postJsonAuth("/api/chats/" + chatId + "/messages", """
                {"content":"   "}
                """, alice.accessToken);
        assertEquals(400, emptyMessage.statusCode);
        assertEquals("Content cannot be empty", JsonPath.read(emptyMessage.body, "$.error"));
    }

    private RegisteredUser registerUser(String username) throws Exception {
        HttpResult result = postJson("/api/auth/register", "{" + "\"username\":\"" + username + "\"," + "\"password\":\"password123\"}");
        assertEquals(201, result.statusCode);
        long id = ((Number) JsonPath.read(result.body, "$.user.id")).longValue();
        String accessToken = JsonPath.read(result.body, "$.accessToken");
        return new RegisteredUser(id, accessToken);
    }

    private HttpResult get(String path, String token) throws IOException, InterruptedException {
        return getAuth(path, token);
    }

    private HttpResult getAuth(String path, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private HttpResult postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private HttpResult postJsonAuth(String path, String body, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record RegisteredUser(long id, String accessToken) {
    }

    private record HttpResult(int statusCode, String body) {
    }
}
