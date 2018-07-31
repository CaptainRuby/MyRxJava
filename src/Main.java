public class Main {

    public static void main(String[] args) {
        AccountHelper.getAndSaveDataJob("123").start(new Callback<Integer>() {
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
}
