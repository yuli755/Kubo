package cn.hamm.spms.module.channel.customer.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>客户读侧 Mapper</h1>
 *
 * <p>客户无跨资源关联主表，故 resultMap 只含主表列；写路径走写侧仓储（`ICurdRepository` + mapper XML）。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "channel.customer", alias = "c")
public interface CustomerMapper extends KuboBaseMapper<CustomerEntity> {

    @Override
    /**
     * @param filter 客户筛选（名称/编码/电话）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<CustomerEntity> selectPageByPermission(
            @Param("filter") CustomerEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    CustomerEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 客户筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") CustomerEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") CustomerEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，固定 id 倒序。
     *
     * @param filter 客户筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<CustomerEntity> selectForExport(@Param("filter") CustomerEntity filter);

}
