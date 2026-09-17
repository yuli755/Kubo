package cn.hamm.spms.framework.curd.datapermission;

import java.util.Map;

/**
 * <h1>Mapper 侧的行数探针（对账用）</h1>
 *
 * <p>§19.35 之后"谁能看哪些行"只有一棵树，于是同一个输入可以有两只眼睛：JPA 的 Criteria 与
 * MyBatis 的片段。要让"两套 ORM 是否一致"这件事变成可查的证据，就得有一个<em>按资源提供
 * "用这段片段数一遍行数"</em>的口子。本接口就是这个口子，它刻意只做一件事、只返回一个数：</p>
 *
 * <ul>
 *   <li>它<b>不是</b>读端点的入口（列表/详情/统计/导出不许挂在这上面）—— 那是"换轨"那一格的事；
 *       探针的产出只进 {@code data_permission_orm_parity_log}，不进业务响应；</li>
 *   <li>它<b>不拼任何条件</b>：参数来自 {@link RowPermissionPredicate#myBatisStatementParameters()}，
 *       片段来自 {@code SqlPermissionRuleRenderer}。谁在本接口的新实现里自己拼了 {@code tenant_id =}，
 *       就又把判据抄了第二遍（§19.35 刚清掉的那种账）。</li>
 * </ul>
 *
 * <p>实现者只有一个：资源的读侧 Mapper（{@code KuboBaseMapper} 子接口，见
 * {@code MaterialMapper}）。注册也只有一处：{@code MapperReadConfiguration} —— 因为那个配置类
 * 由 {@code kubo.data-permission.mapper-read.enabled}（默认 false）整块挡住，
 * 于是"开关关掉 ⇒ 一条对账 SQL 都不会发"这句是可验的，而不是靠人自觉。</p>

 * <p><b>2026-09-18 分层搬迁</b>：本类型原在 {@code cn.hamm.spms.module.system.datapermission.service}，
 * 由<b>框架层</b>搬来 —— 它是纯共享内核（只依赖 JDK），却被 {@code base/BaseService}、
 * {@code base/TenantAwareEntity} 这些下层类使用，构成"下层依赖业务域"的反向依赖。
 * 权限的<b>实现</b>（策略存储、拦截器、审计落库、规则编译）仍留在
 * {@code module.system.datapermission}；这里只留<b>抽象与上下文</b>。</p>
 *
 * @author Hamm.cn
 */
public interface RowParityCounter {

    /**
     * 这个探针属于哪个数据资源（{@code data_resource.resource_key}）
     *
     * <p>由实现方自己说，而不是集中在一张 {@code resourceKey → mapper} 的表里：集中表每加一个资源
     * 都要改地基，久而久之就会落后于代码，变成"看起来有清单、其实没人维护"的那种注册表。</p>
     *
     * @return 资源标识，非空
     */
    String parityResourceKey();

    /**
     * 用给定的参数对象数一遍行数
     *
     * @param parameters 至少含 {@link RowPermissionPredicate#FRAGMENT_KEY} 键（空串 = 不加条件），
     *                   其余是片段用到的命名参数
     * @return 行数；实现方拿不到数时返回 {@code null}（会被记成 {@code ERROR}，不会记成"一致"）
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
