package cn.hamm.spms.module.asset.contract.mapper;

import cn.hamm.spms.module.asset.contract.ContractEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>合同 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class ContractReadShadowService {
    public static final String RESOURCE_KEY = "asset.contract";
    private final MapperShadowReadService mapperShadowReadService;

    public ContractReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的合同筛选（编码 / 名称 / 内容）
     */
    public void submit(ContractEntity filter) {
        ContractEntity normalized = filter == null ? new ContractEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "contract", ContractEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<ContractEntity> businessFilter(ContractEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getContent() != null && !filter.getContent().isBlank()) {
                predicates.add(builder.like(root.get("content"), "%" + filter.getContent() + "%"));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
