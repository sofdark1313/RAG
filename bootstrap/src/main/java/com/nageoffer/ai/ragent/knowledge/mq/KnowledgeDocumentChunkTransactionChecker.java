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

package com.nageoffer.ai.ragent.knowledge.mq;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.mq.MessageWrapper;
import com.nageoffer.ai.ragent.framework.mq.producer.DelegatingTransactionListener;
import com.nageoffer.ai.ragent.framework.mq.producer.TransactionChecker;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.knowledge.enums.DocumentStatus;
import com.nageoffer.ai.ragent.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文档分块事务消息回查器
 * <p>
 * 按 topic 注册，Broker 回查时可路由到任意实例，通过查询 DB 中文档状态判断本地事务是否已提交
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentChunkTransactionChecker implements TransactionChecker {

    private static final int MAX_ID_LENGTH = 20;

    private final KnowledgeDocumentMapper documentMapper;
    private final DelegatingTransactionListener transactionListener;

    @Value("knowledge-document-chunk_topic${unique-name:}")
    private String chunkTopic;

    @PostConstruct
    public void init() {
        transactionListener.registerChecker(chunkTopic, this);
    }

    @Override
    public boolean check(MessageWrapper<?> message) {
        if (message == null || !(message.getBody() instanceof KnowledgeDocumentChunkEvent)) {
            log.warn("[事务回查] 文档分块消息体无效");
            return false;
        }
        KnowledgeDocumentChunkEvent event = (KnowledgeDocumentChunkEvent) message.getBody();
        String normalizedDocId;
        try {
            normalizedDocId = normalizeOptionalId(event.getDocId(), "文档ID");
        } catch (IllegalArgumentException e) {
            log.warn("[事务回查] 文档分块消息文档ID不合法, keys={}", message.getKeys());
            return false;
        }
        if (StrUtil.isBlank(normalizedDocId)) {
            log.warn("[事务回查] 文档分块消息缺少文档ID, keys={}", message.getKeys());
            return false;
        }
        log.info("[事务回查] 文档分块，docId={}, keys={}", normalizedDocId, message.getKeys());
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);

        return documentDO != null
                && DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus());
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() > MAX_ID_LENGTH || !text.matches("\\d{1,20}")) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return text;
    }
}
