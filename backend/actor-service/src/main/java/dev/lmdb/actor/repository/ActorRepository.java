package dev.lmdb.actor.repository;

import dev.lmdb.actor.model.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA repository for typed {@link Actor} profiles (keyed by TMDB person id). */
public interface ActorRepository extends JpaRepository<Actor, Long> {}
