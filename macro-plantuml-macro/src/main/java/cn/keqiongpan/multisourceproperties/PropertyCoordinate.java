package cn.keqiongpan.multisourceproperties;

public class PropertyCoordinate {

    private final String sourceName;
    private final String propertyKey;

    public PropertyCoordinate(String sourceName, String propertyKey) {
        this.sourceName = sourceName;
        this.propertyKey = propertyKey;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getPropertyKey() {
        return propertyKey;
    }

}
