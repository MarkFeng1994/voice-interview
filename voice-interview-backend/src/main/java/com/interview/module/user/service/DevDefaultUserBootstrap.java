package com.interview.module.user.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.interview.module.user.repository.UserAccountRepository;

@Component
@ConditionalOnProperty(prefix = "app.auth", name = "provider", havingValue = "jdbc")
public class DevDefaultUserBootstrap {

	private static final String DEFAULT_USERNAME = "admin";
	private static final String DEFAULT_PASSWORD = "123456";
	private static final String DEFAULT_NICKNAME = "Admin";
	private static final String LEGACY_USERNAME = "coff0xc";
	private static final String DEV_PROFILE = "dev";

	private final Environment environment;
	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;

	public DevDefaultUserBootstrap(
			Environment environment,
			UserAccountRepository userAccountRepository,
			PasswordEncoder passwordEncoder
	) {
		this.environment = environment;
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void ensureDefaultUser() {
		if (!isDevProfileActive()) {
			return;
		}
		userAccountRepository.findByUsername(DEFAULT_USERNAME)
				.ifPresentOrElse(this::syncAdminCredentials, this::migrateLegacyOrCreateDefaultUser);
	}

	private void migrateLegacyOrCreateDefaultUser() {
		userAccountRepository.findByUsername(LEGACY_USERNAME)
				.ifPresentOrElse(this::migrateLegacyUser, this::createDefaultUser);
	}

	private void migrateLegacyUser(UserAccount legacyUser) {
		userAccountRepository.updateCredentials(
				legacyUser.id(),
				DEFAULT_USERNAME,
				encodeDefaultPassword(),
				DEFAULT_NICKNAME
		);
	}

	private void createDefaultUser() {
		userAccountRepository.save(new UserAccount(
				"",
				DEFAULT_USERNAME,
				encodeDefaultPassword(),
				DEFAULT_NICKNAME
		));
	}

	private void syncAdminCredentials(UserAccount adminUser) {
		userAccountRepository.updateCredentials(
				adminUser.id(),
				DEFAULT_USERNAME,
				encodeDefaultPassword(),
				DEFAULT_NICKNAME
		);
	}

	private boolean isDevProfileActive() {
		for (String profile : environment.getActiveProfiles()) {
			if (DEV_PROFILE.equals(profile)) {
				return true;
			}
		}
		return false;
	}

	private String encodeDefaultPassword() {
		return passwordEncoder.encode(DEFAULT_PASSWORD);
	}
}
