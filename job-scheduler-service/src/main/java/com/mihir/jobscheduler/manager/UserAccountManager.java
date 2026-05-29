package com.mihir.jobscheduler.manager;

import com.mihir.jobscheduler.entity.UserAccountEntity;
import com.mihir.jobscheduler.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserAccountManager {

    private final UserAccountRepository userAccountRepository;

    public Optional<UserAccountEntity> findByUserId(String userId) {
        return userAccountRepository.findById(userId);
    }

    public boolean existsByUserId(String userId) {
        return userAccountRepository.existsById(userId);
    }

    public UserAccountEntity save(UserAccountEntity userAccountEntity) {
        return userAccountRepository.save(userAccountEntity);
    }
}
