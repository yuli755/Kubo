package cn.hamm.spms.module.mes.routing.mapper;

import cn.hamm.spms.module.mes.routing.RoutingEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>生产工艺 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class RoutingReadShadowService {
    public static final String RESOURCE_KEY = "mes.routing";
    private final MapperShadowReadService mapperShadowReadService;

    public RoutingReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的工艺筛选（编码/名称/状态）
     */
    public void submit(RoutingEntity filter) {
        RoutingEntity normalized = filter == null ? new RoutingEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "routing", RoutingEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<RoutingEntity> businessFilter(RoutingEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getStatus() != null) {
                predicates.add(builder.equal(root.get("status"), filter.getStatus()));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
