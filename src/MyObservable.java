import example.AsyncJob;
import schedulers.Scheduler;

import java.util.*;

public class MyObservable<T> {

    private MyAction1<MyObserver<T>> action;

    private Set<Scheduler> schedulers;

    private MyObservable(MyAction1<MyObserver<T>> action) {
        this.action = action;
        this.schedulers = new HashSet<>();
    }

    private MyObservable(MyAction1<MyObserver<T>> action, Set<Scheduler> schedulers) {
        this.action = action;
        this.schedulers = schedulers;
    }

    public static <T> MyObservable<T> create(MyAction1<MyObserver<T>> action) {
        return new MyObservable<T>(action);
    }

    public static <T> MyObservable<T> just(Iterable<T> iterable) {
        return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
            @Override
            public void call(MyObserver<T> myObserver) {
                for (T anIterable : iterable) {
                    myObserver.onNext(anIterable);
                }
                myObserver.onCompleted();
            }
        });
    }

    public void subscribe(MyObserver<T> myObserver) {
        action.call(new MyObserver<T>() {
            @Override
            public void onNext(T t) {
                myObserver.onNext(t);
            }

            @Override
            public void onCompleted() {
                myObserver.onCompleted();
                for (Scheduler scheduler : schedulers) {
                    scheduler.finish();
                }
            }

            @Override
            public void onError(Throwable e) {
                myObserver.onError(e);
                for (Scheduler scheduler : schedulers) {
                    scheduler.finish();
                }
            }
        });
    }


    public <R> MyObservable<R> map(Func<T, R> func) {
        final MyObservable<T> upstream = this;
        return new MyObservable<R>(new MyAction1<MyObserver<R>>() {
            @Override
            public void call(MyObserver<R> myObserver) {
                upstream.subscribe(new MyObserver<T>() {
                    @Override
                    public void onNext(T t) {
                        myObserver.onNext(func.call(t));
                    }

                    @Override
                    public void onCompleted() {
                        myObserver.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        myObserver.onError(e);
                    }
                });
            }
        }, schedulers);
    }

    public MyObservable<T> subscribeOn(Scheduler scheduler) {
        schedulers.add(scheduler);
        MyObservable<T> upstream = this;
        return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
            @Override
            public void call(MyObserver<T> myObserver) {
                scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        upstream.subscribe(new MyObserver<T>() {
                            @Override
                            public void onNext(T t) {
                                myObserver.onNext(t);
                            }

                            @Override
                            public void onCompleted() {
                                myObserver.onCompleted();
                            }

                            @Override
                            public void onError(Throwable e) {
                                myObserver.onError(e);
                            }
                        });
                    }
                });
            }
        }, schedulers);
    }

    public MyObservable<T> observeOn(Scheduler scheduler) {
        schedulers.add(scheduler);
        MyObservable<T> upstream = this;
        return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
            @Override
            public void call(MyObserver<T> myObserver) {
                upstream.subscribe(new MyObserver<T>() {
                    @Override
                    public void onNext(T t) {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                myObserver.onNext(t);
                            }
                        });
                    }

                    @Override
                    public void onCompleted() {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                myObserver.onCompleted();
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                myObserver.onError(e);
                            }
                        });
                    }
                });
            }
        }, schedulers);
    }


}
