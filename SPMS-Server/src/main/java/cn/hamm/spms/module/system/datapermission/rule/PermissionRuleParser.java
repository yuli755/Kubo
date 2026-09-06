package cn.hamm.spms.module.system.datapermission.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <h1>条件树解析器</h1>
 *
 * <p>把 {@code data_policy.condition_json} 解析成 {@link PermissionRule}。策略刻意保守：</p>
 * <ul>
 *   <li><b>未知键即拒绝</b>：出现 {@code sql}/{@code raw}/{@code where}/{@code expression}
 *       这类键时给出"禁止裸 SQL"的明确原因，而不是静默忽略后照常拼接；</li>
 *   <li>空对象 {@code {}} → {@link PermissionRule.AllowAll}。存量 {@code condition_json}
 *       全是 {@code {}}（该字段此前从未被读取过），必须保持"无附加条件"语义，
 *       否则内核一上线就把所有已授权查询打空；</li>
 *   <li>{@code {"deny":true}} / {@code {"allow":true}} 显式表达恒假/恒真；</li>
 *   <li>叶子缺省 {@code op} 视为 {@code EQ}。</li>
 * </ul>
 *
 * <h2>JSON 形状</h2>
 * <pre>{@code
 * {
 *   "op": "ALL_OF",
 *   "children": [
 *     {"field": "tenantId", "contextVar": "tenantId"},
 *     {"op": "ANY_OF", "children": [
 *         {"field": "creatorId", "op": "EQ", "value": 7},
 *         {"field": "warehouseId", "op": "IN", "contextVar": "warehouseIds"}
 *     ]}
 *   ]
 * }
 * }</pre>
 *
 * @author Hamm.cn
 */
public final class PermissionRuleParser {

    /**
     * 允许出现的键；不在其中的一律拒绝（这是"禁止裸 SQL"的真正实现点）
     */
    private static final Set<String> KNOWN_KEYS = Set.of("op", "type", "field", "value", "contextVar", "children", "deny", "allow");

    /**
     * 看起来像在拼 SQL 的键名：单独给更醒目的报错文案
     */
    private static final Set<String> RAW_SQL_KEYS = Set.of("sql", "raw", "rawSql", "expression", "where", "clause", "fragment", "template", "condition");

    private final ObjectMapper mapper;

