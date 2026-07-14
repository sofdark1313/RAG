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
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.nageoffer.ai.ragent.rag.controller.request.IntentNodeCreateRequest;
import com.nageoffer.ai.ragent.rag.controller.request.IntentNodeUpdateRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.IntentNodeTreeVO;
import com.nageoffer.ai.ragent.rag.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.enums.IntentLevel;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.IntentTreeCacheManager;
import com.nageoffer.ai.ragent.rag.core.intent.IntentTreeFactory;
import com.nageoffer.ai.ragent.ingestion.service.IntentTreeService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class IntentTreeServiceImpl extends ServiceImpl<IntentNodeMapper, IntentNodeDO> implements IntentTreeService {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final int MAX_MCP_TOOL_ID_LENGTH = 128;
    private static final int MAX_PROMPT_TEXT_LENGTH = 8000;
    private static final int MAX_EXAMPLES_COUNT = 20;
    private static final int MAX_EXAMPLE_LENGTH = 255;
    private static final int MAX_TOP_K = 50;
    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_BATCH_NODE_COUNT = 100;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IntentTreeCacheManager intentTreeCacheManager;

    private static final Gson GSON = new Gson();

    @Override
    public List<IntentNodeTreeVO> getFullTree() {
        List<IntentNodeDO> list = this.list(new LambdaQueryWrapper<IntentNodeDO>()
                .eq(IntentNodeDO::getDeleted, 0)
                .orderByAsc(IntentNodeDO::getSortOrder, IntentNodeDO::getId));

        // 先按 parentCode 分组
        Map<String, List<IntentNodeDO>> parentMap = list.stream()
                .collect(Collectors.groupingBy(node -> {
                    String parent = node.getParentCode();
                    return parent == null ? "ROOT" : parent;
                }));

        // 根节点：parentCode 为空
        List<IntentNodeDO> roots = parentMap.getOrDefault("ROOT", Collections.emptyList());

        // 递归构建树
        List<IntentNodeTreeVO> tree = new ArrayList<>();
        for (IntentNodeDO root : roots) {
            tree.add(buildTree(root, parentMap));
        }
        return tree;
    }

    private IntentNodeTreeVO buildTree(IntentNodeDO current,
                                       Map<String, List<IntentNodeDO>> parentMap) {
        IntentNodeTreeVO result = BeanUtil.toBean(current, IntentNodeTreeVO.class);
        List<IntentNodeDO> children = parentMap.getOrDefault(current.getIntentCode(), Collections.emptyList());

        if (!CollectionUtils.isEmpty(children)) {
            List<IntentNodeTreeVO> childVOs = children.stream()
                    .map(child -> buildTree(child, parentMap))
                    .collect(Collectors.toList());

            result.setChildren(childVOs);
        }

        return result;
    }

    @Override
    public String createNode(IntentNodeCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        String intentCode = normalizeRequiredText(requestParam.getIntentCode(), MAX_CODE_LENGTH, "意图标识");
        String name = normalizeRequiredText(requestParam.getName(), MAX_NAME_LENGTH, "意图名称");
        String kbId = normalizeOptionalId(requestParam.getKbId(), "知识库ID");
        Integer level = normalizeLevel(requestParam.getLevel());
        Integer kind = normalizeKind(requestParam.getKind());

        // 简单重复校验：intentCode 不允许重复
        long count = this.count(new LambdaQueryWrapper<IntentNodeDO>()
                .eq(IntentNodeDO::getIntentCode, intentCode)
                .eq(IntentNodeDO::getDeleted, 0));
        if (count > 0) {
            throw new ClientException("意图标识已存在: " + intentCode);
        }

        if (Objects.equals(level, IntentLevel.TOPIC.getCode())
                && Objects.equals(kind, IntentKind.KB.getCode())
                && StrUtil.isBlank(kbId)) {
            throw new ClientException("TOPIC级别的RAG检索节点必须指定目标知识库");
        }

        String collectionName = resolveCollectionName(kbId);
        IntentNodeDO node = IntentNodeDO.builder()
                .intentCode(intentCode)
                .kbId(kbId)
                .collectionName(collectionName)
                .name(name)
                .level(level)
                .parentCode(normalizeOptionalText(requestParam.getParentCode(), MAX_CODE_LENGTH, "父意图标识"))
                .description(normalizeOptionalText(requestParam.getDescription(), MAX_DESCRIPTION_LENGTH, "意图描述"))
                .mcpToolId(normalizeOptionalText(requestParam.getMcpToolId(), MAX_MCP_TOOL_ID_LENGTH, "MCP工具ID"))
                .examples(normalizeExamples(requestParam.getExamples()))
                .topK(normalizeTopK(requestParam.getTopK()))
                .kind(kind)
                .sortOrder(
                        requestParam.getSortOrder() == null ? 0 : requestParam.getSortOrder()
                )
                .enabled(normalizeEnabled(requestParam.getEnabled()))
                .createBy(UserContext.getUsername())
                .updateBy(UserContext.getUsername())
                .paramPromptTemplate(normalizePromptText(requestParam.getParamPromptTemplate(), "参数提取提示词模板"))
                .promptSnippet(normalizePromptText(requestParam.getPromptSnippet(), "提示词片段"))
                .promptTemplate(normalizePromptText(requestParam.getPromptTemplate(), "提示词模板"))
                .deleted(0)
                .build();

        this.save(node);

        // 清除Redis缓存，下次读取时会重新从数据库加载
        intentTreeCacheManager.clearIntentTreeCache();

        return String.valueOf(node.getId());
    }

    @Override
    public void updateNode(String id, IntentNodeUpdateRequest req) {
        Assert.notNull(req, () -> new ClientException("请求不能为空"));
        String normalizedId = normalizeRequiredId(id, "节点ID");
        IntentNodeDO node = this.getById(normalizedId);
        if (node == null || Objects.equals(node.getDeleted(), 1)) {
            throw new ServiceException("节点不存在或已删除: id=" + normalizedId);
        }

        if (req.getName() != null) {
            node.setName(normalizeRequiredText(req.getName(), MAX_NAME_LENGTH, "意图名称"));
        }
        if (req.getLevel() != null) {
            node.setLevel(normalizeLevel(req.getLevel()));
        }
        if (req.getParentCode() != null) {
            node.setParentCode(normalizeOptionalText(req.getParentCode(), MAX_CODE_LENGTH, "父意图标识"));
        }
        if (req.getDescription() != null) {
            node.setDescription(normalizeOptionalText(req.getDescription(), MAX_DESCRIPTION_LENGTH, "意图描述"));
        }
        if (req.getExamples() != null) {
            node.setExamples(normalizeExamples(req.getExamples()));
        }
        if (req.getCollectionName() != null) {
            node.setCollectionName(normalizeOptionalText(req.getCollectionName(), 128, "Collection名称"));
        }
        if (req.getTopK() != null) {
            node.setTopK(normalizeTopK(req.getTopK()));
        }
        if (req.getKind() != null) {
            node.setKind(normalizeKind(req.getKind()));
        }
        if (req.getSortOrder() != null) {
            node.setSortOrder(req.getSortOrder());
        }
        if (req.getEnabled() != null) {
            node.setEnabled(normalizeEnabled(req.getEnabled()));
        }
        if (req.getPromptSnippet() != null) {
            node.setPromptSnippet(normalizePromptText(req.getPromptSnippet(), "提示词片段"));
        }
        if (req.getPromptTemplate() != null) {
            node.setPromptTemplate(normalizePromptText(req.getPromptTemplate(), "提示词模板"));
        }
        if (req.getParamPromptTemplate() != null) {
            node.setParamPromptTemplate(normalizePromptText(req.getParamPromptTemplate(), "参数提取提示词模板"));
        }
        node.setUpdateBy(UserContext.getUsername());
        this.updateById(node);

        // 清除Redis缓存，下次读取时会重新从数据库加载
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    public void deleteNode(String id) {
        String normalizedId = normalizeRequiredId(id, "节点ID");
        this.removeById(normalizedId);

        // 清除Redis缓存，下次读取时会重新从数据库加载
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnableNodes(List<String> ids) {
        List<IntentNodeDO> targetNodes = listAndValidateTargetNodes(ids);
        String operator = UserContext.getUsername();
        targetNodes.forEach(node -> {
            node.setEnabled(1);
            node.setUpdateBy(operator);
        });
        this.updateBatchById(targetNodes);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisableNodes(List<String> ids) {
        List<IntentNodeDO> targetNodes = listAndValidateTargetNodes(ids);
        List<IntentNodeDO> allActiveNodes = listActiveNodes();
        Map<String, List<IntentNodeDO>> childrenMap = buildChildrenMap(allActiveNodes);
        Set<String> targetIdSet = targetNodes.stream().map(IntentNodeDO::getId).collect(Collectors.toSet());
        for (IntentNodeDO targetNode : targetNodes) {
            List<IntentNodeDO> descendants = collectDescendants(targetNode.getIntentCode(), childrenMap);
            List<IntentNodeDO> enabledButNotSelected = descendants.stream()
                    .filter(item -> Objects.equals(item.getEnabled(), 1) && !targetIdSet.contains(item.getId()))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(enabledButNotSelected)) {
                throw new ClientException(
                        String.format(
                                "批量停用失败：节点 [%s] 存在已启用的子节点未包含在本次操作中（如：%s），请先选择全量子节点",
                                targetNode.getName(),
                                summarizeNodeNames(enabledButNotSelected)
                        )
                );
            }
        }
        String operator = UserContext.getUsername();
        targetNodes.forEach(node -> {
            node.setEnabled(0);
            node.setUpdateBy(operator);
        });
        this.updateBatchById(targetNodes);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteNodes(List<String> ids) {
        List<IntentNodeDO> targetNodes = listAndValidateTargetNodes(ids);
        List<IntentNodeDO> allActiveNodes = listActiveNodes();
        Map<String, List<IntentNodeDO>> childrenMap = buildChildrenMap(allActiveNodes);
        Set<String> targetIdSet = targetNodes.stream().map(IntentNodeDO::getId).collect(Collectors.toSet());
        for (IntentNodeDO targetNode : targetNodes) {
            List<IntentNodeDO> descendants = collectDescendants(targetNode.getIntentCode(), childrenMap);
            List<IntentNodeDO> notSelectedDescendants = descendants.stream()
                    .filter(item -> !targetIdSet.contains(item.getId()))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(notSelectedDescendants)) {
                List<IntentNodeDO> enabledDescendants = notSelectedDescendants.stream()
                        .filter(item -> Objects.equals(item.getEnabled(), 1))
                        .collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(enabledDescendants)) {
                    throw new ClientException(
                            String.format(
                                    "批量删除失败：节点 [%s] 存在已启用的子节点未包含在本次操作中（如：%s），请先选择全量子节点",
                                    targetNode.getName(),
                                    summarizeNodeNames(enabledDescendants)
                            )
                    );
                }
                throw new ClientException(
                        String.format(
                                "批量删除失败：节点 [%s] 未包含全量子节点（如：%s），请先勾选完整子树后再删除",
                                targetNode.getName(),
                                summarizeNodeNames(notSelectedDescendants)
                        )
                );
            }
        }
        this.removeByIds(targetIdSet);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    @Override
    public int initFromFactory() {
        List<IntentNode> roots = IntentTreeFactory.buildIntentTree();
        List<IntentNode> allNodes = flatten(roots);

        int sort = 0;
        int created = 0;

        for (IntentNode node : allNodes) {
            // 如果已经存在相同 intentCode，就跳过，避免重复初始化
            if (existsByIntentCode(node.getId())) {
                continue;
            }

            IntentNodeCreateRequest nodeCreateRequest = IntentNodeCreateRequest.builder()
                    .kbId(node.getKbId())
                    .intentCode(node.getId())
                    .name(node.getName())
                    .level(mapLevel(node.getLevel()))
                    .parentCode(node.getParentId())
                    .description(node.getDescription())
                    .examples(node.getExamples())
                    .topK(normalizeTopK(node.getTopK()))
                    .kind(mapKind(node.getKind()))
                    .mcpToolId(node.getMcpToolId())
                    .sortOrder(sort++)
                    .enabled(1)
                    .promptTemplate(node.getPromptTemplate())
                    .promptSnippet(node.getPromptSnippet())
                    .paramPromptTemplate(node.getParamPromptTemplate())
                    .build();
            createNode(nodeCreateRequest);
            created++;
        }

        return created;
    }

    /**
     * 展平树结构：保证父节点在前，子节点在后（先根遍历）
     */
    private List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> result = new ArrayList<>();
        Deque<IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            IntentNode n = stack.pop();
            result.add(n);
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                // 为了保证父在前 / 子在后，这里逆序压栈
                List<IntentNode> children = n.getChildren();
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return result;
    }

    /**
     * IntentNode.Level -> Integer（0/1/2）
     */
    private int mapLevel(IntentLevel level) {
        return level.getCode();
    }

    /**
     * IntentKind -> Integer（0=KB, 1=SYSTEM, 2=MCP）
     */
    private int mapKind(IntentKind kind) {
        if (kind == null) {
            return 0; // 默认 KB
        }
        return kind.getCode();
    }

    /**
     * 判断 intentCode 是否已存在，避免重复插入
     */
    private boolean existsByIntentCode(String intentCode) {
        return baseMapper.selectCount(
                new LambdaQueryWrapper<IntentNodeDO>()
                        .eq(IntentNodeDO::getIntentCode, intentCode)
                        .eq(IntentNodeDO::getDeleted, 0)
        ) > 0;
    }

    /**
     * 规范化节点级 TopK：
     * - null 表示未配置，回退全局默认
     * - 仅允许正整数
     */
    private Integer normalizeTopK(Integer topK) {
        if (topK == null) {
            return null;
        }
        if (topK <= 0) {
            throw new ClientException("节点级 TopK 必须大于 0");
        }
        if (topK > MAX_TOP_K) {
            throw new ClientException("节点级 TopK 不能超过" + MAX_TOP_K);
        }
        return topK;
    }

    private Integer normalizeLevel(Integer level) {
        if (IntentLevel.fromCode(level) == null) {
            throw new ClientException("意图层级不合法");
        }
        return level;
    }

    private Integer normalizeKind(Integer kind) {
        Integer actualKind = kind == null ? IntentKind.KB.getCode() : kind;
        if (IntentKind.fromCode(actualKind) == null) {
            throw new ClientException("意图类型不合法");
        }
        return actualKind;
    }

    private Integer normalizeEnabled(Integer enabled) {
        if (enabled == null) {
            return 1;
        }
        if (!Objects.equals(enabled, 0) && !Objects.equals(enabled, 1)) {
            throw new ClientException("启用状态不合法");
        }
        return enabled;
    }

    private List<IntentNodeDO> listAndValidateTargetNodes(List<String> ids) {
        Assert.notEmpty(ids, () -> new ClientException("请至少选择一个节点"));
        if (ids.size() > MAX_BATCH_NODE_COUNT) {
            throw new ClientException("单次最多操作" + MAX_BATCH_NODE_COUNT + "个节点");
        }
        List<String> normalizedIds = ids.stream()
                .map(id -> normalizeOptionalId(id, "节点ID"))
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        Assert.notEmpty(normalizedIds, () -> new ClientException("节点ID不能为空"));
        List<IntentNodeDO> targetNodes = this.list(new LambdaQueryWrapper<IntentNodeDO>()
                .in(IntentNodeDO::getId, normalizedIds)
                .eq(IntentNodeDO::getDeleted, 0));
        if (targetNodes.size() != normalizedIds.size()) {
            Set<String> existingIds = targetNodes.stream().map(IntentNodeDO::getId).collect(Collectors.toSet());
            List<String> missingIds = normalizedIds.stream()
                    .filter(id -> !existingIds.contains(id))
                    .limit(5)
                    .toList();
            throw new ClientException("节点不存在或已删除: " + missingIds);
        }
        return targetNodes;
    }

    private List<IntentNodeDO> listActiveNodes() {
        return this.list(new LambdaQueryWrapper<IntentNodeDO>()
                .eq(IntentNodeDO::getDeleted, 0));
    }

    private Map<String, List<IntentNodeDO>> buildChildrenMap(List<IntentNodeDO> nodes) {
        return nodes.stream().collect(Collectors.groupingBy(node -> {
            String parentCode = node.getParentCode();
            return parentCode == null ? "ROOT" : parentCode;
        }));
    }

    private List<IntentNodeDO> collectDescendants(String intentCode, Map<String, List<IntentNodeDO>> childrenMap) {
        if (StrUtil.isBlank(intentCode)) {
            return Collections.emptyList();
        }
        List<IntentNodeDO> result = new ArrayList<>();
        Deque<IntentNodeDO> stack = new ArrayDeque<>(
                childrenMap.getOrDefault(intentCode, Collections.emptyList())
        );
        while (!stack.isEmpty()) {
            IntentNodeDO current = stack.pop();
            result.add(current);
            List<IntentNodeDO> children = childrenMap.getOrDefault(current.getIntentCode(), Collections.emptyList());
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return result;
    }

    private String summarizeNodeNames(List<IntentNodeDO> nodes) {
        return nodes.stream()
                .limit(3)
                .map(item -> StrUtil.blankToDefault(item.getName(), item.getIntentCode()))
                .collect(Collectors.joining("、"));
    }

    private String resolveCollectionName(String kbId) {
        if (StrUtil.isBlank(kbId)) {
            return null;
        }
        String normalizedKbId = normalizeRequiredId(kbId, "知识库ID");
        var knowledgeBase = knowledgeBaseMapper.selectById(normalizedKbId);
        if (knowledgeBase == null) {
            throw new ClientException("知识库不存在");
        }
        return knowledgeBase.getCollectionName();
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

    private String normalizePromptText(String value, String fieldName) {
        return normalizeOptionalText(value, MAX_PROMPT_TEXT_LENGTH, fieldName);
    }

    private String normalizeExamples(List<String> examples) {
        if (examples == null) {
            return null;
        }
        if (examples.size() > MAX_EXAMPLES_COUNT) {
            throw new ClientException("示例问题数量不能超过" + MAX_EXAMPLES_COUNT + "个");
        }
        List<String> normalized = examples.stream()
                .map(item -> normalizeOptionalText(item, MAX_EXAMPLE_LENGTH, "示例问题"))
                .filter(StrUtil::isNotBlank)
                .toList();
        return normalized.isEmpty() ? null : GSON.toJson(normalized);
    }
}
