package cn.hamm.spms.module.asset.material;

import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.annotation.Extends;
import cn.hamm.spms.framework.curd.base.Curd;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.base.BaseController;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.spms.framework.curd.base.Curd.GetDetail;
import static cn.hamm.spms.framework.curd.base.Curd.GetPage;
import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;

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
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
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
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
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

    /**
     * 物料导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；
     * 关闭时回退框架 JPA 导出。返回 fileCode 契约与框架一致，前端 queryExport 不变。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public Json export(@RequestBody QueryListRequest<MaterialEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return Json.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}