    public PermissionRuleParser() {
        this(JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }

    private PermissionRuleParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 解析条件树
     *
     * @param resourceKey 资源标识（仅用于异常信息与指标打点）
     * @param json        {@code condition_json} 原文，可为 null / 空
     * @return 条件树，永不返回 null
     */
    public PermissionRule parse(String resourceKey, String json) {
        if (json == null || json.isBlank()) {
            return PermissionRule.AllowAll.of();
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        }
        catch (JsonProcessingException ex) {
            throw new PermissionRuleException(resourceKey, "condition_json 不是合法 JSON：" + ex.getOriginalMessage());
        }
        if (root.isNull() || root.isMissingNode()) {
            return PermissionRule.AllowAll.of();
        }
        if (!root.isObject()) {
            throw new PermissionRuleException(resourceKey, "condition_json 根节点必须是对象，实际为 " + root.getNodeType());
        }
        return parseNode(resourceKey, root, 1);
    }

    /**
     * 解析并校验（管理端保存与读链路统一走这个入口，避免"存进去才发现不合法"）
     */
    public PermissionRule parseAndValidate(String resourceKey, String json, PermissionRuleValidator.FieldAllowList allowList) {
        PermissionRule rule = parse(resourceKey, json);
        new PermissionRuleValidator(allowList).validate(resourceKey, rule);
        return rule;
    }

    private PermissionRule parseNode(String resourceKey, JsonNode node, int depth) {
        if (depth > PermissionRuleValidator.MAX_DEPTH) {
            throw new PermissionRuleException(resourceKey,
                    "条件树嵌套超过 " + PermissionRuleValidator.MAX_DEPTH + " 层，请拆成多条策略而不是无限套娃");
        }
        if (!node.isObject()) {
            throw new PermissionRuleException(resourceKey, "children 的每个元素必须是对象");
        }
        rejectUnknownKeys(resourceKey, node);

        if (node.has("deny") || node.has("allow")) {
            boolean deny = node.path("deny").asBoolean(false);
            if (node.size() > 1) {
                throw new PermissionRuleException(resourceKey, "deny/allow 节点不能与其他键并存");
            }
            return deny ? PermissionRule.Deny.of() : PermissionRule.AllowAll.of();
        }

        String opText = text(node.has("op") ? node.get("op") : node.get("type"));
        JsonNode children = node.get("children");
        if (children != null) {
            if (!children.isArray() || children.isEmpty()) {
                throw new PermissionRuleException(resourceKey, "children 必须是非空数组");
            }
            PermissionRuleOp op = parseGroupOp(resourceKey, opText);
            List<PermissionRule> parsed = new ArrayList<>();
            for (JsonNode child : children) {
                parsed.add(parseNode(resourceKey, child, depth + 1));
            }
            return new PermissionRule.Group(op, parsed);
        }

        JsonNode fieldNode = node.get("field");
        if (fieldNode == null && opText == null) {
            // {} —— 无附加条件
            return PermissionRule.AllowAll.of();
        }

        PermissionRuleOp op = parseLeafOp(resourceKey, opText);
        String field = text(fieldNode);
        if (field == null || field.isBlank()) {
            throw new PermissionRuleException(resourceKey, "叶子节点缺少 field");
        }
        String contextVar = text(node.get("contextVar"));
        JsonNode valueNode = node.get("value");
        if (op.needsValue() && valueNode == null && contextVar == null) {
            throw new PermissionRuleException(resourceKey, "算子 " + op + " 需要 value 或 contextVar（field=" + field + "）");
        }
        if (!op.needsValue() && (valueNode != null || contextVar != null)) {
            throw new PermissionRuleException(resourceKey, "算子 " + op + " 不接受 value/contextVar");
        }
        Object value = valueNode == null || valueNode.isNull() ? null : toPlainValue(resourceKey, valueNode);
        return new PermissionRule.Leaf(op, field, value, contextVar);
    }

    private void rejectUnknownKeys(String resourceKey, JsonNode node) {
        Set<String> raw = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(name -> {
            if (KNOWN_KEYS.contains(name)) {
                return;
            }
            if (RAW_SQL_KEYS.contains(name)) {
                raw.add(name);
            } else {
                unknown.add(name);
            }
        });
        if (!raw.isEmpty()) {
            throw new PermissionRuleException(resourceKey,
                    "禁止裸 SQL：condition_json 不接受 " + raw + " 这类键；条件只能用 field + op + value|contextVar 表达，值一律参数绑定");
        }
        if (!unknown.isEmpty()) {
            throw new PermissionRuleException(resourceKey, "condition_json 含未知键 " + unknown + "；可用键：" + KNOWN_KEYS);
        }
    }

    private PermissionRuleOp parseGroupOp(String resourceKey, String opText) {
        PermissionRuleOp op = parseOp(resourceKey, opText);
        if (!op.isGroup()) {
            throw new PermissionRuleException(resourceKey, "带 children 的节点只能用 ALL_OF / ANY_OF，实际为 " + op);
        }
        return op;
    }

    private PermissionRuleOp parseLeafOp(String resourceKey, String opText) {
        if (opText == null) {
            return PermissionRuleOp.EQ;
        }
        PermissionRuleOp op = parseOp(resourceKey, opText);
        if (op.isGroup()) {
            throw new PermissionRuleException(resourceKey, "叶子节点不能使用分组算子 " + op);
        }
        return op;
    }

    private PermissionRuleOp parseOp(String resourceKey, String opText) {
        if (opText == null || opText.isBlank()) {
            throw new PermissionRuleException(resourceKey, "分组节点缺少 op（ALL_OF / ANY_OF）");
        }
        try {
            return PermissionRuleOp.valueOf(opText.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex) {
            throw new PermissionRuleException(resourceKey, "不支持的算子：" + opText + "；可用算子见 PermissionRuleOp");
        }
    }

    /**
     * 转成 JDK 值（Long/Double/Boolean/String/List），避免 JsonNode 泄漏进编译层
     */
    private Object toPlainValue(String resourceKey, JsonNode node) {
        if (node.isNumber()) {
            return node.isIntegralNumber() ? (Object) node.longValue() : (Object) node.doubleValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isArray()) {
            List<Object> items = new ArrayList<>();
            node.forEach(item -> items.add(toPlainValue(resourceKey, item)));
            return List.copyOf(items);
        }
        throw new PermissionRuleException(resourceKey, "value 只能是标量或标量数组，实际为 " + node.getNodeType());
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() || !node.isValueNode() ? null : node.asText();
    }
}
