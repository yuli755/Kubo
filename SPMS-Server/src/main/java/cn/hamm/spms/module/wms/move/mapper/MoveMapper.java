package cn.hamm.spms.module.wms.move.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.framework.curd.datapermission.RowParityCounter;
import cn.hamm.spms.module.wms.move.MoveEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>移库单读侧 Mapper</h1>
 *
 * @author Hamm.cn
 */
@DataResource(key = "wms.move", alias = "move")
public interface MoveMapper extends KuboBaseMapper<MoveEntity>, RowParityCounter {
    @Override
    default String parityResourceKey() {
        return "wms.move";
    }

    List<MoveEntity> selectPageByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    MoveEntity selectDetailByPermission(@Param("id") Long id);

    Long countByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status
    );

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status
    );

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     */
    List<MoveEntity> selectForExport(
            @Param("billCode") String billCode,
            @Param("status") Integer status
    );

    Long countByPermissionFragment(Map<String, Object> parameters);
}
