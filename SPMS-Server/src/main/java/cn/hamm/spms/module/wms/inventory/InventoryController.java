package cn.hamm.spms.module.wms.inventory;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.annotation.Extends;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.spms.base.BaseController;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.airpower.curd.base.Curd.GetDetail;
import static cn.hamm.airpower.curd.base.Curd.GetPage;
import static cn.hamm.airpower.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("inventory")
@Description("库存")
@Extends({GetDetail, GetPage})
public class InventoryController extends BaseController<InventoryEntity, InventoryService, InventoryRepository> {

    /**
     * 库存分页读出口（Mapper）；开关关闭时回到 AirPower JPA。
     */
    @Override
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<InventoryEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return Json.data(service.getMapperPage(request));
    }

    /**
     * 库存详情读出口（Mapper）；无权行不回退 JPA。
     */
    @Override
    @PostMapping("getDetail")
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull InventoryEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        InventoryEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return Json.data(entity);
    }
}
