# 自己动手造一个RxJava
---
## **序言**

最近在公司做一个数据同步需求的时候，碰到了这样的场景。

> 客户端从服务器拉取用户账号数据，拿到数据后进行处理并对数据库进行更新操作，最后显示到UI上。

我们知道网络操作一般是异步处理的，在回调中拿到数据并进行处理。为了防止主线程阻塞，数据库的操作往往也会放到子线程中去执行，所以同样需要一个回调来判断数据库操作是否成功，最后由于安卓不允许在子线程中更新 UI，我们还需要将更新 UI 的操作切换到主线程来做。

这样的一个流程涉及到多级事件的回调和线程切换，熟悉响应式编程的人可能会第一时间想到用 RxJava 来实现，再搭配 Retrofit 实现网络部分的操作，简直分分钟搞定。但是考虑到公司的项目所依赖的第三方库并不多，并且这种场景也不是很多，于是最终考虑自己实现一个简易的 RxJava，来完成上述需求。

在实现自己的 RxJava 之前，肯定要提前弄懂 RxJava 的原理。**我们不能只会用轮子而不知道怎么造轮子**，拆轮子的一个高效的方法就是理解**它为什么会被造出来。**

**项目地址：**
[https://github.com/CaptainRuby/MyRxJava][1]

## **目录**
- [自己动手造一个 RxJava](#自己动手造一个-rxjava)  
- [序言](#序言)
- [目录](#目录)  
  - [1.理解临时任务对象](#1理解临时任务对象) 
     - [场景](#场景) 
     - [同步](#同步) 
     - [异步](#异步) 
     - [泛型回调](#泛型回调) 
     - [临时任务对象](#临时任务对象) 
     - [组装任务](#组装任务) 
     - [改装流水线](#改装流水线) 
     - [简单的映射](#简单的映射) 
  - [2.事件的发送与接收](#2事件的发送与接收)
     - [RxJava 的发送和接收原理](#rxjava-的发送和接收原理)
     - [事件发送](#事件发送)
     - [接收](#接收)
     - [操作符 just 的实现](#操作符-just-的实现) 
  - [3.映射](#3映射) 
  - [4.线程调度](#4线程调度) 
     - [subscribeOn 的实现](#subscribeOn-的实现)
     - [observeOn 的实现](#observeOn-的实现)
     - [利用线程池进行调度](#利用线程池进行调度)
     - [关闭线程池](#关闭线程池)
 - [结语](#结语) 

## **1.理解临时任务对象**

首先我们要理解一个概念，叫临时任务对象，它是 RxJava 的核心部分。我们将序言中提到的场景简化一下。

### **场景**

>  1. 从服务器拉取用户数据
>  2. 处理数据
>  3. 插入数据库

我们先看同步情况下的操作。

### **同步**

创建一个网络请求的管理类，它实现了一个名为 ```getData(String param)``` 的方法，用于从服务器拉取数据，它返回一个 String 类型的列表，其中 param 是需要发送给服务器的参数。

```
public class HttpManager {

    public static List<String> getData(String param) {
        /*网络操作*/
        return new ArrayList<>();//简化操作，模拟服务器返回的结果
    }
}
```

创建一个数据库的操作类，它实现了一个名为 ```insert(String value)``` 方法，将用户传入的参数 value 插入数据库。

```
public class DBHelper {

    public static void insert(String value){
        /*数据库操作*/
    }
}
```

创建一个用户账号的帮助类，实现了一个 ```process(List<String> data)``` 方法，将网络拉取的数据列表进行处理，返回一个 String 字符串，供数据库插入。

```
public class AccountHelper {

    private static String process(List<String> data){
        return data.get(0);
    }
}
```

整个过程的调用操作如下，看起来十分简单，我们再来看下异步情况下的代码。

```
//Main.java
public static void main(String[] args) {
    List<String> data = HttpManager.getData("123");
    String value = AccountHelper.process(data);
    DBHelper.insert(value);
}
```

### **异步**

修改 HttpManager 类，新增一个回调接口，包含响应和错误的回调，并在 ```getData()``` 方法中传入 Callback 参数。

```
public class HttpManager {

    interface Callback{
        void onResponse(List<String> data);
        void onError(Throwable e);
    }

    public static void getData(String param,Callback callback){
        try {
            callback.onResponse(new ArrayList<>());
        }catch (Throwable e){
            callback.onError(e);
        }
    }
}
```

修改 DBHelper 类，新增一个回调接口，包含成功和错误的回调，并在 ```insert()``` 方法中传入 Callback 参数。

```
public class DBHelper {

    interface Callback{
        void onSuccess(int result);
        void onError(Throwable e);
    }

    public static void insert(String value,Callback callback){
        try {
            callback.onSuccess(1);//返回1表示插入成功
        }catch (Throwable e){
            callback.onError(e);
        }
    }
}
```

于是我们的调用是这样的。

```
//Main.java
public static void main(String[] args) {
    HttpManager.getData("123", new HttpManager.Callback() {
        @Override
        public void onResponse(List<String> data) {
            String value = AccountHelper.process(data);//数据处理
            DBHelper.insert(value, new DBHelper.Callback() {
                @Override
                public void onSuccess(int result) {
                    System.out.println("成功");
                }
                @Override
                public void onError(Throwable e) {
                    System.out.println("失败");
                }
            });
        }

        @Override
        public void onError(Throwable e) {
            System.out.println("失败");
        }
    });
}
```

我们发现从同步到异步，代码突然间膨胀了好几行，多了很多大小括号和缩进，一眼看去眼花缭乱，虽然现在只有两个回调嵌套，看起来还不算太糟糕，但是假设再多一两个嵌套，可能阅读起来就不是那么心情愉快了。我们应该思考一下有什么办法可以对其简化。

### **泛型回调**

观察上述代码，我们在修改 HttpManager 和 DBHelper 类的时候，分别定义了两个不同的 Callback 接口，事实上它们之间的差异并不大，只有命名和参数类型不一样，基本都是一个结果和错误的回调，因此我们可以将它们抽象成一个泛型的接口。

```
public interface Callback<T> {
    void onSuccess(T t);
    void onError(Throwable e);
}
```

这样我们的代码就变成下面这个样子。

```
public class HttpManager {
    //移除了内部接口，Callback泛型声明为List<String>
    public static void getData(String param, Callback<List<String>> callback){
        try {
            callback.onSuccess(new ArrayList<>());
        }catch (Throwable e){
            callback.onError(e);
        }
    }
}
```

```
public class DBHelper {
    //移除了内部接口，Callback泛型声明为Integer
    public static void insert(String value,Callback<Integer> callback){
        try {
            callback.onSuccess(1);
        }catch (Throwable e){
            callback.onError(e);
        }
    }
}
```

```
//Main.java
public static void main(String[] args) {
    HttpManager.getData("123", new Callback<List<String>>() {
        @Override
        public void onSuccess(List<String> data) {
            String value = AccountHelper.process(data);
            DBHelper.insert(value, new Callback<Integer>() {
                @Override
                public void onSuccess(Integer result) {
                    System.out.println("成功");
                }
                
                @Override
                public void onError(Throwable e) {
                    System.out.println("失败");
                }
            });
        }
        
        @Override
        public void onError(Throwable e) {
            System.out.println("失败");
        }
    });
}
```

现在，我们把回调接口统一改成泛型接口了，减少了多余接口的声明，但是咋一看我们的主函数似乎没什么变化，当然这不是我们最终的优化结果。

### **临时任务对象**

上一步我们观察到接口的方法和参数存在共性可以抽象出相同的模式，事实上我们依旧可以利用这个思路将这些回调做进一步的封装。

可以看到，```getData()``` 和 ```insert()``` 方法的共性在于**它们都需要接收一个回调参数**。

利用这个共性，我们可以使用抽象类的方式将回调参数封装到类内部的一个抽象方法中，我们先来看这种写法有什么巧妙之处。

创建一个名为 AsyncJob 的泛型抽象类，定义一个抽象的 ```start(Callback<T> callback)``` 方法，接收一个 Callback 参数。

```
public abstract class AsyncJob<T> {
    public abstract void start(Callback<T> callback);
}
```

正如我们前面定义的 Callback 接口一样，作为一个抽象出来的模板，它看起来非常的简单。接下来我们再看怎么使用它。

我们将 ```HttpManager.getData()``` 方法改造成一下。

```
public class HttpManager {
    
    public static AsyncJob<List<String>> getDataJob(String param){
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
```

接收一个 param 请求参数，返回一个 AsyncJob&lt;List&lt;String&gt;&gt; 类型的对象，由于 AsyncJob 是抽象类，所以在实例化的时候必须实现它的 ```start()``` 方法，注意看，这个方法包含了我们对数据的处理，并将结果通过传进来的 **callback** 回调出去。

这是什么意思呢？

这就相当于我们把 ```getData()``` 的这一系列操作封装成了一个**临时的任务对象**，它在内部声明了对数据的处理方式，当你调用 ```start()``` 方法时，它便会执行声明的步骤，随后回调执行的结果，而这个结果将由你传进去的 callback 接收。

为什么要这么做呢？

因为按照直观的理解，原本我们希望数据的处理步骤是这样子的。

![此处输入图片的描述][2]

数据像原料一样在流水线上加工，每经过一台机器（方法），就变成新的半成品，直到最后一台机器，变成我们所需要的产品。

但是前面我们所编写的代码表现出来的却不是这个样子，它们呈现的是一种多级嵌套的形式。我们现在要做的就是将它们拆分出来，所以我们抽象出**任务对象**的概念。每个任务对象就像流水线上机器，包含了对半成品（上一步的结果）的加工方式。

我们按照同样的方式对 ```DBHelper.insert()``` 进行改造，返回一个新的临时任务对象 insertJob 。

```
public class DBHelper {

    public static AsyncJob<Integer> insertJob(String value){
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
```

好了，现在我们已经有了 ```getDataJob()``` 和 ```insertJob()``` 这两台机器（任务）了，我们试着把他们组装一下。

### **组装任务**

在AccountHelper中定义一个 ```getAndSaveDataJob(String param)``` 方法，返回一个泛型为 Integer 的临时任务对象。

```
//AccountHelper.java
private static AsyncJob<Integer> getAndSaveDataJob(String param){
    return new AsyncJob<Integer>() {
        @Override
        public void start(Callback<Integer> callback) {
            AsyncJob<List<String>> getDataJob = HttpManager.getDataJob(param);
            getDataJob.start(new Callback<List<String>>() {
                @Override
                public void onSuccess(List<String> data) {
                    String value = process(data);
                    AsyncJob<Integer> insertJob = DBHelper.insertJob(value);
                    insertJob.start(new Callback<Integer>() {
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
```

我们来看 ```start()``` 方法内部具体做了什么，生成一个 getDataJob 的临时任务，调用 ```getDataJob.start()``` 启动任务后，在 ```onSuccess()``` 回调中调用 ```process(data)``` 将列表转化为字符串，再生成一个 insertJob 的临时任务，最后调用 ```insertJob.start()``` 方法启动任务，将结果回传出去。

咦？这不是和我们前面的逻辑一样吗？还是在做回调嵌套的事情。不要急，我们先看下main方法里面的调用。

```
//Main.java
public static void main(String[] args) {
    AsyncJob<Integer> getAndSaveDataJob = AccountHelper.getAndSaveDataJob("123");
    getAndSaveDataJob.start(new Callback<Integer>() {
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
```

相比之前的main函数，我们这里只需要生成一个 getAndSaveDataJob 任务，启动任务并传入回调，简化了一系列操作。

整理下，我们构造了 getDataJob 和 insertJob 这两个任务，并将它们组装成一个新的任务 getAndSaveDataJob ，此时我们的作业流水线图如下。

![此处输入图片的描述][3]

和我们预想的并不一样，没关系，因为到这一步，我们已经初步明确了：

>  1. 任务对象的概念
>  2. 如何组装两个任务

我们接下来要利用这两点对流水线进行重组改装。

### **改装流水线**

为了贴近整个流程顺序执行的思路，我们考虑将 getDataJob 和 ```process()``` 方法结合起来，组装一个 processNetDataJob，用来表示一个**从网络获取数据并处理**的任务。

```
//AccountHelper.java
public static AsyncJob<String> processNetDataJob(String param){
    return new AsyncJob<String>() {
        @Override
        public void start(Callback<String> callback) {
            AsyncJob<List<String>> getDataJob = HttpManager.getDataJob(param);
            getDataJob.start(new Callback<List<String>>() {
                @Override
                public void onSuccess(List<String> data) {
                    String value = process(data);
                    callback.onSuccess(value);
                }

                @Override
                public void onError(Throwable e) {
                    callback.onError(e);
                }
            });
        }
    };
}
```

再将这个任务与 ```DBHelper.insertJob()``` 组装一下，得到新的 ```getAndSaveDataJob()``` 。

```
//AccountHelper.java
private static AsyncJob<Integer> getAndSaveDataJob(String param){
    return new AsyncJob<Integer>() {
        @Override
        public void start(Callback<Integer> callback) {
            AsyncJob<String> processNetDataJob = processNetDataJob(param)；
            processNetDataJob.start(new Callback<String>() {
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
```

main函数保持不变。

```
//Main.java
public static void main(String[] args) {
    AsyncJob<Integer> getAndSaveDataJob = AccountHelper.getAndSaveDataJob("123");
    getAndSaveDataJob.start(new Callback<Integer>() {
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
```

但是流水线的形式已经发生了改变。

![此处输入图片的描述][4]

仔细看，我们从最开始的 getDataJob 层层往外套，到最后将其包装成 getAndSaveDataJob，其中每一层都由前后两个任务组装成一个新的任务。

现在你发现了吗？这种临时任务对象的优势就在于，**它可以将多个流程的嵌套，拆解成两两嵌套的方式，任由你组装。**

![此处输入图片的描述][5]

### **简单的映射**

回到前面 ```processNetDataJob()``` 这个方法。

```
//AccountHelper.java
public static AsyncJob<String> processNetDataJob(String param){
    return new AsyncJob<String>() {
        @Override
        public void start(Callback<String> callback) {
            AsyncJob<List<String>> getDataJob = HttpManager.getDataJob(param);
            getDataJob.start(new Callback<List<String>>() {
                @Override
                public void onSuccess(List<String> data) {
                    String value = process(data);
                    callback.onSuccess(value);
                }
                @Override
                public void onError(Throwable e) {
                    callback.onError(e);
                }
            });
        }
    };
}
```

我们发现它只有下面这一句核心代码，其它都是模板代码。

```
String value = process(data);
```

这个方法的作用是返回了一个泛型为 String 的任务对象，它其实是由 getDataJob 演变过来的，而 getDataJob 是一个泛型为 List&lt;String&gt; 的任务对象，即它把一个 AsyncJob&lt;List&lt;String&gt;&gt; 的对象转变为一个 AsyncJob&lt;String&gt; 对象。 

事实上，我们可以在外部为其定义一个接口，来帮助它实现这种转变。

接口定义如下，声明一个名为 Func 的接口，接收两个泛型参数，接口中包含一个名为 call 的方法，其中 T 为传入的方法参数类型，R 为该方法返回的类型。

```
public interface Func<T,R> {
    R call(T t);
}
```

紧接着，我们在 AsyncJob 类中添加一个 map 方法，主要作用就是实现上述的转化。

```
//AsyncJob.java
public <R>AsyncJob<R> map(Func<T,R> func){
    AsyncJob<T> upstream = this;
    return new AsyncJob<R>() {
        @Override
        public void start(Callback<R> callback) {
            upstream.start(new Callback<T>() {
                @Override
                public void onSuccess(T t) {
                    R r = func.call(t);
                    callback.onSuccess(r);
                }

                @Override
                public void onError(Throwable e) {
                    callback.onError(e);
                }
            });
        }
    };
}
```

在这个方法中，我们传入了一个 Func 接口的实现，新建一个引用 upsteam 指向当前实例，并返回一个新的任务对象，在 upsteam 的 ```onSuccess()``` 回调中，调用 ```func.call()``` 方法，传入回调结果 t，得到 R 类型的值 r，再将该值通过新建任务对象的 ```onSuccess()``` 方法回调出去。

现在我们的 ```processNetDataJob()``` 方法就变成这样了。

```
//AccountHelper.java
public static AsyncJob<String> processNetDataJob(String param){
    AsyncJob<List<String>> getDataJob = HttpManager.getDataJob(param);
    AsyncJob<String> processNetData = getDataJob.map(new Func<List<String>, String>() {
        @Override
        public String call(List<String> data) {
            return process(data);
        }
    });
    return processNetData;
}
```
processNetDataJob 直接由 getDataJob 通过 ```map()``` 方法变换得到，处理的逻辑在 ```call()``` 中实现。

这就是简单的映射。

好了，说了这么多，我们才刚刚介绍完第一部分。再次强调一下，这个部分的目的就是让你理解**临时任务对象**的概念，它是 RxJava 的核心思想。我们在这一部分所实现的类和接口，在 RxJava 中都能找到它们的影子。

>  - AsyncJob&lt;T&gt;，实际上就是 Observable，在 RxJava 中，它不仅可以只分发一个事件也可以是一个事件序列。
>  - Callback&lt;T&gt;，就是 Observer， 只是少了一个 ```onComplete( )``` 方法。
>  - ```start(Callback callback)``` 方法，则对应 ```subscribe(Observer observer)``` 方法。

## 2.事件的发送与接收

鉴于网上有很多从源码角度深入理解 RxJava 的文章，这里就不再做过多重复的分析。我们直接用 RxJava 所提供的设计思想，来看如何实现自己的 RxJava。

众所周知，RxJava 采用的是观察者设计模式。由被观察者通知观察者自己的行为发生了变化，让观察者做出响应。在 RxJava 中，上游的 Observable 扮演了被观察者的角色，它能够发送事件，由下游的观察者 Observer 进行监听，在接收到事件后做出响应。

### **RxJava的发送和接收原理**

来看一个简单的例子。

```
\\这里用的是 RxJava 1 的最后一个版本 1.3.8
Observable.create(new Observable.OnSubscribe<Integer>() {
    @Override
    public void call(Subscriber<? super Integer> subscriber) {
        subscriber.onNext(1);
        subscriber.onNext(2);
        subscriber.onNext(3);
        subscriber.onCompleted();
    }
})
    .subscribe(new Subscriber<Integer>() {
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
```

使用 ```Observable.create()``` 方法创建了一个 Observable 对象，在 call 中调用了三次 ```subscriber.onNext()``` 和一次 ```subscriber.onCompleted()``` 。我们先不管 ```create()``` 方法中传入的参数以及 ```call()``` 方法中的 subscriber 是什么，我们可以看到在 ```subscribe()``` 方法中传入了一个 Subscriber，它是一个 Observer，相当于我们前面自己实现的 Callback，它定义了接受到事件时的响应。当我们调用了 ```subscribe()``` 的时候，上面 OnSubscribe 中的 ```call()``` 方法便会开始执行，事件便从上游发送出去了。一旦完成发送，下游的观察者会立即作出响应。可以这么理解，事件的发送是 ```onNext()``` 方法的调用，而事件的接收是 ```onNext() ```方法的执行，它们是一个前后的逻辑关系。

那么回到 ```create()``` 方法中，Observable.OnSubscribe 是什么呢，它继承自一个名为 Action1 的接口。

```
public interface Action1<T> extends Action {
    void call(T t);
}
```

Action1 中定义了一个 ```call()``` 方法，传入一个泛型参数，没有返回值，它的作用在于封装一个执行方法。事实上，在 RxJava 中还存在的许多同样命名的接口，Action0，Action2，Action3，Action4，它们的区别在于传入的参数个数不同，如 Action5 的定义是这样的。

```
public interface Action5<T1, T2, T3, T4, T5> extends Action {
    void call(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5);
}
```

Observable 在调用 ```create()``` 方法的时候，传入的 OnSubscribe 对象，会被存储在返回的 Observable 对象中，由于 OnSubscribe 中封装了事件的执行方法，所以在 Observable 调用 ```subscribe()``` 的时候，就可以通过这个 OnSubscribe 调用自身的 ```call()```方法。

弄懂了 RxJava 中的事件是如何产生和发送之后，我们就可以来实现自己的事件发送机制。

### **事件发送**

我们先模仿定义一个 MyAction1 接口。

```
public interface MyAction1<T> {
    void call(T t);
}
```

为了更加贴近 RxJava 的命名，我们重新定义一下 Callback，将其改名为 MyObserver，并添加 ```onNext()``` 和 ```onCompleted()``` 方法。

```
public interface MyObserver<T> {
    void onNext(T t);
    void onCompleted();
    void onError(Throwable e);
}
```

将 AsyncJob 重命名为 MyObservable，同时将 `start()` 方法改为 `subscribe()` 方法。 由于我们要新增一些方法，所以它不再是一个抽象类。

```
public class MyObservable<T> {

    private MyAction1<MyObserver<T>> action;

    private MyObservable(MyAction1<MyObserver<T>> action){
        this.action = action;
    }

    public void subscribe(MyObserver<T> myObserver) {
        action.call(myObserver);
    }
    
    public static <T> MyObservable<T> create(MyAction1<MyObserver<T>> action){
        return new MyObservable<T>(action);
    }
}
```

可以看到，我们在构造函数里面接收了一个泛型为 MyObserver&lt;T&gt; 类型的 action 并保存。在调用 ```subscribe()``` 方法的时候，会调用 ```action.call()``` 方法，并传入一个 MyObserver 对象，它实现了对结果的回调。

我们还增加了一个 ```create()``` 的静态方法，接收一个 MyAction1&lt;MyObserver&lt;T&gt;&gt; 的参数，它返回了一个含有该 action 的 MyObservable 对象。事实上它只是调用了内部的构造函数，我们完全可以直接从外部调用 ```new MyObservable()``` 的方式去创建，但是为了和 RxJava 保持一致，我们采用声明一个静态方法 ```create()``` 的方式，并将构造函数声明为 private 。

接着我们来看怎么使用。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
    @Override
    public void call(MyObserver<Integer> myObserver) {
        myObserver.onNext(1);
        myObserver.onNext(2);
        myObserver.onNext(3);
        myObserver.onCompleted();
    }
});
```

通过调用 ```MyObservable.create()``` 方法传入一个匿名内部类 MyAction1<MyObserver<Integer>，其中回调接口 MyObserver 的泛型声明为 Integer 。然后我们在 ```call()``` 方法中定义了事件的发送逻辑，调用三次 ```onNext()``` ,最后调用一次 ```onCompleted()``` 方法，跟前面使用 RxJava 的方式是一样的，这样我们就完成了事件的发送。

注意，这个时候我们还没有调用 ```subscribe()``` 方法，所以它实际上还没有发送出去，而是处于“待命”状态。

### **接收**

接下来我们调用 ```subscribe()``` 方法。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
    @Override
    public void call(MyObserver<Integer> myObserver) {
        myObserver.onNext(1);
        myObserver.onNext(2);
        myObserver.onNext(3);
        myObserver.onCompleted();
    }
})
    .subscribe(new MyObserver<Integer>() {
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
```

一切还是那么熟悉，唯一的不同是我们把 Callback 换成了 MyObserver 。

运行一下，输出结果如下：

> onNext:1  
> onNext:2  
> onNext:3  
> onCompleted

### **操作符 just 的实现**

RxJava 不仅支持单一事件的发送，还支持序列事件的发送，来看下面的例子。

```
Observable.just(1,2,3)
            .subscribe(new Subscriber<Integer>() {
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
```

调用 ```Observable.just()``` 方法顺序发送了 1,2,3 三个值，在调用 ```subscribe()``` 方法后同样会收到三次 ```onNext()``` 和一次 ```onCompleted()``` 的回调。

虽然外部隐藏了事件的发送，但是内部的执行原理依旧是不变的。

我们在 MyObservable 中新增一个 ```just()``` 方法。

```
//MyObservable.java
public static <T> MyObservable<T> just(Iterable<T> iterable) {
    return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
        @Override
        public void call(MyObserver<T> myObserver) {
            for (T anIterable : iterable) {
                myObserver.onNext(anIterable);
            }
            myObserver.onCompleted();
        }
    });
}
```

接收一个可迭代序列，同 ```craete()``` 方法一样，我们调用了构造函数，在匿名内部类 MyAction1 的 ```call()``` 方法中，遍历序列，调用 ```onNext()``` 方法，最后调用 ```onCompleted()``` 方法。

使用方式如下。

```
List<Integer> list = new ArrayList<>();
list.add(1);
list.add(2);
list.add(3);

MyObservable.just(list)
        .subscribe(new MyObserver<Integer>() {
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
```

跟 RxJava 不同的是，我们接收的是一个序列，而它可以直接接收多个相同类型的值。我们可以看下它的方法定义。

```
public static <T> Observable<T> just(T t1, T t2, T t3) {}

public static <T> Observable<T> just(T t1, T t2, T t3, T t4) {}

public static <T> Observable<T> just(T t1, T t2, T t3, T t4, T t5) {}
```

可以说是非常暴力了。但是内部实现是一样的，都是对这个序列进行遍历调用 `onNext()` 方法，最后再调用 `onCompleted()` 。

知道这个原理之后，我们就可以按照自己想要的方式自行定义我们的操作符，这里不做展开了。

## **3.映射**


在前面 [简单的映射](#简单的映射) 中，我们已经介绍了如何将一个 AsyncJob&lt;T&gt; 映射成一个 AsyncJob&lt;R&gt; 。

现在我们只需要对原来的 `map()` 修改一下，就能实现 MyObservable 的 `map()` 方法。

```
public <R> MyObservable<R> map(Func<T, R> func) {
    final MyObservable<T> upstream = this;
    return new MyObservable<R>(new MyAction1<MyObserver<R>>() {
        @Override
        public void call(MyObserver<R> myObserver) {
            upstream.start(new MyObserver<T>() {
                @Override
                public void onNext(T t) {
                    myObserver.onNext(func.call(t));
                }

                @Override
                public void onCompleted() {
                    myObserver.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    myObserver.onError(e);
                }
            });
        }
    });
}
```

Func 接口保持不变，唯一需要改变的是将 Callback 接口替换成新的 MyObserver 接口，实现对应的回调方法。

我们再来看一下使用。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
            @Override
            public void call(MyObserver<Integer> myObserver) {
                myObserver.onNext(1);
                myObserver.onNext(2);
                myObserver.onNext(3);
                myObserver.onCompleted();
            }
        })
            .map(new Func<Integer, String>() {
                @Override
                public String call(Integer integer) {
                    return String.valueOf(integer);
                }
            })
            .subscribe(new MyObserver<String>() {
                @Override
                public void onNext(String string) {
                    System.out.println("onNext:" + string);
                }

                @Override
                public void onCompleted() {
                    System.out.println("onCompleted");
                }

                @Override
                public void onError(Throwable e) {

                }
            });
```

我们在 `create()` 和 `subscribe()` 中间加入 `map()` 方法，在 `call()` 中实现了整形变量 integer 到 String 的转换。对于下游的 `subscribe()` 方法来说，调用它的主体已经从原来的 MyObservable&lt;Integer&gt; 类型转变为 MyObservable&lt;String&gt; 类型。

## **4.线程调度**

终于来到最后一个 part 了。线程调度是 RxJava 中另一核心部分，这也是我花最多时间去理解的地方。

RxJava 是通过 `subscribeOn(Scheduler scheduler)` 和 `observeOn(Scheduler scheduler)` 两个方法来实现线程调度的。
> - `subscribeOn()`，指定上游事件发送所在的线程，可以放在任何位置，但是只有第一次的指定是有效的。  
> - `observeOn()`，指定下游事件接收所在的线程，可以多次指定，即如果有多次切换线程的需求，只要在每个需要切换的地方之前调用一次 `observeOn()` 即可。  
> - Scheduler 是一个调度器的类，它指定了事件应该运行在什么线程。


我们先来看下面这个例子。

```
Observable.just(1,2,3)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.newThread())
        .map(new Func1<Integer, String>() {
            @Override
            public String call(Integer integer) {
                return String.valueOf(integer);
            }
        })
        .observeOn(Schedulers.computation())
        .subscribe(new Subscriber<String>() {
            @Override
            public void onCompleted() {
                System.out.println("onCompleted");
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(String string) {
                System.out.println("onNext:"+string);
            }
        });
```

使用 `just()` 方法创建一个 Observable，随后通过 `subscribeOn(Schedulers.io())` 指定 1,2,3 在 io 线程发送，并使用 `observeOn(Schedulers.newThread())` 指定 `map()` 操作在新的线程执行，最后调用 `observeOn(Schedulers.computation())` 让下游的回调在 computation 线程执行，总共完成了 3 次线程切换。

接下来我们来看怎么实现。

### **subscribeOn 的实现**

我们先忽略 Schedule 的实现，只关注如何将上游的事件切换到新的线程中去执行。

在 [事件发送](#事件发送) 中，我们是在 `action.call()` 中通过调用 `onNext()` 、`onCompleted()` 来产生事件的，因此我们可以将这些方法的放到一个新的线程中去调用。

就像这样。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
    @Override
    public void call(MyObserver<Integer> myObserver) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                myObserver.onNext(1);
                myObserver.onNext(2);
                myObserver.onNext(3);
                myObserver.onCompleted();
            }
        }).start();
    }
})
```

当然我们不能这么简单粗暴的将新建线程的操作暴露在外面，使用者在调用 `create()` 方法的时候只关注事件如何发送，线程切换应该放在 `subscribeOn()` 方法中实现，所以我们要思考如何将这一系列的事件包裹到新的线程中运行。

回顾 [简单的映射](#简单的映射) 中，我们在 `map()` 方法中将原来的 MyObservable 转变为一个新的 MyObservable，结合这种思想，我们是不是可以将普通的 MyObservable 转变成一个新的封装了线程操作的 MyObservable 呢？

答案是肯定的。来看我们的 `subscribeOn()` 是怎么实现的。

```
public MyObservable<T> subscribeOn() {
    MyObservable<T> upstream = this;
    return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
        @Override
        public void call(MyObserver<T> myObserver) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    upstream.subscribe(new MyObserver<T>() {
                        @Override
                        public void onNext(T t) {
                            myObserver.onNext(t);
                        }

                        @Override
                        public void onCompleted() {
                            myObserver.onCompleted();
                        }

                        @Override
                        public void onError(Throwable e) {
                            myObserver.onError(e);
                        }
                    });
                }
            }).start();
        }
    });
}
```

同 `map()` 方法一样，我们用 `upsteam` 变量保存了当前的 MyObservable 实例，随后返回一个新的 MyObservable 对象，并在 `call()` 方法中开启了一个子线程，在 `run()` 方法中调用 `upsteam.subscribe()`，将上游 upsteam 中的回调全部转移到新 MyObservable 的回调中去，于是我们就实现了将一个普通的 MyObservable 转变为一个新的含有线程操作的 MyObservable 。

看下使用效果。

```
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
                    System.out.println("onCompleted:" + Thread.currentThread().getName());
                }

                @Override
                public void onError(Throwable e) {

                }
            });
```

我们在 `call()` 、`onNext()` 和 `onCompleted()` 中打印了所在线程的名字，运行结果如下。

> call:Thread-0  
onNext:Thread-0  
onNext:Thread-0  
onNext:Thread-0  
onCompleted:Thread-0  

可以看到事件的发送和接收都在一个新的子线程 Thread-0 里面。

我们来梳理一下执行的流程。

![此处输入图片的描述][6]

通过 `Observable.create()` 创建了 MyObservable 1 ，随后调用 `subscribeOn()` 变换得到新的 MyObservable 2 ，最后调用 `subscribe()` 传入一个 MyObserver  。注意，这里的 MyObserver 是传给 MyObservable 2 的，所以我们将其命名为 MyObserver 2 。

在主线程的时候，由 MyObservable 2 调用 `subscribe()` 。 

```
public void subscribe(MyObserver<T> myObserver) {
    action.call(myObserver);
}
```

`subscribe()` 会调用 MyObservable 2 中的 action 执行 `call()` 方法，它的实现就在刚才的 `subscribeOn()` 里面。

```
public MyObservable<T> subscribeOn() {
    MyObservable<T> upstream = this;
    return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
        @Override
        public void call(MyObserver<T> myObserver) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    upstream.subscribe(new MyObserver<T>() {
                        @Override
                        public void onNext(T t) {
                            myObserver.onNext(t);
                        }

                        @Override
                        public void onCompleted() {
                            myObserver.onCompleted();
                        }

                        @Override
                        public void onError(Throwable e) {
                            myObserver.onError(e);
                        }
                    });
                }
            }).start();
        }
    });
}
```

这里的 `call()` 我们已经在内部开启一个新线程，所以会进入 Thread-0 线程。在线程执行体中，我们调用了 `upsteam.subscirbe()` ，即 `1.subscribe()` ， ` subscribe()` 又会调用 MyObservable 1 中的 action 执行 `1.call()` ， `1.call()` 的实现在我们最开始的 `create()` 里面。

```
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
```

我们调用了三次 `onNext()` 和一次 `onCompleted()` ，上图我只画了第一个 `onNext()` 的调用，即 `1.onNext()` ， `1.onNext()` 的回调在 `subscribe()` 中我们将其转发给了 `2.onNext()` 。

```
public MyObservable<T> subscribeOn() {
    /*省略*/
            upstream.subscribe(new MyObserver<T>() {
                @Override
                public void onNext(T t) {
                    myObserver.onNext(t);
                }
        
                @Override
                public void onCompleted() {
                    myObserver.onCompleted();
                }
        
                @Override
                public void onError(Throwable e) {
                    myObserver.onError(e);
                }
            });
    /*省略*/
}
```

所以最终会来到一开始我们传入的 MyObserver 中，执行 `System.out.println()` 方法。

```
MyObservable.create()//省略实现
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
```

### **为什么 subscribeOn 只在第一次生效**

我们来看下面的例子。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
        @Override
        public void call(MyObserver<Integer> myObserver) {
            System.out.println("call:" + Thread.currentThread().getName());
            myObserver.onNext(1);
        }
    })
            .subscribeOn()
            .map(new Func<Integer, String>() {
                @Override
                public String call(Integer integer) {
                    System.out.println("map:" + Thread.currentThread().getName());
                    return String.valueOf(integer);
                }
            })
            .subscribeOn()
            .subscribe(new MyObserver<String>() {
                @Override
                public void onNext(String string) {
                    System.out.println("onNext:" + Thread.currentThread().getName());
                }

                @Override
                public void onCompleted() {}

                @Override
                public void onError(Throwable e) {}
            });
```

在 `create()` 后面和 `map()` 后面都调用了一次 `subscribeOn()` ，可能一开始我们会理所当然的觉得，` create()` 中 `print()` 方法会发生在子线程1，`map()` 中的 `print()` 会发生在子线程2，那么实际结果是怎样的呢？

> call:Thread-1  
map:Thread-1  
onNext:Thread-1  

所有的 `print()` 方法都发生在子线程1，也就是说第二个 `subscribeOn()` 是无效的。来看下流程图就知道为什么了。

![此处输入图片的描述][7]

可以看到，虽然我们在第二次调用 `subscribeOn()` 的时候，从主线程切换到了 Thread-0 线程，但是在第一次调用 `subscribe()` 的时候，它又让接下来的流程从 Thread-0 切换到 Thread-1 ，而真正的事件发送，即 `onNext()` 以及它们的回调，统统发生在 Thread-1 里面，所以不管我们在第一次调用 `subscribeOn()` 之后，又调用了几次 `subscribeOn()` ，它们的作用只会让你的线程从 main 切换 Thread-0，Thread-1，Thread-2，……，Thread-n，而 `onNext()` 以及它们的回调将会在最后一个新建出来的子线程执行（忽略 `observeOn()` 的影响）。

### **observeOn 的实现**

前面讲过， `observeOn()` 方法作用的是它的直接下游，如果是在 `subscribe()` 前面调用的，那么它改变的是回调所在的线程，即 `onNext()` 、 `onCompleted()` 和 `onError()` 的实现。如果是在其他操作符如 `map()` 前面调用的呢？其实也是一样的，我们再次回顾 `map()` 的实现。

```
public <R> MyObservable<R> map(Func<T, R> func) {
    final MyObservable<T> upstream = this;
    return new MyObservable<R>(new MyAction1<MyObserver<R>>() {
        @Override
        public void call(MyObserver<R> myObserver) {
            upstream.start(new MyObserver<T>() {
                @Override
                public void onNext(T t) {
                    myObserver.onNext(func.call(t));
                }
                @Override
                public void onCompleted() {
                    myObserver.onCompleted();
                }
                @Override
                public void onError(Throwable e) {
                    myObserver.onError(e);
                }
            });
        }
    });
}
```

 `map()` 中的核心语句是 `func.call(t)` 的调用，并将其传递到下游的 myObserver ，所以要想切换 `func.call(t)` 所在的线程，就必须改变 `onNext()` 回调所在的线程。

写法很简单，我们返回一个新的 MyObservable，并在上游的 `onNext()` 回调中新建一个线程，再将回调传递给下游，也就是当前新返回的 MyObservable。

```
public MyObservable<T> observeOn(Scheduler scheduler) {
    MyObservable<T> upstream = this;
    return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
        @Override
        public void call(MyObserver<T> myObserver) {
            upstream.subscribe(new MyObserver<T>() {
                @Override
                public void onNext(T t) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            myObserver.onNext(t);
                        }
                    }).start();
                }

                @Override
                public void onCompleted() {
                    myObserver.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    myObserver.onError(e);
                }
            });
        }
    });
}
```

这里我们忽略了对 `onCompleted()` 和 `onError()` 的处理，因为我们要保证它们和 `onNext()` 是执行在同一个子线程中的，需要借助线程池来实现，这个我们待会再讨论，现在只需关注怎么改变下游的线程。先来看看我们的 `observeOn()` 怎么使用吧。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
    @Override
    public void call(MyObserver<Integer> myObserver) {
        System.out.println("call:" + Thread.currentThread().getName());
        myObserver.onNext(1);
    }
})
        .observeOn()
        .subscribe(new MyObserver<Integer>() {
            @Override
            public void onNext(Integer integer) {
                System.out.println("onNext:" + Thread.currentThread().getName());
            }

            @Override
            public void onCompleted() {}

            @Override
            public void onError(Throwable e) {}
        });
```

将下游的回调指定在新的子线程，运行结果如下。

> call:main  
onNext:Thread-0

达到了我们想要的效果，再来梳理下执行流程。

![此处输入图片的描述][8]

我们通过 `create()` 操作和 `observeOn()` 生成了 MyObservable2 对象，随后调用 `subscribe()` 方法， `subscribe()` 方法会调用 `2.call()` 方法，而 `2.call()` 的实现我们是在 `observeOn()` 中声明的，即调用上游 MyObservable1 的 `subscribe()` 方法， `1.subscribe()` 方法调用 `1.call()` 方法， 它的实现在 `create()` 中已经声明，即调用 `1.onNext()` 方法， `1.onNext()` 的回调同样在 `observeOn()` 内部，此时会开启一个新的子线程，进入 Thread-0 ，在线程体中调用 `2.onNext()` ，它的回调在我们声明的 MyObserver2 中，即打印输出当前线程。

讲起来很啰嗦，大家可以自己根据流程在纸上画一遍，一下子会清晰很多。

最后我们再来看一个比较复杂的场景，由一个 `subscribeOn()` 和多个 `observeOn()` 同时使用的例子，代码如下。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
    @Override
    public void call(MyObserver<Integer> myObserver) {
        System.out.println("call:" + Thread.currentThread().getName());
        myObserver.onNext(1);
    }
})
        .subscribeOn()
        .observeOn()
        .map(new Func<Integer, String>() {
            @Override
            public String call(Integer integer) {
                System.out.println("map:" + Thread.currentThread().getName());
                return String.valueOf(integer);
            }
        })
        .observeOn()
        .subscribe(new MyObserver<Integer>() {
            @Override
            public void onNext(Integer integer) {
                System.out.println("onNext:" + Thread.currentThread().getName());
            }

            @Override
            public void onCompleted() {}

            @Override
            public void onError(Throwable e) {}
        });
```

这里一共切换了三次线程，运行结果如下。

> call:Thread-0  
map:Thread-1  
onNext:Thread-2

发送事件运行在 Thread-0 线程，map 映射运行在 Thread-1 线程，结果回调发生在 Thread-2 线程。流程如下所示。

![此处输入图片的描述][9]

看起来非常复杂，这里就不再赘述，需要大家比较耐心的看下去，跟随代码，理解线程是如何在整个流程中发生切换的。

### **利用线程池进行调度**

前面在写 `observeOn()` 方法的时候我们只对 `onNext()` 方法开启了子线程，而没有对 `onCompleted()` 和 `onError()` 进行操作。

```
public MyObservable<T> observeOn(Scheduler scheduler) {
    MyObservable<T> upstream = this;
    return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
        @Override
        public void call(MyObserver<T> myObserver) {
            upstream.subscribe(new MyObserver<T>() {
                @Override
                public void onNext(T t) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            myObserver.onNext(t);
                        }
                    }).start();
                }

                @Override
                public void onCompleted() {
                    myObserver.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    myObserver.onError(e);
                }
            });
        }
    });
}
```

因为它们之间其实是独立的关系，我们在 `onNext()` 中通过 `new Thread().start()` 的方式开启了一个子线程，但是我们没有办法让 `onCompleted()` 同样执行在这个新建出来的线程中。事实上，`onNext()` 的写法也是有问题的。一旦我们在发送事件的时候，调用了多次 `onNext()` ，那么它在每次回调的时候，就会新开辟一个线程，导致所有事件都在不同的子线程中去处理，就不能保证事件能够按照发送的顺序进行接收了。

那么解决的办法就是使用线程池来管理我们的线程。

还记得RxJava在切换线程的时候是怎么写的吗？

```
Observable.just(1,2,3)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.newThread())
```

在调用 `subscribeOn()` 和 `observeOn()` 的时候，需要传入一个是 Scheduler 类的对象，前面说过，它相当于一个调度器，能够指定我们事件执行在什么线程，而 Schedulers 是一个单例，它用来管理和提供不同的调度器（即线程池）供开发者调用。

我们可以模仿 RxJava 的方式来实现线程池的管理。首先定义一个 Scheduler 抽象类，它包含 `schedule()` 、 `finish()` 和 `isFinished()` 方法。

```
public abstract class Scheduler {
    public abstract void schedule(Runnable runnable);
    public abstract void finish();
    public abstract boolean isFinished();
}
```

接下来是我们提供两个 Scheduler 的实现类。

```
public class NewThreadScheduler extends Scheduler {

    private ExecutorService executor;

    private boolean isFinished = false;

    public NewThreadScheduler() {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NewThread-" + System.currentTimeMillis());
            }
        };
        executor = Executors.newSingleThreadExecutor(threadFactory);
    }

    @Override
    public void schedule(Runnable runnable) {
        if (!isFinished) {
            executor.execute(runnable);
        }
    }

    @Override
    public void finish() {
        if (!isFinished) {
            executor.shutdown();
            isFinished = true;
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

}
```

```
public class ChildThreadScheduler extends Scheduler {

    private ExecutorService executor;
    
    private boolean isFinished = false;

    public ChildThreadScheduler() {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "ChildThread-" + System.currentTimeMillis());
            }
        };
        executor = Executors.newSingleThreadExecutor(threadFactory);
    }

    @Override
    public void schedule(Runnable runnable) {
        if (!isFinished) {
            executor.execute(runnable);
        }
    }

    @Override
    public void finish() {
        if (!isFinished) {
            executor.shutdown();
            isFinished = true;
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

}
```
可以看到，我们分别在两个类的构造函数中，声明了一个 ThreadFactory，并将其传入 ` Executors.newSingleThreadExecutor()` 方法中，返回一个 ExecutorService 对象。注意，这里使用 `newSingleThreadExecutor()` 是为了保证 runnable 对象能够按顺序进入线程池，以确保事件能够按照我们定义的顺序去执行。

在 `schedule()` 方法中，我们调用 `executor.execute(runnable)` 方法，让线程池执行runnable对象，在 `finish()` 方法中，调用了 `executor.shutdown()` 方法，它会在线程池执行完任务后，关闭线程池，在两个方法在执行前都会提前判断 isFinished 的值，避免抛出 RejectedExecutionException 的异常。

以上这些涉及到一些线程池的知识，不清楚地同学可以先去了解一下。

这两个类的唯一区别，就是构造函数中 ThreadFactory 返回的线程的名字不一样。在这里，我们只是为了做一个简单的区分。

接着我们定义一个 Schedulers 的单例。

```
public class Schedulers {

    private static class Singleton {
        private static Schedulers instance = new Schedulers();
    }

    private static Schedulers getInstance() {
        return Singleton.instance;
    }

    private ChildThreadScheduler childThreadScheduler;

    public static Scheduler newThread() {
        return new NewThreadScheduler();
    }

    public static Scheduler childThread() {
        if (getInstance().childThreadScheduler == null) {
            getInstance().childThreadScheduler = new ChildThreadScheduler();
        }else if (getInstance().childThreadScheduler.isFinished()){
            getInstance().childThreadScheduler = new ChildThreadScheduler();
        }
        return getInstance().childThreadScheduler;
    }
}
```

当调用 `Schedulers.newThread()` 方法时，直接返回一个新的 NewThreadScheduler 对象。
当调用 `Schedulers.childThread()` 方法时，会返回一个单例中维护的 ChildThreadScheduler 对象，如果这个线程池为空或者已经被关闭，那么再重新返回一个新的实例。

现在我们可以看出这两个线程池的区别，`newThread()` 每次都会开启一个新的线程池，而 `childThread()` 则会使用同一个线程池。

定义好线程管理相关的类后，我们就可以改造 `subscribeOn()` 方法了。

```
public MyObservable<T> subscribeOn(Scheduler scheduler) {
    MyObservable<T> upstream = this;
    return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
        @Override
        public void call(MyObserver<T> myObserver) {
            scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    upstream.subscribe(new MyObserver<T>() {
                        @Override
                        public void onNext(T t) {
                            myObserver.onNext(t);
                        }

                        @Override
                        public void onCompleted() {
                            myObserver.onCompleted();
                        }

                        @Override
                        public void onError(Throwable e) {
                            myObserver.onError(e);
                        }
                    });
                }
            });
        }
    });
}
```

我们将 `new Thread().start()` 的方式，改成了 `scheduler.schedule()` ，非常的简单。

再看看 `ObserverOn()` 方法。

```
public MyObservable<T> observeOn(Scheduler scheduler) {
    MyObservable<T> upstream = this;
    return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
        @Override
        public void call(MyObserver<T> myObserver) {
            upstream.subscribe(new MyObserver<T>() {
                @Override
                public void onNext(T t) {
                    scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            myObserver.onNext(t);
                        }
                    });
                }

                @Override
                public void onCompleted() {
                    scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            myObserver.onCompleted();
                            scheduler.finish();
                        }
                    });
                }

                @Override
                public void onError(Throwable e) {
                    scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            myObserver.onError(e);
                            scheduler.finish();
                        }
                    });
                }
            });
        }
    });
}
```

现在，我们不管是在 `onNext()` 、`onCompleted()` 还是 `onError()` 方法中，调用 `scheduler.schedule()` 方法，都是同一个 scheduler 对象在执行，即它们都跑在同一个线程池中。

再来测试一下。

```
MyObservable.create(new MyAction1<MyObserver<Integer>>() {
        @Override
        public void call(MyObserver<Integer> myObserver) {
            System.out.println("call:" + Thread.currentThread().getName());
            myObserver.onNext(1);
            myObserver.onNext(2);
            myObserver.onCompleted();
        }
    })
            .subscribeOn(Schedulers.newThread())
            .observeOn(Schedulers.newThread())
            .subscribe(new MyObserver<Integer>() {
                @Override
                public void onNext(Integer integer) {
                    System.out.println("onNext:" + integer+" "+Thread.currentThread().getName());
                }

                @Override
                public void onCompleted() {
                    System.out.println("onCompleted:"+Thread.currentThread().getName());
                }

                @Override
                public void onError(Throwable e) {}
            });
