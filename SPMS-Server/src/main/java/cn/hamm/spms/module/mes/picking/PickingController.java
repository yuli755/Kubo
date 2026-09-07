package cn.hamm.spms.module.mes.picking;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.spms.base.bill.BaseBillController;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import cn.hamm.spms.module.mes.picking.detail.PickingDetailEntity;
import cn.hamm.spms.module.mes.picking.detail.PickingDetailRepository;
import cn.hamm.spms.module.mes.picking.detail.PickingDetailService;

import static cn.hamm.airpower.curd.base.Curd.GetDetail;
import static cn.hamm.airpower.curd.base.Curd.GetPage;
import static cn.hamm.airpower.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("picking")
@Description("领料单")
public class PickingController extends BaseBillController<
        PickingEntity, PickingService, PickingRepository,
        PickingDetailEntity, PickingDetailService, PickingDetailRepository> {

    @Override
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<PickingEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return Json.data(service.getMapperPage(request));
    }

    @Override
    @PostMapping("getDetail")
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull PickingEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        PickingEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return Json.data(entity);
    }
}
