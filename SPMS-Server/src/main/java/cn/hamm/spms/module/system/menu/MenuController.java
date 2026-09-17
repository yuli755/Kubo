package cn.hamm.spms.module.system.menu;

import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.TreeUtil;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.framework.curd.permission.Permission;
import cn.hamm.spms.base.BaseController;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Objects;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("menu")
@Description("菜单")
public class MenuController extends BaseController<MenuEntity, MenuService, MenuRepository> {
    @Permission(authorize = false)
    @Override
    public Json getList(@RequestBody @NotNull QueryListRequest<MenuEntity> queryListRequest) {
        MenuEntity filter = queryListRequest.getFilter();
        queryListRequest.setSort(Objects.requireNonNullElse(
                queryListRequest.getSort(),
                new Sort().setField(MenuService.ORDER_FIELD_NAME)
        ));
        queryListRequest.setFilter(filter);
        return Json.data(TreeUtil.buildTreeList(service.getList(queryListRequest)));
    }
}
