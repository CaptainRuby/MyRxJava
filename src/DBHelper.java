public class DBHelper {

//    interface Callback{
//        void onSuccess(int result);
//        void onError(Throwable e);
//    }

//    public static void insert(String value,Callback callback){
//        try {
//            callback.onSuccess(1);
//        }catch (Throwable e){
//            callback.onError(e);
//        }
//    }

//    public static void insert(String value,Callback<Integer> callback){
//        try {
//            callback.onSuccess(1);
//        }catch (Throwable e){
//            callback.onError(e);
//        }
//    }

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
