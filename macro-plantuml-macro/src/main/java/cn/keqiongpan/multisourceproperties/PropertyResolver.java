package cn.keqiongpan.multisourceproperties;

public interface PropertyResolver {
    Object getObject(String key);

    default <V> V get(String key) {
        return (V) getObject(key);
    }
}
