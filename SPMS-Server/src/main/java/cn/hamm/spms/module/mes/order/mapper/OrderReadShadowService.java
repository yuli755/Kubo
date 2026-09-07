package cn.hamm.spms.module.mes.order.mapper;

import cn.hamm.spms.module.mes.order.OrderEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <h1>生产订单 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class OrderReadShadowService {
    public static final String RESOURCE_KEY = "mes.order";
    private final MapperShadowReadService mapperShadowReadService;

    public OrderReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    public void submit(OrderEntity filter) {
        OrderEntity normalized = filter == null ? new OrderEntity() : filter;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("billCode", normalized.getBillCode());
        parameters.put("status", normalized.getStatus());
        parameters.put("type", normalized.getType());
        mapperShadowReadService.submit(RESOURCE_KEY, "orders", OrderEntity.class,
                businessFilter(normalized), parameters);
    }

    private Specification<OrderEntity> businessFilter(OrderEntity filter) {
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
