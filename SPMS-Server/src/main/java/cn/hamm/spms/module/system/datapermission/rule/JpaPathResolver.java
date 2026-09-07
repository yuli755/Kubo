package cn.hamm.spms.module.system.datapermission.rule;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import java.util.List;

/**
 * <h1>把登记名解析成 JPA 路径（标量属性或一跳关联）</h1>
 *
 * <p>归属列历史上只能是标量属性名：{@code root.get("warehouseId")}。但真实模型里仓库归属是
 * {@code @ManyToOne StorageEntity storage}（见施工图 D1(b) 与 §19.25），于是"实体形状不对"这一类
 * 只能永远绑不上，而运行期把它抹平成一条审计。本类就是补这一格：登记成 {@code storage.id} 时
 * 解析成 {@code root.get("storage").get("id")}。</p>
 *
 * <h2>为什么不用 {@code root.join("storage")}</h2>
 *
 * <p>{@code root.get(关联名).get(目标列)} 就是 JPQL 里 {@code o.storage.id} 的写法，JPA 自己会补隐式
 * inner join，因此这里只需要 {@code Path.get(String)} —— 每个 JPA 版本都有。相反，显式 {@code join}
 * 要依赖 {@code Root} 是否继承 {@code From}（Jakarta Persistence 3.1 才这样定义），本项目编译期的 API
 * 版本由父 POM 传递决定，把安全边界压在"版本恰好够新"上不值得。</p>
 *
 * <h2>三条边界</h2>
 * <ol>
 *   <li><b>只支持一跳</b>：由 {@link OwnerColumnRegistration#MAX_SEGMENTS} 封顶。再深就说明该把中间列
 *       冗余出来，而不是让权限内核去拼任意深度的路径 —— 那种形状既没法被静态盘点复现（面板就会说谎），
 *       也很容易把执行计划拖没；</li>
 *   <li><b>取不到就返回 null，不抛</b>：调用方决定失败语义 —— 条件树编译器要报错（不能静默放开），
 *       主体谓词要计入绑定失败数（由上层按 enforce / shadow 分别拒绝或记账）。解析规则只有一份，
 *       失败取向留给调用方，这是 {@link PermissionRuleSupport#findPath} 已经确立的分工；</li>
 *   <li><b>不做集合基数校验</b>：登记成 {@code roles.code} 这种一对多路径，JPA 会展开成 join 放大行数。
 *       运行期不拦（拦了要读元模型，代价与收益不成比例），但 {@link cn.hamm.spms.module.system.datapermission.service.EntityShapeIndex}
 *       不会把集合目标算成"绑得上"，所以这种登记一定会被盘点列出来。</li>
 * </ol>
 *
 * @author Hamm.cn
 */
public final class JpaPathResolver {

    private JpaPathResolver() {
    }

    /**
     * 解析登记名
     *
     * @param root 查询根，可为 {@code null}
     * @param path 登记名：{@code creatorId} 或 {@code storage.id}（列名与属性名两种写法都认）
     * @return 路径；解析不出来返回 {@code null}
     */
    public static Path<?> resolve(Root<?> root, String path) {
        if (root == null) {
            return null;
        }
        List<String> segments = PermissionRuleSupport.pathSegments(path);
        if (segments.isEmpty()) {
            return null;
        }
        if (segments.size() == 1) {
            return PermissionRuleSupport.findPath(root, segments.get(0));
        }
        Path<?> association = PermissionRuleSupport.findPath(root, segments.get(0));
        if (association == null) {
            return null;
        }
        return findOnPath(association, segments.get(1));
    }

    private static Path<?> findOnPath(Path<?> parent, String name) {
        for (String candidate : PermissionRuleSupport.pathCandidates(name)) {
            try {
                Path<?> path = parent.get(candidate);
                if (path != null) {
                    return path;
                }
            }
            catch (IllegalArgumentException ignored) {
                // 关联目标上没有这一列：换下一个写法
            }
        }
        return null;
    }
}
