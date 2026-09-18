package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.base.CurdEntity;
import cn.hamm.spms.framework.curd.base.EntityRepositories;
import cn.hamm.spms.framework.curd.base.ICurdRepository;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.framework.curd.model.query.QueryHelper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;

/**
 * <h1>行可见性探针（MyBatis-Plus 实现，J4）</h1>
 *
 * <p>只做一件事：{@code SELECT COUNT(*) FROM 实体 WHERE id = ? AND <读侧条件>}。
 * 条件由 {@link DataPermissionSpecification#applyQuery} 生成 —— <b>不另写一份</b>，
 * 否则"看得见才改得动"这句话就不成立了（读侧与写侧各判一次，迟早给出不同答案）。</p>
 *
 * <h2>J4 换轨时改掉的两个前提</h2>
 * <ol>
 *   <li><b>不再是 JPA 查询</b>：原来走 {@code EntityManager} + Criteria，J4 之后与读侧一样走实体仓储
 *       （{@link EntityRepositories#bindingOf} 按实体类型解析；解析不到就抛，由调用方按不可见处理，
 *       <b>绝不放行</b>）。</li>
 *   <li><b>"不拿改完之后的状态证明改之前有权"换了实现方式</b>：JPA 侧靠
 *       {@code FlushModeType.COMMIT}（查询前不 flush 脏值）表达这条纪律；MyBatis-Plus 的 DML
 *       是立即执行的，而本探针在 {@code BaseService} 里排在 {@code saveToDatabase} <b>之前</b>
 *       （见 {@code enforceRowPermission}），所以查询读到的就是库里既存的那一行 —— 语义等价，
 *       不再需要一个 flush 开关（留着它反而会让人以为"改成 AUTO 就会先刷脏值"）。</li>
 * </ol>
 *
 * <p>空条件（该动作一条策略都没配、或当前是 OFF / SHADOW 档）在这里返回 {@code true}：
 * 是否允许写入由 {@link DataPermissionGuard} 的动作级判定负责，本探针只回答"这一行在不在视野里"
 * （SHADOW 档的"本会拒绝什么"由 {@link ShadowDiffService} 记进差异表，不在这里拒答），
 * 两件事各归各处，才能各自被单独测。</p>
 *
 * @author Hamm.cn
 */
@Component
public class RowVisibilityProbe implements RowWriteGuard.VisibilityProbe {

    private final DataPermissionSpecification specification;
    private final EntityRepositories entityRepositories;

    public RowVisibilityProbe(DataPermissionSpecification specification,
                              EntityRepositories entityRepositories) {
        this.specification = specification;
        this.entityRepositories = entityRepositories;
    }

    @Override
    public boolean visible(String resourceKey, Class<?> entityClass, DataPermissionAction action, Long id) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        specification.applyQuery(wrapper, entityClass, resourceKey, action);
        wrapper.eq(QueryHelper.column(entityClass, CurdEntity.STRING_ID), id);

        EntityRepositories.Binding binding = entityRepositories.bindingOf(entityClass);
        if (binding == null) {
            // 没有仓储就数不了：抛出去让调用方按不可见处理（RowWriteGuard 把 RuntimeException 读成
            // REASON_PARSE_FAILED + visible=false），静默 true 等于探针坏了自动放行
            throw new IllegalStateException("实体 " + entityClass.getSimpleName()
                    + " 没有对应的 ICurdRepository，无法校验行可见性");
        }
        // 条件已经由 applyQuery 按当前主体拼好；这一次数数绕开拦截器，避免被再注入一层（含未绑定
        // statement 的恒定假条件）——探针的结论必须只由 wrapper 决定，见 DataPermissionProbeContext
        Long count;
        try (PermissionContextHolder.Scope ignored = DataPermissionProbeContext.open()) {
            count = selectCount(binding.repository(), wrapper);
        }
        return count != null && count > 0L;
    }

    /**
     * 仓储的泛型参数在解析器里已经擦成通配符，而行数查询与实体类型无关（wrapper 只带列名与值）
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Long selectCount(ICurdRepository<?> repository, QueryWrapper<Object> wrapper) {
        return ((ICurdRepository) repository).selectCount(wrapper);
    }
}
