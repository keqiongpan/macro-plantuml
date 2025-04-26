package cn.keqiongpan.multisourceproperties;

import java.util.Map;

public class PropertyDescriptor<V> {

    private final String name;
    private final Class<V> valueType;
    private final boolean valuableEmpty;
    private final Map<Object, Object> additionalParameters;
    private final PropertyCoordinate<?>[] orderedCoordinates;

    public PropertyDescriptor(String name,
                              Class<V> valueType,
                              boolean valuableEmpty,
                              PropertyCoordinate<?>... orderedCoordinates) {
        this(name, valueType, valuableEmpty, null, orderedCoordinates);
    }

    public PropertyDescriptor(String name,
                              Class<V> valueType,
                              boolean valuableEmpty,
                              Map<Object, Object> additionalParameters,
                              PropertyCoordinate<?>... orderedCoordinates) {
        this.name = name;
        this.valueType = valueType;
        this.valuableEmpty = valuableEmpty;
        this.additionalParameters = additionalParameters;
        this.orderedCoordinates = orderedCoordinates;
    }

    public String getName() {
        return name;
    }

    public Class<V> getValueType() {
        return valueType;
    }

    public boolean isValuableEmpty() {
        return valuableEmpty;
    }

    public Map<Object, Object> getAdditionalParameters() {
        return additionalParameters;
    }

    public PropertyCoordinate<?>[] getOrderedCoordinates() {
        return orderedCoordinates;
    }

}
