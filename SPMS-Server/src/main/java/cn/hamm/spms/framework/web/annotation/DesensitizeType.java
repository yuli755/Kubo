package cn.hamm.spms.framework.web.annotation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>脱敏方式枚举</h1>
 *
 * <p>替代旧 {@code cn.hamm.airpower.core.enums.DesensitizeType}。</p>
 *
 * @author Hamm.cn
 */
@AllArgsConstructor
@Getter
public enum DesensitizeType {
    TELEPHONE(0, 0),
    MOBILE(3, 4),
    ID_CARD(6, 4),
    BANK_CARD(4, 4),
    CAR_NUMBER(2, 1),
    EMAIL(2, 2),
    CHINESE_NAME(1, 1),
    ADDRESS(3, 0),
    IP_V4(0, 0),
    CUSTOM(0, 0);

    private final int minHead;
    private final int minTail;
}
