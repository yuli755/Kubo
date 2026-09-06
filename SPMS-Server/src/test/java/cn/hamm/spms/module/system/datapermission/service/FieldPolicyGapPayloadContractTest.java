package cn.hamm.spms.module.system.datapermission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>缺口条目的 JSON 字段名契约</h1>
 *
 * <p>这条用例存在的唯一理由：{@code ExportGapItem} 是个 record，而 record 能否被 Jackson
 * 按组件名序列化取决于 databind 版本（<b>2.12 之前会序列化成空对象</b>）。
 * 那是一种最阴的失败：后端 200、前端拿到 {@code {}}、界面显示"没有缺口" ——
 * 编译通过、测试通过、CI 全绿，只有人在界面上看不出任何异常。</p>
 *
 * <p>字段名清单必须与前端 {@code src/api/system/types.ts} 的 {@code ExportGapItem} 一致，
 * 那侧还有一条用例做双向对账（见 {@code src/config/__tests__/maskContract.spec.ts}）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("缺口条目序列化 · 字段名必须与前端读的完全一致")
class FieldPolicyGapPayloadContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("五个字段都以组件名出现在 JSON 里（record 未被序列化成空对象）")
    void serializesAllComponentNames() throws Exception {
        String json = MAPPER.writeValueAsString(new DataResourceService.ExportGapItem(
                7L, "asset.material", "price", "buyer", "缺一条 EXPORT 明文授权"));

        for (String name : new String[] {"resourceId", "resourceKey", "fieldName", "roleCode", "reason"}) {
            assertTrue(json.contains("\"" + name + "\""), name + " 没有出现在 JSON 里：" + json);
        }
        assertTrue(json.contains("\"price\""), json);
    }
}
