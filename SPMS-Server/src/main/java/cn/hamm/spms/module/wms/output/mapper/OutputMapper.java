package cn.hamm.spms.module.wms.output.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
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

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type
    );

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     */
    List<OutputEntity> selectForExport(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type
    );

    Long countByPermissionFragment(Map<String, Object> parameters);
}
