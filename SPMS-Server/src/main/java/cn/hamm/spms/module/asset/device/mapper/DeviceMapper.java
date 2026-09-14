package cn.hamm.spms.module.asset.device.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.asset.device.DeviceEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>设备读侧 Mapper</h1>
 *
 * <p>设备主表（{@code device}）的列表 / 详情 / 总数 / 统计 / 导出走显式 SQL；参数列表是
 * {@code @ManyToMany}（无 {@code device_id} 外键），故 resultMap 只投影主表列，参数由
 * {@code DeviceService#getMapperDetail} 沿 JPA 关系补齐。权限条件由 MyBatis-Plus 运行时拦截器
 * 按 statement ID 注入。</p>
 *
 * <p>采集端的三个 {@code @Permission(login = false)} 出口（实时采集 / 采集配置 / 历史）不在本 Mapper
 * 覆盖范围内，它们仍然走原 Service 方法，不经过权限拦截链。</p>
 *
 * @author zfy
 */
@DataResource(key = "asset.device", alias = "device")
public interface DeviceMapper extends KuboBaseMapper<DeviceEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "asset.device";
    }

    /**
     * @param filter 设备筛选（名称 / UUID）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<DeviceEntity> selectPageByPermission(
            @Param("filter") DeviceEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    DeviceEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 设备筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") DeviceEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") DeviceEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     *
     * @param filter 设备筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<DeviceEntity> selectForExport(@Param("filter") DeviceEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
