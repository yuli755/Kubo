package cn.hamm.spms.module.asset.material;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.annotation.Extends;
import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
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
 * <h1>物料控制器</h1>
 *
 * <p>写接口继续复用 AirPower JPA CRUD；读接口在本类显式换到 MaterialMapper，
 * 让 page/detail/stat/count 都经过同一条 statement 绑定和行级权限拦截链。</p>
 *
 * @author Hamm.cn
 */
@Api("material")
@Description("物料")
@Extends({Curd.Export, Curd.QueryExport})
public class MaterialController extends BaseController<MaterialEntity, MaterialService, MaterialRepository> {

    /**
     * 物料分页读出口（Mapper）。
     *
     * @param request 分页与筛选
     * @return 分页结果
     */
    @Override
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<MaterialEntity> request) {
        GetPage.checkApiAvailable(this);
        return Json.data(service.getMapperPage(request));
    }

    /**
     * 物料详情读出口（Mapper）。无权行不通过 JPA 回退，直接按不存在处理。
     *
     * @param source 只含 ID 的请求实体
     * @return 详情
     */
    @Override
    @PostMapping("getDetail")
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull MaterialEntity source) {
        GetDetail.checkApiAvailable(this);
        MaterialEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return Json.data(entity);
    }

    /**
     * 物料总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询物料总数")
    @PostMapping("getCount")
    public Json getCount(@RequestBody(required = false) QueryListRequest<MaterialEntity> request) {
        MaterialEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperCount(filter));
    }

    /**
     * 物料统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询物料统计")
    @PostMapping("getStat")
    public Json getStat(@RequestBody(required = false) QueryListRequest<MaterialEntity> request) {
        MaterialEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperStat(filter));
    }
}
