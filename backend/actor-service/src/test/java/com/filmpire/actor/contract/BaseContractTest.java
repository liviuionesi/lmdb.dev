package com.filmpire.actor.contract;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.filmpire.actor.controller.ActorController;
import com.filmpire.actor.dto.ActorDtos.ActorDto;
import com.filmpire.actor.service.ActorService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base setup class for Spring Cloud Contract generated producer tests in {@code actor-service}.
 * Mocks {@link ActorService} responses and configures {@link RestAssuredMockMvc}.
 */
public abstract class BaseContractTest {

  /**
   * Sets up mock MVC controller environment with stubbed actor responses before each contract test execution.
   */
  @BeforeEach
  public void setup() {
    ActorService actorService = mock(ActorService.class);

    ActorDto actorDto =
        new ActorDto(
            819L,
            "Edward Norton",
            "Edward Harrison Norton is an American actor and filmmaker.",
            LocalDate.of(1969, Month.AUGUST, 18),
            "Boston, Massachusetts, USA",
            "/5XB9m1Jl51VI9DchxIMjG6EKOxJ.jpg",
            28.5,
            List.of("Edward Harrison Norton"),
            "Acting",
            2,
            "nm0001570",
            null,
            false);

    when(actorService.getActor(819L)).thenReturn(actorDto);

    ActorController actorController = new ActorController(actorService);
    RestAssuredMockMvc.standaloneSetup(actorController);
  }
}
