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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationSummaryDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationSummaryMapper;
import com.nageoffer.ai.ragent.rag.enums.ConversationMessageOrder;
import com.nageoffer.ai.ragent.rag.service.MessageFeedbackService;
import com.nageoffer.ai.ragent.rag.service.ConversationMessageService;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationMessageBO;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationSummaryBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ConversationMessageServiceImpl implements ConversationMessageService {

    private static final int MAX_QUERY_LIMIT = 500;
    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_ROLE_LENGTH = 16;
    private static final int MAX_MESSAGE_CONTENT_LENGTH = 100_000;
    private static final int MAX_SUMMARY_CONTENT_LENGTH = 20_000;
    private static final int MAX_THINKING_DURATION_SECONDS = 86_400;

    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationSummaryMapper conversationSummaryMapper;
    private final ConversationMapper conversationMapper;
    private final MessageFeedbackService feedbackService;

    @Override
    public String addMessage(ConversationMessageBO conversationMessage) {
        normalizeMessage(conversationMessage);
        ConversationMessageDO messageDO = BeanUtil.toBean(conversationMessage, ConversationMessageDO.class);
        conversationMessageMapper.insert(messageDO);
        return messageDO.getId();
    }

    @Override
    public List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order) {
        String normalizedConversationId = normalizeOptionalId(conversationId, "会话ID");
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedConversationId) || StrUtil.isBlank(normalizedUserId)) {
            return List.of();
        }

        ConversationDO conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, normalizedConversationId)
                        .eq(ConversationDO::getUserId, normalizedUserId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (conversation == null) {
            return List.of();
        }

        Integer safeLimit = normalizeLimit(limit);
        if (safeLimit != null && safeLimit == 0) {
            return List.of();
        }
        boolean asc = order == null || order == ConversationMessageOrder.ASC;
        List<ConversationMessageDO> records = conversationMessageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, normalizedConversationId)
                        .eq(ConversationMessageDO::getUserId, normalizedUserId)
                        .eq(ConversationMessageDO::getDeleted, 0)
                        .orderBy(true, asc, ConversationMessageDO::getCreateTime)
                        .last(safeLimit != null, "limit " + safeLimit)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        if (!asc) {
            Collections.reverse(records);
        }

        List<String> assistantMessageIds = records.stream()
                .filter(record -> "assistant".equalsIgnoreCase(record.getRole()))
                .map(ConversationMessageDO::getId)
                .toList();
        Map<String, Integer> votesByMessageId = feedbackService.getUserVotes(normalizedUserId, assistantMessageIds);

        List<ConversationMessageVO> result = new ArrayList<>();
        for (ConversationMessageDO record : records) {
            ConversationMessageVO vo = ConversationMessageVO.builder()
                    .id(String.valueOf(record.getId()))
                    .conversationId(record.getConversationId())
                    .role(record.getRole())
                    .content(record.getContent())
                    .thinkingContent(record.getThinkingContent())
                    .thinkingDuration(record.getThinkingDuration())
                    .vote(votesByMessageId.get(record.getId()))
                    .createTime(record.getCreateTime())
                    .build();
            result.add(vo);
        }

        return result;
    }

    @Override
    public void addMessageSummary(ConversationSummaryBO conversationSummary) {
        normalizeSummary(conversationSummary);
        ConversationSummaryDO conversationSummaryDO = BeanUtil.toBean(conversationSummary, ConversationSummaryDO.class);
        conversationSummaryMapper.insert(conversationSummaryDO);
    }

    private void normalizeMessage(ConversationMessageBO message) {
        if (message == null) {
            throw new ClientException("消息内容不能为空");
        }
        message.setConversationId(normalizeRequiredId(message.getConversationId(), "会话ID"));
        message.setUserId(normalizeRequiredId(message.getUserId(), "用户ID"));
        message.setRole(normalizeRole(message.getRole()));
        message.setContent(normalizeRequiredText(message.getContent(), MAX_MESSAGE_CONTENT_LENGTH, "消息内容"));
        message.setThinkingContent(normalizeOptionalText(message.getThinkingContent(), MAX_MESSAGE_CONTENT_LENGTH, "深度思考内容"));
        message.setThinkingDuration(normalizeThinkingDuration(message.getThinkingDuration()));
    }

    private void normalizeSummary(ConversationSummaryBO summary) {
        if (summary == null) {
            throw new ClientException("摘要内容不能为空");
        }
        summary.setConversationId(normalizeRequiredId(summary.getConversationId(), "会话ID"));
        summary.setUserId(normalizeRequiredId(summary.getUserId(), "用户ID"));
        summary.setLastMessageId(normalizeRequiredId(summary.getLastMessageId(), "最后消息ID"));
        summary.setContent(normalizeRequiredText(summary.getContent(), MAX_SUMMARY_CONTENT_LENGTH, "摘要内容"));
    }

    private String normalizeRole(String role) {
        String value = normalizeRequiredText(role, MAX_ROLE_LENGTH, "消息角色").toLowerCase(Locale.ROOT);
        if ("system".equals(value) || "user".equals(value) || "assistant".equals(value)) {
            return value;
        }
        throw new ClientException("消息角色不合法");
    }

    private Integer normalizeThinkingDuration(Integer durationSeconds) {
        if (durationSeconds == null) {
            return null;
        }
        if (durationSeconds < 0 || durationSeconds > MAX_THINKING_DURATION_SECONDS) {
            throw new ClientException("深度思考耗时不合法");
        }
        return durationSeconds;
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String text = normalizeRequiredText(value, MAX_ID_LENGTH, fieldName);
        if (!text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String text = normalizeOptionalText(value, MAX_ID_LENGTH, fieldName);
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

    private static Integer normalizeLimit(Integer limit) {
        if (limit == null) {
            return null;
        }
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, MAX_QUERY_LIMIT);
    }
}
