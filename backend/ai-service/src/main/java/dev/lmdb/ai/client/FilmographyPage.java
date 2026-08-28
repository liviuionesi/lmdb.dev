package dev.lmdb.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;

/**
 * The subset of actor-service's paged cast-filmography envelope ({@code FilmographyPageDto}) this
 * feature needs — just the credits on the page (#203). Actor-service's own pagination metadata
 * (page/totalPages/totalItems) is unused here: {@link
 * dev.lmdb.ai.client.ActorCatalogClient#fetchCastCredits} requests one bounded page, not the full
 * paged sequence.
 *
 * @param results cast credits on this page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FilmographyPage(List<PersonCredit> results) implements Serializable {}
