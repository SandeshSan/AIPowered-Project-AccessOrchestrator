package com.accessorchestrator.repository;

import com.accessorchestrator.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdIgnoreCase(String userId);

    /** People whose line manager is one of the given users. */
    List<User> findByManager_IdIn(Collection<Long> managerPks);
}
