package cn.hamm.spms.module.system.datapermission.rule;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * <h1>关联路径解析的用例</h1>
 *
 * <p>{@code JpaPathResolver} 是"实体形状不对"这一类唯一的功能性补丁，所以它必须被钉住两件事：</p>
 * <ul>
 *   <li>能走通时走的是 {@code root.get(关联).get(列)}，也就是 JPQL 的 {@code o.storage.id}
 *       —— 用隐式 join，不依赖 {@code Root} 是否继承 {@code From}（那是 Jakarta Persistence 3.1 才有的形状，
 *       而本项目的 API 版本由父 POM 决定，安全边界不能压在这上面）；</li>
 *   <li>走不通时只返回 {@code null}，绝不抛、也绝不"退回某个能取到的列"。抛出去会让读接口 500，
 *       退回去会静默放宽数据范围，两者都比"绑不上"严重。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("JpaPathResolver · 一跳关联与绑不上")
class JpaPathResolverTest {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("单段登记：直接落到 root.get")
    void scalarSegmentUsesRootGet() {
        Root<Object> root = mock(Root.class);
        Path<Object> path = mock(Path.class);
        given(root.get("creatorId")).willReturn(path);

        assertSame(path, JpaPathResolver.resolve(root, "creatorId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("登记成列名也认：creator_id 试出 creatorId")
    void columnStyleRegistrationIsRescued() {
        Root<Object> root = mock(Root.class);
        Path<Object> path = mock(Path.class);
        given(root.get("creator_id")).willThrow(new IllegalArgumentException("Unable to locate Attribute"));
        given(root.get("creatorId")).willReturn(path);

        assertSame(path, JpaPathResolver.resolve(root, "creator_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("一跳关联：先取关联、再取目标列，两段各自都认两种写法")
    void associationPathWalksTwoGets() {
        Root<Object> root = mock(Root.class);
        Path<Object> association = mock(Path.class);
        Path<Object> leaf = mock(Path.class);
        given(root.get("storage_detail")).willThrow(new IllegalArgumentException("Unable to locate Attribute"));
        given(root.get("storageDetail")).willReturn(association);
        given(association.get("id")).willReturn(leaf);

        assertSame(leaf, JpaPathResolver.resolve(root, "storage_detail.id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("关联目标上没有那一列 → null（不能退回关联本身，那等于把整行对象拿去比 ID）")
    void missingLeafReturnsNull() {
        Root<Object> root = mock(Root.class);
        Path<Object> association = mock(Path.class);
        given(root.get(anyString())).willReturn(association);
        given(association.get(anyString())).willThrow(new IllegalArgumentException("Unable to locate Attribute"));

        assertNull(JpaPathResolver.resolve(root, "storage.whId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("根上没有这个关联 → null，且不去碰任何子路径")
    void missingAssociationStopsEarly() {
        Root<Object> root = mock(Root.class);
        given(root.get(anyString())).willThrow(new IllegalArgumentException("Unable to locate Attribute"));
        Path<Object> association = mock(Path.class);

        assertNull(JpaPathResolver.resolve(root, "storage.id"));
        verifyNoInteractions(association);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("形状不合法（三跳、空白、注入）→ 直接 null，一次 root 都不碰")
    void malformedPathNeverTouchesRoot() {
        Root<Object> root = mock(Root.class);

        assertNull(JpaPathResolver.resolve(root, "a.b.c"));
        assertNull(JpaPathResolver.resolve(root, "  "));
        assertNull(JpaPathResolver.resolve(root, null));
        // SQL-IGNORE: 恶意输入样例（路径注入，断言解析不出属性路径）
        assertNull(JpaPathResolver.resolve(root, "a; DROP TABLE x"));
        verifyNoInteractions(root);
        assertNull(JpaPathResolver.resolve(null, "creatorId"));
    }
}
