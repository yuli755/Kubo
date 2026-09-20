package cn.hamm.spms.module.system.file.enums;

/**
 * <h1>上传平台</h1>
 *
 * <p>这里只是应用自己的选项，不参与旧框架的字典校验或状态接口。
 * 键值/标签及 getter 保持原样；不再为枚举数据依赖框架接口或注解处理器。</p>
 *
 * @author Hamm.cn
 */
public enum FileUploadPlatform {
    /**
     * 本地上传
     */
    LOCAL(0, "本地上传");

    private final int key;
    private final String label;

    FileUploadPlatform(int key, String label) {
        this.key = key;
        this.label = label;
    }

    /** 稳定的外部键值，不用 ordinal 代替。 */
    public int getKey() {
        return key;
    }

    /** 选项显示文案。 */
    public String getLabel() {
        return label;
    }
}
