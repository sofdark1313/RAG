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

package com.nageoffer.ai.ragent.ingestion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.ingestion.controller.request.IngestionPipelineCreateRequest;
import com.nageoffer.ai.ragent.ingestion.controller.request.IngestionPipelineNodeRequest;
import com.nageoffer.ai.ragent.ingestion.controller.request.IngestionPipelineUpdateRequest;
import com.nageoffer.ai.ragent.ingestion.controller.vo.IngestionPipelineNodeVO;
import com.nageoffer.ai.ragent.ingestion.controller.vo.IngestionPipelineVO;
import com.nageoffer.ai.ragent.ingestion.dao.entity.IngestionPipelineDO;
import com.nageoffer.ai.ragent.ingestion.dao.entity.IngestionPipelineNodeDO;
import com.nageoffer.ai.ragent.ingestion.dao.mapper.IngestionPipelineMapper;
import com.nageoffer.ai.ragent.ingestion.dao.mapper.IngestionPipelineNodeMapper;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.web.PageRequests;
import com.nageoffer.ai.ragent.ingestion.domain.enums.IngestionNodeType;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.PipelineDefinition;
import com.nageoffer.ai.ragent.ingestion.service.IngestionPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据清洗流水线业务逻辑实现
 */
@Service
@RequiredArgsConstructor
public class IngestionPipelineServiceImpl implements IngestionPipelineService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_NODE_COUNT = 50;
    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_NODE_ID_LENGTH = 20;
    private static final int MAX_NODE_TYPE_LENGTH = 16;
    private static final int MAX_NODE_JSON_LENGTH = 20_000;

    private final IngestionPipelineMapper pipelineMapper;
    private final IngestionPipelineNodeMapper nodeMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IngestionPipelineVO create(IngestionPipelineCreateRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        IngestionPipelineDO pipeline = IngestionPipelineDO.builder()
                .name(normalizeRequiredText(request.getName(), MAX_NAME_LENGTH, "流水线名称"))
                .description(normalizeOptionalText(request.getDescription(), MAX_DESCRIPTION_LENGTH, "流水线描述"))
                .createdBy(UserContext.getUsername())
                .updatedBy(UserContext.getUsername())
                .build();
        try {
            pipelineMapper.insert(pipeline);
        } catch (DuplicateKeyException dke) {
            throw new ClientException("流水线名称已存在");
        }
        upsertNodes(pipeline.getId(), request.getNodes());
        return toVO(pipeline, fetchNodes(pipeline.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IngestionPipelineVO update(String pipelineId, IngestionPipelineUpdateRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        String normalizedPipelineId = normalizeRequiredId(pipelineId, "流水线ID");
        IngestionPipelineDO pipeline = pipelineMapper.selectById(normalizedPipelineId);
        Assert.notNull(pipeline, () -> new ClientException("未找到流水线"));

        if (StringUtils.hasText(request.getName())) {
            pipeline.setName(normalizeRequiredText(request.getName(), MAX_NAME_LENGTH, "流水线名称"));
        }
        if (request.getDescription() != null) {
            pipeline.setDescription(normalizeOptionalText(request.getDescription(), MAX_DESCRIPTION_LENGTH, "流水线描述"));
        }
        pipeline.setUpdatedBy(UserContext.getUsername());
        pipelineMapper.updateById(pipeline);

        if (request.getNodes() != null) {
            upsertNodes(pipeline.getId(), request.getNodes());
        }
        return toVO(pipeline, fetchNodes(pipeline.getId()));
    }

    @Override
    public IngestionPipelineVO get(String pipelineId) {
        String normalizedPipelineId = normalizeRequiredId(pipelineId, "流水线ID");
        IngestionPipelineDO pipeline = pipelineMapper.selectById(normalizedPipelineId);
        Assert.notNull(pipeline, () -> new ClientException("未找到流水线"));
        return toVO(pipeline, fetchNodes(pipeline.getId()));
    }

    @Override
    public IPage<IngestionPipelineVO> page(Page<IngestionPipelineVO> page, String keyword) {
        Page<IngestionPipelineDO> mpPage = PageRequests.from(page);
        String normalizedKeyword = normalizeOptionalText(keyword, MAX_NAME_LENGTH, "关键词");
        LambdaQueryWrapper<IngestionPipelineDO> qw = new LambdaQueryWrapper<IngestionPipelineDO>()
                .eq(IngestionPipelineDO::getDeleted, 0)
                .like(StringUtils.hasText(normalizedKeyword), IngestionPipelineDO::getName, normalizedKeyword)
                .orderByDesc(IngestionPipelineDO::getUpdateTime);
        IPage<IngestionPipelineDO> result = pipelineMapper.selectPage(mpPage, qw);
        Page<IngestionPipelineVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(each -> toVO(each, fetchNodes(each.getId())))
                .toList());
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String pipelineId) {
        String normalizedPipelineId = normalizeRequiredId(pipelineId, "流水线ID");
        IngestionPipelineDO pipeline = pipelineMapper.selectById(normalizedPipelineId);
        Assert.notNull(pipeline, () -> new ClientException("未找到流水线"));
        pipeline.setDeleted(1);
        pipeline.setUpdatedBy(UserContext.getUsername());
        pipelineMapper.deleteById(pipeline);

        LambdaQueryWrapper<IngestionPipelineNodeDO> qw = new LambdaQueryWrapper<IngestionPipelineNodeDO>()
                .eq(IngestionPipelineNodeDO::getPipelineId, pipeline.getId());
        nodeMapper.delete(qw);
    }

    @Override
    public PipelineDefinition getDefinition(String pipelineId) {
        String normalizedPipelineId = normalizeRequiredId(pipelineId, "流水线ID");
        IngestionPipelineDO pipeline = pipelineMapper.selectById(normalizedPipelineId);
        Assert.notNull(pipeline, () -> new ClientException("未找到流水线"));

        List<NodeConfig> nodes = fetchNodes(pipeline.getId()).stream()
                .map(this::toNodeConfig)
                .toList();
        return PipelineDefinition.builder()
                .id(String.valueOf(pipeline.getId()))
                .name(pipeline.getName())
                .description(pipeline.getDescription())
                .nodes(nodes)
                .build();
    }

    private void upsertNodes(String normalizedPipelineId, List<IngestionPipelineNodeRequest> nodes) {
        if (nodes == null) {
            return;
        }
        if (nodes.size() > MAX_NODE_COUNT) {
            throw new ClientException("流水线节点数量不能超过" + MAX_NODE_COUNT + "个");
        }
        LambdaQueryWrapper<IngestionPipelineNodeDO> qw = new LambdaQueryWrapper<IngestionPipelineNodeDO>()
                .eq(IngestionPipelineNodeDO::getPipelineId, normalizedPipelineId);
        nodeMapper.delete(qw);
        for (IngestionPipelineNodeRequest node : nodes) {
            if (node == null) {
                continue;
            }
            IngestionPipelineNodeDO entity = IngestionPipelineNodeDO.builder()
                    .pipelineId(normalizedPipelineId)
                    .nodeId(normalizeRequiredText(node.getNodeId(), MAX_NODE_ID_LENGTH, "节点ID"))
                    .nodeType(normalizeNodeType(node.getNodeType()))
                    .nextNodeId(normalizeOptionalText(node.getNextNodeId(), MAX_NODE_ID_LENGTH, "下一节点ID"))
                    .settingsJson(toJson(node.getSettings()))
                    .conditionJson(toJson(node.getCondition()))
                    .createdBy(UserContext.getUsername())
                    .updatedBy(UserContext.getUsername())
                    .build();
            nodeMapper.insert(entity);
        }
    }

    private List<IngestionPipelineNodeDO> fetchNodes(String normalizedPipelineId) {
        LambdaQueryWrapper<IngestionPipelineNodeDO> qw = new LambdaQueryWrapper<IngestionPipelineNodeDO>()
                .eq(IngestionPipelineNodeDO::getPipelineId, normalizedPipelineId)
                .eq(IngestionPipelineNodeDO::getDeleted, 0);
        return nodeMapper.selectList(qw);
    }

    private IngestionPipelineVO toVO(IngestionPipelineDO pipeline, List<IngestionPipelineNodeDO> nodes) {
        IngestionPipelineVO vo = BeanUtil.toBean(pipeline, IngestionPipelineVO.class);
        vo.setNodes(nodes.stream().map(this::toNodeVO).toList());
        return vo;
    }

    private IngestionPipelineNodeVO toNodeVO(IngestionPipelineNodeDO node) {
        IngestionPipelineNodeVO vo = BeanUtil.toBean(node, IngestionPipelineNodeVO.class);
        vo.setNodeType(normalizeNodeTypeForOutput(node.getNodeType()));
        vo.setSettings(parseJson(node.getSettingsJson()));
        vo.setCondition(parseJson(node.getConditionJson()));
        return vo;
    }

    private NodeConfig toNodeConfig(IngestionPipelineNodeDO node) {
        return NodeConfig.builder()
                .nodeId(node.getNodeId())
                .nodeType(normalizeNodeType(node.getNodeType()))
                .settings(parseJson(node.getSettingsJson()))
                .condition(parseJson(node.getConditionJson()))
                .nextNodeId(node.getNextNodeId())
                .build();
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String json = node.toString();
        if (json.length() > MAX_NODE_JSON_LENGTH) {
            throw new ClientException("节点配置 JSON 长度不能超过" + MAX_NODE_JSON_LENGTH + "个字符");
        }
        return json;
    }

    private JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeNodeType(String nodeType) {
        String actualNodeType = normalizeRequiredText(nodeType, MAX_NODE_TYPE_LENGTH, "节点类型");
        try {
            return IngestionNodeType.fromValue(actualNodeType).getValue();
        } catch (IllegalArgumentException ex) {
            throw new ClientException("未知节点类型: " + actualNodeType);
        }
    }

    private String normalizeNodeTypeForOutput(String nodeType) {
        if (!StringUtils.hasText(nodeType)) {
            return nodeType;
        }
        try {
            return IngestionNodeType.fromValue(nodeType).getValue();
        } catch (IllegalArgumentException ex) {
            return nodeType;
        }
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        String text = StrUtil.trimToNull(value);
        Assert.notBlank(text, () -> new ClientException(fieldName + "不能为空"));
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

    private String normalizeRequiredId(String value, String fieldName) {
        String text = normalizeRequiredText(value, MAX_ID_LENGTH, fieldName);
        if (!text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }
}
