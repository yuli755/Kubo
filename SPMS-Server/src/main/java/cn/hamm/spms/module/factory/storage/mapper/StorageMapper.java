package cn.hamm.spms.module.factory.storage.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>仓库读侧 Mapper</h1>
 *
 * <p>仓库主表（{@code storage}）的列表 / 详情 / 总数 / 统计 / 导出走显式 SQL。仓库是树形实体
 * （{@code ITree}），其树导航走 {@code getList}（JPA 路径，经 {@code beforeGetList}/{@code afterGetList}
 * 递归补 {@code children}），不在本 Mapper 覆盖范围内；本 Mapper 只承载扁平的 page/detail/count/stat/export。
 * 权限条件由 MyBatis-Plus 运行时拦截器按 statement ID 注入。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "factory.storage", alias = "storage")
public interface StorageMapper extends KuboBaseMapper<StorageEntity> {

    @Override
    /**
     * @param filter 仓库筛选（编码 / 名称）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<StorageEntity> selectPageByPermission(
            @Param("filter") StorageEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    StorageEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 仓库筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") StorageEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") StorageEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     *
     * @param filter 仓库筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<StorageEntity> selectForExport(@Param("filter") StorageEntity filter);

}
