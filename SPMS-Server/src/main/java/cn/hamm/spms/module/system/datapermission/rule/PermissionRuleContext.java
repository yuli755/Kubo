package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <h1>条件树的上下文变量</h1>
 *
 * <p>策略里写 {@code "contextVar": "warehouseIds"}，实际值只能来自服务端已认证主体
 * （{@link PermissionContext}），<b>永远不接受</b>请求体/请求头提供的同名字段 —— 这是
 * 条件树与"前端传条件"的本质区别。</p>
 *
 * <p>{@code factoryIds} 目前由上下文固定为空集合（无 {@code user_factory} 关系表），
 * 引用它的策略在校验期会被判为绑定异常而不是静默放行，见施工图决策 D13。</p>
 *
 * @author Hamm.cn
 */
public record PermissionRuleContext(PermissionContext source) {

    /**
     * 标量维度
     */
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";

    /**
     * 集合维度
     */
    public static final String DEPARTMENT_IDS = "departmentIds";
    public static final String FACTORY_IDS = "factoryIds";
    public static final String WAREHOUSE_IDS = "warehouseIds";
    public static final String ROLE_CODES = "roleCodes";

    private static final Set<String> SCALAR = Set.of(USER_ID, TENANT_ID);
    private static final Set<String> COLLECTION = Set.of(DEPARTMENT_IDS, FACTORY_IDS, WAREHOUSE_IDS, ROLE_CODES);

    public static boolean isSupported(String variable) {
        return variable != null && (SCALAR.contains(variable) || COLLECTION.contains(variable));
    }

    public static boolean isCollectionVar(String variable) {
        return variable != null && COLLECTION.contains(variable);
    }

    public static Set<String> supported() {
        Set<String> all = new LinkedHashSet<>(SCALAR);
        all.addAll(COLLECTION);
        return Set.copyOf(all);
    }

    /**
     * 解析为标量值
     */
    public Object scalar(String variable) {
        if (source == null) {
            return null;
        }
        return switch (variable) {
            case USER_ID -> source.userId();
            case TENANT_ID -> source.tenantId();
            default -> null;
        };
    }

    /**
     * 解析为集合值（集合维度专用；标量维度自动包成单元素集合，方便 {@code IN}）
     */
    public Collection<?> collection(String variable) {
        if (source == null) {
            return Set.of();
        }
        return switch (variable) {
            case DEPARTMENT_IDS -> source.departmentIds();
            case FACTORY_IDS -> source.factoryIds();
            case WAREHOUSE_IDS -> source.warehouseIds();
            case ROLE_CODES -> source.roleCodes();
            case USER_ID -> source.userId() == null ? Set.of() : Set.of(source.userId());
            case TENANT_ID -> source.tenantId() == null ? Set.of() : Set.of(source.tenantId());
            default -> Set.of();
        };
    }

    /**
     * 该变量是否"应当非空但为空"—— 用于把配置/数据缺口暴露出来而不是静默放行
     */
    public boolean isEmpty(String variable) {
        return !isSupported(variable) || collection(variable).isEmpty();
    }
}
