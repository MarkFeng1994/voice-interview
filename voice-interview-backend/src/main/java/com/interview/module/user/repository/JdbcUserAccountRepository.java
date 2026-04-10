package com.interview.module.user.repository;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.module.user.entity.UserEntity;
import com.interview.module.user.mapper.UserMapper;
import com.interview.module.user.service.UserAccount;

@Repository
@ConditionalOnProperty(prefix = "app.auth", name = "provider", havingValue = "jdbc")
public class JdbcUserAccountRepository implements UserAccountRepository {

	private final UserMapper userMapper;

	public JdbcUserAccountRepository(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Override
	public Optional<UserAccount> findByUsername(String username) {
		UserEntity entity = userMapper.selectOne(
				new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
		return Optional.ofNullable(entity).map(this::toRecord);
	}

	@Override
	public Optional<UserAccount> findById(String id) {
		UserEntity entity = userMapper.selectById(parseId(id));
		return Optional.ofNullable(entity).map(this::toRecord);
	}

	@Override
	public UserAccount save(UserAccount account) {
		UserEntity entity = new UserEntity();
		entity.setUsername(account.username());
		entity.setPassword(account.password());
		entity.setNickname(account.nickname());
		userMapper.insert(entity);
		return toRecord(entity);
	}

	@Override
	public UserAccount updateNickname(String userId, String nickname) {
		UserEntity entity = userMapper.selectById(parseId(userId));
		if (entity == null) {
			throw new IllegalArgumentException("用户不存在");
		}
		entity.setNickname(nickname);
		userMapper.updateById(entity);
		return toRecord(entity);
	}

	private UserAccount toRecord(UserEntity e) {
		return new UserAccount(String.valueOf(e.getId()), e.getUsername(), e.getPassword(), e.getNickname());
	}

	private long parseId(String id) {
		try {
			return Long.parseLong(id);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("用户ID格式非法");
		}
	}
}
