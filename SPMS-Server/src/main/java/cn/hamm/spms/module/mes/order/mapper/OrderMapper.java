package cn.hamm.spms.module.mes.order.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.mes.order.OrderEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>生产订单读侧 Mapper</h1>
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.order", alias = "orders")
public interface OrderMapper extends KuboBaseMapper<OrderEntity>, RowParityCounter {
    @Override
    default String parityResourceKey() {
        return "mes.order";
    }

    List<OrderEntity> selectPageByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    OrderEntity selectDetailByPermission(@Param("id") Long id);

    Long countByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type
    );

    Long countByPermissionFragment(Map<String, Object> parameters);
}
