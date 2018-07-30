public abstract class AsyncJob<T> {
    public abstract void start(Callback<T> callback);

    public <R>AsyncJob<R> map(Func<T,R> func){
        AsyncJob<T> upstream = this;
        return new AsyncJob<R>() {
            @Override
            public void start(Callback<R> callback) {
                upstream.start(new Callback<T>() {
                    @Override
                    public void onSuccess(T t) {
                        R r = func.call(t);
                        callback.onSuccess(r);
                    }

                    @Override
                    public void onError(Throwable e) {
                        callback.onError(e);
                    }
                });
            }
        };
    }
}
