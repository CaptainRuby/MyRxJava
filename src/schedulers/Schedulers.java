package schedulers;

public class Schedulers {

    private static class Singleton {
        private static Schedulers instance = new Schedulers();
    }

    private static Schedulers getInstance() {
        return Singleton.instance;
    }

    private ChildThreadScheduler childThreadScheduler;

    public static Scheduler newThread() {
        return new NewThreadScheduler();
    }

    public static Scheduler childThread() {
        if (getInstance().childThreadScheduler == null) {
            getInstance().childThreadScheduler = new ChildThreadScheduler();
        } else if (getInstance().childThreadScheduler.isFinished()) {
            getInstance().childThreadScheduler = new ChildThreadScheduler();
        }
        return getInstance().childThreadScheduler;
    }
}
