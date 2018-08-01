import schedulers.Scheduler;

import java.util.LinkedList;

public class MyObservable<T> {

    private MyAction1<MyObserver<T>> action;

    private MyObservable(MyAction1<MyObserver<T>> action) {
        this.action = action;
    }

    private LinkedList<Scheduler> schedulers = new LinkedList<>();

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
        action.call(myObserver);
    }

    public <R> MyObservable<R> map(Func<T, R> func) {
        final MyObservable<T> upstream = this;
        return new MyObservable<R>(new MyAction1<MyObserver<R>>() {
            @Override
            public void call(MyObserver<R> callback) {
                upstream.subscribe(new MyObserver<T>() {
                    @Override
                    public void onNext(T t) {
                        callback.onNext(func.call(t));
                    }

                    @Override
                    public void onCompleted() {
                        callback.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        callback.onError(e);
                    }
                });
            }
        });
    }

    public MyObservable<T> subscribeOn(Scheduler scheduler) {
        MyObservable<T> upstream = this;
        return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
            @Override
            public void call(MyObserver<T> callback) {
                scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        upstream.subscribe(new MyObserver<T>() {
                            @Override
                            public void onNext(T t) {
                                callback.onNext(t);
                            }

                            @Override
                            public void onCompleted() {
                                callback.onCompleted();
                            }

                            @Override
                            public void onError(Throwable e) {
                                callback.onError(e);
                            }
                        });
                    }
                });
                scheduler.finish();
            }
        });
    }

    public MyObservable<T> observeOn(Scheduler scheduler) {
        MyObservable<T> upstream = this;
        return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
            @Override
            public void call(MyObserver<T> callback) {
                upstream.subscribe(new MyObserver<T>() {
                    @Override
                    public void onNext(T t) {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                callback.onNext(t);
                            }
                        });
                    }

                    @Override
                    public void onCompleted() {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                callback.onCompleted();
                            }
                        });
                        scheduler.finish();
                    }

                    @Override
                    public void onError(Throwable e) {
                        scheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(e);
                            }
                        });
                        scheduler.finish();
                    }
                });
            }
        });

    }
}
