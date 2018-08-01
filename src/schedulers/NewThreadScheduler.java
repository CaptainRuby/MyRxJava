package schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class NewThreadScheduler extends Scheduler {

    private ExecutorService executor;

    public NewThreadScheduler() {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "MyRxJava-" + System.currentTimeMillis());
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
