package com.filmpire.movie.facade;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * MongoDB repository for {@link TmdbDocument} raw TMDB responses.
 *
 * <p>Only primary-key access is needed: the facade builds a canonical
 * request key and looks it up directly, so no derived query methods are
 * defined.</p>
 */
public interface TmdbDocumentRepository extends MongoRepository<TmdbDocument, String> {
}