```

运行结果如下。

> call:NewThread-1533382601665  
onNext:1 NewThread-1533382601666  
onNext:2 NewThread-1533382601666  
onCompleted:NewThread-1533382601666  

效果不错，我们成功让事件在 NewThread-1533382601665 线程中发送，并在 NewThread-1533382601666 中回调结果，但是我们会发现，程序依然在运行状态，不会自动结束进程。这是因为我们传进去的 scheduler 都没有被关闭，那么现在问题来了，我们要怎样关闭这个 scheduler？

### **关闭线程池**

为了确保线程池在不再有任务的情况下关闭，我们必须在最后一刻才调用 `scheduler.finish()` 方法。观察前面的那几个流程图，我们知道整个流程在执行到最后都会来到我们一开始传进去的 MyObserver 回调中，所以我们可以对 `subscribe()` 方法做些改变，让它能够在 `onCompleted()` 或者 `onError()` 方法执行完关闭线程池。

新建一个 `mySubscribe()` 方法，同`subscribe()` 一样，它调用了 `action.call()` 方法，但是传进去的是一个新的 MyObserver ，在回调中再去调用外部传进去的 `myObserver.onCompleted()` 和 `myObserver.onError()` ，最后执行 `finish()` 方法，这样就能确保我们对线程池的关闭是在整个流程的最后一刻执行的。

```
public void mySubscribe(MyObserver<T> myObserver) {
    action.call(new MyObserver<T>() {
        @Override
        public void onNext(T t) {
            myObserver.onNext(t);
        }

        @Override
        public void onCompleted() {
            myObserver.onCompleted();
            finish();//关闭线程池
        }

        @Override
        public void onError(Throwable e) {
            myObserver.onError(e);
            finish();//关闭线程池
        }
    });
}
```

注意，现在这个方法与 `subscribe()` 的区别是， `mySubscribe()` 是我们在外部调用的，而 `subscribe()` 是在内部调用的。

再看下 `finish()` 怎么实现。

```
public class MyObservable<T> {

