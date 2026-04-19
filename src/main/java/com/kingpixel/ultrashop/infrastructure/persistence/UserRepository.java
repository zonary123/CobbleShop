package com.kingpixel.ultrashop.infrastructure.persistence;

import com.kingpixel.ultrashop.domain.model.UserInfo;

import java.util.UUID;

/**
 * Repository for user data (buy limits, cooldowns).
 */
public interface UserRepository {
  UserInfo findByUuid(UUID uuid);
  void save(UserInfo userInfo);
  void remove(UUID uuid);
}

