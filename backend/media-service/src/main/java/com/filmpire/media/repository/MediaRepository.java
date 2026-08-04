package com.filmpire.media.repository;

import com.filmpire.media.model.EntityType;
import com.filmpire.media.model.MediaFile;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository interface for managing {@link MediaFile} metadata documents.
 * Provides custom queries for indexing and filtering uploads by entity association.
 */
public interface MediaRepository extends MongoRepository<MediaFile, String> {

  /**
   * Retrieves all uploaded media assets associated with a target entity identifier.
   *
   * @param entityId Unique identifier of the associated entity (e.g. user ID, review ID).
   * @return Unordered list of matching {@link MediaFile} metadata documents.
   */
  List<MediaFile> findByEntityId(String entityId);

  /**
   * Retrieves all uploaded media assets associated with a target entity and classification type.
   *
   * @param entityId Unique identifier of the associated entity.
   * @param entityType Domain classification of the target entity (USER, MOVIE_REVIEW, etc.).
   * @return Unordered list of matching {@link MediaFile} metadata documents.
   */
  List<MediaFile> findByEntityIdAndEntityType(String entityId, EntityType entityType);
}
