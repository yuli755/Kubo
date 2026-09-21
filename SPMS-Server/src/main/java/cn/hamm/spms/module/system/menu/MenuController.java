package cn.hamm.spms.module.system.menu;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Permission;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.framework.http.ApiResponse;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.Sort;
import cn.hamm.spms.framework.util.Trees;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
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
    public ApiResponse<List<MenuEntity>> getList(@RequestBody @NotNull QueryListRequest<MenuEntity> queryListRequest) {
        MenuEntity filter = queryListRequest.getFilter();
        queryListRequest.setSort(Objects.requireNonNullElse(
                queryListRequest.getSort(),
                new Sort().setField(MenuService.ORDER_FIELD_NAME)
        ));
        queryListRequest.setFilter(filter);
        return ApiResponse.data(Trees.buildTreeList(service.getList(queryListRequest)));
    }
}
