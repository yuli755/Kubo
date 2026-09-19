package demo.other;

/** 同包外存在、引用它的文件却没 import —— 判「缺 import」（名字带项目味后缀才会进候选，见脚本头部说明） */
public class NeighborService {
    private Long id;

    public Long getId() {
        return id;
    }
}
