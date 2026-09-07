package cn.hamm.spms.module.wms.move.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
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

    Long countByPermissionFragment(Map<String, Object> parameters);
}
