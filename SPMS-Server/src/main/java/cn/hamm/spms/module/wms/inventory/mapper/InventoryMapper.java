package cn.hamm.spms.module.wms.inventory.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>库存读侧 Mapper</h1>
 *
 * <p>库存是 Material 之后的第二个换轨资源。列表、详情和总数都使用显式 SQL；行级权限不在
 * XML 中复制，而由 statement ID 对应的 MyBatis-Plus 拦截器追加。关联对象先只投影关联 ID，
 * Service 再通过各自受控的详情出口补齐，禁止用未登记 JOIN 表绕过 fail-closed。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "wms.inventory", alias = "inventory")
public interface InventoryMapper extends KuboBaseMapper<InventoryEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "wms.inventory";
    }

    /**
     * 按库存类型/树节点筛选分页。
     *
     * @param type         库存类型
     * @param storageIds   仓库及子仓库 ID；无筛选时为空列表
     * @param structureIds 生产单元及子单元 ID；无筛选时为空列表
     * @param offset       分页偏移
     * @param limit        分页大小
     * @return 当前页
     */
    List<InventoryEntity> selectPageByPermission(
            @Param("type") Integer type,
            @Param("storageIds") List<Long> storageIds,
            @Param("structureIds") List<Long> structureIds,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 按行级权限获取详情；无权行返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    InventoryEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * 与分页筛选完全相同的总数。
     *
     * @param type         库存类型
     * @param storageIds   仓库及子仓库 ID
     * @param structureIds 生产单元及子单元 ID
     * @return 行数
     */
    Long countByPermission(
            @Param("type") Integer type,
            @Param("storageIds") List<Long> storageIds,
            @Param("structureIds") List<Long> structureIds
    );

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     *
     * @param type         库存类型
     * @param storageIds   仓库及子仓库 ID
     * @param structureIds 生产单元及子单元 ID
     * @return 权限范围内统计
     */
    ReadStat statByPermission(
            @Param("type") Integer type,
            @Param("storageIds") List<Long> storageIds,
            @Param("structureIds") List<Long> structureIds
    );

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     *
     * @param type         库存类型
     * @param storageIds   仓库及子仓库 ID
     * @param structureIds 生产单元及子单元 ID
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<InventoryEntity> selectForExport(
            @Param("type") Integer type,
            @Param("storageIds") List<Long> storageIds,
            @Param("structureIds") List<Long> structureIds
    );

    /**
     * ORM parity 探针。
     *
     * @param parameters fragment 与本资源业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
