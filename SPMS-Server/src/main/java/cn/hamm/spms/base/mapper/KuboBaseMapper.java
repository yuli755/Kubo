package cn.hamm.spms.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <h1>读侧 Mapper 统一基类（P2 · A2「读走 Mapper，写留 JPA」）</h1>
 *
 * <p>受保护资源的 {@code page / detail / stat / export} 走这里，写路径继续留在 AirPower JPA。
 * 基类本身不加任何方法，它存在的意义是<b>给"哪些接口算读侧 Mapper"一个可判定的边界</b>：
 * 注册用的是 {@code markerInterface = KuboBaseMapper.class}，权限片段用 {@code ${fragment}}
 * 单点注入，门禁能按名字找文件。继承它 = 这个实体的读端点进入了"两套 ORM 并存"的核对范围，
 * 所以三条纪律必须写在离代码最近的地方：</p>
 *
 * <h2>一、不要标 {@code @Mapper}</h2>
 * <p>MyBatis-Plus 的自动扫描器专门注册带 {@code @Mapper} 的接口，<b>它不认识我们的开关</b>。
 * 本仓的读端点由 {@code kubo.data-permission.mapper-read.enabled} 一刀控制（默认 false），
 * 一旦有人"顺手"加上 {@code @Mapper}，默认关就会变成默认开，而这个开关的全部价值就在于
 * "关着的时候容器里根本没有这条读路径"。注册只走 {@code MapperReadConfiguration} 里的
 * {@code @MapperScan}（由测试钉住）。</p>
 *
 * <h2>二、暂时只用自己声明的 XML 语句</h2>
 * <p>{@link BaseMapper} 注入的 {@code selectList / selectCount / selectPage} 会用
 * <b>MyBatis-Plus 自己的规则解析表名</b>（类名驼峰转下划线 + {@code @TableName}），它不读 JPA 的
 * {@code @Table}。{@code MaterialEntity} 恰好两边一致（{@code material}），但那是巧合不是约定：
 * 任何带包名/前缀差异的实体都会静默指到另一张表或报"表不存在"。所以在"逐个资源换轨"之前，
 * 本接口只许调用带 XML 实现的方法；哪天要用注入方法，先给那个实体验一遍表名解析（见施工图 §19.34）。</p>
 *
 * <h2>三、权限条件不许出现在 XML 里</h2>
 * <p>行级条件只有一份实现：{@code SqlPermissionRuleRenderer} 渲染出的片段，经
 * {@code RowPermissionPredicate#forMyBatis()} 换写成 MyBatis 命名参数后由 {@code ${fragment}} 注入。
 * 在 XML 里手写 {@code tenant_id = #{...}} 看着等价，实则把"谁能看见哪些行"变成了两处真相 ——
 * 改策略时改一处、漏一处，而漏的那处不会报错，只会多放行一些行。</p>
 *
 * @param <T> 实体类型
 * @author Hamm.cn
 */
public interface KuboBaseMapper<T> extends BaseMapper<T> {
}
