package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记「不参与默认筛选」的实体字段</h1>
 *
 * <p>{@code BaseService#buildPredicates} 反射遍历筛选实体的全部字段，把非 null 值一律变成
 * WHERE 条件。这对业务字段是对的，但对<b>带 Java 侧默认值的框架状态字段</b>是陷阱：
 * 筛选体是 Jackson 反序列化出来的实体实例，调用方没写的字段会保留成员初始化的值，于是
 * 「没传这个条件」与「传了 false」在服务端无法区分 —— 空筛选被静默加上了条件，
 * 而且从请求体、日志、SQL 里都看不出是谁加的。</p>
 *
 * <p>标了本注解的字段永不参与默认筛选。确实需要按它筛时，写显式查询
 * （Repository 派生方法 / 自建 Specification），把条件摆在代码里，而不是靠默认值偷偷生效。</p>
 *
 * <h2>为什么 {@code BaseEntity.isPublished} 必须标它（真实事故，2026-09-29 联调实测）</h2>
 *
 * <p>{@code V10__menu_canonical_paths.sql} 以 {@code is_published = 1} 种下「工厂与库位」
 * 「人员组织」「数据权限」三个分组与「操作日志」页面。而 {@code isPublished} 在
 * {@code BaseEntity} 里默认 {@code false}，于是每次 {@code POST /menu/getList} 都隐式带上
 * {@code is_published = 0}：</p>
 * <ol>
 *   <li>这 4 条菜单不在响应里；</li>
 *   <li>它们的子菜单因父节点缺失，被 {@code Trees.buildTreeList} 连带丢弃；</li>
 *   <li>前端路由是<b>按返回的菜单动态注册</b>的（{@code appRouter.initVueRouter}），
 *       菜单没返回 → 路由没注册 → 直连地址落 catch-all 的 404 页。</li>
 * </ol>
 *
 * <p>净效果：仓库管理 / 库位结构 / 用户管理 / 角色管理 / 部门管理 / 操作日志 / 数据资源 /
 * 行级策略 / 列级脱敏 —— <b>9 个页面在界面上彻底不可达</b>，其中包括整个「人员组织」与整个
 * 「数据权限」管理台。而后端 911 个测试全绿、前端 778 个测试全绿、浏览器控制台 0 报错：
 * 没有任何一层能看见它，只有真机点开侧栏才发现少了两个分组。</p>
 *
 * <p>同一根因还有一个更隐蔽的推论：{@code /publish} 是所有资源都开放的动作，
 * 而「发布」在本框架里等于「冻结」（{@code BaseController} 拒改拒删已发布数据）。
 * 叠加默认筛选后，<b>发布一条记录 = 它从自己的列表页消失</b>。菜单只是最先撞上的一例。</p>
 *
 * @author Hamm.cn
 * @see cn.hamm.spms.base.BaseEntity
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface FilterIgnore {
}
