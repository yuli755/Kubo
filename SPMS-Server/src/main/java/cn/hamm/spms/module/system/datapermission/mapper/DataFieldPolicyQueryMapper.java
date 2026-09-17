package cn.hamm.spms.module.system.datapermission.mapper;

import cn.hamm.spms.base.mapper.KuboInfraMapper;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>字段脱敏策略的查询 Mapper（带资源 join 的那条）</h1>
 *
 * <p>原来它是 {@code DataFieldPolicyRepository} 上的一个 JPA {@code @Query}：
 * JPQL 里 {@code JOIN dfp.resource dr} 直接走对象关联。换成 SQL 之后，关联变成显式列
 * {@code dr.resource_key = ...}，语义不变但**可读、可 EXPLAIN**，而且语句住在
 * {@code src/main/resources/mapper/system/datapermission/DataFieldPolicyQueryMapper.xml}
 * （用户 2026-09-18 的分层规则：禁止 {@code .java} 写 SQL）。</p>
 *
 * <p><b>为什么不放在 {@code DataFieldPolicyRepository}</b>：那个接口还在 Spring Data JPA 族里
 * （它的 CRUD 走 CRUD 引擎，等引擎换轨的那一批才能整体搬）。SQL 分层与 ORM 换轨是两件事，
 * 前者现在就能做：把语句摘出来放进专属 Mapper，等引擎换到 MyBatis-Plus 时这个 Mapper
 * 直接并回去即可（方法名与签名保持一致，调用点不用再动）。</p>
 *
 * <p><b>注册</b>：与证据/审计族同一种待遇 —— {@code KuboInfraMapper}，无条件注册，
 * 不受 {@code kubo.data-permission.mapper-read.enabled} 影响（脱敏策略是权限判定链的一部分，
 * 不能因为读侧灰度关了就查不到策略 → 那就成了"关掉开关等于放行"）。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataFieldPolicyQueryMapper extends KuboInfraMapper<DataFieldPolicyEntity> {
    /**
     * 根据资源键、字段名、角色编码查询启用的脱敏策略（前端动态取脱敏规则用）
     *
     * @param resourceKey 资源标识（如 {@code asset.material}）
     * @param fieldName   字段名
     * @param roleCode    角色编码
     * @return 策略列表（无匹配返回空列表）
     */
    List<DataFieldPolicyEntity> findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue(
            @Param("resourceKey") String resourceKey,
            @Param("fieldName") String fieldName,
            @Param("roleCode") String roleCode);
}
