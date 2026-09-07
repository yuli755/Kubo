package cn.hamm.spms.module.wms.inventory;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.NumberUtil;
import cn.hamm.airpower.core.TreeUtil;
import cn.hamm.airpower.curd.base.CurdEntity;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.airpower.exception.Errors;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
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
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

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

        Integer type = filter.getType();
        List<Long> storageIds = storageIds(filter, type);
        List<Long> structureIds = structureIds(filter, type);
        inventoryReadShadowService.submit(type, storageIds, structureIds);
        List<InventoryEntity> rows = inventoryMapper.selectPageByPermission(type, storageIds, structureIds,
                (int) offsetValue, pageSize).stream().map(this::hydrateRelations).toList();
        long total = inventoryMapper.countByPermission(type, storageIds, structureIds);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<InventoryEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
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
            InputStream stream = CollectionUtil.toCsvInputStream(masked, InventoryEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    private List<Long> storageIds(InventoryEntity filter, Integer type) {
        if (type == null || DictionaryUtil.getDictionary(InventoryType.class, type) != InventoryType.STORAGE
                || filter.getStorage() == null || filter.getStorage().getId() == null) {
            return List.of();
        }
        Set<Long> ids = TreeUtil.getChildrenIdList(filter.getStorage().getId(),
                id -> storageService.filter(new StorageEntity().setParentId(id)));
        ids.add(filter.getStorage().getId());
        return List.copyOf(ids);
    }

    private List<Long> structureIds(InventoryEntity filter, Integer type) {
        if (type == null || DictionaryUtil.getDictionary(InventoryType.class, type) != InventoryType.STRUCTURE
                || filter.getStructure() == null || filter.getStructure().getId() == null) {
            return List.of();
        }
        Set<Long> ids = TreeUtil.getChildrenIdList(filter.getStructure().getId(),
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
        // 需要移除本身的查询条件
        switch (DictionaryUtil.getDictionary(InventoryType.class, inventory.getType())) {
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
        switch (DictionaryUtil.getDictionary(InventoryType.class, search.getType())) {
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
        Set<Long> idList = TreeUtil.getChildrenIdList(structure.getId(), id -> structureService.filter(new StructureEntity().setParentId(id)));
        idList.add(structure.getId());
        Join<InventoryEntity, StructureEntity> join = root.join("structure");
        Predicate inPredicate = join.get(CurdEntity.STRING_ID).in(idList);
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
        Set<Long> idList = TreeUtil.getChildrenIdList(storage.getId(), id -> storageService.filter(new StorageEntity().setParentId(id)));
        idList.add(storage.getId());
        Join<InventoryEntity, StorageEntity> join = root.join("storage");
        Predicate inPredicate = join.get(CurdEntity.STRING_ID).in(idList);
        predicateList.add(inPredicate);
        search.setStorage(null);
    }

    /**
     * 添加库存数量
     *
     * @param inventoryId 库存ID
     * @param quantity    数量
     */
    public void addInventoryQuantity(long inventoryId, double quantity) {
        updateWithLock(inventoryId, exist -> exist.setQuantity(NumberUtil.add(exist.getQuantity(), quantity)));
    }

    /**
     * 减少库存数量
     *
     * @param inventoryId 库存ID
     * @param quantity    数量
     */
    public void reduceInventoryQuantity(long inventoryId, double quantity) {
        updateWithLock(inventoryId, exist -> {
            double subtract = NumberUtil.subtract(exist.getQuantity(), quantity);
            Errors.FORBIDDEN_EDIT.when(subtract < 0, "库存数量不足");
            exist.setQuantity(subtract);
        });
    }
}
