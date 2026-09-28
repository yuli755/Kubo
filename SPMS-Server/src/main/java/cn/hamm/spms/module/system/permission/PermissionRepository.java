package cn.hamm.spms.module.system.permission;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * <h1>数据库连接信息</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface PermissionRepository extends BaseRepository<PermissionEntity> {
    /**
     * 根据权限标识获取一个权限
     *
     * @param identity 权限标识
     * @return 权限实体
     */
    PermissionEntity getByIdentity(String identity);

    /**
     * 批量解除一批权限与角色的关联。
     *
     * <p>{@code RoleEntity.permissionList} 是 {@code @ManyToMany}，Hibernate 生成的连接表是
     * {@code role_permission_list(role_entity_id, permission_list_id)}，并对
     * {@code permission_list_id} 建了外键。因此清理过期权限时**必须先删连接行再删权限行**，
     * 否则整批删除会以外键约束冲突失败。这里用原生 SQL 而不是加载角色实体逐个
     * {@code getPermissionList().remove(...)}：后者是 LAZY 集合，在启动期遍历全部角色
     * 会触发 N+1 且需要事务边界，代价与风险都更高。</p>
     *
     * <p>调用方需保证 {@code ids} 非空：空集合会让 {@code in ()} 变成非法 SQL。</p>
     *
     * @param ids 待解除关联的权限主键集合
     * @return 实际删除的连接行数
     */
    @Modifying
    @Transactional
    @Query(value = "delete from role_permission_list where permission_list_id in (:ids)", nativeQuery = true)
    int detachRolesByPermissionIds(@Param("ids") Collection<Long> ids);
}
