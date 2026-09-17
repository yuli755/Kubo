package cn.hamm.spms.module.channel.purchaseprice;

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
import cn.hamm.spms.module.channel.purchaseprice.mapper.PurchasePriceMapper;
import cn.hamm.spms.module.channel.purchaseprice.mapper.PurchasePriceReadShadowService;
import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;
import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_EXIST;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class PurchasePriceService extends BaseService<PurchasePriceEntity, PurchasePriceRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private PurchasePriceMapper purchasePriceMapper;

    @Autowired
    private PurchasePriceReadShadowService purchasePriceReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用采购价 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return purchasePriceMapper != null;
    }

    /**
     * 采购价分页读出口；写路径仍走 AirPower JPA。
     */
    public @NotNull QueryPageResponse<PurchasePriceEntity> getMapperPage(QueryPageRequest<PurchasePriceEntity> request) {
        QueryPageRequest<PurchasePriceEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        PurchasePriceEntity filter = normalized.getFilter() == null ? new PurchasePriceEntity() : normalized.getFilter();
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
        purchasePriceReadShadowService.submit(filter);
        List<PurchasePriceEntity> list = purchasePriceMapper.selectPageByPermission(filter, offset, pageSize)
                .stream().map(this::hydrateRelations).toList();
        long total = purchasePriceMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<PurchasePriceEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 采购价详情读出口；无权行与不存在行都由 Mapper 返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public PurchasePriceEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        purchasePriceReadShadowService.submit();
        PurchasePriceEntity entity = purchasePriceMapper.selectDetailByPermission(id);
        return entity == null ? null : hydrateRelations(entity);
    }

    /**
     * 采购价总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(PurchasePriceEntity filter) {
        PurchasePriceEntity normalized = filter == null ? new PurchasePriceEntity() : filter;
        if (isMapperReadEnabled()) {
            purchasePriceReadShadowService.submit(normalized);
            return purchasePriceMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 采购价统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(PurchasePriceEntity filter) {
        PurchasePriceEntity normalized = filter == null ? new PurchasePriceEntity() : filter;
        if (isMapperReadEnabled()) {
            purchasePriceReadShadowService.submit(normalized);
            return purchasePriceMapper.statByPermission(normalized);
        }
        List<PurchasePriceEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 采购价真实导出出口（Mapper）。
     *
     * <p>行集经 {@link PurchasePriceMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在<b>请求线程</b>
     * 完成后交给 {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。{@code price} 是
     * {@code @SensitiveField}，遮蔽由 {@link ExportColumnMasker#maskForExport} 按 QUERY 口径处理。</p>
     *
     * @param request 导出请求
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<PurchasePriceEntity> request) {
        PurchasePriceEntity filter = request == null || request.getFilter() == null
                ? new PurchasePriceEntity() : request.getFilter();
        purchasePriceReadShadowService.submit(filter);
        List<PurchasePriceEntity> rows = purchasePriceMapper.selectForExport(filter).stream()
                .map(this::hydrateRelations).toList();
        List<PurchasePriceEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), PurchasePriceEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    /**
     * 关系只在主表通过 Mapper 权限过滤后按独立资源服务补齐，禁止把物料/供应商 JOIN 到权限 SQL。
     */
    private PurchasePriceEntity hydrateRelations(PurchasePriceEntity purchasePrice) {
        if (purchasePrice.getMaterial() != null && purchasePrice.getMaterial().getId() != null) {
            purchasePrice.setMaterial(AssetServices.getMaterialService().get(purchasePrice.getMaterial().getId()));
        }
        if (purchasePrice.getSupplier() != null && purchasePrice.getSupplier().getId() != null) {
            purchasePrice.setSupplier(ChannelServices.getSupplierService().get(purchasePrice.getSupplier().getId()));
        }
        return purchasePrice;
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<PurchasePriceEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull PurchasePriceEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "channel.purchasePrice", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    protected @NotNull PurchasePriceEntity beforeAdd(@NotNull PurchasePriceEntity purchasePrice) {
        PurchasePriceEntity exist = repository.getBySupplierAndMaterial(
                purchasePrice.getSupplier(),
                purchasePrice.getMaterial()
        );
        if (Objects.nonNull(exist)) {
            FORBIDDEN_EXIST.show(String.format(
                    "物料 %s 在供应商 %s 下的采购价已存在!",
                    exist.getMaterial().getName(),
                    exist.getSupplier().getName()
            ));
        }
        return purchasePrice;
    }

    protected PurchasePriceEntity getByMaterialAndSupplier(MaterialEntity material, SupplierEntity supplier) {
        PurchasePriceEntity exist = repository.getBySupplierAndMaterial(supplier, material);
        DATA_NOT_FOUND.whenNull(exist, "没有查询到该物料在此供应商下提供的采购价格，请参考物料标准采购价填写。");
        return exist;
    }
}
