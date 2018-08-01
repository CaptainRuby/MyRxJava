package example;

public interface Callback<T> {
    void onSuccess(T t);

    void onError(Throwable e);
}
