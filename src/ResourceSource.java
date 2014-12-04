
public interface ResourceSource<T> {
    T open();
    void close(T resource);
}
