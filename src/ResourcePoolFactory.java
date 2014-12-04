import java.util.concurrent.TimeUnit;

public class ResourcePoolFactory {
    public <T> ResourcePool<T> newResourcePool(ResourceSource<T> resourceSource, int size) {
        return new FixedResourcePool<>(resourceSource, size);
    }

    public <T> ResourcePool<T> newCachedResourcePool(ResourceSource<T> resourceSource, int minSize, int maxSize,
                                                     long keepAliveTime, TimeUnit unit) {
        if (minSize == 0) {
            return new CachedResourcePool<>(resourceSource, maxSize, keepAliveTime, unit);
        } else {
            return new LimitedCachedResourcePool<>(resourceSource, minSize, maxSize, keepAliveTime, unit);
        }
    }

    public <T> ResourcePool<T> newLazyResourcePool(ResourceSource<T> resourceSource, int size) {
        return null;
    }

    public <T> ResourcePool<T> newLazyCachedResourcePool(ResourceSource<T> resourceSource, int minSize, int maxSize,
                                                         long keepAliveTime, TimeUnit unit) {
        return null;
    }
}
