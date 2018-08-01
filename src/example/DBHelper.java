package example;

public class DBHelper {

    public static AsyncJob<Integer> insert(String value){
        return new AsyncJob<Integer>() {
            @Override
            public void start(Callback<Integer> callback) {
                try {
                    callback.onSuccess(1);
                }catch (Throwable e){
                    callback.onError(e);
                }
            }
        };
    }
}
