package cn.hamm.spms.module.asset.material.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.asset.material.MaterialEntity;

import java.util.Map;

/**
 * <h1>物料读侧 Mapper（P2 第一个资源）</h1>
 *
 * <p>选它做第一个换轨对象是因为它已经是 {@code TenantAwareEntity} 的试点：
 * {@code tenant_id / creator_id} 在实体上真映射了，行级条件绑得上列 —— 这是"读侧能算出条件"的前提，
 * 24 个资源里只有它先满足。</p>
 *
 * <p>本接口只有一个方法，而且刻意不叫 {@code page}/{@code list}：它现在回答的是
 * <b>"这段权限片段在 Mapper 侧数出多少行"</b>，供 JPA 侧与 Mapper 侧对账用。真正的读端点接管
 * （列表/详情/统计/导出四条出口一起换）要等"判据合一"那一格，见施工图 §19.34。</p>
 *
 * @author Hamm.cn
 */
public interface MaterialMapper extends KuboBaseMapper<MaterialEntity> {

    /**
     * 按权限片段数一遍 {@code material}
     *
     * <p>参数用<b>一个 Map</b> 而不是 {@code @Param} 分列：片段文本里的
     * {@code #{dpRule0}} 之类占位符要在同一个参数对象上解析得出来，Map 是最小可行的形状
     * （见 {@code RowPermissionPredicate#myBatisStatementParameters}）。片段来自渲染器，
     * 调用方不许自己拼 SQL —— 这条由门禁看着。</p>
     *
     * @param parameters 至少含 {@code fragment} 键（可为空串 = 不加条件），其余是片段用到的命名参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
