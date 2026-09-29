package cn.hamm.spms.base;

import cn.hamm.spms.framework.web.annotation.FilterIgnore;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * <h1>默认筛选体不得携带「发布态」条件</h1>
 *
 * <p>钉住 {@link FilterIgnore} 的语义。事故背景（2026-09-29 联调实测）：
 * {@code BaseEntity.isPublished} 的 Java 默认值 {@code false} 会被
 * {@link BaseService#buildPredicates} 当成调用方传入的条件，于是每次列表查询都隐式带上
 * {@code is_published = 0}；V10 以 {@code is_published = 1} 种下的 4 条菜单因此不返回，
 * 子菜单被 {@code Trees.buildTreeList} 连带丢弃，前端又按返回的菜单注册路由 ——
 * 9 个页面（含用户/角色/部门管理与整个数据权限管理台）在界面上不可达，直连地址落 404。
 * 当时后端 911 个测试全绿：没有任何一条测试覆盖「空筛选会产生哪些条件」。</p>
 *
 * <p>{@code isDisabled} 刻意<b>不</b>标 {@code @FilterIgnore}：默认只看启用行是产品语义，
 * 各列表页的「显示已禁用」开关显式传 {@code isDisabled=true}。这个不对称是设计决定，
 * 所以最后一条测试把它也钉住，防止后来者「顺手统一」。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class BaseServiceFilterIgnoreTest {

    /**
     * {@code buildPredicates} 收的是 {@code From<?, ?>}；{@code Root<X>} 即 {@code From<X, X>}，
     * 且只有一个类型参数（{@code From<Z, X>} 是两个，直接写 {@code From<Object>} 编不过）。
     */
    @Mock
    private Root<Object> root;

    @Mock
    private CriteriaBuilder builder;

    private final ProbeService service = new ProbeService();

    /**
     * 被测实体：一个业务字段 + 继承来的框架状态位
     */
    static class ProbeEntity extends BaseEntity<ProbeEntity> {
        private String name;

        ProbeEntity setName(String name) {
            this.name = name;
            return this;
        }
    }

    /**
     * 依赖全是 {@code @Autowired(required = false)} 字段注入，测试里直接 new 即可；
     * {@code buildPredicates} 只做反射 + 调 root/builder，不碰数据库。
     */
    static class ProbeService extends BaseService<ProbeEntity, BaseRepository<ProbeEntity>> {
    }

    @Test
    @DisplayName("空筛选体：isDisabled 成条件（默认只看启用行），isPublished 不成条件")
    void emptyFilterSkipsPublishedButKeepsDisabled() {
        service.buildPredicates(root, builder, new ProbeEntity(), false);

        verify(root).get("isDisabled");
        verify(root, never()).get("isPublished");
    }

    @Test
    @DisplayName("显式传 isPublished=true 也不成条件：注解语义是「永不参与默认筛选」")
    void explicitPublishedIsStillIgnored() {
        ProbeEntity filter = new ProbeEntity();
        filter.setIsPublished(true);

        service.buildPredicates(root, builder, filter, false);

        verify(root, never()).get("isPublished");
    }

    @Test
    @DisplayName("业务字段照常成条件：跳过 @FilterIgnore 不是把筛选一并关掉")
    void businessFieldIsStillFiltered() {
        service.buildPredicates(root, builder, new ProbeEntity().setName("MacBook"), false);

        verify(root).get("name");
        verify(root, never()).get("isPublished");
    }

    @Test
    @DisplayName("契约：isPublished 标了 @FilterIgnore，isDisabled 刻意没标")
    void baseEntityAnnotationContract() throws NoSuchFieldException {
        Field published = BaseEntity.class.getDeclaredField("isPublished");
        Field disabled = BaseEntity.class.getDeclaredField("isDisabled");

        assertTrue(published.isAnnotationPresent(FilterIgnore.class),
                "isPublished 必须标 @FilterIgnore，否则发布态会重新变成隐式默认筛选条件");
        assertFalse(disabled.isAnnotationPresent(FilterIgnore.class),
                "isDisabled 不能标 @FilterIgnore：默认只看启用行是产品语义，标了会让禁用行涌进所有列表");
    }
}
