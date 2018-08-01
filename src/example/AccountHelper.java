package example;

import java.util.List;

public class AccountHelper {
    private static String process(List<String> data){
        return data.get(0);
    }

    public static AsyncJob<String> processNetData(String param){
        AsyncJob<List<String>> getDataJob = HttpManager.getData(param);
        AsyncJob<String> processNetData = getDataJob.map(new Func<List<String>, String>() {
            @Override
            public String call(List<String> data) {
                return process(data);
            }
        });
        return processNetData;
    }

    public static AsyncJob<Integer> getAndSaveDataJob(String param){
        return new AsyncJob<Integer>() {
            @Override
            public void start(Callback<Integer> callback) {
                processNetData(param).start(new Callback<String>() {
                    @Override
                    public void onSuccess(String value) {
                        DBHelper.insert(value).start(new Callback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                callback.onSuccess(result);
                            }

                            @Override
                            public void onError(Throwable e) {
                                callback.onError(e);
                            }
                        });
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
