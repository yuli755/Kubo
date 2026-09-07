package cn.hamm.spms.module.mes.picking.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.mes.picking.PickingEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>领料单读侧 Mapper</h1>
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.picking", alias = "picking")
public interface PickingMapper extends KuboBaseMapper<PickingEntity>, RowParityCounter {
    @Override
    default String parityResourceKey() {
        return "mes.picking";
    }

    List<PickingEntity> selectPageByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    PickingEntity selectDetailByPermission(@Param("id") Long id);

    Long countByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type
    );

    Long countByPermissionFragment(Map<String, Object> parameters);
}
