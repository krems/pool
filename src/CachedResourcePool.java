import net.jcip.annotations.GuardedBy;

import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class CachedResourcePool<T> implements ResourcePool<T> {
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private final long keepAliveTime;
    private final Semaphore allowedAllocations;
    private final Lock heapsLock = new ReentrantLock();
    @GuardedBy("heapsLock")
    private final ResourceSource<T> source;
    @GuardedBy("heapsLock")
    private final PriorityQueue<ResourceEntry<T>> resourceFreeHeap;
    @GuardedBy("heapsLock")
    private final PriorityQueue<ResourceEntry<T>> resourceCleanUpHeap;

    public CachedResourcePool(ResourceSource<T> source, int size, long keepAliveTime, TimeUnit unit) {
        this.source = source;
        this.allowedAllocations = new Semaphore(size);
        this.resourceFreeHeap = initYoungFirstHeap(size);
        this.resourceCleanUpHeap = initOldFirstHeap(resourceFreeHeap);

        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.cleaner.schedule(this::cleanUpStaleResources, this.keepAliveTime, TimeUnit.NANOSECONDS);
    }

    private PriorityQueue<ResourceEntry<T>> initYoungFirstHeap(int size) {
        PriorityQueue<ResourceEntry<T>> resources = new PriorityQueue<>(size, ResourceEntry::youngFirstComparator);
        for (int i = 0; i < size; i++) {
            resources.add(new ResourceEntry<>(source.open()));
        }
        return resources;
    }

    private PriorityQueue<ResourceEntry<T>> initOldFirstHeap(PriorityQueue<ResourceEntry<T>> freeResources) {
        PriorityQueue<ResourceEntry<T>> resources =
                new PriorityQueue<>(freeResources.size(), ResourceEntry::oldFirstComparator);
        for (ResourceEntry<T> resource : freeResources) {
            resources.add(resource);
        }
        return resources;
    }

    @Override
    public T get() throws InterruptedException {
        allowedAllocations.acquire();
        return getResource();
    }

    @Override
    public T tryGet() {
        if (allowedAllocations.tryAcquire()) {
            return getResource();
        }
        return null;
    }

    private T getResource() {
        return underHeapsLock(() -> {
            ResourceEntry<T> entry = resourceFreeHeap.poll();
            if (entry == null) {
                return source.open();
            } else {
                boolean inv = resourceCleanUpHeap.remove(entry);
                assert inv;
                return entry.resource;
            }
        });
    }

    @Override
    public void release(T resource) {
        ResourceEntry<T> entry = new ResourceEntry<>(resource);
        underHeapsLock(() -> {
            resourceFreeHeap.add(entry);
            resourceCleanUpHeap.add(entry);
        });
        allowedAllocations.release();
    }

    private void cleanUpStaleResources() {
        long now = System.nanoTime();
        ResourceEntry<T> oldestResource = removeOldResources(now);
        reschedule(oldestResource, now);
    }

    private ResourceEntry<T> removeOldResources(long now) {
        ResourceEntry<T> oldestResource = resourceCleanUpHeap.peek();
        while (oldestResource != null && now >= oldestResource.timestamp + keepAliveTime) {
            underHeapsLock(() -> removeOldestIfNeeded(now));
            oldestResource = resourceCleanUpHeap.peek();
        }
        return oldestResource;
    }

    private void removeOldestIfNeeded(long now) {
        ResourceEntry<T> oldestResource = resourceCleanUpHeap.peek();
        if (oldestResource != null && now >= oldestResource.timestamp + keepAliveTime) {
            resourceFreeHeap.remove(oldestResource);
            resourceCleanUpHeap.remove(oldestResource);
        }
    }

    private void reschedule(ResourceEntry<T> oldestResource, long now) {
        long delay = keepAliveTime;
        if (oldestResource != null) {
            delay = oldestResource.timestamp + keepAliveTime - now;
        }
        this.cleaner.schedule(this::cleanUpStaleResources, delay, TimeUnit.NANOSECONDS);
    }

    private void underHeapsLock(Runnable t) {
        heapsLock.lock();
        try {
            t.run();
        } finally {
            heapsLock.unlock();
        }
    }

    private <V> V underHeapsLock(Callable<V> t) {
        heapsLock.lock();
        try {
            return t.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            heapsLock.unlock();
        }
    }

    private static class ResourceEntry<E> {
        final long timestamp;
        final E resource;

        private ResourceEntry(E resource) {
            this.timestamp = System.nanoTime();
            this.resource = resource;
        }

        private static int youngFirstComparator(ResourceEntry lhs, ResourceEntry rhs) {
            if (lhs.timestamp < rhs.timestamp) {
                return -1;
            }
            if (lhs.timestamp == rhs.timestamp) {
                return 0;
            }
            return 1;
        }

        private static int oldFirstComparator(ResourceEntry lhs, ResourceEntry rhs) {
            if (lhs.timestamp < rhs.timestamp) {
                return 1;
            }
            if (lhs.timestamp == rhs.timestamp) {
                return 0;
            }
            return -1;
        }
    }
}
