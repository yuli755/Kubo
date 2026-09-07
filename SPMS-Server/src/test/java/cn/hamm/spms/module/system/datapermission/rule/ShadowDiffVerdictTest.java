package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity;
import cn.hamm.spms.module.system.datapermission.service.ShadowDiffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <h1>影子比对结论分类测试</h1>
 *
 * <p>分类决定"能不能把某资源推进 enforce"，所以它必须是个可测的纯函数，
 * 而不是散在 SQL 报表里的 CASE WHEN。</p>
 *
 * <p>这里有两把尺子，而且<em>刻意不合成一把</em>：{@code verdictOf} 比"加条件会不会少行"，
 * {@code parityVerdictOf} 比"两个渲染方向数到的一样不一样"。前者的"变多"是实现 bug（ANOMALY），
 * 后者的"变多"是有明确含义的方向（MAPPER_LOOSE = Mapper 多放行 = 泄露面变大）。
 * 一个 {@code <> 基线} 的判据能把两件事都覆盖，但那正好把"哪一边更松"这个唯一要紧的信息抹平了。</p>
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
    @Nested
    @DisplayName("ORM 对账分类（两个渲染方向，方向性判据）")
    class Parity {

        @Test
        @DisplayName("两边一样 → CONSISTENT")
        void equalIsConsistent() {
            assertEquals(DataPermissionOrmParityEntity.VERDICT_CONSISTENT, ShadowDiffService.parityVerdictOf(90L, 90L));
        }

        @Test
        @DisplayName("Mapper 多放行 → MAPPER_LOOSE（唯一阻塞放开的一档）")
        void looserWhenMapperHasMoreRows() {
            assertEquals(DataPermissionOrmParityEntity.VERDICT_MAPPER_LOOSER, ShadowDiffService.parityVerdictOf(1L, 3L));
        }

        @Test
        @DisplayName("Mapper 少放行 → MAPPER_STRICT（可用性缺陷，不泄露，不阻塞）")
        void stricterWhenMapperHasFewerRows() {
            assertEquals(DataPermissionOrmParityEntity.VERDICT_MAPPER_STRICTER, ShadowDiffService.parityVerdictOf(3L, 1L));
        }

        @Test
        @DisplayName("任一侧没数出来 → ERROR，且必须与 CONSISTENT 区分开")
        void missingNumberIsError() {
            assertEquals(DataPermissionOrmParityEntity.VERDICT_ERROR, ShadowDiffService.parityVerdictOf(null, 3L));
            assertEquals(DataPermissionOrmParityEntity.VERDICT_ERROR, ShadowDiffService.parityVerdictOf(3L, null));
            // 两侧都是 null 也算"没数出来"：把它们读成"相等 ⇒ 一致"是最省事也最危险的一种写法
            assertEquals(DataPermissionOrmParityEntity.VERDICT_ERROR, ShadowDiffService.parityVerdictOf(null, null));
        }

        @Test
        @DisplayName("0 与 0 是一致；0 与 3 是 LOOSE（别把「恒假」当成「两边都空」）")
        void zeroRowsStillCompare() {
            assertEquals(DataPermissionOrmParityEntity.VERDICT_CONSISTENT, ShadowDiffService.parityVerdictOf(0L, 0L));
            assertEquals(DataPermissionOrmParityEntity.VERDICT_MAPPER_LOOSER, ShadowDiffService.parityVerdictOf(0L, 3L));
            assertEquals(DataPermissionOrmParityEntity.VERDICT_MAPPER_STRICTER, ShadowDiffService.parityVerdictOf(3L, 0L));
        }
    }
}
