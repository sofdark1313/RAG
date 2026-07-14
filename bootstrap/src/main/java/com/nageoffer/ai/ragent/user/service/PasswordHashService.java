/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.user.service;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 用户密码哈希服务，统一处理新密码哈希和历史明文密码兼容。
 */
@Component
public class PasswordHashService {

    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 为原始密码生成 PBKDF2 哈希串。
     */
    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt, ITERATIONS);
        return HASH_PREFIX
                + "$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 校验原始密码是否匹配存储值。历史明文值仅用于兼容旧数据。
     */
    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (!isHashed(storedPassword)) {
            return constantTimeEquals(rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = storedPassword.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = derive(rawPassword, salt, iterations);
            return constantTimeEquals(actualHash, expectedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 判断存储值是否仍需升级为当前哈希格式。
     */
    public boolean needsRehash(String storedPassword) {
        return !StringUtils.hasText(storedPassword) || !isHashed(storedPassword);
    }

    private boolean isHashed(String storedPassword) {
        return storedPassword.startsWith(HASH_PREFIX + "$");
    }

    private byte[] derive(String rawPassword, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new ServiceException("密码哈希参数不合法");
        } catch (Exception e) {
            throw new ServiceException("密码哈希算法不可用");
        } finally {
            spec.clearPassword();
        }
    }

    private boolean constantTimeEquals(byte[] actual, byte[] expected) {
        return MessageDigest.isEqual(actual, expected);
    }
}
