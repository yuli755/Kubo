package cn.hamm.spms.module.channel.saleprice;

import cn.hamm.spms.core.CollectionUtil;
import cn.hamm.spms.framework.curd.helper.ExportHelper;
import cn.hamm.spms.framework.curd.model.query.Page;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageResponse;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.asset.AssetServices;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.channel.saleprice.mapper.SalePriceMapper;
import cn.hamm.spms.module.channel.saleprice.mapper.SalePriceReadShadowService;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;
import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_EXIST;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class SalePriceService extends BaseService<SalePriceEntity, SalePriceRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private SalePriceMapper salePriceMapper;

    @Autowired
    private SalePriceReadShadowService salePriceReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用销售价 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return salePriceMapper != null;
    }

    /**
     * 销售价分页读出口；写路径仍走 AirPower JPA。
     */
    public @NotNull QueryPageResponse<SalePriceEntity> getMapperPage(QueryPageRequest<SalePriceEntity> request) {
        QueryPageRequest<SalePriceEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        SalePriceEntity filter = normalized.getFilter() == null ? new SalePriceEntity() : normalized.getFilter();
        Page requestedPage = normalized.getPage() == null ? new Page() : normalized.getPage();
        int pageNum = requestedPage.getPageNum() == null ? 1 : requestedPage.getPageNum();
        int pageSize = requestedPage.getPageSize() == null
                ? DEFAULT_READ_PAGE_SIZE : requestedPage.getPageSize();
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_READ_PAGE_SIZE);
        long offsetValue = ((long) pageNum - 1L) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        int offset = (int) offsetValue;
        salePriceReadShadowService.submit(filter);
        List<SalePriceEntity> list = salePriceMapper.selectPageByPermission(filter, offset, pageSize)
                .stream().map(this::hydrateRelations).toList();
        long total = salePriceMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<SalePriceEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 销售价详情读出口；无权行与不存在行都由 Mapper 返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public SalePriceEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        salePriceReadShadowService.submit();
        SalePriceEntity entity = salePriceMapper.selectDetailByPermission(id);
        return entity == null ? null : hydrateRelations(entity);
    }

    /**
     * 销售价总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(SalePriceEntity filter) {
        SalePriceEntity normalized = filter == null ? new SalePriceEntity() : filter;
        if (isMapperReadEnabled()) {
            salePriceReadShadowService.submit(normalized);
            return salePriceMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 销售价统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(SalePriceEntity filter) {
        SalePriceEntity normalized = filter == null ? new SalePriceEntity() : filter;
        if (isMapperReadEnabled()) {
            salePriceReadShadowService.submit(normalized);
            return salePriceMapper.statByPermission(normalized);
        }
        List<SalePriceEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 销售价真实导出出口（Mapper）。
     *
     * <p>行集经 {@link SalePriceMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在<b>请求线程</b>
     * 完成后交给 {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。{@code price} 是
     * {@code @SensitiveField}，遮蔽由 {@link ExportColumnMasker#maskForExport} 按 QUERY 口径处理。</p>
     *
     * @param request 导出请求
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<SalePriceEntity> request) {
        SalePriceEntity filter = request == null || request.getFilter() == null
                ? new SalePriceEntity() : request.getFilter();
        salePriceReadShadowService.submit(filter);
        List<SalePriceEntity> rows = salePriceMapper.selectForExport(filter).stream()
                .map(this::hydrateRelations).toList();
        List<SalePriceEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), SalePriceEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    /**
     * 关系只在主表通过 Mapper 权限过滤后按独立资源服务补齐，禁止把物料/客户 JOIN 到权限 SQL。
     */
    private SalePriceEntity hydrateRelations(SalePriceEntity salePrice) {
        if (salePrice.getMaterial() != null && salePrice.getMaterial().getId() != null) {
            salePrice.setMaterial(AssetServices.getMaterialService().get(salePrice.getMaterial().getId()));
        }
        if (salePrice.getCustomer() != null && salePrice.getCustomer().getId() != null) {
            salePrice.setCustomer(ChannelServices.getCustomerService().get(salePrice.getCustomer().getId()));
        }
        return salePrice;
    }

    @Override
    protected void addSearchPredicate(@NotNull QueryWrapper<SalePriceEntity> wrapper, @NotNull SalePriceEntity search) {
        // 数据权限：渲染器产出的 SQL 片段直接挂到 wrapper 上（J2 起不再编 JPA Criteria）。
        // 引擎把这个钩子接在通用条件之后，所以片段与用户条件天然是 AND 关系。
        dataPermissionSpecification.applyQuery(wrapper, getEntityClass(), "channel.salePrice", DataPermissionAction.QUERY);
    }

    @Override
    protected @NotNull SalePriceEntity beforeAdd(@NotNull SalePriceEntity salePrice) {
        SalePriceEntity exist = repository.getByCustomerAndMaterial(
                salePrice.getCustomer(),
                salePrice.getMaterial()
        );
        if (Objects.nonNull(exist)) {
            FORBIDDEN_EXIST.show(String.format(
                    "%s 在客户 %s 的销售价已经存在!",
                    exist.getMaterial().getName(),
                    exist.getCustomer().getName()
            ));
        }
        return salePrice;
    }

    @Override
    protected @NotNull SalePriceEntity beforeUpdate(@NotNull SalePriceEntity salePrice) {
        return salePrice.setMaterial(null).setCustomer(null);
    }

    protected SalePriceEntity getByMaterialAndCustomer(MaterialEntity material, CustomerEntity customer) {
        SalePriceEntity exist = repository.getByCustomerAndMaterial(customer, material);
        DATA_NOT_FOUND.whenNull(exist, "没有查询到该物料在此客户下提供的销售价格，请参考物料标准销售价填写。");
        return exist;
    }
}
