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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgRetrieverService implements RetrieverService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 100;
    private static final int MAX_COLLECTION_NAME_LENGTH = 128;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        RetrieveRequest actualRequest = normalizeRequest(request);
        if (StrUtil.isBlank(actualRequest.getQuery())) {
            return List.of();
        }
        List<Float> embedding = embeddingService.embed(actualRequest.getQuery());
        float[] vector = toArray(embedding);
        if (!isUsableVector(vector)) {
            return List.of();
        }
        return retrieveByVector(vector, actualRequest);
    }

    @Override
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest request) {
        RetrieveRequest actualRequest = normalizeRequest(request);
        if (!isUsableVector(vector)) {
            return List.of();
        }
        // 设置ef_search提升召回率
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        jdbcTemplate.execute("SET hnsw.ef_search = 200");

        String vectorLiteral = toVectorLiteral(normalize(vector));
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        return jdbcTemplate.query("SELECT id, content, 1 - (embedding <=> ?::vector) AS score FROM t_knowledge_vector WHERE metadata->>'collection_name' = ? ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> RetrievedChunk.builder()
                        .id(rs.getString("id"))
                        .text(rs.getString("content"))
                        .score(rs.getFloat("score"))
                        .build(),
                vectorLiteral, actualRequest.getCollectionName(), vectorLiteral, actualRequest.getTopK()
        );
    }

    private float[] normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        float[] normalized = new float[vector.length];
        if (norm <= 0) {
            return normalized;
        }
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }

    private float[] toArray(List<Float> list) {
        if (list == null || list.isEmpty()) {
            return new float[0];
        }
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Float value = list.get(i);
            arr[i] = value == null ? 0F : value;
        }
        return arr;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }

    private RetrieveRequest normalizeRequest(RetrieveRequest request) {
        RetrieveRequest source = request == null ? new RetrieveRequest() : request;
        return RetrieveRequest.builder()
                .query(StrUtil.trimToNull(source.getQuery()))
                .topK(normalizeTopK(source.getTopK()))
                .collectionName(normalizeCollectionName(source.getCollectionName()))
                .metadataFilters(source.getMetadataFilters())
                .build();
    }

    private int normalizeTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private String normalizeCollectionName(String collectionName) {
        String normalized = StrUtil.trimToNull(collectionName);
        if (normalized == null || normalized.length() > MAX_COLLECTION_NAME_LENGTH) {
            return null;
        }
        return normalized;
    }

    private boolean isUsableVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return false;
        }
        boolean hasMagnitude = false;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
            hasMagnitude = hasMagnitude || value != 0F;
        }
        return hasMagnitude;
    }
}
