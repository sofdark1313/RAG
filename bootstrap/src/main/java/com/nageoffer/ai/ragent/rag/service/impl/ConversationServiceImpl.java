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

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.controller.request.ConversationUpdateRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.ConversationVO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationSummaryDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationSummaryMapper;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.service.ConversationService;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationCreateBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话服务实现类
 * 处理会话的创建、更新、重命名和删除等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int MAX_USER_ID_LENGTH = 20;
    private static final int MAX_QUESTION_LENGTH = 4000;

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final MemoryProperties memoryProperties;
    private final ConversationTitleGenerator titleGenerator;

    @Override
    public List<ConversationVO> listByUserId(String userId) {
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedUserId)) {
            return List.of();
        }

        List<ConversationDO> records = conversationMapper.selectList(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getUserId, normalizedUserId)
                        .eq(ConversationDO::getDeleted, 0)
                        .orderByDesc(ConversationDO::getLastTime)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .map(item -> ConversationVO.builder()
                        .conversationId(item.getConversationId())
                        .title(item.getTitle())
                        .lastTime(item.getLastTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void createOrUpdate(ConversationCreateBO request) {
        if (request == null) {
            throw new ClientException("请求内容不能为空");
        }
        String normalizedUserId = normalizeRequiredId(request.getUserId(), "用户ID");
        String normalizedConversationId = normalizeConversationId(request.getConversationId());
        String question = normalizeRequiredText(request.getQuestion(), MAX_QUESTION_LENGTH, "用户问题");

        ConversationDO existing = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, normalizedConversationId)
                        .eq(ConversationDO::getUserId, normalizedUserId)
                        .eq(ConversationDO::getDeleted, 0)
        );

        if (existing == null) {
            String title = titleGenerator.generate(question);
            ConversationDO record = ConversationDO.builder()
                    .conversationId(normalizedConversationId)
                    .userId(normalizedUserId)
                    .title(title)
                    .lastTime(request.getLastTime() == null ? new Date() : request.getLastTime())
                    .build();
            conversationMapper.insert(record);
            return;
        }

        existing.setLastTime(request.getLastTime() == null ? new Date() : request.getLastTime());
        conversationMapper.updateById(existing);
    }

    @Override
    public void rename(String conversationId, ConversationUpdateRequest request) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("会话信息缺失");
        }
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedUserId = normalizeRequiredId(userId, "用户ID");

        if (request == null) {
            throw new ClientException("请求内容不能为空");
        }
        String title = StrUtil.trimToNull(request.getTitle());
        if (StrUtil.isBlank(title)) {
            throw new ClientException("会话名称不能为空");
        }
        int maxLen = memoryProperties.getTitleMaxLength() == null ? 30 : memoryProperties.getTitleMaxLength();
        if (title.length() > maxLen) {
            throw new ClientException("会话名称长度不能超过" + maxLen + "个字符");
        }

        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, normalizedConversationId)
                        .eq(ConversationDO::getUserId, normalizedUserId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (record == null) {
            throw new ClientException("会话不存在");
        }

        record.setTitle(title);
        conversationMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(String conversationId) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("会话信息缺失");
        }
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedUserId = normalizeRequiredId(userId, "用户ID");

        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, normalizedConversationId)
                        .eq(ConversationDO::getUserId, normalizedUserId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (record == null) {
            throw new ClientException("会话不存在");
        }

        conversationMapper.deleteById(record.getId());
        messageMapper.delete(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, normalizedConversationId)
                        .eq(ConversationMessageDO::getUserId, normalizedUserId)
                        .eq(ConversationMessageDO::getDeleted, 0)
        );
        summaryMapper.delete(
                Wrappers.lambdaQuery(ConversationSummaryDO.class)
                        .eq(ConversationSummaryDO::getConversationId, normalizedConversationId)
                        .eq(ConversationSummaryDO::getUserId, normalizedUserId)
                        .eq(ConversationSummaryDO::getDeleted, 0)
        );
    }

    private String normalizeConversationId(String conversationId) {
        String text = normalizeRequiredText(conversationId, 20, "会话ID");
        if (!text.matches("\\d{1,20}")) {
            throw new ClientException("会话ID不合法");
        }
        return text;
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String text = normalizeOptionalId(value, fieldName);
        if (StrUtil.isBlank(text)) {
            throw new ClientException(fieldName + "不能为空");
        }
        return text;
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String text = normalizeOptionalText(value, MAX_USER_ID_LENGTH, fieldName);
        if (text == null) {
            return null;
        }
        if (!text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        String text = normalizeOptionalText(value, maxLength, fieldName);
        if (StrUtil.isBlank(text)) {
            throw new ClientException(fieldName + "不能为空");
        }
        return text;
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (text != null && text.length() > maxLength) {
            throw new ClientException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return text;
    }

}
