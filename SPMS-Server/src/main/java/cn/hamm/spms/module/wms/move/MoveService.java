package cn.hamm.spms.module.wms.move;

import cn.hamm.spms.core.CollectionUtil;
import cn.hamm.spms.core.interfaces.IDictionary;
import cn.hamm.spms.framework.curd.helper.ExportHelper;
import cn.hamm.spms.framework.curd.model.query.Page;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageResponse;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.input.detail.InputDetailEntity;
import cn.hamm.spms.module.wms.input.enums.InputStatus;
import cn.hamm.spms.module.wms.input.enums.InputType;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.inventory.enums.InventoryType;
import cn.hamm.spms.module.wms.move.detail.MoveDetailEntity;
import cn.hamm.spms.module.wms.move.detail.MoveDetailRepository;
import cn.hamm.spms.module.wms.move.detail.MoveDetailService;
import cn.hamm.spms.module.wms.move.enums.MoveStatus;
import cn.hamm.spms.module.wms.move.mapper.MoveMapper;
import cn.hamm.spms.module.wms.move.mapper.MoveReadShadowService;
import cn.hamm.spms.module.wms.output.OutputEntity;
import cn.hamm.spms.module.wms.output.detail.OutputDetailEntity;
import cn.hamm.spms.module.wms.output.enums.OutputStatus;
import cn.hamm.spms.module.wms.output.enums.OutputType;
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

