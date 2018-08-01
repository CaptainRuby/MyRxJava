public class MyObservable<T> {

    private MyAction1<MyObserver<T>> action;

    private MyObservable(MyAction1<MyObserver<T>> action) {
        this.action = action;
    }

    public void start(MyObserver<T> myObserver) {
        action.call(myObserver);
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
}
