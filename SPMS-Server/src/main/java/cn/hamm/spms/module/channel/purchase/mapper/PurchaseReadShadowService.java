package cn.hamm.spms.module.channel.purchase.mapper;

import cn.hamm.spms.module.channel.purchase.PurchaseEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <h1>采购单 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class PurchaseReadShadowService {
    public static final String RESOURCE_KEY = "channel.purchase";
    private final MapperShadowReadService mapperShadowReadService;

    public PurchaseReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * @param filter 与真实列表相同的主表筛选
     */
    public void submit(PurchaseEntity filter) {
        PurchaseEntity normalized = filter == null ? new PurchaseEntity() : filter;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("billCode", normalized.getBillCode());
        parameters.put("reason", normalized.getReason());
        parameters.put("status", normalized.getStatus());
        mapperShadowReadService.submit(RESOURCE_KEY, "purchase", PurchaseEntity.class,
                businessFilter(normalized), parameters);
    }

    private Specification<PurchaseEntity> businessFilter(PurchaseEntity filter) {
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
