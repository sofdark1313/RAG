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

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStoreService implements VectorStoreService {

    private static final Gson GSON = new Gson();
    private static final int MAX_ID_LENGTH = 20;

    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        Assert.isFalse(chunks == null || chunks.isEmpty(), () -> new ClientException("文档分块不允许为空"));
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");

        final int dim = ragDefaultProperties.getDimension();
        List<float[]> vectors = extractVectors(chunks, dim);

        List<JsonObject> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            VectorChunk chunk = chunks.get(i);
            Assert.notNull(chunk, () -> new ClientException("Chunk 对象不能为空"));
            String chunkId = normalizeRequiredId(chunk.getChunkId(), "Chunk ID");

            String content = chunk.getContent() == null ? "" : chunk.getContent();
            if (content.length() > 65535) {
                content = content.substring(0, 65535);
            }

            JsonObject metadata = buildMetadata(collectionName, normalizedDocId, chunk);

            JsonObject row = new JsonObject();
            row.addProperty("id", chunkId);
            row.addProperty("content", content);
            row.add("metadata", metadata);
            row.add("embedding", toJsonArray(vectors.get(i)));

            rows.add(row);
        }

        InsertReq req = InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build();

        InsertResp resp = milvusClient.insert(req);
        log.info("Milvus chunk 建立/写入向量索引成功, collection={}, rows={}", collectionName, resp.getInsertCnt());
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        Assert.isFalse(chunk == null, () -> new ClientException("Chunk 对象不能为空"));
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");

        final int dim = ragDefaultProperties.getDimension();
        float[] vector = extractVector(chunk, dim);

        String chunkPk = normalizeOptionalId(chunk.getChunkId());
        if (chunkPk == null) {
            if (StrUtil.isNotBlank(chunk.getChunkId())) {
                throw new ClientException("Chunk ID不合法");
            }
            chunkPk = IdUtil.getSnowflakeNextIdStr();
        }

        String content = chunk.getContent() == null ? "" : chunk.getContent();
        if (content.length() > 65535) {
            content = content.substring(0, 65535);
        }

        JsonObject metadata = buildMetadata(collectionName, normalizedDocId, chunk);

        JsonObject row = new JsonObject();
        row.addProperty("id", chunkPk);
        row.addProperty("content", content);
        row.add("metadata", metadata);
        row.add("embedding", toJsonArray(vector));

        UpsertReq upsertReq = UpsertReq.builder()
                .collectionName(collectionName)
                .data(List.of(row))
                .build();

        UpsertResp resp = milvusClient.upsert(upsertReq);
        log.info("Milvus 更新 chunk 向量索引成功, collection={}, docId={}, chunkId={}, upsertCnt={}",
                collectionName, docId, chunkPk, resp.getUpsertCnt());
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        // 已通过 collectionName 定位集合，只需按 doc_id 过滤即可
        String filter = "metadata[\"doc_id\"] == \"" + normalizedDocId + "\"";

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .build();

        DeleteResp resp = milvusClient.delete(deleteReq);
        log.info("Milvus 删除指定文档的所有 chunk 向量索引成功, collection={}, docId={}, deleteCnt={}",
                collectionName, normalizedDocId, resp.getDeleteCnt());
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        String normalizedChunkId = normalizeRequiredId(chunkId, "Chunk ID");
        // chunkId 就是 Milvus 中的 doc_id（主键），直接通过主键删除
        String filter = "id == \"" + normalizedChunkId + "\"";

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .build();

        DeleteResp resp = milvusClient.delete(deleteReq);
        log.info("Milvus 删除指定 chunk 向量索引成功, collection={}, chunkId={}, deleteCnt={}",
                collectionName, normalizedChunkId, resp.getDeleteCnt());
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
        String idList = normalizedChunkIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        String filter = "id in [" + idList + "]";

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .build();

        DeleteResp resp = milvusClient.delete(deleteReq);
        log.info("Milvus 批量删除 chunk 向量索引成功, collection={}, count={}, deleteCnt={}",
                collectionName, normalizedChunkIds.size(), resp.getDeleteCnt());
    }

    private List<float[]> extractVectors(List<VectorChunk> chunks, int expectedDim) {
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (VectorChunk chunk : chunks) {
            vectors.add(extractVector(chunk, expectedDim));
        }
        return vectors;
    }

    private float[] extractVector(VectorChunk chunk, int expectedDim) {
        Assert.notNull(chunk, () -> new ClientException("Chunk 对象不能为空"));
        float[] vector = chunk.getEmbedding();
        if (vector == null || vector.length == 0) {
            throw new ClientException("向量不能为空");
        }
        if (vector.length != expectedDim) {
            throw new ClientException("向量维度不匹配，期望维度为 " + expectedDim);
        }
        boolean hasMagnitude = false;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new ClientException("向量包含非法数值");
            }
            hasMagnitude = hasMagnitude || value != 0F;
        }
        if (!hasMagnitude) {
            throw new ClientException("向量不能全为0");
        }
        return vector;
    }

    private JsonArray toJsonArray(float[] v) {
        JsonArray arr = new JsonArray(v.length);
        for (float x : v) {
            arr.add(x);
        }
        return arr;
    }

    private JsonObject buildMetadata(String collectionName, String docId, VectorChunk chunk) {
        JsonObject metadata = new JsonObject();
        if (chunk.getMetadata() != null) {
            chunk.getMetadata().forEach((k, v) -> metadata.add(k, GSON.toJsonTree(v)));
        }

        metadata.addProperty("collection_name", collectionName);
        metadata.addProperty("doc_id", docId);
        metadata.addProperty("chunk_index", chunk.getIndex());
        return metadata;
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
