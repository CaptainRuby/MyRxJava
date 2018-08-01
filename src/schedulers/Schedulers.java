package schedulers;

public class Schedulers {

    private static class Singleton {
        public static Schedulers instance = new Schedulers();
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
        }
        return getInstance().childThreadScheduler;
    }
}
