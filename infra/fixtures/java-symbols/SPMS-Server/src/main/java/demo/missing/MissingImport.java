package demo.missing;

/** 反例：类还在，只是忘了 import */
public class MissingImport {
    private NeighborService neighbor;

    public NeighborService getNeighbor() {
        return neighbor;
    }
}
