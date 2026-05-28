package com.apidesign.repository;

import com.apidesign.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByFirstNameAndIsActive(String firstName, Boolean isActive);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query(
        """
        SELECT u FROM User u
        WHERE u.isActive = :isActive
        ORDER BY u.firstName, u.lastName
        """)
    Page<User> findActiveUsers(@Param("isActive") Boolean isActive, Pageable pageable);

    @Query(
        value =
            """
            SELECT * FROM USERS
            WHERE CREATED_AT > SYSDATE - 7
              AND IS_ACTIVE = 1
            """,
        nativeQuery = true)
    Page<User> findUsersCreatedInLastWeek(Pageable pageable);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();
}
