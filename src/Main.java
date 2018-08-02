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

//        List<Integer> list = new ArrayList<>();
//        list.add(1);
//        list.add(2);
//        list.add(3);
//        MyObservable.just(list)
//                .subscribe(new MyObserver<Integer>() {
//                    @Override
//                    public void onNext(Integer integer) {
//                        System.out.println("onNext:" + integer);
//                    }
//
//                    @Override
//                    public void onCompleted() {
//                        System.out.println("onCompleted");
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//
//                    }
//                });

//        MyObservable.create(new MyAction1<MyObserver<Integer>>() {
//            @Override
//            public void call(MyObserver<Integer> myObserver) {
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        myObserver.onNext(1);
//                        myObserver.onNext(2);
//                        myObserver.onNext(3);
//                        myObserver.onCompleted();
//                    }
//                }).start();
//            }
//        })
//                .subscribeOn()
//                .observeOn(Schedulers.childThread())
//                .map(new Func<Integer, String>() {
//                    @Override
//                    public String call(Integer integer) {
//                        System.out.println("map:"+Thread.currentThread().getName());
//                        return String.valueOf(integer);
//                    }
//                })
//                .observeOn(Schedulers.newThread())
//                .subscribe(new MyObserver<String>() {
//                    @Override
//                    public void onNext(String string) {
//                        System.out.println("onNext:" + Thread.currentThread().getName());
//                    }
//
//                    @Override
//                    public void onCompleted() {
//                        System.out.println("onCompleted");
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//
//                    }
//                });

        MyObservable.create(new MyAction1<MyObserver<Integer>>() {
            @Override
            public void call(MyObserver<Integer> myObserver) {
                System.out.println("call:" + Thread.currentThread().getName());
                myObserver.onNext(1);
                myObserver.onNext(2);
                myObserver.onNext(3);
                myObserver.onCompleted();
            }
        })
                .subscribeOn()
                .subscribe(new MyObserver<Integer>() {
                    @Override
                    public void onNext(Integer integer) {
                        System.out.println("onNext:" + Thread.currentThread().getName());
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("onCompleted" + Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });

    }
}
