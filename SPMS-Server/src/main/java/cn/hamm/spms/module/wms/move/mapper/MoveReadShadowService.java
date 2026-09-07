package cn.hamm.spms.module.wms.move.mapper;

import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import cn.hamm.spms.module.wms.move.MoveEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <h1>移库单 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class MoveReadShadowService {
    public static final String RESOURCE_KEY = "wms.move";
    private final MapperShadowReadService mapperShadowReadService;

    public MoveReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    public void submit(MoveEntity filter) {
        MoveEntity normalized = filter == null ? new MoveEntity() : filter;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("billCode", normalized.getBillCode());
        parameters.put("status", normalized.getStatus());
        mapperShadowReadService.submit(RESOURCE_KEY, "move", MoveEntity.class,
                businessFilter(normalized), parameters);
    }

    private Specification<MoveEntity> businessFilter(MoveEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getBillCode() != null && !filter.getBillCode().isBlank()) {
                predicates.add(builder.like(root.get("billCode"), "%" + filter.getBillCode() + "%"));
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
