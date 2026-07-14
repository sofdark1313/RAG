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
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeBaseCreateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeBasePageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeBaseVO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.framework.web.PageRequests;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceNames;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceSpec;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreAdmin;
import com.nageoffer.ai.ragent.rag.util.S3BucketNames;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_EMBEDDING_MODEL_LENGTH = 64;
    private static final int MAX_COLLECTION_NAME_LENGTH = 64;
    private static final int MAX_ID_LENGTH = 20;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final VectorStoreAdmin vectorStoreAdmin;
    private final S3Client s3Client;

    @Transactional
    @Override
    public String create(KnowledgeBaseCreateRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("请求不能为空");
        }
        String name = normalizeRequiredText(requestParam.getName(), MAX_NAME_LENGTH, "知识库名称");
        String embeddingModel = normalizeRequiredText(requestParam.getEmbeddingModel(), MAX_EMBEDDING_MODEL_LENGTH, "嵌入模型");
        String collectionName = normalizeKnowledgeCollectionName(requestParam.getCollectionName());

        // 名称重复校验
        Long count = knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBaseDO>()
                        .eq(KnowledgeBaseDO::getName, name)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
        );
        if (count > 0) {
            throw new ServiceException("知识库名称已存在：" + name);
        }

        KnowledgeBaseDO kbDO = KnowledgeBaseDO.builder()
                .name(name)
                .embeddingModel(embeddingModel)
                .collectionName(collectionName)
                .createdBy(UserContext.getUsername())
                .updatedBy(UserContext.getUsername())
                .deleted(0)
                .build();

        knowledgeBaseMapper.insert(kbDO);

        String bucketName = collectionName;
        try {
            s3Client.createBucket(builder -> builder.bucket(bucketName));
            log.info("成功创建RestFS存储桶，Bucket名称: {}", bucketName);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            if (e instanceof BucketAlreadyOwnedByYouException) {
                log.error("RestFS存储桶已存在，Bucket名称: {}", bucketName, e);
            } else {
                log.error("RestFS存储桶已存在但由其他账户拥有，Bucket名称: {}", bucketName, e);
            }
            throw new ServiceException("存储桶名称已被占用：" + bucketName);
        }

        VectorSpaceSpec spaceSpec = VectorSpaceSpec.builder()
                .spaceId(VectorSpaceId.builder()
                        .logicalName(collectionName)
                        .build())
                .remark(name)
                .build();
        vectorStoreAdmin.ensureVectorSpace(spaceSpec);

        return String.valueOf(kbDO.getId());
    }

    @Override
    public void update(KnowledgeBaseUpdateRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("请求不能为空");
        }
        String normalizedKbId = normalizeRequiredId(requestParam.getId(), "知识库ID");
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(normalizedKbId);
        if (kb == null || kb.getDeleted() != null && kb.getDeleted() == 1) {
            throw new ClientException("知识库不存在：" + normalizedKbId);
        }

        String embeddingModel = normalizeOptionalText(requestParam.getEmbeddingModel(), MAX_EMBEDDING_MODEL_LENGTH, "嵌入模型");
        if (StringUtils.hasText(embeddingModel)
                && !embeddingModel.equals(kb.getEmbeddingModel())) {

            Long docCount = knowledgeDocumentMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeDocumentDO>()
                            .eq(KnowledgeDocumentDO::getKbId, normalizedKbId)
                            .gt(KnowledgeDocumentDO::getChunkCount, 0)
                            .eq(KnowledgeDocumentDO::getDeleted, 0)
            );
            if (docCount > 0) {
                throw new ClientException("知识库已存在向量化文档，不允许修改嵌入模型");
            }

            kb.setEmbeddingModel(embeddingModel);
        }

        String name = normalizeOptionalText(requestParam.getName(), MAX_NAME_LENGTH, "知识库名称");
        if (StringUtils.hasText(name)) {
            kb.setName(name);
        }

        kb.setUpdatedBy(UserContext.getUsername());
        knowledgeBaseMapper.updateById(kb);
    }

    @Override
    public void rename(String kbId, KnowledgeBaseUpdateRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("请求不能为空");
        }
        String normalizedKbId = normalizeRequiredId(kbId, "知识库ID");
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(normalizedKbId);
        if (kb == null || kb.getDeleted() != null && kb.getDeleted() == 1) {
            throw new ClientException("知识库不存在");
        }

        String name = normalizeRequiredText(requestParam.getName(), MAX_NAME_LENGTH, "知识库名称");

        // 名称重复校验（排除当前知识库）
        Long count = knowledgeBaseMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getName, name)
                        .ne(KnowledgeBaseDO::getId, normalizedKbId)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
        );
        if (count > 0) {
            throw new ServiceException("知识库名称已存在：" + name);
        }

        kb.setName(name);
        kb.setUpdatedBy(UserContext.getUsername());
        knowledgeBaseMapper.updateById(kb);

        log.info("成功重命名知识库, kbId={}, newName={}", normalizedKbId, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String kbId) {
        String normalizedKbId = normalizeRequiredId(kbId, "知识库ID");
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(normalizedKbId);
        if (kbDO == null || kbDO.getDeleted() != null && kbDO.getDeleted() == 1) {
            throw new ClientException("知识库不存在");
        }

        Long docCount = knowledgeDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, normalizedKbId)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
        );
        if (docCount != null && docCount > 0) {
            throw new ClientException("当前知识库下还有文档，请删除文档");
        }

        kbDO.setDeleted(1);
        kbDO.setUpdatedBy(UserContext.getUsername());
        knowledgeBaseMapper.deleteById(kbDO);
    }

    @Override
    public KnowledgeBaseVO queryById(String kbId) {
        String normalizedKbId = normalizeRequiredId(kbId, "知识库ID");
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(normalizedKbId);
        if (kbDO == null || kbDO.getDeleted() != null && kbDO.getDeleted() == 1) {
            throw new ClientException("知识库不存在");
        }
        return BeanUtil.toBean(kbDO, KnowledgeBaseVO.class);
    }

    @Override
    public IPage<KnowledgeBaseVO> pageQuery(KnowledgeBasePageRequest requestParam) {
        String name = normalizeOptionalText(requestParam == null ? null : requestParam.getName(), MAX_NAME_LENGTH, "知识库名称");
        LambdaQueryWrapper<KnowledgeBaseDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .like(StringUtils.hasText(name), KnowledgeBaseDO::getName, name)
                .eq(KnowledgeBaseDO::getDeleted, 0)
                .orderByDesc(KnowledgeBaseDO::getUpdateTime);

        Page<KnowledgeBaseDO> page = PageRequests.from(requestParam);
        IPage<KnowledgeBaseDO> result = knowledgeBaseMapper.selectPage(page, queryWrapper);
        Map<String, Long> docCountMap = new HashMap<>();
        if (CollUtil.isNotEmpty(result.getRecords())) {
            List<String> kbIds = result.getRecords().stream()
                    .map(KnowledgeBaseDO::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!kbIds.isEmpty()) {
                List<Map<String, Object>> rows = knowledgeDocumentMapper.selectMaps(
                        Wrappers.query(KnowledgeDocumentDO.class)
                                .select("kb_id", "COUNT(1) AS doc_count")
                                .in("kb_id", kbIds)
                                .eq("deleted", 0)
                                .groupBy("kb_id")
                );
                for (Map<String, Object> row : rows) {
                    Object kbIdValue = row.get("kb_id");
                    Object countValue = row.get("doc_count");
                    if (kbIdValue == null || countValue == null) {
                        continue;
                    }
                    docCountMap.put(kbIdValue.toString(), ((Number) countValue).longValue());
                }
            }
        }
        return result.convert(each -> {
            KnowledgeBaseVO vo = BeanUtil.toBean(each, KnowledgeBaseVO.class);
            Long docCount = docCountMap.get(each.getId());
            vo.setDocumentCount(docCount != null ? docCount : 0L);
            return vo;
        });
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (StrUtil.isBlank(text)) {
            throw new ClientException(fieldName + "不能为空");
        }
        if (text.length() > maxLength) {
            throw new ClientException(fieldName + "长度不能超过" + maxLength + "个字符");
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

    private String normalizeKnowledgeCollectionName(String value) {
        String collectionName = normalizeRequiredText(value, MAX_COLLECTION_NAME_LENGTH, "Collection名称");
        VectorSpaceNames.normalizeRequiredLogicalName(collectionName, "Collection名称");
        S3BucketNames.normalizeRequiredBucketName(collectionName, "Collection名称");
        return collectionName;
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String text = normalizeRequiredText(value, MAX_ID_LENGTH, fieldName);
        if (!text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }
}
