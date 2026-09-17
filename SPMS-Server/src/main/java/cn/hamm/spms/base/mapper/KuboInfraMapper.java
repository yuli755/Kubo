package cn.hamm.spms.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <h1>基础设施表 Mapper 的标记接口（不受读侧灰度开关控制）</h1>
 *
 * <p>2026-09-18 建立。用来区分两类 MyBatis-Plus Mapper：</p>
 *
 * <ul>
 *   <li>{@link KuboBaseMapper}：<b>业务读链路</b>。由
 *       {@code MapperReadConfiguration} 在 {@code kubo.data-permission.mapper-read.enabled=true}
 *       时才扫描注册 —— 这个开关是灰度与回滚位，所以业务 Mapper 必须能"整体不存在"。</li>
 *   <li>{@link KuboInfraMapper}（本接口）：<b>基础设施表</b>（审计/证据日志等）。
 *       它们由 {@code InfraMapperConfiguration} <b>无条件</b>扫描注册，因为写入这些表是
 *       "业务失败也要留下证据"的旁路，绝不能随业务开关一起消失。</li>
 * </ul>
 *
 * <p>两套 marker 互不相交，所以同一个接口不会被两次 {@code @MapperScan} 命中
 * （Spring Boot 2.1+ 禁止 Bean 覆盖，重复注册会直接启动失败）。</p>
 *
 * @param <T> 实体类型
 * @author Hamm.cn
 */
public interface KuboInfraMapper<T> extends BaseMapper<T> {
}
