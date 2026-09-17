package cn.hamm.spms.module.factory.structure;

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
 * <h1>Controller</h1>
 *
 * <p>写接口继续复用 AirPower JPA CRUD；page/detail/stat/count/export 在本类显式换到 StructureMapper。
 * 树导航 {@code getList}（前端 tree selector 用）仍走 JPA，经 {@code beforeGetList}/{@code afterGetList}
 * 递归补 {@code children}。</p>
 *
 * @author Hamm.cn
 */
@Api("structure")
@Description("生产单元")
@Extends({Curd.Export, Curd.QueryExport})
public class StructureController extends BaseController<StructureEntity, StructureService, StructureRepository> {

    /**
     * 生产单元分页读出口（Mapper）。
     *
     * @param request 分页与筛选
     * @return 分页结果
     */
    @Override
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<StructureEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return Json.data(service.getMapperPage(request));
    }

    /**
     * 生产单元详情读出口（Mapper）。无权行不通过 JPA 回退，直接按不存在处理。
     *
     * @param source 只含 ID 的请求实体
     * @return 详情
     */
    @Override
    @PostMapping("getDetail")
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull StructureEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        StructureEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return Json.data(entity);
    }

    /**
     * 生产单元总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询生产单元总数")
    @PostMapping("getCount")
    public Json getCount(@RequestBody(required = false) QueryListRequest<StructureEntity> request) {
        StructureEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperCount(filter));
    }

    /**
     * 生产单元统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询生产单元统计")
    @PostMapping("getStat")
    public Json getStat(@RequestBody(required = false) QueryListRequest<StructureEntity> request) {
        StructureEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperStat(filter));
    }

    /**
     * 生产单元导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public Json export(@RequestBody QueryListRequest<StructureEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return Json.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}
