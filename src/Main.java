import java.util.ArrayList;
import java.util.List;

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
                myObserver.onNext(1);
                myObserver.onNext(2);
                myObserver.onNext(3);
                myObserver.onCompleted();
            }
        }).start(new MyObserver<Integer>() {
            @Override
            public void onNext(Integer integer) {
                System.out.println("onNext:" + integer);
            }

            @Override
            public void onCompleted() {
                System.out.println("onCompleted");
            }

            @Override
            public void onError(Throwable e) {

            }
        });

        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        MyObservable.just(list)
                .start(new MyObserver<Integer>() {
                    @Override
                    public void onNext(Integer integer) {
                        System.out.println("onNext:" + integer);
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });
    }
}
