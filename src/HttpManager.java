import java.util.ArrayList;
import java.util.List;

public class HttpManager {

//    interface Callback{
//        void onResponse(List<String> data);
//        void onError(Throwable e);
//    }

//    public static List<String> getData(String param){
//        return new ArrayList<>();
//    }

//    public static void getData(String param,Callback callback){
//        try {
//            callback.onResponse(new ArrayList<>());
//        }catch (Throwable e){
//            callback.onError(e);
//        }
//    }

//    public static void getData(String param, Callback<List<String>> callback){
//        try {
//            callback.onSuccess(new ArrayList<>());
//        }catch (Throwable e){
//            callback.onError(e);
//        }
//    }

    public static AsyncJob<List<String>> getData(String param){
        return new AsyncJob<List<String>>() {
            @Override
            public void start(Callback<List<String>> callback) {
                try {
                    callback.onSuccess(new ArrayList<>());
                }catch (Throwable e){
                    callback.onError(e);
                }
            }
        };

    }
}
