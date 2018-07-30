import java.util.List;

public class Main {
//    public static void main(String[] args) {
//        List<String> data = HttpManager.getData();
//        String value = process(data);
//        DBHelper.insert(value);
//    }

//    public static void main(String[] args) {
//        HttpManager.getData("123", new HttpManager.Callback() {
//            @Override
//            public void onResponse(List<String> data) {
//                String value = process(data);
//                DBHelper.insert(value, new DBHelper.Callback() {
//                    @Override
//                    public void onSuccess(int result) {
//                        System.out.println("成功");
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//                        System.out.println("失败");
//                    }
//                });
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                System.out.println("失败");
//            }
//        });
//    }

//    public static void main(String[] args) {
//        HttpManager.getData("123", new Callback<List<String>>() {
//            @Override
//            public void onSuccess(List<String> data) {
//                String value = process(data);
//                DBHelper.insert(value, new Callback<Integer>() {
//                    @Override
//                    public void onSuccess(Integer result) {
//                        System.out.println("成功");
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//                        System.out.println("失败");
//                    }
//                });
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                System.out.println("失败");
//            }
//        });
//    }

//    public static void main(String[] args) {
//        saveData("123").start(new Callback<Integer>() {
//            @Override
//            public void onSuccess(Integer integer) {
//                System.out.println("成功");
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                System.out.println("失败");
//            }
//        });
//    }

    public static void main(String[] args) {
        AsyncJob<List<String>> getDataJob = HttpManager.getData("123");
//        AsyncJob<String> processJob = new AsyncJob<String>() {
//            @Override
//            public void start(Callback<String> callback) {
//                getDataJob.start(new Callback<List<String>>() {
//                    @Override
//                    public void onSuccess(List<String> data) {
//                        String value = process(data);
//                        callback.onSuccess(value);
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//                        callback.onError(e);
//                    }
//                });
//            }
//        };
        AsyncJob<String> processJob = getDataJob.map(new Func<List<String>, String>() {
            @Override
            public String call(List<String> data) {
                return process(data);
            }
        });
        AsyncJob<Integer> saveDataJob = new AsyncJob<Integer>() {
            @Override
            public void start(Callback<Integer> callback) {
                processJob.start(new Callback<String>() {
                    @Override
                    public void onSuccess(String s) {
                        AsyncJob<Integer> insertJob = DBHelper.insert(s);
                        insertJob.start(new Callback<Integer>() {
                            @Override
                            public void onSuccess(Integer integer) {
                                callback.onSuccess(integer);
                            }

                            @Override
                            public void onError(Throwable e) {
                                callback.onError(e);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });
            }
        };
        saveDataJob.start(new Callback<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                System.out.println("成功");
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("失败");
            }
        });
    }

    private static AsyncJob<Integer> saveData(String param){
        return new AsyncJob<Integer>() {
            @Override
            public void start(Callback<Integer> callback) {
                HttpManager.getData(param).start(new Callback<List<String>>() {
                    @Override
                    public void onSuccess(List<String> data) {
                        String value = process(data);
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

    private static String process(List<String> data){
        return data.get(0);
    }
}
