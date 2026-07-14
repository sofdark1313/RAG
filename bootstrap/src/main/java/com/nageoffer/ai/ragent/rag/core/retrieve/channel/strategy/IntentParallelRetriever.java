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

package com.nageoffer.ai.ragent.rag.core.retrieve.channel.strategy;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.AbstractParallelRetriever;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 意图并行检索器
 * 继承模板类，实现意图特定的检索逻辑
 */
@Slf4j
public class IntentParallelRetriever extends AbstractParallelRetriever<IntentParallelRetriever.IntentTask> {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 100;
    private static final int DEFAULT_TOP_K_MULTIPLIER = 1;
    private static final int MAX_TOP_K_MULTIPLIER = 10;

    private final RetrieverService retrieverService;

    public record IntentTask(NodeScore nodeScore, int intentTopK) {
    }

    public IntentParallelRetriever(RetrieverService retrieverService,
                                   Executor executor) {
        super(executor);
        this.retrieverService = retrieverService;
    }

    /**
     * 执行并行检索（重载方法，支持动态 TopK 计算）
     */
    public List<RetrievedChunk> executeParallelRetrieval(String question,
                                                         List<NodeScore> targets,
                                                         int fallbackTopK,
                                                         int topKMultiplier) {
        List<IntentTask> intentTasks = targets.stream()
                .map(nodeScore -> new IntentTask(
                        nodeScore,
                        resolveIntentTopK(nodeScore, fallbackTopK, topKMultiplier)
                ))
                .toList();
        return super.executeParallelRetrieval(question, intentTasks, fallbackTopK);
    }

    @Override
    protected List<RetrievedChunk> createRetrievalTask(String question, IntentTask task, int ignoredTopK) {
        NodeScore nodeScore = task.nodeScore();
        IntentNode node = nodeScore.getNode();
        try {
            return retrieverService.retrieve(
                    RetrieveRequest.builder()
                            .collectionName(node.getCollectionName())
                            .query(question)
                            .topK(task.intentTopK())
                            .build()
            );
        } catch (Exception e) {
            log.error("意图检索失败 - 意图ID: {}, 意图名称: {}, Collection: {}, 错误: {}",
                    node.getId(), node.getName(), node.getCollectionName(), e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    protected String getTargetIdentifier(IntentTask task) {
        NodeScore nodeScore = task.nodeScore();
        IntentNode node = nodeScore.getNode();
        return String.format("意图ID: %s, 意图名称: %s", node.getId(), node.getName());
    }

    @Override
    protected String getStatisticsName() {
        return "意图检索";
    }

    /**
     * 计算单个意图节点检索 TopK
     */
    private int resolveIntentTopK(NodeScore nodeScore, int fallbackTopK, int topKMultiplier) {
        int baseTopK = normalizeTopK(fallbackTopK);
        if (nodeScore != null && nodeScore.getNode() != null) {
            Integer nodeTopK = nodeScore.getNode().getTopK();
            if (nodeTopK != null && nodeTopK > 0) {
                baseTopK = normalizeTopK(nodeTopK);
            }
        }

        int multiplier = Math.min(topKMultiplier, MAX_TOP_K_MULTIPLIER);
        if (topKMultiplier <= 0) {
            log.warn("意图定向通道倍率配置异常: {}, 使用基础 TopK: {}", topKMultiplier, baseTopK);
            multiplier = DEFAULT_TOP_K_MULTIPLIER;
        }
        return baseTopK * multiplier;
    }

    private int normalizeTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }
}
