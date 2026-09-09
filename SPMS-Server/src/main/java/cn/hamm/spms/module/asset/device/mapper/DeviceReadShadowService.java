package cn.hamm.spms.module.asset.device.mapper;

import cn.hamm.spms.module.asset.device.DeviceEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>设备 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author zfy
 */
@Service
public class DeviceReadShadowService {
    public static final String RESOURCE_KEY = "asset.device";
    private final MapperShadowReadService mapperShadowReadService;

    public DeviceReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的设备筛选（名称 / UUID）
     */
    public void submit(DeviceEntity filter) {
        DeviceEntity normalized = filter == null ? new DeviceEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "device", DeviceEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<DeviceEntity> businessFilter(DeviceEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getUuid() != null && !filter.getUuid().isBlank()) {
                predicates.add(builder.like(root.get("uuid"), "%" + filter.getUuid() + "%"));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
