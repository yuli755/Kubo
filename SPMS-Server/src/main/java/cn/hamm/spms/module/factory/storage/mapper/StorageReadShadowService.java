package cn.hamm.spms.module.factory.storage.mapper;

import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>仓库 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class StorageReadShadowService {
    public static final String RESOURCE_KEY = "factory.storage";
    private final MapperShadowReadService mapperShadowReadService;

    public StorageReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的仓库筛选（编码 / 名称）
     */
    public void submit(StorageEntity filter) {
        StorageEntity normalized = filter == null ? new StorageEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "storage", StorageEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<StorageEntity> businessFilter(StorageEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
