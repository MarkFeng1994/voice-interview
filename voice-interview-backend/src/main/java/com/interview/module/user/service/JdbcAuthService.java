package com.interview.module.user.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.interview.module.user.repository.UserAccountRepository;

@Service
@ConditionalOnProperty(prefix = "app.auth", name = "provider", havingValue = "jdbc")
public class JdbcAuthService implements AuthService {

	private static final long DEFAULT_EXPIRES_IN = 7L * 24 * 60 * 60;

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;

	public JdbcAuthService(
			UserAccountRepository userAccountRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService
	) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
	}

	@Override
	public AuthSession register(String username, String password, String nickname) {
		String normalizedUsername = normalizeRequired(username, "username");
		String normalizedPassword = normalizeRequired(password, "password");
		String normalizedNickname = normalizeRequired(nickname, "nickname");
		if (userAccountRepository.findByUsername(normalizedUsername).isPresent()) {
			throw new IllegalArgumentException("用户名已存在");
		}
		UserAccount savedAccount = userAccountRepository.save(new UserAccount(
				"",
				normalizedUsername,
				passwordEncoder.encode(normalizedPassword),
				normalizedNickname
		));
		String token = issueToken(savedAccount);
		return new AuthSession(token, jwtTokenService.getExpireSeconds(), savedAccount.toProfile());
	}

	@Override
	public AuthSession login(String username, String password) {
		String normalizedUsername = normalizeRequired(username, "username");
		String normalizedPassword = normalizeRequired(password, "password");
		UserAccount account = userAccountRepository.findByUsername(normalizedUsername)
				.orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
		if (!passwordEncoder.matches(normalizedPassword, account.password())) {
			throw new IllegalArgumentException("用户名或密码错误");
		}
		String token = issueToken(account);
		return new AuthSession(token, jwtTokenService.getExpireSeconds(), account.toProfile());
	}

	@Override
	public UserProfile getProfileByToken(String token) {
		String normalizedToken = normalizeRequired(token, "token");
		JwtPrincipal principal = jwtTokenService.parseToken(normalizedToken);
		return userAccountRepository.findById(principal.userId())
				.map(UserAccount::toProfile)
				.orElseThrow(() -> new IllegalArgumentException("无效的登录状态，请重新登录"));
	}

	@Override
	public UserProfile updateProfileByToken(String token, String nickname) {
		String normalizedToken = normalizeRequired(token, "token");
		String normalizedNickname = normalizeRequired(nickname, "nickname");
		UserProfile profile = getProfileByToken(normalizedToken);
		if (profile == null) {
			throw new IllegalArgumentException("无效的登录状态，请重新登录");
		}
		UserAccount updatedAccount = userAccountRepository.updateNickname(profile.id(), normalizedNickname);
		return updatedAccount.toProfile();
	}

	private String issueToken(UserAccount account) {
		return jwtTokenService.issueToken(account.toProfile());
	}

	private String normalizeRequired(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " 不能为空");
		}
		return value.trim();
	}
}
