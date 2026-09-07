package cn.hamm.spms.module.wms.input.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import cn.hamm.spms.module.wms.input.InputEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>入库单读侧 Mapper</h1>
 *
 * <p>单据主表的列表、详情和总数使用显式 SQL；明细仍由原明细服务按单据 ID 加载。关联主表
 * 只投影 ID，再由 Service 通过受控详情服务补齐，避免把多个资源 JOIN 到同一条权限 SQL。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "wms.input", alias = "input")
public interface InputMapper extends KuboBaseMapper<InputEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "wms.input";
    }

    /**
     * @param billCode 单号模糊筛选
     * @param status   单据状态
     * @param type     单据类型
     * @param offset   分页偏移
     * @param limit    分页大小
     * @return 当前页
     */
    List<InputEntity> selectPageByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    InputEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param billCode 单号模糊筛选
     * @param status   单据状态
     * @param type     单据类型
     * @return 行数
     */
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
     *
     * @param billCode 单号模糊筛选
     * @param status   单据状态
     * @param type     单据类型
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<InputEntity> selectForExport(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type
    );

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
