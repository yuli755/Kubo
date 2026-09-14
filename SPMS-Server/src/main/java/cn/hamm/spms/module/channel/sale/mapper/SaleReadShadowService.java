package cn.hamm.spms.module.channel.sale.mapper;

import cn.hamm.spms.module.channel.sale.SaleEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <h1>销售单 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class SaleReadShadowService {
    public static final String RESOURCE_KEY = "channel.sale";
    private final MapperShadowReadService mapperShadowReadService;

    public SaleReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * @param filter 与真实列表相同的主表筛选
     */
    public void submit(SaleEntity filter) {
        SaleEntity normalized = filter == null ? new SaleEntity() : filter;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("billCode", normalized.getBillCode());
        parameters.put("reason", normalized.getReason());
        parameters.put("status", normalized.getStatus());
        mapperShadowReadService.submit(RESOURCE_KEY, "sale", SaleEntity.class,
                businessFilter(normalized), parameters);
    }

    private Specification<SaleEntity> businessFilter(SaleEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getBillCode() != null && !filter.getBillCode().isBlank()) {
                predicates.add(builder.like(root.get("billCode"), "%" + filter.getBillCode() + "%"));
            }
            if (filter.getReason() != null && !filter.getReason().isBlank()) {
                predicates.add(builder.like(root.get("reason"), "%" + filter.getReason() + "%"));
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
