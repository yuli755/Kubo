package cn.hamm.spms.module.channel.customer.mapper;

import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>客户 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class CustomerReadShadowService {
    public static final String RESOURCE_KEY = "channel.customer";
    private final MapperShadowReadService mapperShadowReadService;

    public CustomerReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的客户筛选（名称/编码/电话）
     */
    public void submit(CustomerEntity filter) {
        CustomerEntity normalized = filter == null ? new CustomerEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "customer", CustomerEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<CustomerEntity> businessFilter(CustomerEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            if (filter.getPhone() != null && !filter.getPhone().isBlank()) {
                predicates.add(builder.like(root.get("phone"), "%" + filter.getPhone() + "%"));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
