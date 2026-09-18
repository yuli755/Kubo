package cn.hamm.spms.module.channel.saleprice.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.channel.saleprice.SalePriceEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>销售价格读侧 Mapper</h1>
 *
 * <p>销售价无业务筛选（列表按租户全量 + 行级过滤），关联主表（物料/客户）只投影 ID，
 * 由 Service 通过受控详情服务补齐；写路径走写侧仓储（`ICurdRepository` + mapper XML）。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "channel.salePrice", alias = "sp")
public interface SalePriceMapper extends KuboBaseMapper<SalePriceEntity> {

    @Override
    /**
     * @param filter 销售价筛选（无业务字段，保留占位）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<SalePriceEntity> selectPageByPermission(
            @Param("filter") SalePriceEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    SalePriceEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 销售价筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") SalePriceEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") SalePriceEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，固定 id 倒序。
     *
     * @param filter 销售价筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<SalePriceEntity> selectForExport(@Param("filter") SalePriceEntity filter);

}
