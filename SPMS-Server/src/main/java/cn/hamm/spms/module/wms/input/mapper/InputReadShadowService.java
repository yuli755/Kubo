package cn.hamm.spms.module.wms.input.mapper;

import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import cn.hamm.spms.module.wms.input.InputEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <h1>入库单 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class InputReadShadowService {
    public static final String RESOURCE_KEY = "wms.input";
    private final MapperShadowReadService mapperShadowReadService;

    public InputReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * @param filter 与真实列表相同的主表筛选
     */
    public void submit(InputEntity filter) {
        InputEntity normalized = filter == null ? new InputEntity() : filter;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("billCode", normalized.getBillCode());
        parameters.put("status", normalized.getStatus());
        parameters.put("type", normalized.getType());
        mapperShadowReadService.submit(RESOURCE_KEY, "input", InputEntity.class,
                businessFilter(normalized), parameters);
    }

    private Specification<InputEntity> businessFilter(InputEntity filter) {
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
