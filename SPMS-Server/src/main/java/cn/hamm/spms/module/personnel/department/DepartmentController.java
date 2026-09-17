package cn.hamm.spms.module.personnel.department;

import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.TreeUtil;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.annotation.Extends;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.framework.curd.permission.Permission;
import cn.hamm.spms.base.BaseController;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Objects;

import static cn.hamm.spms.framework.curd.base.Curd.Export;
import static cn.hamm.spms.framework.curd.base.Curd.QueryExport;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("department")
@Description("部门")
@Extends(exclude = {Export, QueryExport})
public class DepartmentController extends BaseController<DepartmentEntity, DepartmentService, DepartmentRepository> {
    @Permission(authorize = false)
    @Override
    public Json getList(@RequestBody @NotNull QueryListRequest<DepartmentEntity> queryListRequest) {
        DepartmentEntity filter = queryListRequest.getFilter();
        queryListRequest.setSort(Objects.requireNonNullElse(
                queryListRequest.getSort(),
                new Sort().setField(DepartmentService.ORDER_FIELD_NAME)
        ));
        queryListRequest.setFilter(filter);
        return Json.data(TreeUtil.buildTreeList(service.getList(queryListRequest)));
    }
}
