import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FixedResourcePool<T> implements ResourcePool<T> {
    private final ResourceSource<T> source;
    private final BlockingQueue<T> freeResources;
    private final AtomicInteger allocatedResources = new AtomicInteger();

    public FixedResourcePool(ResourceSource<T> source, int size) {
        this.source = source;
        this.freeResources = init(size);
    }

    private BlockingQueue<T> init(int size) {
        ArrayBlockingQueue<T> freeResources = new ArrayBlockingQueue<>(size);
        while (allocatedResources.getAndIncrement() < size) {
            boolean inv = freeResources.offer(source.open());
            assert inv;
        }
        return freeResources;
    }

    @Override
    public T get() throws InterruptedException {
        return freeResources.take();
    }

    @Override
    public T tryGet() {
        return freeResources.poll();
    }

    @Override
    public void release(T resource) {
        boolean inv = freeResources.offer(resource);
        assert inv;
    }

    public void shutdown() throws InterruptedException {
        while (allocatedResources.getAndDecrement() > 0) {
            T resource = freeResources.take();
            source.close(resource);
        }
    }
}
