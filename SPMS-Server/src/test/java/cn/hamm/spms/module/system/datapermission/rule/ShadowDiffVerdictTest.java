package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import cn.hamm.spms.module.system.datapermission.service.ShadowDiffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <h1>影子比对结论分类测试</h1>
 *
 * <p>分类决定"能不能把某资源推进 enforce"，所以它必须是个可测的纯函数，
 * 而不是散在 SQL 报表里的 CASE WHEN。</p>
 *
 * @author Hamm.cn
 */
class ShadowDiffVerdictTest {

    @Test
    @DisplayName("行数一致 → CONSISTENT（说明该主体本来就只能看到这些行）")
    void consistent() {
        assertEquals(DataPermissionDiffEntity.VERDICT_CONSISTENT, ShadowDiffService.verdictOf(100L, 100L));
    }

    @Test
    @DisplayName("加了条件变少 → EXPECTED_FILTER（enforce 会缩小结果，需评估影响面）")
    void expectedFilter() {
        assertEquals(DataPermissionDiffEntity.VERDICT_EXPECTED_FILTER, ShadowDiffService.verdictOf(100L, 90L));
    }

    @Test
    @DisplayName("加了条件反而变多 → ANOMALY（实现有 bug，不能进 enforce）")
    void anomaly() {
        assertEquals(DataPermissionDiffEntity.VERDICT_ANOMALY, ShadowDiffService.verdictOf(100L, 110L));
    }

    @Test
    @DisplayName("任一侧取不到数 → ERROR：比对本身失败不能当成没问题")
    void error() {
        assertEquals(DataPermissionDiffEntity.VERDICT_ERROR, ShadowDiffService.verdictOf(null, 100L));
        assertEquals(DataPermissionDiffEntity.VERDICT_ERROR, ShadowDiffService.verdictOf(100L, null));
    }
}
