package cn.hamm.spms.module.wms.output.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import cn.hamm.spms.module.wms.output.OutputEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>出库单读侧 Mapper</h1>
 *
 * <p>主表读 SQL 只投影关联 ID，关联单据由 Service 在各自资源边界受控补齐。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "wms.output", alias = "output")
public interface OutputMapper extends KuboBaseMapper<OutputEntity>, RowParityCounter {
    @Override
    default String parityResourceKey() {
        return "wms.output";
    }

    List<OutputEntity> selectPageByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    OutputEntity selectDetailByPermission(@Param("id") Long id);

    Long countByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type
    );

    Long countByPermissionFragment(Map<String, Object> parameters);
}
