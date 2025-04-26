package cn.keqiongpan.multisourceproperties;

public class PropertyCoordinate<K> {

    private final String sourceName;
    private final K propertyKey;

    public PropertyCoordinate(String sourceName, K propertyKey) {
        this.sourceName = sourceName;
        this.propertyKey = propertyKey;
    }

    public String getSourceName() {
        return sourceName;
    }

    public K getPropertyKey() {
        return propertyKey;
    }

}
