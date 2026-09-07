package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账探针登记面的判据
 *
 * <p>这张表小，但它决定"哪些资源欠着 ORM 对账证据"。两种错法都致命：重复登记（两个 Mapper
 * 各数一张表，静默取一个 ⇒ 证据是假的）与空表被读成"全都一致"。所以这两条在这里钉住。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("RowParityRegistry · 探针登记")
class RowParityRegistryTest {

    private static RowParityCounter probe(String key, Long answer) {
        return new RowParityCounter() {
            @Override
            public String parityResourceKey() {
                return key;
            }

            @Override
            public Long countByPermissionFragment(Map<String, Object> parameters) {
                return answer;
            }
        };
    }

    @Test
    @DisplayName("按资源键索引；未登记返回 null（调用方必须把 null 读成「无证据」）")
    void indexesByResourceKey() {
        RowParityCounter material = probe("asset.material", 7L);
        RowParityRegistry registry = new RowParityRegistry(List.of(material));

        assertSame(material, registry.counterFor("asset.material"));
        assertNull(registry.counterFor("wms.inventory"));
        assertNull(registry.counterFor(null), "null 资源键不能抛，也不能被读成命中");
        assertEquals(1, registry.resources().size());
    }

    @Test
    @DisplayName("空注册表是合法状态（mapper-read 关着时的常态）")
    void emptyRegistryIsLegal() {
        RowParityRegistry registry = new RowParityRegistry(List.of());

        assertTrue(registry.isEmpty());
        assertNull(registry.counterFor("asset.material"));
    }

    @Test
    @DisplayName("同一资源两个探针 ⇒ 启动就失败，不许静默取第一个")
    void duplicatedResourceFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new RowParityRegistry(
                List.of(probe("asset.material", 1L), probe("asset.material", 2L))));

        assertTrue(ex.getMessage().contains("asset.material"),
                "报错要说出是哪个资源漂了：" + ex.getMessage());
    }

    @Test
    @DisplayName("探针不报资源键 ⇒ 直接失败（宁可启动不起来，也不要在对账时才发现没索引上）")
    void blankKeyRejected() {
        assertThrows(IllegalStateException.class, () -> new RowParityRegistry(
                List.of(probe("  ", 1L))));
    }
}
