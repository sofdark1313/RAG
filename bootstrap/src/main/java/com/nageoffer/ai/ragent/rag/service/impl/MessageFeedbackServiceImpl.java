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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import com.nageoffer.ai.ragent.rag.controller.request.MessageFeedbackRequest;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.MessageFeedbackDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.MessageFeedbackMapper;
import com.nageoffer.ai.ragent.rag.mq.event.MessageFeedbackEvent;
import com.nageoffer.ai.ragent.rag.service.MessageFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageFeedbackServiceImpl implements MessageFeedbackService {

    private static final int MAX_REASON_LENGTH = 255;
    private static final int MAX_COMMENT_LENGTH = 1024;
    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_VOTE_LOOKUP_SIZE = 500;

    private final MessageFeedbackMapper feedbackMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final MessageQueueProducer messageQueueProducer;

    @Value("message-feedback_topic${unique-name:}")
    private String feedbackTopic;

    @Override
    public void submitFeedbackAsync(String messageId, MessageFeedbackRequest request) {
        String normalizedUserId = normalizeRequiredId(UserContext.getUserId(), "用户ID");
        String normalizedMessageId = normalizeRequiredId(messageId, "消息ID");
        Assert.notNull(request, () -> new ClientException("反馈内容不能为空"));
        Integer vote = request.getVote();
        Assert.notNull(vote, () -> new ClientException("反馈值不能为空"));
        Assert.isTrue(vote == 1 || vote == -1, () -> new ClientException("反馈值必须为 1 或 -1"));
        loadAssistantMessage(normalizedMessageId, normalizedUserId);
        String reason = normalizeText(request.getReason(), MAX_REASON_LENGTH, "反馈原因");
        String comment = normalizeText(request.getComment(), MAX_COMMENT_LENGTH, "反馈评论");

        MessageFeedbackEvent event = MessageFeedbackEvent.builder()
                .messageId(normalizedMessageId)
                .userId(normalizedUserId)
                .vote(vote)
                .reason(reason)
                .comment(comment)
                .submitTime(System.currentTimeMillis())
                .build();
        messageQueueProducer.send(feedbackTopic, normalizedUserId + ":" + normalizedMessageId, "消息反馈", event);
    }

    @Override
    public void submitFeedback(String messageId, MessageFeedbackRequest request) {
        String normalizedUserId = normalizeRequiredId(UserContext.getUserId(), "用户ID");
        String normalizedMessageId = normalizeRequiredId(messageId, "消息ID");
        Assert.notNull(request, () -> new ClientException("反馈内容不能为空"));

        Integer vote = request.getVote();
        Assert.notNull(vote, () -> new ClientException("反馈值不能为空"));
        Assert.isTrue(vote == 1 || vote == -1, () -> new ClientException("反馈值必须为 1 或 -1"));

        ConversationMessageDO message = loadAssistantMessage(normalizedMessageId, normalizedUserId);
        String reason = normalizeText(request.getReason(), MAX_REASON_LENGTH, "反馈原因");
        String comment = normalizeText(request.getComment(), MAX_COMMENT_LENGTH, "反馈评论");
        doUpsertFeedback(normalizedMessageId, normalizedUserId, message.getConversationId(),
                vote, reason, comment, System.currentTimeMillis());
    }

    @Override
    public Map<String, Integer> getUserVotes(String userId, List<String> messageIds) {
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedUserId) || CollUtil.isEmpty(messageIds)) {
            return Collections.emptyMap();
        }
        List<String> normalizedMessageIds = messageIds.stream()
                .map(id -> normalizeOptionalId(id, "消息ID"))
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (normalizedMessageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        if (normalizedMessageIds.size() > MAX_VOTE_LOOKUP_SIZE) {
            throw new ClientException("消息ID数量不能超过" + MAX_VOTE_LOOKUP_SIZE);
        }
        List<MessageFeedbackDO> records = feedbackMapper.selectList(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getUserId, normalizedUserId)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .in(MessageFeedbackDO::getMessageId, normalizedMessageIds)
        );
        if (CollUtil.isEmpty(records)) {
            return Collections.emptyMap();
        }
        return records.stream()
                .collect(Collectors.toMap(
                        MessageFeedbackDO::getMessageId,
                        MessageFeedbackDO::getVote,
                        (first, second) -> first
                ));
    }

    private ConversationMessageDO loadAssistantMessage(String normalizedMessageId, String normalizedUserId) {
        ConversationMessageDO message = conversationMessageMapper.selectOne(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getId, normalizedMessageId)
                        .eq(ConversationMessageDO::getUserId, normalizedUserId)
                        .eq(ConversationMessageDO::getDeleted, 0)
        );
        Assert.notNull(message, () -> new ClientException("消息不存在"));
        Assert.isTrue("assistant".equalsIgnoreCase(message.getRole()), () -> new ClientException("仅支持对助手消息反馈"));
        return message;
    }

    private void doUpsertFeedback(String normalizedMessageId, String normalizedUserId, String conversationId,
                                  Integer vote, String reason, String comment, long submitTime) {
        MessageFeedbackDO existing = feedbackMapper.selectOne(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getMessageId, normalizedMessageId)
                        .eq(MessageFeedbackDO::getUserId, normalizedUserId)
                        .eq(MessageFeedbackDO::getDeleted, 0)
        );

        if (existing == null) {
            MessageFeedbackDO feedback = MessageFeedbackDO.builder()
                    .messageId(normalizedMessageId)
                    .conversationId(conversationId)
                    .userId(normalizedUserId)
                    .vote(vote)
                    .reason(reason)
                    .comment(comment)
                    .build();
            feedbackMapper.insert(feedback);
        } else {
            // 仅当本次提交时间晚于记录最后更新时间时才覆盖，避免多节点并行消费乱序
            feedbackMapper.update(
                    MessageFeedbackDO.builder()
                            .vote(vote)
                            .reason(reason)
                            .comment(comment)
                            .build(),
                    Wrappers.lambdaUpdate(MessageFeedbackDO.class)
                            .eq(MessageFeedbackDO::getId, existing.getId())
                            .lt(MessageFeedbackDO::getUpdateTime, new Date(submitTime))
            );
        }
    }

    @Override
    public void submitFeedbackByEvent(MessageFeedbackEvent event) {
        Assert.notNull(event, () -> new ClientException("反馈事件不能为空"));
        String normalizedMessageId = normalizeRequiredId(event.getMessageId(), "消息ID");
        String normalizedUserId = normalizeRequiredId(event.getUserId(), "用户ID");
        Assert.notNull(event.getVote(), () -> new ClientException("反馈值不能为空"));
        Assert.isTrue(event.getVote() == 1 || event.getVote() == -1, () -> new ClientException("反馈值必须为 1 或 -1"));

        ConversationMessageDO message = loadAssistantMessage(normalizedMessageId, normalizedUserId);
        doUpsertFeedback(
                normalizedMessageId,
                normalizedUserId,
                message.getConversationId(),
                event.getVote(),
                normalizeText(event.getReason(), MAX_REASON_LENGTH, "反馈原因"),
                normalizeText(event.getComment(), MAX_COMMENT_LENGTH, "反馈评论"),
                event.getSubmitTime());
    }

    private String normalizeText(String value, int maxLength, String fieldName) {
        String normalized = StrUtil.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ClientException(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String normalized = normalizeOptionalId(value, fieldName);
        if (StrUtil.isBlank(normalized)) {
            throw new ClientException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String normalized = StrUtil.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > MAX_ID_LENGTH || !normalized.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return normalized;
    }
}
