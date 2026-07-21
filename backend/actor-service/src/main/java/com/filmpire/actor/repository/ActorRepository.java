package com.filmpire.actor.repository;

import com.filmpire.actor.model.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for typed {@link Actor} profiles (keyed by TMDB person id).
 */
public interface ActorRepository extends JpaRepository<Actor, Long> {
}
