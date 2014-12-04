
public interface ResourcePool<T> {
    T get() throws InterruptedException;
    T tryGet();
    void release(T resource);
}
