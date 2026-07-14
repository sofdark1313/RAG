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

package com.nageoffer.ai.ragent.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.user.controller.request.LoginRequest;
import com.nageoffer.ai.ragent.user.controller.vo.LoginVO;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.service.AuthService;
import com.nageoffer.ai.ragent.user.service.PasswordHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/583231?v=4";
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final UserMapper userMapper;
    private final PasswordHashService passwordHashService;

    @Override
    public LoginVO login(LoginRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("请求不能为空");
        }
        String username = normalizeRequiredText(requestParam.getUsername(), MAX_USERNAME_LENGTH, "用户名");
        String password = normalizeRequiredText(requestParam.getPassword(), MAX_PASSWORD_LENGTH, "密码");
        UserDO user = findByUsername(username);
        if (user == null || !passwordHashService.matches(password, user.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }
        if (user.getId() == null) {
            throw new ClientException("用户信息异常");
        }
        upgradePasswordHashIfNecessary(user, password);
        String loginId = user.getId().toString();
        StpUtil.login(loginId);
        String avatar = StrUtil.isBlank(user.getAvatar()) ? DEFAULT_AVATAR_URL : user.getAvatar();
        return new LoginVO(loginId, user.getRole(), StpUtil.getTokenValue(), avatar);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    private UserDO findByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getDeleted, 0)
        );
    }

    private void upgradePasswordHashIfNecessary(UserDO user, String rawPassword) {
        if (!passwordHashService.needsRehash(user.getPassword())) {
            return;
        }
        UserDO update = new UserDO();
        update.setId(user.getId());
        update.setPassword(passwordHashService.hash(rawPassword));
        userMapper.updateById(update);
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (StrUtil.isBlank(text)) {
            throw new ClientException(fieldName + "不能为空");
        }
        if (text.length() > maxLength) {
            throw new ClientException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return text;
    }
}
