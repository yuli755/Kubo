package cn.hamm.spms.module.wms.inventory;

import cn.hamm.spms.framework.util.Trees;
import cn.hamm.spms.common.export.EntityCsvExport;
import cn.hamm.spms.common.dictionary.EnumDictionaries;
import cn.hamm.spms.common.export.ExportHelper;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.query.ReadPage;
import cn.hamm.spms.common.exception.Errors;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.wms.inventory.mapper.InventoryMapper;
import cn.hamm.spms.module.wms.inventory.mapper.InventoryReadShadowService;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.storage.StorageService;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.factory.structure.StructureService;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.inventory.enums.InventoryType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class InventoryService extends BaseService<InventoryEntity, InventoryRepository> {

    /**
     * mapper-read 关闭时 Bean 不存在，保持 JPA 回退可启动。
     */
    @Autowired(required = false)
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryReadShadowService inventoryReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private StructureService structureService;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用库存 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return inventoryMapper != null;
    }

    /**
     * 库存分页读出口。请求方排序不下沉到 SQL，实际排序固定为 ID 倒序。
     *
     * @param request 分页与库存类型/树节点筛选
     * @return 当前页
     */
    public @NotNull QueryPageResponse<InventoryEntity> getMapperPage(QueryPageRequest<InventoryEntity> request) {
        QueryPageRequest<InventoryEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        InventoryEntity filter = normalized.getFilter() == null ? new InventoryEntity() : normalized.getFilter();
        ReadPage paging = mapperReadPage(normalized.getPage());

        Integer type = filter.getType();
        List<Long> storageIds = storageIds(filter, type);
        List<Long> structureIds = structureIds(filter, type);
        inventoryReadShadowService.submit(type, storageIds, structureIds);
        List<InventoryEntity> rows = inventoryMapper.selectPageByPermission(type, storageIds, structureIds,
                paging.offset(), paging.pageSize()).stream().map(this::hydrateRelations).toList();
        long total = inventoryMapper.countByPermission(type, storageIds, structureIds);
        return mapperPageResponse(paging, rows, total, normalized.getSort());
    }

    /**
     * 库存详情读出口；无权行不回退到 JPA。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public InventoryEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        InventoryEntity row = inventoryMapper.selectDetailByPermission(id);
        return row == null ? null : hydrateRelations(row);
    }

    /**
     * 与分页使用同一筛选和权限语句的总数出口。
     */
    public long getMapperCount(InventoryEntity filter) {
        if (!isMapperReadEnabled()) {
            return filter(filter == null ? new InventoryEntity() : filter).size();
        }
        InventoryEntity normalized = filter == null ? new InventoryEntity() : filter;
        return inventoryMapper.countByPermission(normalized.getType(), storageIds(normalized, normalized.getType()),
                structureIds(normalized, normalized.getType()));
    }

    /**
     * 库存统计读出口；与总数使用同一筛选和权限语句，只返回行数与已发布行数。
     *
     * @param filter 业务筛选（库存类型/树节点）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(InventoryEntity filter) {
        InventoryEntity normalized = filter == null ? new InventoryEntity() : filter;
        if (isMapperReadEnabled()) {
            Integer type = normalized.getType();
            List<Long> storageIds = storageIds(normalized, type);
            List<Long> structureIds = structureIds(normalized, type);
            inventoryReadShadowService.submit(type, storageIds, structureIds);
            return inventoryMapper.statByPermission(type, storageIds, structureIds);
        }
        List<InventoryEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 库存真实导出出口（Mapper）。
     *
     * <p>行集经 {@link InventoryMapper#selectForExport} 走与分页/详情/总数相同的权限拦截链，
     * 列级遮蔽在<b>请求线程</b>完成后再交给 {@link ExportHelper#createExportTask} 落盘，
     * 保留 {@code fileCode → queryExport} 的异步下载契约。库存无 {@code @SensitiveField}，
     * {@link ExportColumnMasker#maskForExport} 不改变行内容，但保留同一份遮蔽实现以防后续加敏感列。</p>
     *
     * @param request 导出请求（含库存类型/树节点筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<InventoryEntity> request) {
        InventoryEntity filter = request == null || request.getFilter() == null
                ? new InventoryEntity() : request.getFilter();
        Integer type = filter.getType();
        List<Long> storageIds = storageIds(filter, type);
        List<Long> structureIds = structureIds(filter, type);
        inventoryReadShadowService.submit(type, storageIds, structureIds);
        List<InventoryEntity> rows = inventoryMapper.selectForExport(type, storageIds, structureIds);
        List<InventoryEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), InventoryEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    private List<Long> storageIds(InventoryEntity filter, Integer type) {
        if (type == null || EnumDictionaries.require(InventoryType.class, type, InventoryType::getKey, InventoryType::getLabel) != InventoryType.STORAGE
                || filter.getStorage() == null || filter.getStorage().getId() == null) {
            return List.of();
        }
        Set<Long> ids = Trees.getChildrenIdList(filter.getStorage().getId(),
                id -> storageService.filter(new StorageEntity().setParentId(id)));
        ids.add(filter.getStorage().getId());
        return List.copyOf(ids);
    }

    private List<Long> structureIds(InventoryEntity filter, Integer type) {
        if (type == null || EnumDictionaries.require(InventoryType.class, type, InventoryType::getKey, InventoryType::getLabel) != InventoryType.STRUCTURE
                || filter.getStructure() == null || filter.getStructure().getId() == null) {
            return List.of();
        }
        Set<Long> ids = Trees.getChildrenIdList(filter.getStructure().getId(),
                id -> structureService.filter(new StructureEntity().setParentId(id)));
        ids.add(filter.getStructure().getId());
        return List.copyOf(ids);
    }

    /**
     * Mapper 只投影关联 ID；关联名称/编码通过各自服务取回，避免库存 SQL 使用未登记 JOIN 表。
     */
    private InventoryEntity hydrateRelations(InventoryEntity inventory) {
        if (inventory.getMaterial() != null && inventory.getMaterial().getId() != null) {
            inventory.setMaterial(materialService.isMapperReadEnabled()
                    ? materialService.getMapperDetail(inventory.getMaterial().getId())
                    : materialService.get(inventory.getMaterial().getId()));
        }
        if (inventory.getStorage() != null && inventory.getStorage().getId() != null) {
            inventory.setStorage(storageService.get(inventory.getStorage().getId()));
        }
        if (inventory.getStructure() != null && inventory.getStructure().getId() != null) {
            inventory.setStructure(structureService.get(inventory.getStructure().getId()));
        }
        return inventory;
    }

    /**
     * 查询指定物料 ID 和仓库 ID 下的库存
     *
     * @param materialId 物料 ID
     * @param storageId  仓库 ID
     * @return 库存
     */
    public InventoryEntity getByMaterialIdAndStorageId(Long materialId, Long storageId) {
        return repository.getByMaterialAndStorage(
                new MaterialEntity().setId(materialId),
                new StorageEntity().setId(storageId)
        );
    }

    /**
     * 查询指定物料 ID 和生产单元 ID 下的库存
     *
     * @param materialId  物料 ID
     * @param structureId 生产单元 ID
     * @return 库存
     */
    @SuppressWarnings("unused")
    public InventoryEntity getByMaterialIdAndStructureId(Long materialId, Long structureId) {
        return repository.getByMaterialAndStructure(
                new MaterialEntity().setId(materialId),
                new StructureEntity().setId(structureId)
        );
    }

    @Override
    protected @NotNull QueryPageRequest<InventoryEntity> beforeGetPage(@NotNull QueryPageRequest<InventoryEntity> queryPageRequest) {
        return super.beforeGetPage(queryPageRequest);
    }

    @Override
    protected InventoryEntity beforeCreatePredicate(@NotNull InventoryEntity inventory) {
        // type 为空表示「不按库存维度过滤」——前端清空筛选、或首次进列表页只带分页/排序时就是这样。
        // EnumDictionaries.require 的第二个形参是原生 int，传入 Integer 的 null 会在调用点拆箱抛
        // NullPointerException，而不是被 require 内部的字典校验拦住（那道校验只在值「非空但不合法」时生效）。
        // 后果实测：/inventory 的 getPage / getList / getCount / getStat 四个读端点全部 500，
        // 「库存查询」这个 WMS 核心页面整条链路不可用。此时没有需要移除的维度条件，直接返回。
        if (inventory.getType() == null) {
            return inventory;
        }
        // 需要移除本身的查询条件
        switch (EnumDictionaries.require(InventoryType.class, inventory.getType(), InventoryType::getKey, InventoryType::getLabel)) {
            case STORAGE -> inventory.setStorage(null);
            case STRUCTURE -> inventory.setStructure(null);
            default -> {
            }
        }
        return inventory;
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<InventoryEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull InventoryEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();

        // 叠加数据权限过滤条件
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "wms.inventory", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);

        if (Objects.isNull(search.getType())) {
            return predicateList;
        }
        switch (EnumDictionaries.require(InventoryType.class, search.getType(), InventoryType::getKey, InventoryType::getLabel)) {
            case STORAGE -> addStoragePredicate(root, search, predicateList);
            case STRUCTURE -> addStructurePredicate(root, search, predicateList);
            default -> {
            }
        }
        return predicateList;
    }

    /**
     * 添加生产单元查询条件
     *
     * @param root          ROOT
     * @param search        查询条件
     * @param predicateList 查询条件列表
     */
    private void addStructurePredicate(@NotNull Root<InventoryEntity> root, @NotNull InventoryEntity search, List<Predicate> predicateList) {
        StructureEntity structure = search.getStructure();
        if (Objects.isNull(structure)) {
            return;
        }
        Set<Long> idList = Trees.getChildrenIdList(structure.getId(), id -> structureService.filter(new StructureEntity().setParentId(id)));
        idList.add(structure.getId());
        Join<InventoryEntity, StructureEntity> join = root.join("structure");
        Predicate inPredicate = join.get(BaseEntity.STRING_ID).in(idList);
        predicateList.add(inPredicate);
        search.setStructure(null);
    }

    /**
     * 添加仓库查询条件
     *
     * @param root          ROOT
     * @param search        查询条件
     * @param predicateList 查询条件列表
     */
    private void addStoragePredicate(@NotNull Root<InventoryEntity> root, @NotNull InventoryEntity search, List<Predicate> predicateList) {
        StorageEntity storage = search.getStorage();
        if (Objects.isNull(storage)) {
            return;
        }
        Set<Long> idList = Trees.getChildrenIdList(storage.getId(), id -> storageService.filter(new StorageEntity().setParentId(id)));
        idList.add(storage.getId());
        Join<InventoryEntity, StorageEntity> join = root.join("storage");
        Predicate inPredicate = join.get(BaseEntity.STRING_ID).in(idList);
        predicateList.add(inPredicate);
        search.setStorage(null);
    }

    /**
     * 添加库存数量
     *
     * <p>阶段 F-3：改为数据库端原子#acquire UPDATE（quantity = quantity + ?）。
     * 原 updateWithLock 读-改-写实测在交叉移库并发下会丢失数量写入
     * （SQL 日志显示只更新了 update_time），算术 UPDATE 无读窗口，任意交错下总量守恒。</p>
     *
     * @param inventoryId 库存ID
     * @param quantity    数量
     */
    public void addInventoryQuantity(long inventoryId, double quantity) {
        Errors.PARAM_INVALID.when(quantity <= 0, "入库数量必须大于 0");
        int rows = repository.increaseQuantityById(inventoryId, quantity, System.currentTimeMillis());
        Errors.FORBIDDEN.when(rows == 0, "库存记录不存在或已被删除");
    }

    /**
     * 减少库存数量
     *
     * <p>阶段 F-3：同 {@link #addInventoryQuantity}——负库存守卫移至 WHERE 条件，
     * 扣减与校验在同一条语句内完成，并发下不存在"读到旧值再扣穿"的窗口。</p>
     *
     * @param inventoryId 库存ID
     * @param quantity    数量
     */
    public void reduceInventoryQuantity(long inventoryId, double quantity) {
        Errors.PARAM_INVALID.when(quantity <= 0, "出库扣减数量必须大于 0");
        int rows = repository.decreaseQuantityById(inventoryId, quantity, System.currentTimeMillis());
        Errors.FORBIDDEN_EDIT.when(rows == 0, "库存数量不足");
    }
}
