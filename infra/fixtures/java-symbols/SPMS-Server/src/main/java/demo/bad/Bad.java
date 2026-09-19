package demo.bad;

/** 反例：类型被整域删除后留下的使用处（javac：找不到符号） */
public class Bad {
    private DeletedEntity legacy;

    public void touch() {
        Object probe = new DeletedService();
        boolean same = probe instanceof DeletedMapper;
        Class<?> type = DeletedEnum.class;
        System.out.println(same ? type : probe);
    }
}
