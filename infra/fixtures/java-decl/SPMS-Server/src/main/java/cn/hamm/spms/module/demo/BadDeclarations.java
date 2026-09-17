package cn.hamm.spms.module.demo;

/**
 * 反例夹具：声明级门禁（`check-java-declarations.mjs`）
 *
 * 这里的每一处命中都是 javac 会拒绝的错，同时也钉住一条**判据口径**：
 * 局部变量与字段同名不是错误，门禁不该报（判据只看类体第一层）。
 */
public class BadDeclarations {
    private String name;

    private String name;

    private String nickname;

    public void save(String code, String code) {
        // 局部变量与字段同名：合法，门禁不该报
        final String name = code;
        System.out.println(name + this.name + nickname);
    }
}
