// "Remove type arguments" "true"
abstract class SomeClass<K, T> implements Some<K, T> {
    public abstract void doSomething(K key, Node<caret><K, T> root);
}
class Node {}
interface Some<II, OO>{}

