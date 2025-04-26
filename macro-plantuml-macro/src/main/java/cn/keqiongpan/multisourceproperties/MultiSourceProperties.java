package cn.keqiongpan.multisourceproperties;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiSourceProperties {

    private final Map<String, PropertyResolver> sourcePropertyResolvers = new HashMap<>();

    public void putResolver(String sourceName, PropertyResolver propertyResolver) {
        this.sourcePropertyResolvers.put(sourceName, propertyResolver);
    }

    public <V> V get(PropertyDescriptor propertyDescriptor) {
        PropertyCoordinate[] coordinates = propertyDescriptor.getOrderedCoordinates();
        if (coordinates == null) {
            return null;
        }

        for (PropertyCoordinate coordinate : coordinates) {
            PropertyResolver resolver = this.sourcePropertyResolvers.get(coordinate.getSourceName());
            if (resolver == null) {
                continue;
            }

            V value = resolver.get(coordinate.getPropertyKey());
            if (value == null) {
                continue;
            }

            if (propertyDescriptor.isValuableEmpty()) {
                return value;
            }

            if (value instanceof String && ((String) value).length() <= 0) {
                continue;
            }

            if (value instanceof Array && ((Object[]) value).length <= 0) {
                continue;
            }

            if (value instanceof List && ((List) value).size() <= 0) {
                continue;
            }

            if (value instanceof Map && ((Map) value).size() <= 0) {
                continue;
            }

            return value;
         }

        return null;
    }
}
