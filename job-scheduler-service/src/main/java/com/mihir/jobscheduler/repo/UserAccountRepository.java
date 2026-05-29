package com.mihir.jobscheduler.repo;

import com.mihir.jobscheduler.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {
}
