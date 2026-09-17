package cn.hamm.spms.base.spi;

import cn.hamm.spms.base.config.ConfigFlag;

/**
 * <h1>底座向业务域要的能力：读一个系统开关</h1>
 *
 * <p>单据基类在"新增后是否自动审核"这类地方要读管理端配的开关，而配置的存储、
 * 缓存与字典校验都在 {@code module.system.config}。底座只需要回答一个问题：
 * <b>这个开关现在是开的吗</b>。</p>
 *
 * <p><b>为什么参数是类型化的枚举而不是 String</b>：换成字符串就把"哪个开关管我"变成运行期才知道的事，
 * 写错一个字母的后果是<b>静默不审核</b>（没有任何报错，只是行为变了）；
 * 枚举至少保证编译期能对上。这是本仓在"可读性 vs 类型安全"上的一贯取舍。</p>
 *
 * @author Hamm.cn
 */
public interface SystemConfigSwitch {
    /**
     * 该开关是否处于开启状态
     *
     * @param flag 配置标识
     * @return 是否开启
     */
    boolean isEnabled(ConfigFlag flag);
}
