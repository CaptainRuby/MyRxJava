public interface MyObserver<T> {
    void onNext(T t);

    void onCompleted();

    void onError(Throwable e);
}
