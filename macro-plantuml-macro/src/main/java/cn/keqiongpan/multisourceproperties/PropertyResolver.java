package cn.keqiongpan.multisourceproperties;

public interface PropertyResolver<K> {
    <V> V get(K key);
}
