package com.filmpire.user.contract;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.filmpire.user.controller.UserController;
import com.filmpire.user.dto.AuthDtos.UserProfileResponse;
import com.filmpire.user.service.UserAccountService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public abstract class BaseContractTest {

  @BeforeEach
  public void setup() {
    UserAccountService userAccountService = Mockito.mock(UserAccountService.class);

    UserProfileResponse profileResponse =
        new UserProfileResponse(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            "liviu",
            "liviu@example.com",
            "ROLE_USER",
            LocalDateTime.of(2026, 8, 5, 12, 0),
            LocalDateTime.of(2026, 8, 5, 12, 0));

    when(userAccountService.getProfile(anyString())).thenReturn(profileResponse);

    UserController userController = new UserController(userAccountService);

    Authentication auth =
        new UsernamePasswordAuthenticationToken("liviu", null, java.util.List.of());

    RestAssuredMockMvc.standaloneSetup(userController);
    RestAssuredMockMvc.postProcessors(
        req -> {
          req.setUserPrincipal(auth);
          return req;
        });
  }
}
