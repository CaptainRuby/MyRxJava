package schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ChildThreadScheduler extends Scheduler {

    private ExecutorService executor;

    public ChildThreadScheduler() {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "MyRxJava-ChildThread");
            }
        };
        executor = Executors.newSingleThreadExecutor(threadFactory);
    }

    @Override
    public void schedule(Runnable runnable) {
        executor.execute(runnable);
    }

    @Override
    public void finish() {
        executor.shutdown();
    }

}
