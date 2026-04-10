package com.interview.module.user.repository;

import java.util.Optional;

import com.interview.module.user.service.UserAccount;

public interface UserAccountRepository {

	Optional<UserAccount> findByUsername(String username);

	Optional<UserAccount> findById(String id);

	UserAccount save(UserAccount account);

	UserAccount updateNickname(String userId, String nickname);
}
