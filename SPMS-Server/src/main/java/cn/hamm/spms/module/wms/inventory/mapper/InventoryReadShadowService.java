package cn.hamm.spms.module.wms.inventory.mapper;

import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h1>库存 Mapper 读侧 shadow 参数适配</h1>
 *
 * <p>库存自身只有类型和树节点筛选；权限树、JPA/SQL 的权限渲染和异步对账统一交给
 * {@link MapperShadowReadService}，这里不复制租户/owner 判据。</p>
 *
 * @author Hamm.cn
 */
@Service
public class InventoryReadShadowService {
    public static final String RESOURCE_KEY = "wms.inventory";
    private final MapperShadowReadService mapperShadowReadService;

    public InventoryReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * @param type         库存类型
     * @param storageIds   仓库及子仓库 ID
     * @param structureIds 生产单元及子单元 ID
     */
    public void submit(Integer type, List<Long> storageIds, List<Long> structureIds) {
        List<Long> safeStorageIds = storageIds == null ? List.of() : List.copyOf(storageIds);
        List<Long> safeStructureIds = structureIds == null ? List.of() : List.copyOf(structureIds);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", type);
        parameters.put("storageIds", safeStorageIds);
        parameters.put("structureIds", safeStructureIds);
        mapperShadowReadService.submit(RESOURCE_KEY, "inventory", InventoryEntity.class,
                businessFilter(type, safeStorageIds, safeStructureIds), parameters);
    }

    private Specification<InventoryEntity> businessFilter(Integer type,
                                                           List<Long> storageIds,
                                                           List<Long> structureIds) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (!storageIds.isEmpty()) {
                predicates.add(root.get("storage").get("id").in(storageIds));
            }
            if (!structureIds.isEmpty()) {
                predicates.add(root.get("structure").get("id").in(structureIds));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
