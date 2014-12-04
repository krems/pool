import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class LimitedCachedResourcePool<T> implements ResourcePool<T> {
    private final Semaphore allowedAllocations;
    private final ResourcePool<T> fixedPool;
    private final ResourcePool<T> cachedPool;

    public LimitedCachedResourcePool(ResourceSource<T> source, int min, int max, long keepAlive, TimeUnit unit) {
        allowedAllocations = new Semaphore(max);
        fixedPool = new FixedResourcePool<>(source, min);
        cachedPool = new CachedResourcePool<>(source, max - min, keepAlive, unit);
    }

    @Override
    public T get() throws InterruptedException {
        allowedAllocations.acquire();
        T resource = fixedPool.tryGet();
        if (resource == null) {
            return cachedPool.tryGet();
        }
        return resource;
    }

    @Override
    public T tryGet() {
        allowedAllocations.tryAcquire();
        T resource = fixedPool.tryGet();
        if (resource == null) {
            return cachedPool.tryGet();
        }
        return resource;
    }

    @Override
    public void release(T resource) {

    }
}
