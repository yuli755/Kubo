package cn.hamm.spms.module.wms.output;

import cn.hamm.spms.common.export.EntityCsvExport;
import cn.hamm.spms.common.dictionary.EnumDictionaries;
import cn.hamm.spms.framework.dictionary.IDictionary;
import cn.hamm.spms.common.export.ExportHelper;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.query.ReadPage;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.output.detail.OutputDetailEntity;
import cn.hamm.spms.module.wms.output.detail.OutputDetailRepository;
import cn.hamm.spms.module.wms.output.detail.OutputDetailService;
import cn.hamm.spms.module.wms.output.enums.OutputStatus;
import cn.hamm.spms.module.wms.output.enums.OutputType;
import cn.hamm.spms.module.wms.output.mapper.OutputMapper;
import cn.hamm.spms.module.wms.output.mapper.OutputReadShadowService;
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

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;
import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;
import static cn.hamm.spms.common.exception.Errors.PARAM_MISSING;
import static cn.hamm.spms.module.system.config.enums.ConfigFlag.OUTPUT_BILL_AUTO_AUDIT;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class OutputService extends AbstractBaseBillService<OutputEntity, OutputRepository, OutputDetailEntity, OutputDetailService, OutputDetailRepository> {

    @Autowired(required = false)
    private OutputMapper outputMapper;

    @Autowired
    private OutputReadShadowService outputReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    public boolean isMapperReadEnabled() {
        return outputMapper != null;
    }

    /**
     * 出库单分页读出口；Mapper 关闭时回退原 JPA 分页。
     */
    public @NotNull QueryPageResponse<OutputEntity> getMapperPage(QueryPageRequest<OutputEntity> request) {
        QueryPageRequest<OutputEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        OutputEntity filter = normalized.getFilter() == null ? new OutputEntity() : normalized.getFilter();
        ReadPage paging = mapperReadPage(normalized.getPage());
        outputReadShadowService.submit(filter);
        List<OutputEntity> rows = outputMapper.selectPageByPermission(filter.getBillCode(), filter.getStatus(),
                filter.getType(), paging.offset(), paging.pageSize());
        long total = outputMapper.countByPermission(filter.getBillCode(), filter.getStatus(), filter.getType());
        return mapperPageResponse(paging, rows, total, normalized.getSort());
    }

    /**
     * 出库单详情读出口；主表先过 Mapper 权限，再加载明细和关联对象。
     */
    public OutputEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        outputReadShadowService.submit(new OutputEntity());
        OutputEntity bill = outputMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        if (bill.getSale() != null && bill.getSale().getId() != null) {
            bill.setSale(ChannelServices.getSaleService().get(bill.getSale().getId()));
        }
        if (bill.getMove() != null && bill.getMove().getId() != null) {
            bill.setMove(WmsServices.getMoveService().getMapperDetail(bill.getMove().getId()));
        }
        return bill;
    }

    /**
     * 出库单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内总数
     */
    public long getMapperCount(OutputEntity filter) {
        OutputEntity normalized = filter == null ? new OutputEntity() : filter;
        if (isMapperReadEnabled()) {
            outputReadShadowService.submit(normalized);
            return outputMapper.countByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        return filter(normalized).size();
    }

    /**
     * 出库单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(OutputEntity filter) {
        OutputEntity normalized = filter == null ? new OutputEntity() : filter;
        if (isMapperReadEnabled()) {
            outputReadShadowService.submit(normalized);
            return outputMapper.statByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        List<OutputEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 出库单真实导出出口（Mapper）。与入库单同模板：取数走权限拦截链，遮蔽在请求线程，落盘走框架异步契约。
     *
     * @param request 导出请求（含单号/状态/类型筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<OutputEntity> request) {
        OutputEntity filter = request == null || request.getFilter() == null ? new OutputEntity() : request.getFilter();
        outputReadShadowService.submit(filter);
        List<OutputEntity> rows = outputMapper.selectForExport(filter.getBillCode(), filter.getStatus(), filter.getType());
        List<OutputEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), OutputEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<OutputEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull OutputEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "wms.output", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return OutputStatus.OUTPUTTING;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return OutputStatus.AUDITING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return OutputStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return OutputStatus.DONE;
    }

    @Override
    protected void afterBillFinished(long billId) {
        OutputEntity outputBill = get(billId);
        OutputType outputType = EnumDictionaries.require(OutputType.class, outputBill.getType(), OutputType::getKey, OutputType::getLabel);
        switch (outputType) {
            case SALE -> {
                if (outputBill.getSale() != null && outputBill.getSale().getId() != null) {
                    ChannelServices.getSaleService().setBillFinished(outputBill.getSale().getId());
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void afterDetailFinishAdded(long detailId, @NotNull OutputDetailEntity outputDetail) {
        InventoryService inventoryService = WmsServices.getInventoryService();

        // 出库明细
        OutputDetailEntity existDetail = detailService.get(outputDetail.getId());

        // 物料 ID
        Long materialId = existDetail.getMaterial().getId();

        // 出库单
        OutputEntity bill = get(existDetail.getBillId());

        if (Objects.isNull(outputDetail.getInventory()) || Objects.isNull(outputDetail.getInventory().getId())) {
            // 「请求少传了字段」用 PARAM_MISSING(4001)，不用 FORBIDDEN(403)：
            // 403 会让调用方与监控误判为权限问题。show() 恒抛，故不需要 return。
            PARAM_MISSING.show("请传入出库来源库存信息");
        }

        // 库存信息
        InventoryEntity inventory = inventoryService.get(outputDetail.getInventory().getId());
        FORBIDDEN.whenNull(inventory, "未找到指定的出库来源库存");
        // 物料一致性是「调用方传的两个值互相对不上」的输入校验，不是授权边界 → PARAM_INVALID(4002)。
        // 与上方 L224 同一条规矩：403 会让调用方与监控把参数错误误判成权限问题。
        PARAM_INVALID.whenNotEquals(inventory.getMaterial().getId(), materialId, "物料信息不匹配");

        // 库存细粒度分布式排他锁：防止并发扣减超卖与销售单明细状态竞态
        String lockKey = "lock:wms:inventory:" + inventory.getId();
        Runnable deductAction = () -> {
            transactionHelper.run(() -> {
                Double outputDetailQuantity = outputDetail.getQuantity();
                inventoryService.reduceInventoryQuantity(inventory.getId(), outputDetailQuantity);
                // 获取出库单类型
                OutputType outputType = EnumDictionaries.require(OutputType.class, bill.getType(), OutputType::getKey, OutputType::getLabel);
                switch (outputType) {
                    case SALE -> {
                        if (bill.getSale() != null && bill.getSale().getId() != null) {
                            ChannelServices.getSaleDetailService().updateDetailQuantity(
                                    bill.getSale().getId(),
                                    outputDetailQuantity,
                                    ChannelServices.getSaleService(),
                                    // 行筛选（不是校验）：只反写物料匹配的销售明细，其余行跳过。
                                    // 「这一行不是本次出库的物料」是正常业务分支，不该用抛异常表达；
                                    // 物料一致性的**校验**在上方 L235（PARAM_INVALID）已经做过一次。
                                    detail -> Objects.equals(detail.getMaterial().getId(), materialId));
                        }
                    }
                    default -> {
                    }
                }
            });
        };

        if (redisHelper != null) {
            redisHelper.runWithLock(lockKey, 5, deductAction);
        } else {
            deductAction.run();
        }
    }

    @Override
    protected ConfigFlag getAutoAuditConfigFlag() {
        return OUTPUT_BILL_AUTO_AUDIT;
    }
}
