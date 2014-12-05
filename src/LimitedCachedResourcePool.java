import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class LimitedCachedResourcePool<T> implements ResourcePool<T> {
    private final Semaphore allowedAllocations;
    private final int fixedPoolSize;
    private final Lock lock = new ReentrantLock();
    @GuardedBy("lock")
    private final ResourcePool<T> fixedPool;
    @GuardedBy("lock")
    private final ResourcePool<T> cachedPool;
    @GuardedBy("lock")
    private int freeSpaceInFixedPool;

    public LimitedCachedResourcePool(ResourceSource<T> source, int min, int max, long keepAlive, TimeUnit unit) {
        fixedPoolSize = min;
        freeSpaceInFixedPool = min;
        allowedAllocations = new Semaphore(max);
        fixedPool = new FixedResourcePool<>(source, min);
        cachedPool = new CachedResourcePool<>(source, max - min, keepAlive, unit);
    }

    @Override
    public T get() throws InterruptedException {
        allowedAllocations.acquire();
        lock.lock();
        try {
            T resource = fixedPool.tryGet();
            if (resource == null) {
                return cachedPool.tryGet();
            } else {
                freeSpaceInFixedPool--;
            }
            return resource;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T tryGet() {
        allowedAllocations.tryAcquire();
        lock.lock();
        try {
            T resource = fixedPool.tryGet();
            if (resource == null) {
                return cachedPool.tryGet();
            } else {
                freeSpaceInFixedPool--;
            }
            return resource;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release(T resource) {
        lock.lock();
        try {
            if (freeSpaceInFixedPool < fixedPoolSize) {
                fixedPool.release(resource);
                freeSpaceInFixedPool++;
            } else {
                cachedPool.release(resource);
            }
        } finally {
            lock.unlock();
        }
        allowedAllocations.release();
    }
}
