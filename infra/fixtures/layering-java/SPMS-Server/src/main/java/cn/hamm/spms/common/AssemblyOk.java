package cn.hamm.spms.common;

import cn.hamm.spms.module.channel.sale.SaleLeak;
import org.springframework.context.annotation.Configuration;

/** 允许：common 里的装配类（@Configuration）算组合根，认识业务域是它的职责 */
@Configuration
public class AssemblyOk {
    private SaleLeak saleLeak;
}
