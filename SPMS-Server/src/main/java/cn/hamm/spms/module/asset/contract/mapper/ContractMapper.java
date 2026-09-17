package cn.hamm.spms.module.asset.contract.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.asset.contract.ContractEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.framework.curd.datapermission.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>合同读侧 Mapper</h1>
 *
 * <p>合同主表（{@code contract}）的列表 / 详情 / 总数 / 统计 / 导出走显式 SQL；附件与参与方是
 * {@code @ManyToMany}（无外键），故 resultMap 只投影主表列，关系由 {@code ContractService#getMapperDetail}
 * 沿 JPA 补齐。权限条件由 MyBatis-Plus 运行时拦截器按 statement ID 注入。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "asset.contract", alias = "contract")
public interface ContractMapper extends KuboBaseMapper<ContractEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "asset.contract";
    }

    /**
     * @param filter 合同筛选（编码 / 名称 / 内容）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<ContractEntity> selectPageByPermission(
            @Param("filter") ContractEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    ContractEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 合同筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") ContractEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") ContractEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     *
     * @param filter 合同筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<ContractEntity> selectForExport(@Param("filter") ContractEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
