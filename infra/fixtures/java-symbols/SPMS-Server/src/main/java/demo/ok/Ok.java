package demo.ok;

import java.util.List;
import java.util.Map;

/** 干净样本：import 齐全 / 同文件声明 / 泛型实参 / 内建类型，都不许被点出来 */
public class Ok {
    private List<String> names;
    private Map<String, Ok> children;
    private StringBuilder buffer = new StringBuilder();

    public Ok build() {
        return new Ok();
    }
}
