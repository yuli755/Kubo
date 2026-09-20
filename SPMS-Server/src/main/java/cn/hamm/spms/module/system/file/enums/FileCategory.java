package cn.hamm.spms.module.system.file.enums;

import cn.hamm.spms.common.AppConstant;

/**
 * <h1>文件分类</h1>
 *
 * <p>这里只是应用自己的选项，不参与旧框架的字典校验或状态接口。
 * 键值/标签及 getter 保持原样；不再为枚举数据依赖框架接口或注解处理器。</p>
 *
 * @author Hamm.cn
 */
public enum FileCategory {
    /**
     * 临时文件
     */
    TEMP(0, "临时文件", AppConstant.DEFAULT_EXTENSIONS),

    /**
     * 普通文件
     */
    NORMAL(1, "普通文件", AppConstant.DEFAULT_EXTENSIONS),

    /**
     * 头像
     */
    AVATAR(1001, "头像", new String[]{
            "jpg", "jpeg", "png"
    }),

    /**
     * 合同附件
     */
    CONTRACT_ATTACHMENT(2001, "合同附件", new String[]{
            "jpg", "jpeg", "png", "bmp", "pdf", "zip"
    }, true),
    ;

    private final int key;
    private final String label;
    private final String[] extensions;
    private final Boolean isProtected;

    FileCategory(int key, String label, String[] extensions) {
        this.key = key;
        this.label = label;
        this.extensions = extensions;
        this.isProtected = false;
    }

    FileCategory(int key, String label, String[] extensions, Boolean isProtected) {
        this.key = key;
        this.label = label;
        this.extensions = extensions;
        this.isProtected = isProtected;
    }

    /** 稳定的外部键值，不用 ordinal 代替。 */
    public int getKey() {
        return key;
    }

    /** 选项显示文案。 */
    public String getLabel() {
        return label;
    }

    /** 沿用既有上传白名单；此批不修改其内容与数组返回口径。 */
    public String[] getExtensions() {
        return extensions;
    }

    /** 是否为受保护的上传分类。 */
    public Boolean getIsProtected() {
        return isProtected;
    }
}
