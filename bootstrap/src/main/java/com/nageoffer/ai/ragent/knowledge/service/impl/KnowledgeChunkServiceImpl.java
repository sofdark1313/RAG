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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeChunkVO;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.framework.web.PageRequests;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.knowledge.enums.DocumentStatus;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import cn.hutool.crypto.SecureUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库 Chunk 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_BATCH_CHUNK_COUNT = 500;

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingService embeddingService;
    private final TokenCounterService tokenCounterService;
    private final VectorStoreService vectorStoreService;
    private final TransactionOperations transactionOperations;

    @Override
    public IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));
        Integer enabled = normalizeEnabledFilter(requestParam == null ? null : requestParam.getEnabled());

        LambdaQueryWrapper<KnowledgeChunkDO> queryWrapper = new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, normalizedDocId)
                .eq(enabled != null, KnowledgeChunkDO::getEnabled, enabled)
                .orderByAsc(KnowledgeChunkDO::getChunkIndex);

        Page<KnowledgeChunkDO> page = PageRequests.from(requestParam);
        IPage<KnowledgeChunkDO> result = chunkMapper.selectPage(page, queryWrapper);
        return result.convert(each -> BeanUtil.toBean(each, KnowledgeChunkVO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("文档正在分块处理中，暂不支持新增 Chunk");
        }
        if (!Integer.valueOf(1).equals(documentDO.getEnabled())) {
            throw new ClientException("文档未启用，暂不支持新增 Chunk");
        }

        String content = requestParam.getContent();
        Assert.notBlank(content, () -> new ClientException("Chunk 内容不能为空"));

        KnowledgeChunkDO latest = chunkMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, normalizedDocId)
                        .orderByDesc(KnowledgeChunkDO::getChunkIndex)
                        .last("LIMIT 1")
        );
        int chunkIndex = requestParam.getIndex() != null
                ? requestParam.getIndex()
                : (latest != null ? latest.getChunkIndex() + 1 : 0);

        String contentHash = SecureUtil.sha256(content);
        int charCount = content.length();
        KnowledgeBaseDO kbDO = requireKnowledgeBase(documentDO.getKbId());
        String embeddingModel = kbDO.getEmbeddingModel();
        String collectionName = kbDO.getCollectionName();
        Integer tokenCount = resolveTokenCount(content);

        String chunkId = normalizeOptionalId(requestParam.getChunkId(), "Chunk ID");
        KnowledgeChunkDO chunkDO = KnowledgeChunkDO.builder()
                .id(chunkId)
                .kbId(documentDO.getKbId())
                .docId(normalizedDocId)
                .chunkIndex(chunkIndex)
                .content(content)
                .contentHash(contentHash)
                .charCount(charCount)
                .tokenCount(tokenCount)
                .enabled(1)
                .createdBy(UserContext.getUsername())
                .updatedBy(UserContext.getUsername())
                .build();

        chunkMapper.insert(chunkDO);
        log.info("新增 Chunk 成功, kbId={}, docId={}, chunkId={}, chunkIndex={}", documentDO.getKbId(), normalizedDocId, chunkDO.getId(), chunkIndex);

        documentMapper.update(Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getId, normalizedDocId)
                .eq(KnowledgeDocumentDO::getDeleted, 0)
                .setSql("chunk_count = chunk_count + {0}", 1));

        // 同步写入向量库
        syncChunkToVector(collectionName, normalizedDocId, chunkDO, embeddingModel);

        return BeanUtil.toBean(chunkDO, KnowledgeChunkVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams) {
        batchCreate(docId, requestParams, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams, boolean writeVector) {
        if (CollUtil.isEmpty(requestParams)) {
            return;
        }
        if (requestParams.size() > MAX_BATCH_CHUNK_COUNT) {
            throw new ClientException("单次批量新增 Chunk 数量不能超过 " + MAX_BATCH_CHUNK_COUNT);
        }

        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));

        requestParams.forEach(request -> Assert.notNull(request, () -> new ClientException("Chunk 请求不能为空")));
        boolean needAutoIndex = requestParams.stream().anyMatch(request -> request.getIndex() == null);
        int nextIndex = 0;
        if (needAutoIndex) {
            KnowledgeChunkDO latest = chunkMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeChunkDO>()
                            .eq(KnowledgeChunkDO::getDocId, normalizedDocId)
                            .orderByDesc(KnowledgeChunkDO::getChunkIndex)
                            .last("LIMIT 1")
            );
            nextIndex = latest != null && latest.getChunkIndex() != null ? latest.getChunkIndex() + 1 : 0;
        }

        String kbId = documentDO.getKbId();
        String username = UserContext.getUsername();
        KnowledgeBaseDO kbDO = requireKnowledgeBase(kbId);
        String embeddingModel = kbDO.getEmbeddingModel();
        String collectionName = kbDO.getCollectionName();
        List<KnowledgeChunkDO> chunkDOList = new ArrayList<>(requestParams.size());

        for (KnowledgeChunkCreateRequest request : requestParams) {
            String content = request.getContent();
            Assert.notBlank(content, () -> new ClientException("Chunk 内容不能为空"));

            Integer chunkIndex = request.getIndex();
            if (chunkIndex == null) {
                chunkIndex = nextIndex++;
            }

            String chunkId = normalizeOptionalId(request.getChunkId(), "Chunk ID");
            if (!StringUtils.hasText(chunkId)) {
                chunkId = IdUtil.getSnowflakeNextIdStr();
            }

            KnowledgeChunkDO chunkDO = KnowledgeChunkDO.builder()
                    .id(chunkId)
                    .kbId(kbId)
                    .docId(normalizedDocId)
                    .chunkIndex(chunkIndex)
                    .content(content)
                    .contentHash(SecureUtil.sha256(content))
                    .charCount(content.length())
                    .tokenCount(resolveTokenCount(content))
                    .enabled(1)
                    .createdBy(username)
                    .updatedBy(username)
                    .build();
            chunkDOList.add(chunkDO);
        }

        // 批量写入数据库，向量索引由上层统一处理以避免重复计算
        chunkMapper.insert(chunkDOList);

        documentMapper.update(Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getId, normalizedDocId)
                .eq(KnowledgeDocumentDO::getDeleted, 0)
                .setSql("chunk_count = chunk_count + {0}", chunkDOList.size()));

        if (writeVector) {
            List<VectorChunk> vectorChunks = chunkDOList.stream()
                    .map(each -> VectorChunk.builder()
                            .chunkId(String.valueOf(each.getId()))
                            .content(each.getContent())
                            .index(each.getChunkIndex())
                            .build())
                    .toList();
            if (CollUtil.isNotEmpty(vectorChunks)) {
                attachEmbeddings(vectorChunks, embeddingModel);
                vectorStoreService.indexDocumentChunks(collectionName, normalizedDocId, vectorChunks);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        String normalizedChunkId = normalizeRequiredId(chunkId, "Chunk ID");
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("文档正在分块处理中，暂不支持修改 Chunk");
        }

        KnowledgeChunkDO chunkDO = chunkMapper.selectById(normalizedChunkId);
        Assert.notNull(chunkDO, () -> new ClientException("Chunk 不存在"));
        Assert.isTrue(normalizedDocId.equals(chunkDO.getDocId()), () -> new ClientException("Chunk 不属于该文档"));

        String newContent = requestParam.getContent();
        Assert.notBlank(newContent, () -> new ClientException("Chunk 内容不能为空"));

        if (newContent.equals(chunkDO.getContent())) {
            return;
        }

        chunkDO.setContent(newContent);
        chunkDO.setContentHash(SecureUtil.sha256(newContent));
        chunkDO.setCharCount(newContent.length());
        KnowledgeBaseDO kbDO = requireKnowledgeBase(documentDO.getKbId());
        String embeddingModel = kbDO.getEmbeddingModel();
        String collectionName = kbDO.getCollectionName();
        chunkDO.setTokenCount(resolveTokenCount(newContent));
        chunkDO.setUpdatedBy(UserContext.getUsername());

        chunkMapper.updateById(chunkDO);

        log.info("更新 Chunk 成功, kbId={}, docId={}, chunkId={}", documentDO.getKbId(), normalizedDocId, normalizedChunkId);

        // 同步向量数据库
        vectorStoreService.updateChunk(
                collectionName,
                normalizedDocId,
                VectorChunk.builder()
                        .chunkId(normalizedChunkId)
                        .content(newContent)
                        .index(chunkDO.getChunkIndex())
                        .embedding(toArray(embedContent(newContent, embeddingModel)))
                        .build()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId, String chunkId) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        String normalizedChunkId = normalizeRequiredId(chunkId, "Chunk ID");
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("文档正在分块处理中，暂不支持删除 Chunk");
        }

        KnowledgeChunkDO chunkDO = chunkMapper.selectById(normalizedChunkId);
        Assert.notNull(chunkDO, () -> new ClientException("Chunk 不存在"));
        Assert.isTrue(normalizedDocId.equals(chunkDO.getDocId()), () -> new ClientException("Chunk 不属于该文档"));

        KnowledgeBaseDO kbDO = requireKnowledgeBase(documentDO.getKbId());
        String collectionName = kbDO.getCollectionName();

        chunkMapper.deleteById(normalizedChunkId);

        documentMapper.update(Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getId, normalizedDocId)
                .setSql("chunk_count = CASE WHEN chunk_count > 0 THEN chunk_count - 1 ELSE 0 END"));

        log.info("删除 Chunk 成功, kbId={}, docId={}, chunkId={}", documentDO.getKbId(), normalizedDocId, normalizedChunkId);

        deleteChunkFromVector(collectionName, normalizedChunkId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableChunk(String docId, String chunkId, boolean enabled) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        String normalizedChunkId = normalizeRequiredId(chunkId, "Chunk ID");
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("文档正在分块处理中，暂不支持修改 Chunk 状态");
        }
        validateDocumentEnabledForChunkEnable(documentDO, enabled);

        KnowledgeChunkDO chunkDO = chunkMapper.selectById(normalizedChunkId);
        Assert.notNull(chunkDO, () -> new ClientException("Chunk 不存在"));
        Assert.isTrue(normalizedDocId.equals(chunkDO.getDocId()), () -> new ClientException("Chunk 不属于该文档"));

        // 如果状态没变，直接返回
        int enabledValue = enabled ? 1 : 0;
        if (Integer.valueOf(enabledValue).equals(chunkDO.getEnabled())) {
            return;
        }

        chunkDO.setEnabled(enabledValue);
        chunkDO.setUpdatedBy(UserContext.getUsername());
        chunkMapper.updateById(chunkDO);

        KnowledgeBaseDO kbDO = requireKnowledgeBase(documentDO.getKbId());
        String collectionName = kbDO.getCollectionName();
        log.info("{}Chunk 成功, kbId={}, docId={}, chunkId={}", enabled ? "启用" : "禁用", documentDO.getKbId(), normalizedDocId, normalizedChunkId);

        if (enabled) {
            String embeddingModel = kbDO.getEmbeddingModel();
            syncChunkToVector(collectionName, normalizedDocId, chunkDO, embeddingModel);
        } else {
            deleteChunkFromVector(collectionName, normalizedChunkId);
        }
    }

    @Override
    public void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        if (requestParam == null || CollUtil.isEmpty(requestParam.getChunkIds())) {
            throw new ClientException("请指定需要操作的 Chunk，全量启用/禁用请使用文档启用接口");
        }
        if (requestParam.getChunkIds().size() > MAX_BATCH_CHUNK_COUNT) {
            throw new ClientException("单次批量操作 Chunk 数量不能超过 " + MAX_BATCH_CHUNK_COUNT);
        }
        List<String> requestedIds = requestParam.getChunkIds().stream()
                .map(id -> normalizeRequiredId(id, "Chunk ID"))
                .distinct()
                .collect(Collectors.toList());

        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));
        if (DocumentStatus.RUNNING.getCode().equals(documentDO.getStatus())) {
            throw new ClientException("文档正在分块处理中，暂不支持批量修改 Chunk 状态");
        }
        validateDocumentEnabledForChunkEnable(documentDO, enabled);

        List<KnowledgeChunkDO> found = chunkMapper.selectByIds(requestedIds);
        if (found.size() != requestedIds.size()) {
            throw new ClientException("存在无效的 Chunk ID，请求 " + requestedIds.size() + " 个，实际找到 " + found.size() + " 个");
        }
        found.forEach(c -> {
            if (!normalizedDocId.equals(c.getDocId())) {
                throw new ClientException("Chunk " + c.getId() + " 不属于文档 " + normalizedDocId);
            }
        });
        List<String> targetIds = found.stream().map(KnowledgeChunkDO::getId).collect(Collectors.toList());

        if (CollUtil.isEmpty(targetIds)) {
            return;
        }

        int enabledValue = enabled ? 1 : 0;
        List<KnowledgeChunkDO> needUpdateChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkDO>()
                        .in(KnowledgeChunkDO::getId, targetIds)
                        .ne(KnowledgeChunkDO::getEnabled, enabledValue)
        );
        List<String> needUpdateIds = needUpdateChunks.stream().map(KnowledgeChunkDO::getId).collect(Collectors.toList());

        if (CollUtil.isEmpty(needUpdateIds)) {
            throw new ClientException(enabled ? "所有 Chunk 已全部启用，无需重复操作" : "所有 Chunk 已全部禁用，无需重复操作");
        }

        KnowledgeBaseDO kbDO = requireKnowledgeBase(documentDO.getKbId());
        String collectionName = kbDO.getCollectionName();

        if (enabled) {
            List<VectorChunk> vectorChunks = needUpdateChunks.stream()
                    .map(c -> VectorChunk.builder()
                            .chunkId(c.getId())
                            .content(c.getContent())
                            .index(c.getChunkIndex())
                            .build())
                    .collect(Collectors.toList());
            attachEmbeddings(vectorChunks, kbDO.getEmbeddingModel());

            transactionOperations.executeWithoutResult(status -> {
                chunkMapper.update(
                        Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                                .in(KnowledgeChunkDO::getId, needUpdateIds)
                                .set(KnowledgeChunkDO::getEnabled, 1)
                                .set(KnowledgeChunkDO::getUpdatedBy, UserContext.getUsername())
                );
                vectorStoreService.indexDocumentChunks(collectionName, normalizedDocId, vectorChunks);
            });
        } else {
            transactionOperations.executeWithoutResult(status -> {
                chunkMapper.update(
                        Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                                .in(KnowledgeChunkDO::getId, needUpdateIds)
                                .set(KnowledgeChunkDO::getEnabled, 0)
                                .set(KnowledgeChunkDO::getUpdatedBy, UserContext.getUsername())
                );
                vectorStoreService.deleteChunksByIds(collectionName, needUpdateIds);
            });
        }

        log.info("批量{}Chunk 成功, kbId={}, docId={}, count={}", enabled ? "启用" : "禁用",
                documentDO.getKbId(), normalizedDocId, needUpdateIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabledByDocId(String docId, String kbId, boolean enabled) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        String normalizedKbId = normalizeRequiredId(kbId, "知识库ID");
        int enabledValue = enabled ? 1 : 0;
        chunkMapper.update(
                Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, normalizedDocId)
                        .set(KnowledgeChunkDO::getEnabled, enabledValue)
                        .set(KnowledgeChunkDO::getUpdatedBy, UserContext.getUsername())
        );
        log.info("根据文档ID更新所有Chunk启用状态, kbId={}, docId={}, enabled={}", normalizedKbId, normalizedDocId, enabled);
    }

    @Override
    public List<KnowledgeChunkVO> listByDocId(String docId) {
        String normalizedDocId = normalizeRequiredId(docId, "文档ID");
        KnowledgeDocumentDO documentDO = documentMapper.selectById(normalizedDocId);
        Assert.notNull(documentDO, () -> new ClientException("文档不存在"));

        List<KnowledgeChunkDO> chunkDOList = chunkMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                        .eq(KnowledgeChunkDO::getDocId, normalizedDocId)
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex)
        );

        return chunkDOList.stream()
                .map(each -> BeanUtil.toBean(each, KnowledgeChunkVO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocId(String docId) {
        String normalizedDocId = normalizeOptionalId(docId, "文档ID");
        if (normalizedDocId == null) {
            return;
        }
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkDO>().eq(KnowledgeChunkDO::getDocId, normalizedDocId));
    }

    // ==================== 私有方法 ====================

    /**
     * 启用 chunk 前必须保证所属文档为启用状态
     */
    private void validateDocumentEnabledForChunkEnable(KnowledgeDocumentDO documentDO, boolean enableChunk) {
        if (!enableChunk) {
            return;
        }
        if (!Integer.valueOf(1).equals(documentDO.getEnabled())) {
            throw new ClientException("文档未启用，无法启用Chunk，请先启用文档");
        }
    }

    /**
     * 将单个 chunk 同步到向量库
     */
    private void syncChunkToVector(String collectionName, String docId, KnowledgeChunkDO chunkDO, String embeddingModel) {
        List<Float> embedding = embedContent(chunkDO.getContent(), embeddingModel);
        float[] vector = toArray(embedding);

        VectorChunk chunk = VectorChunk.builder()
                .index(chunkDO.getChunkIndex())
                .content(chunkDO.getContent())
                .chunkId(String.valueOf(chunkDO.getId()))
                .embedding(vector)
                .build();
        vectorStoreService.indexDocumentChunks(collectionName, docId, List.of(chunk));

        log.debug("同步 Chunk 到向量库成功, collectionName={}, docId={}, chunkId={}", collectionName, docId, chunkDO.getId());
    }

    /**
     * 从向量库删除单个 chunk
     */
    private void deleteChunkFromVector(String collectionName, String chunkId) {
        vectorStoreService.deleteChunkById(collectionName, chunkId);
        log.debug("从向量库删除 Chunk, collectionName={}, chunkId={}", collectionName, chunkId);
    }

    /**
     * List<Float> 转 float[]
     */
    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private void attachEmbeddings(List<VectorChunk> chunks, String embeddingModel) {
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        List<String> texts = chunks.stream().map(VectorChunk::getContent).toList();
        List<List<Float>> vectors = embedBatch(texts, embeddingModel);
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new ServiceException("向量结果数量不匹配");
        }
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(toArray(vectors.get(i)));
        }
    }

    private List<Float> embedContent(String content, String embeddingModel) {
        return StrUtil.isBlank(embeddingModel)
                ? embeddingService.embed(content)
                : embeddingService.embed(content, embeddingModel);
    }

    private List<List<Float>> embedBatch(List<String> texts, String embeddingModel) {
        return StrUtil.isBlank(embeddingModel)
                ? embeddingService.embedBatch(texts)
                : embeddingService.embedBatch(texts, embeddingModel);
    }

    private Integer resolveTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return tokenCounterService.countTokens(content);
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String text = normalizeOptionalId(value, fieldName);
        Assert.notBlank(text, () -> new ClientException(fieldName + "不能为空"));
        return text;
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() > MAX_ID_LENGTH || !text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }

    private Integer normalizeEnabledFilter(Integer enabled) {
        if (enabled == null) {
            return null;
        }
        if (enabled != 0 && enabled != 1) {
            throw new ClientException("启用状态只能为 0 或 1");
        }
        return enabled;
    }

    private KnowledgeBaseDO requireKnowledgeBase(String kbId) {
        String normalizedKbId = normalizeRequiredId(kbId, "知识库ID");
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(normalizedKbId);
        Assert.notNull(kbDO, () -> new ServiceException("知识库不存在"));
        return kbDO;
    }
}