import static cn.hamm.spms.module.system.config.enums.ConfigFlag.MOVE_BILL_AUTO_AUDIT;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class MoveService extends AbstractBaseBillService<MoveEntity, MoveRepository, MoveDetailEntity, MoveDetailService, MoveDetailRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    @Autowired(required = false)
    private MoveMapper moveMapper;

    @Autowired
    private MoveReadShadowService moveReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    public boolean isMapperReadEnabled() {
        return moveMapper != null;
    }

    /**
     * 移库单分页读出口；Mapper 关闭时回退 JPA。
     */
    public @NotNull QueryPageResponse<MoveEntity> getMapperPage(QueryPageRequest<MoveEntity> request) {
        QueryPageRequest<MoveEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        MoveEntity filter = normalized.getFilter() == null ? new MoveEntity() : normalized.getFilter();
        Page requestedPage = normalized.getPage() == null ? new Page() : normalized.getPage();
        int pageNum = requestedPage.getPageNum() == null ? 1 : requestedPage.getPageNum();
        int pageSize = requestedPage.getPageSize() == null
                ? DEFAULT_READ_PAGE_SIZE : requestedPage.getPageSize();
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_READ_PAGE_SIZE);
        long offset = ((long) pageNum - 1L) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        moveReadShadowService.submit(filter);
        List<MoveEntity> rows = moveMapper.selectPageByPermission(filter.getBillCode(), filter.getStatus(),
                (int) offset, pageSize);
        long total = moveMapper.countByPermission(filter.getBillCode(), filter.getStatus());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        QueryPageResponse<MoveEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(normalized.getSort() == null ? new Sort() : normalized.getSort());
        return response;
    }

    /**
     * 移库单详情读出口；权限通过后再加载明细和仓库详情。
     */
    public MoveEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        moveReadShadowService.submit(new MoveEntity());
        MoveEntity bill = moveMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        if (bill.getStorage() != null && bill.getStorage().getId() != null) {
            bill.setStorage(cn.hamm.spms.module.factory.FactoryServices.getStorageService()
                    .get(bill.getStorage().getId()));
        }
        return bill;
    }

    /**
     * 移库单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/状态）
     * @return 权限范围内总数
     */
    public long getMapperCount(MoveEntity filter) {
        MoveEntity normalized = filter == null ? new MoveEntity() : filter;
        if (isMapperReadEnabled()) {
            moveReadShadowService.submit(normalized);
            return moveMapper.countByPermission(normalized.getBillCode(), normalized.getStatus());
        }
        return filter(normalized).size();
    }

    /**
     * 移库单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/状态）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(MoveEntity filter) {
        MoveEntity normalized = filter == null ? new MoveEntity() : filter;
        if (isMapperReadEnabled()) {
            moveReadShadowService.submit(normalized);
            return moveMapper.statByPermission(normalized.getBillCode(), normalized.getStatus());
        }
        List<MoveEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 移库单真实导出出口（Mapper）。取数走权限拦截链，遮蔽在请求线程，落盘走框架异步契约。
     *
     * @param request 导出请求（含单号/状态筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<MoveEntity> request) {
        MoveEntity filter = request == null || request.getFilter() == null ? new MoveEntity() : request.getFilter();
        moveReadShadowService.submit(filter);
        List<MoveEntity> rows = moveMapper.selectForExport(filter.getBillCode(), filter.getStatus());
        List<MoveEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), MoveEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<MoveEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull MoveEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "wms.move", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return MoveStatus.MOVING;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return MoveStatus.AUDITING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return MoveStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return MoveStatus.DONE;
    }

    @Override
    protected void afterDetailFinishAdded(long detailId, @NotNull MoveDetailEntity moveDetail) {
        // 阶段 E-H1：本次移动数量 = 本次提交的增量（与 detailService.addFinishQuantity
        // 累加的完成量一致），不能取库中明细总量——部分录入时明细未完成、addFinish
        // 可重复调用，按总量搬运会把同一批库存反复搬（与阶段 C-1 补录事故同类）。
        double moveDetailQuantity = moveDetail.getQuantity();
        // 来源库存、单据归属以库中明细为准（提交载荷可能只含 id/quantity）
        MoveDetailEntity existDetail = detailService.get(detailId);

        // 查询移库单
        MoveEntity bill = get(existDetail.getBillId());

        // 目标存储文字
        StorageEntity storage = bill.getStorage();

        InventoryService inventoryService = WmsServices.getInventoryService();

        // 来源库存信息
        InventoryEntity from = existDetail.getInventory();

        // 物料信息
        MaterialEntity material = from.getMaterial();

        transactionHelper.run(() -> {
            // 阶段 F-3：锁序约定——来源/目标两行一律按「行 ID 升序」取锁：目标行 ID 较小时
            // 先对其做 FOR UPDATE 预占，再锁来源行。交叉移库（A→B 与 B→A 并发）下两个事务
            // 各自先锁自己的来源行，会形成互等死锁窗口；全局统一按 ID 升序取锁即消除互等。
            // （数量变更本身已改为数据库端原子算术 UPDATE，见 InventoryService，不再依赖
            // 锁内读-改-写，锁只负责串行化两行的处理顺序。）
            InventoryEntity orderedTarget = inventoryService.getByMaterialIdAndStorageId(material.getId(), storage.getId());
            if (Objects.nonNull(orderedTarget) && orderedTarget.getId() < from.getId()) {
                inventoryService.getForUpdate(orderedTarget.getId());
            }

            // 扣除来源库存
            inventoryService.reduceInventoryQuantity(from.getId(), moveDetailQuantity);

            // 查询目标库信息
            InventoryEntity to = inventoryService.getByMaterialIdAndStorageId(material.getId(), storage.getId());
            if (Objects.nonNull(to)) {
                // 更新目标库存
                inventoryService.addInventoryQuantity(to.getId(), moveDetailQuantity);
                return;
            }
            // 创建目标库存
            to = new InventoryEntity()
                    .setQuantity(moveDetailQuantity)
                    .setMaterial(material)
                    .setStorage(storage)
                    .setType(InventoryType.STORAGE.getKey());
            inventoryService.add(to);
        });
    }

    @Override
    protected void afterAllBillDetailFinished(long billId) {
        MoveEntity moveBill = get(billId);
        List<MoveDetailEntity> details = detailService.getAllByBillId(billId);
        List<OutputDetailEntity> outputDetails = new ArrayList<>();
        List<InputDetailEntity> inputDetails = new ArrayList<>();
        details.forEach(detail -> {
            // 库存信息
            InventoryEntity inventory = detail.getInventory();
            inputDetails.add(new InputDetailEntity()
                    .setStorage(moveBill.getStorage())
                    .setMaterial(inventory.getMaterial())
                    .setQuantity(detail.getQuantity())
                    .setFinishQuantity(detail.getFinishQuantity())
            );
            outputDetails.add(new OutputDetailEntity()
                    .setInventory(inventory)
                    .setMaterial(inventory.getMaterial())
                    .setQuantity(detail.getQuantity())
                    .setFinishQuantity(detail.getFinishQuantity())
            );
        });
        // 添加入库单
        InputEntity inputBill = new InputEntity()
                .setType(InputType.MOVE.getKey())
                .setMove(moveBill)
                .setStatus(InputStatus.DONE.getKey());
        inputBill.setDetails(inputDetails);
        WmsServices.getInputService().add(inputBill);

        // 添加出库单
        OutputEntity outputBill = new OutputEntity()
                .setType(OutputType.MOVE.getKey())
                .setMove(moveBill)
                .setStatus(OutputStatus.DONE.getKey());
        outputBill.setDetails(outputDetails);
        WmsServices.getOutputService().add(outputBill);
    }

    @Override
    protected ConfigFlag getAutoAuditConfigFlag() {
        return MOVE_BILL_AUTO_AUDIT;
    }
}
