package com.filmpire.movie.performance;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * Gatling load test simulation targeting movie facade endpoints (Task #45).
 *
 * <p>Measures API Gateway/Movie Service facade performance against the SLOs in ARCHITECTURE.md
 * §12.4:
 *
 * <ul>
 *   <li>Cache-served reads: P95 &lt; 200 ms
 *   <li>TMDB-fallback reads: P95 &lt; 800 ms
 * </ul>
 */
public class MovieFacadeGatlingSimulation extends Simulation {

  private final String baseUrl = System.getProperty("gatling.baseUrl", "http://localhost:8081");

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl).acceptHeader("application/json").contentTypeHeader("application/json");

  // Scenario 1: Cache-Served Reads (Popular, Search, Movie Details)
  private final ScenarioBuilder cacheServedScenario =
      scenario("Cache-Served Reads")
          .exec(
              http("GET /movie/popular (Cache Hit)")
                  .get("/movie/popular")
                  .queryParam("page", "1")
                  .check(status().is(200)))
          .pause(Duration.ofMillis(100))
          .exec(
              http("GET /search/movie (Cache Hit)")
                  .get("/search/movie")
                  .queryParam("query", "Fight")
                  .queryParam("page", "1")
                  .check(status().is(200)))
          .pause(Duration.ofMillis(100))
          .exec(http("GET /movie/{id} (Cache Hit)").get("/movie/550").check(status().is(200)));

  // Scenario 2: TMDB-Fallback Reads (WireMock with injected latency)
  private final ScenarioBuilder fallbackScenario =
      scenario("TMDB-Fallback Reads")
          .exec(http("GET /movie/{id} (TMDB Fallback)").get("/movie/99999").check(status().is(200)))
          .pause(Duration.ofMillis(100))
          .exec(
              http("GET /discover/movie (TMDB Fallback)")
                  .get("/discover/movie")
                  .queryParam("page", "99")
                  .queryParam("genreId", "28")
                  .check(status().is(200)));

  {
    setUp(
            cacheServedScenario.injectOpen(
                atOnceUsers(10), rampUsers(30).during(Duration.ofSeconds(5))),
            fallbackScenario.injectOpen(
                atOnceUsers(5), rampUsers(15).during(Duration.ofSeconds(5))))
        .protocols(httpProtocol);
  }
}
