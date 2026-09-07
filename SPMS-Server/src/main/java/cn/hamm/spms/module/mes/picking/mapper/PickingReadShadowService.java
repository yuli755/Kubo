package cn.hamm.spms.module.mes.picking.mapper;

import cn.hamm.spms.module.mes.picking.PickingEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <h1>领料单 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class PickingReadShadowService {
    public static final String RESOURCE_KEY = "mes.picking";
    private final MapperShadowReadService mapperShadowReadService;

    public PickingReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    public void submit(PickingEntity filter) {
        PickingEntity normalized = filter == null ? new PickingEntity() : filter;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("billCode", normalized.getBillCode());
        parameters.put("status", normalized.getStatus());
        parameters.put("type", normalized.getType());
        mapperShadowReadService.submit(RESOURCE_KEY, "picking", PickingEntity.class,
                businessFilter(normalized), parameters);
    }

    private Specification<PickingEntity> businessFilter(PickingEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getBillCode() != null && !filter.getBillCode().isBlank()) {
                predicates.add(builder.like(root.get("billCode"), "%" + filter.getBillCode() + "%"));
            }
            if (filter.getStatus() != null) {
                predicates.add(builder.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getType() != null) {
                predicates.add(builder.equal(root.get("type"), filter.getType()));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
