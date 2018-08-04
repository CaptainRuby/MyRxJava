package schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ChildThreadScheduler extends Scheduler {

    private ExecutorService executor;

    private boolean isFinished = false;

    public ChildThreadScheduler() {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "MyRxJava-ChildThread-" + System.currentTimeMillis());
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
        if (!isFinished) {
            executor.shutdown();
            isFinished = true;
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

}