    /*已省略*/

    private Set<Scheduler> schedulers;
    
    private MyObservable(MyAction1<MyObserver<T>> action) {
        this.action = action;
        this.schedulers = new HashSet<>();
    }
    
    private MyObservable(MyAction1<MyObserver<T>> action, Set<Scheduler> schedulers) {
        this.action = action;
        this.schedulers = schedulers;
    }
        
    private void finish(){
        for (Scheduler scheduler : schedulers) {
            scheduler.finish();
        }
    }
    
    /*已省略*/
}
```

我们在内部新增了一个 Scheduler 的集合变量 schedulers ，在单参数的构造函数中初始化，并提供一个双参数的构造函数，方便我们在 `map()` 、`subscribeOn()` 和 `observeOn()` 中创建新实例时传递这个变量。

这几个方法的改动如下。

```
public <R> MyObservable<R> map(Func<T, R> func) {
	final MyObservable<T> upstream = this;
	return new MyObservable<R>(new MyAction1<MyObserver<R>>() {
		@Override
		public void call(MyObserver<R> myObserver) {
			upstream.subscribe(new MyObserver<T>() {
				@Override
				public void onNext(T t) {
					myObserver.onNext(func.call(t));
				}

				@Override
				public void onCompleted() {
					myObserver.onCompleted();
				}

				@Override
				public void onError(Throwable e) {
					myObserver.onError(e);
				}
			});
		}
	}, schedulers);
}
```

```
public MyObservable<T> subscribeOn(Scheduler scheduler) {
	schedulers.add(scheduler);
	MyObservable<T> upstream = this;
	return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
		@Override
		public void call(MyObserver<T> myObserver) {
			scheduler.schedule(new Runnable() {
				@Override
				public void run() {
					upstream.subscribe(new MyObserver<T>() {
						@Override
						public void onNext(T t) {
							myObserver.onNext(t);
						}

						@Override
						public void onCompleted() {
							myObserver.onCompleted();
						}

						@Override
						public void onError(Throwable e) {
							myObserver.onError(e);
						}	
				    });
				}
			});
		}
	}, schedulers);
}
```

```
public MyObservable<T> observeOn(Scheduler scheduler) {
	schedulers.add(scheduler);
	MyObservable<T> upstream = this;
	return new MyObservable<T>(new MyAction1<MyObserver<T>>() {
		@Override
		public void call(MyObserver<T> myObserver) {
			upstream.subscribe(new MyObserver<T>() {
				@Override
				public void onNext(T t) {
					scheduler.schedule(new Runnable() {
						@Override
						public void run() {
							myObserver.onNext(t);
						}
					});
				}

				@Override
				public void onCompleted() {
					scheduler.schedule(new Runnable() {
						@Override
						public void run() {
							myObserver.onCompleted();
						}
					});
				}

				@Override
				public void onError(Throwable e) {
					scheduler.schedule(new Runnable() {
						@Override
						public void run() {
							myObserver.onError(e);
						}
					});
				}
			});
		}
	}, schedulers);
}
```

最后，我们用一个比较复杂的例子来演示。

```
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
                public void onError(Throwable e) {}
            });
