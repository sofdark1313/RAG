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

package com.nageoffer.ai.ragent.rag.core.vector;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgVectorStoreService implements VectorStoreService {

    private static final int MAX_ID_LENGTH = 20;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");

        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        jdbcTemplate.batchUpdate(
                "INSERT INTO t_knowledge_vector (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector)",
                chunks, chunks.size(), (ps, chunk) -> {
                    if (chunk == null) {
                        throw new ClientException("Chunk 对象不能为空");
                    }
                    ps.setString(1, normalizeRequiredId(chunk.getChunkId(), "Chunk ID"));
                    ps.setString(2, chunk.getContent());
                    ps.setString(3, buildMetadataJson(collectionName, normalizedDocId, chunk));
                    ps.setString(4, toVectorLiteral(chunk.getEmbedding()));
                });

        log.info("批量写入向量到 PostgreSQL，collectionName={}, docId={}, count={}", collectionName, normalizedDocId, chunks.size());
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        int deleted = jdbcTemplate.update(
                "DELETE FROM t_knowledge_vector WHERE metadata->>'collection_name' = ? AND metadata->>'doc_id' = ?",
                collectionName, normalizedDocId);
        log.info("删除文档向量，collectionName={}, docId={}, deleted={}", collectionName, normalizedDocId, deleted);
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        String normalizedChunkId = normalizeRequiredId(chunkId, "Chunk ID");
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE id = ?", normalizedChunkId);
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        List<String> normalizedChunkIds = chunkIds.stream()
                .map(id -> normalizeRequiredId(id, "Chunk ID"))
                .distinct()
                .toList();
        String placeholders = normalizedChunkIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(", "));
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        int deleted = jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE id IN (" + placeholders + ")", normalizedChunkIds.toArray());
        log.info("批量删除 chunk 向量，collectionName={}, count={}, deleted={}", collectionName, normalizedChunkIds.size(), deleted);
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        String normalizedChunkId = normalizeRequiredId(chunk == null ? null : chunk.getChunkId(), "Chunk ID");
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_vector (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector) " +
                        "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding",
                normalizedChunkId,
                chunk.getContent(),
                buildMetadataJson(collectionName, normalizedDocId, chunk),
                toVectorLiteral(chunk.getEmbedding())
        );
    }

    private String buildMetadataJson(String collectionName, String docId, VectorChunk chunk) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (chunk.getMetadata() != null) {
            meta.putAll(chunk.getMetadata());
        }

        meta.put("collection_name", collectionName);
        meta.put("doc_id", docId);
        meta.put("chunk_index", chunk.getIndex());
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new RuntimeException("元数据序列化失败", e);
        }
    }

    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new ClientException("向量不能为空");
        }
        StringBuilder sb = new StringBuilder("[");
        boolean hasMagnitude = false;
        for (int i = 0; i < embedding.length; i++) {
            float value = embedding[i];
            if (!Float.isFinite(value)) {
                throw new ClientException("向量包含非法数值");
            }
            hasMagnitude = hasMagnitude || value != 0F;
            if (i > 0) sb.append(",");
            sb.append(value);
        }
        if (!hasMagnitude) {
            throw new ClientException("向量不能全为0");
        }
        return sb.append("]").toString();
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String normalized = normalizeOptionalId(value);
        if (normalized == null) {
            throw new ClientException(fieldName + "不合法");
        }
        return normalized;
    }

    private String normalizeOptionalId(String value) {
        String normalized = StrUtil.trimToNull(value);
        if (normalized == null || normalized.length() > MAX_ID_LENGTH || !normalized.matches("\\d{1,20}")) {
            return null;
        }
        return normalized;
    }
}
