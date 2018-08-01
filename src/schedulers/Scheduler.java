package schedulers;

public abstract class Scheduler {

    public abstract void schedule(Runnable runnable);

    public abstract void finish();

}
