package cn.hamm.spms.module.wms.output.mapper;

import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import cn.hamm.spms.module.wms.output.OutputEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <h1>出库单 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class OutputReadShadowService {
    public static final String RESOURCE_KEY = "wms.output";
    private final MapperShadowReadService mapperShadowReadService;

    public OutputReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    public void submit(OutputEntity filter) {
        OutputEntity normalized = filter == null ? new OutputEntity() : filter;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("billCode", normalized.getBillCode());
        parameters.put("status", normalized.getStatus());
        parameters.put("type", normalized.getType());
        mapperShadowReadService.submit(RESOURCE_KEY, "output", OutputEntity.class,
                businessFilter(normalized), parameters);
    }

    private Specification<OutputEntity> businessFilter(OutputEntity filter) {
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
