import schedulers.Schedulers;

public class Main {

    public static void main(String[] args) {
        //example
//        AccountHelper.getAndSaveDataJob("123").start(new Callback<Integer>() {
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

        MyObservable.create(new MyAction1<MyObserver<Integer>>() {
            @Override
            public void call(MyObserver<Integer> myObserver) {
                System.out.println("call:" + Thread.currentThread().getName());
                myObserver.onNext(1);
                myObserver.onCompleted();
            }
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(Schedulers.childThread())
                .map(new Func<Integer, String>() {
                    @Override
                    public String call(Integer integer) {
                        System.out.println("map:" + Thread.currentThread().getName());
                        return String.valueOf(integer);
                    }
                })
                .observeOn(Schedulers.newThread())
                .map(new Func<String, Integer>() {
                    @Override
                    public Integer call(String string) {
                        System.out.println("map:" + Thread.currentThread().getName());
                        return Integer.parseInt(string);
                    }
                })
                .observeOn(Schedulers.childThread())
                .mySubscribe(new MyObserver<Integer>() {
                    @Override
                    public void onNext(Integer string) {
                        System.out.println("onNext:" + Thread.currentThread().getName());
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("onCompleted:" + Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });

    }
}
