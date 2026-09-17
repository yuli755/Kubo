package cn.hamm.spms.framework.curd.datapermission;

import java.util.HashSet;
import java.util.Set;

/**
 * <h1>本次请求里"被提交成脱敏占位值"的字段登记处</h1>
 *
 * <p>为什么需要它：{@code SensitiveFieldDeserializer} 把 {@code "****"} 变成 {@code null}
 * 之后，"用户清空了该字段"与"用户提交的是掩码"在实体上就长得一模一样了。
 * 这两件事的处置完全相反 —— 前者要照写，后者要恢复原值 —— 所以必须在
 * <b>还知道真相的那一刻</b>（反序列化时）记下来，交给写链路消费。</p>
 *
 * <p>线程安全与泄漏：用 {@link ThreadLocal}，且 {@link #clear()} 用 {@code remove()}
 * 而不是清空集合 —— Tomcat 线程池会复用线程，留着空集合等于给每个长期线程挂一份内存。
 * 清理点在 {@code RequestInterceptor.afterCompletion}；服务侧消费完也会清一次，
 * 保证非 HTTP 入口（测试、异步任务）不残留。</p>

 * <p><b>2026-09-18 分层搬迁（为什么在框架层）</b>：本类原在 {@code cn.hamm.spms.module.system.datapermission.service}，它只依赖 JDK / 框架自身，却被 base 层与业务域同时使用 —— 留在业务域里就构成"下层依赖上层"，于是搬进框架层。
 * 搬迁前它是 base/BaseService 白名单里的"下层依赖业务域"条目之一，搬完白名单条目数 15 → 12。</p>
 *
 * @author Hamm.cn
 */
public final class MaskedInputRegistry {

    private static final ThreadLocal<Set<String>> MASKED_FIELDS = ThreadLocal.withInitial(HashSet::new);

    private MaskedInputRegistry() {
    }

    private static String key(Class<?> owner, String fieldName) {
        return owner.getName() + '#' + fieldName;
    }

    /**
     * 登记一个"提交值是脱敏占位"的字段
     *
     * @param owner     实体类
     * @param fieldName JSON 属性名
     */
    public static void mark(Class<?> owner, String fieldName) {
        MASKED_FIELDS.get().add(key(owner, fieldName));
    }

    /**
     * 该字段在本次请求里是否收到过脱敏占位值
     */
    public static boolean isMarked(Class<?> owner, String fieldName) {
        return MASKED_FIELDS.get().contains(key(owner, fieldName));
    }

    /**
     * 本次请求是否有任何字段被提交成掩码（用于避免无谓的回表）
     */
    public static boolean hasAny() {
        return !MASKED_FIELDS.get().isEmpty();
    }

    /**
     * 清空登记（请求结束时必须调用）
     */
    public static void clear() {
        MASKED_FIELDS.remove();
    }
}
