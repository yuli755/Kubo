package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>JPA 实现的行可见性探针</h1>
 *
 * <p>只做一件事：{@code SELECT COUNT(*) FROM 实体 WHERE id = :id AND <读侧条件>}。
 * 条件由 {@link DataPermissionSpecification#applyQuery} 生成 —— <b>不另写一份</b>，
 * 否则"看得见才改得动"这句话就不成立了（读侧与写侧各判一次，迟早给出不同答案）。</p>
 *
 * <p>flush 模式设为 {@link FlushModeType#COMMIT}：本次事务里已改但尚未落库的值<b>不参与</b>判定。
 * 用默认 AUTO 的话，"把 creatorId 改成别人"会先被刷进会话、再被同一条 COUNT 读到，
 * 于是变成"用改完之后的状态证明改之前有权"—— 那是给绕过留的门，不是细节。</p>
 *
 * <p>为什么是 {@code COMMIT} 而不是 {@code MANUAL}：JPA 的 {@link FlushModeType} 只有
 * {@code AUTO} / {@code COMMIT} 两个值，"查询前不 flush"在 JPA 语义里就是 {@code COMMIT}；
 * {@code MANUAL} / {@code SKIP} 是 Hibernate 自己的 {@code org.hibernate.FlushMode}，
 * 用它要多绑一层实现依赖，而这笔账本模块一直在避免（同 {@link DataPermissionSpecification}
 * 不直接用 JSqlParser 的理由）。这一格是 CI 编译替我抓出来的：常量是我凭印象写的。</p>
 *
 * <p>空条件（该动作一条策略都没配）在这里返回 {@code true}：是否允许写入由
 * {@link DataPermissionGuard} 的动作级判定负责，本探针只回答"这一行在不在视野里"，
 * 两件事各归各处，才能各自被单独测。</p>
 *
 * @author Hamm.cn
 */
@Component
public class JpaRowVisibilityProbe implements RowWriteGuard.VisibilityProbe {

    /**
     * 主键属性名（{@code BaseEntity#id}）
     */
    private static final String ID_ATTRIBUTE = "id";

    private final DataPermissionSpecification specification;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaRowVisibilityProbe(DataPermissionSpecification specification) {
        this.specification = specification;
    }

    @Override
    public boolean visible(String resourceKey, Class<?> entityClass, DataPermissionAction action, Long id) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<?> root = query.from(entityClass);
        List<Predicate> predicates = new ArrayList<>(
                specification.buildPredicates(root, builder, resourceKey, action));
        predicates.add(builder.equal(root.get(ID_ATTRIBUTE), id));
        query.select(builder.count(root));
        query.where(predicates.toArray(new Predicate[0]));
        // 必须先拿到 TypedQuery<Long> 再单独设 flush 模式：Query#setFlushMode 的返回类型是 Query，
        // 而 Query#getSingleResult 返回 Object（泛型只存在于 TypedQuery 上）—— 串成一条链就编译不过。
        // 这一格踩过两次（第一次是凭印象写 FlushModeType.MANUAL），都由 CI 编译抓出：
        // 本地无 JDK 时，"我记得这个 API 长这样"只能当作待验证假设，不能当作事实写进代码。
        TypedQuery<Long> countQuery = entityManager.createQuery(query);
        countQuery.setFlushMode(FlushModeType.COMMIT);
        Long count = countQuery.getSingleResult();
        return count != null && count > 0L;
    }
}
