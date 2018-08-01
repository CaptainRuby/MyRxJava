package example;

import java.util.ArrayList;
import java.util.List;

public class HttpManager {

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
