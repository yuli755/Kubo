package cn.hamm.spms.module.system.coderule;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.util.EnumValues;
import cn.hamm.spms.framework.http.ApiResponse;
import java.util.List;
import java.util.Map;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Permission;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.system.coderule.enums.CodeRuleField;
import cn.hamm.spms.module.system.coderule.enums.CodeRuleParam;
import cn.hamm.spms.module.system.coderule.enums.SerialNumberUpdate;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("coderule")
@Description("编码规则")
public class CodeRuleController extends BaseController<CodeRuleEntity, CodeRuleService, CodeRuleRepository> {
    @Description("获取支持的表")
    @Permission(login = false)
    @PostMapping("getFieldList")
    public ApiResponse<List<Map<String, Object>>> getFieldList() {
        return ApiResponse.data(EnumValues.options(CodeRuleField.class,
                EnumValues.column("key", CodeRuleField::getKey),
                EnumValues.column("label", CodeRuleField::getLabel),
                EnumValues.column("defaultPrefix", CodeRuleField::getDefaultPrefix)
        ));
    }

    @Description("获取支持的参数")
    @Permission(login = false)
    @PostMapping("getParamList")
    public ApiResponse<List<Map<String, Object>>> getParamList() {
        return ApiResponse.data(EnumValues.options(CodeRuleParam.class,
                EnumValues.column("key", CodeRuleParam::getKey),
                EnumValues.column("label", CodeRuleParam::getLabel),
                EnumValues.column("desc", CodeRuleParam::getDesc),
                EnumValues.column("demo", CodeRuleParam::getDemo)
        ));
    }

    @Description("获取流水号更新方式")
    @Permission(login = false)
    @PostMapping("getSerialNumberUpdate")
    public ApiResponse<List<Map<String, Object>>> getSerialNumberUpdate() {
        return ApiResponse.data(EnumValues.options(SerialNumberUpdate.class,
                EnumValues.column("key", SerialNumberUpdate::getKey),
                EnumValues.column("label", SerialNumberUpdate::getLabel)));
    }
}
