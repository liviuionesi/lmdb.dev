package dev.lmdb.user.repository;

import dev.lmdb.user.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA repository for {@link User} accounts. */
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Finds an account by its unique username (the JWT subject).
   *
   * @param username login name
   * @return the account, if registered
   */
  Optional<User> findByUsername(String username);

  /**
   * Checks whether a username is already taken (registration guard).
   *
   * @param username candidate login name
   * @return {@code true} if an account with this username exists
   */
  boolean existsByUsername(String username);

  /**
   * Checks whether an email is already registered (registration guard).
   *
   * @param email candidate address
   * @return {@code true} if an account with this email exists
   */
  boolean existsByEmail(String email);
}