```

执行结果如下。

> call:NewThread-1533441899656  
map:ChildThread-1533441899658  
map:NewThread-1533441899658  
onNext:ChildThread-1533441899658  
onCompleted:ChildThread-1533441899658  

控制台输出了每个打印事件所在的线程，并且自动结束了进程。可以看到，这个流程里面包含了三个不同的线程，两个不同的 NewThread 线程，还有一个 ChildThread 线程。

它们的流程图如下。

![此处输入图片的描述][10]

## **结语**

到这里我们整个《自己动手造一个RxJava》的讲解就结束了，非常感谢大家的阅读，在写本文之前自己是花了一周的时间去理解，然后又花了一周的时间才把整个思路和分析整理出来，算是我第一次花这么大精力去写的一篇文章了。本文篇幅较长，某些地方可能讲得比较啰嗦，但是对新手而言如果能够耐心的看下去，是非常不错的学习资料。若有错误的地方，也请各位读者及时指出，欢迎大家一起探讨。

**同时感谢以下两位作者提供的参考资料：**  

[给 Android 开发者的 RxJava 详解][11]  
[ RxJava 系列文章][12]  


  [1]: https://github.com/CaptainRuby/MyRxJava
  [2]: http://on-img.com/chart_image/5b5fd685e4b0edb750f22768.png
  [3]: http://on-img.com/chart_image/5b5fdbf9e4b025cf492eb91f.png
  [4]: http://on-img.com/chart_image/5b5fed46e4b0edb750f24f4d.png
  [5]: http://on-img.com/chart_image/5b6001cee4b0edb750f27dc5.png
  [6]: http://on-img.com/chart_image/5b62ae73e4b08d36229b5205.png
  [7]: http://on-img.com/chart_image/5b6543d7e4b08d36229e3592.png
  [8]: http://on-img.com/chart_image/5b6556e6e4b08d36229e4b00.png
  [9]: http://on-img.com/chart_image/5b65248be4b0edb750f944c5.png
  [10]: http://on-img.com/chart_image/5b667424e4b0f8477da35bb6.png
  [11]: https://gank.io/post/560e15be2dca930e00da1083
  [12]: https://blog.csdn.net/jeasonlzy/article/category/7004637