package cn.hamm.spms.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <h1>读侧 Mapper 统一基类（P2 · A2「读走 Mapper」）</h1>
 *
 * <p>受保护资源的 {@code page / detail / stat / export} 走这里；写路径走写侧仓储
 * （{@code ICurdRepository} + 各自的 mapper XML）—— J4（2026-09-18）之后两边都是
 * MyBatis-Plus，"两套 ORM"这个概念在本仓已经不存在。
 * 基类本身不加任何方法，它存在的意义是<b>给"哪些接口算读侧 Mapper"一个可判定的边界</b>：
 * 注册用的是 {@code markerInterface = KuboBaseMapper.class}，门禁能按名字找文件。
 * 所以三条纪律必须写在离代码最近的地方：</p>
 *
 * <h2>一、不要标 {@code @Mapper}</h2>
 * <p>MyBatis-Plus 的自动扫描器专门注册带 {@code @Mapper} 的接口，<b>它不认识我们的开关</b>。
 * 本仓的读端点由 {@code kubo.data-permission.mapper-read.enabled} 一刀控制（默认 false），
 * 一旦有人"顺手"加上 {@code @Mapper}，默认关就会变成默认开，而这个开关的全部价值就在于
 * "关着的时候容器里根本没有这条读路径"。注册只走 {@code MapperReadConfiguration} 里的
 * {@code @MapperScan}（由测试钉住）。</p>
 *
 * <h2>二、表名解析：J3/J4 起只有一个来源</h2>
 * <p>{@link BaseMapper} 注入的 {@code selectList / selectCount / selectPage} 用
 * <b>MyBatis-Plus 自己的规则解析表名</b>：有 {@code @TableName} 用它的值，没有才按类名驼峰转下划线。
 * {@code ContractEntity} 按类名推会得到 {@code contract_entity}，而真表是 {@code contract}：
 * 那<b>不是报错，是指向另一张表</b>（施工图 §19.34 记的就是这个坑）。</p>
 *
 * <p>现在这条已经收口：40 个生产实体全部带 {@code @TableName(…)}，由 {@code pnpm ddl:check}
 * 与 {@code schemaContract.spec.ts}（实体 ↔ {@code infra/init-sql/init.sql} 双向比对）钉住，
 * {@code pnpm jpa:check} 另有一条"实体上不许再出现 JPA 映射注解"的硬判据兜底。
 * 所以注入方法可以放心用了 —— 但仍有一条纪律：<b>行级权限条件不许出现在 XML 里</b>（见下一条）。</p>
 *
 * <h2>三、权限条件不许出现在 XML 里</h2>
 * <p>行级条件只有一份实现：{@code SqlPermissionRuleRenderer} 渲染出的片段，由
 * {@code DataPermissionInterceptor}（{@code KuboDataPermissionHandler}）在语句编译期改写 AST 注入
 * —— 不是 XML 占位符（那是 J2 换轨前的形状，已随 `countByPermissionFragment` 一起删除）。
 * 在 XML 里手写 {@code tenant_id = #{...}} 看着等价，实则把"谁能看见哪些行"变成了两处真相 ——
 * 改策略时改一处、漏一处，而漏的那处不会报错，只会多放行一些行。</p>
 *
 * @param <T> 实体类型
 * @author Hamm.cn
 */
public interface KuboBaseMapper<T> extends BaseMapper<T> {
}
