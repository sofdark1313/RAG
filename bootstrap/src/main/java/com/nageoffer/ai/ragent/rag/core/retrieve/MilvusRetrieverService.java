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
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusRetrieverService implements RetrieverService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 100;
    private static final int MAX_COLLECTION_NAME_LENGTH = 128;

    private final EmbeddingService embeddingService;
    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
        RetrieveRequest actualRequest = normalizeRequest(retrieveParam);
        if (StrUtil.isBlank(actualRequest.getQuery())) {
            return List.of();
        }
        List<Float> emb = embeddingService.embed(actualRequest.getQuery());
        float[] vector = toArray(emb);
        if (!isUsableVector(vector)) {
            return List.of();
        }
        return retrieveByVector(vector, actualRequest);
    }

    @Override
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
        RetrieveRequest actualRequest = normalizeRequest(retrieveParam);
        String collectionName = resolveCollectionName(actualRequest.getCollectionName());
        if (StrUtil.isBlank(collectionName) || !isUsableVector(vector)) {
            return List.of();
        }
        List<BaseVector> vectors = List.of(new FloatVec(normalize(vector)));

        Map<String, Object> params = new HashMap<>();
        params.put("metric_type", ragDefaultProperties.getMetricType());
        params.put("ef", 128);

        SearchReq req = SearchReq.builder()
                .collectionName(collectionName)
                .annsField("embedding")
                .data(vectors)
                .topK(actualRequest.getTopK())
                .searchParams(params)
                .outputFields(List.of("id", "content", "metadata"))
                .build();

        SearchResp resp = milvusClient.search(req);
        List<List<SearchResp.SearchResult>> results = resp.getSearchResults();

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        // TODO 需确认后续是否对分数较低数据进行限制，限制多少合适？0.65？
        // TODO 如果本次查询分数都较高，是否应该扩大查询范围？1.5倍？
        return results.get(0).stream()
                .map(r -> new RetrievedChunk(
                        Objects.toString(r.getEntity().get("id"), ""),
                        Objects.toString(r.getEntity().get("content"), ""),
                        r.getScore()))
                .collect(Collectors.toList());
    }

    private static float[] toArray(List<Float> list) {
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

    private static float[] normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) {
            sum += x * x;
        }
        double len = Math.sqrt(sum);
        float[] nv = new float[v.length];
        if (len <= 0) {
            return nv;
        }
        for (int i = 0; i < v.length; i++) {
            nv[i] = (float) (v[i] / len);
        }
        return nv;
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

    private String resolveCollectionName(String collectionName) {
        String normalized = normalizeCollectionName(collectionName);
        if (StrUtil.isNotBlank(normalized)) {
            return normalized;
        }
        return normalizeCollectionName(ragDefaultProperties.getCollectionName());
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
