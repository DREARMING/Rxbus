package com.mvcoder.rxbus;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;

/**
 * RxBus核心处理类
 *
 * @author mvcoder 2017/9/21
 */
public class RxBus {
    private static volatile RxBus defaultInstance;

    /**
     * 每一个订阅对象都有许多个订阅方法，取消订阅的时候要将这些订阅操作进行取消，防止内存泄漏
     */
    private Map<Object, List<Disposable>> subscriptionsByEventType = new HashMap<>();

    /**
     * 每一个subscriber都有一系列的订阅EventType，当我们取消订阅时，需要将subsriberMethod移除，这个时候，可以先根据eventType找出所有method
     * 然后匹配method的subscriber，是否是同一个对象，是的话，就移除。
     */
    private Map<Object, List<Class>> eventTypesBySubscriber = new HashMap<>();

    //发送事件是根据EventType 和 code值进行发送的。当我们发送事件时，根据EventType获取到所有这种类型的SubscriberMethod
    //然后判断code值是否相等，如果相等，就调用subscriberMethod中的subscriber.invoke（arg）
    private Map<Class, List<SubscriberMethod>> subscriberMethodByEventType = new HashMap<>();

    /*stick数据*/
    private final Map<Class<?>, Map<Integer, Message>> stickyEvent = new ConcurrentHashMap<>();
    // 主题
    private final FlowableProcessor bus;

    public RxBus() {
        bus = PublishProcessor.create().toSerialized();
    }

    // 单例RxBus
    public static RxBus getDefault() {
        RxBus rxBus = defaultInstance;
        if (defaultInstance == null) {
            synchronized (RxBus.class) {
                rxBus = defaultInstance;
                if (defaultInstance == null) {
                    rxBus = new RxBus();
                    defaultInstance = rxBus;
                }
            }
        }
        return rxBus;
    }

    /**
     * 发送粘性事件，注意的是：当订阅者发起订阅时，该粘性事件会立刻发送给该订阅者，
     * 如果订阅者是activity，请注意粘性订阅方法里面不涉及跟生命周期有关的方法，否则导致状态异常
     *
     * @param o 事件数据
     */
    public synchronized void postStickyEvent(int code, Object o) {
        List<SubscriberMethod> methodList = subscriberMethodByEventType.get(o.getClass());
        boolean hasSubcriber = false;
        if(methodList != null && methodList.size() > 0) {
            for (SubscriberMethod method : methodList) {
                if (method.sticky && method.code == code) {
                    hasSubcriber = true;
                    break;
                }
            }
        }
        Message message = new Message(code, o);
        if (!hasSubcriber) {
            Map<Integer, Message> stickyMap = stickyEvent.get(o.getClass());
            if (stickyMap == null) {
                stickyMap = new HashMap<>();
                stickyEvent.put(o.getClass(), stickyMap);
            }
            stickyMap.put(code, message);
        } else {
            bus.onNext(message);
        }

    }

    /**
     * 提供了一个新的事件,根据code进行分发
     *
     * @param code 事件code
     * @param o
     */
    public void post(int code, Object o) {
        bus.onNext(new Message(code, o));
    }


    /**
     * 根据传递的code和 eventType 类型返回特定类型(eventType)的 被观察者
     *
     * @param <T>
     * @param code      事件code
     * @param eventType 事件类型
     * @return
     */
    private  <T> Flowable toObservable(final int code, final Class<T> eventType, Scheduler workThread) {
        return bus.ofType(Message.class)
                .filter(new Predicate<Message>() {
                    @Override
                    public boolean test(Message o) throws Exception {
                        return o.getCode() == code && eventType.isInstance(o.getObject());
                    }
                }).map(new Function<Message, Object>() {
                    @Override
                    public Object apply(Message message) throws Exception {
                        return message.getObject();
                    }
                }).cast(eventType).observeOn(workThread);
    }


    private synchronized void sendStickyEventToSubscriber(final SubscriberMethod method, Scheduler workThread) {
        Map<Integer, Message> map = stickyEvent.get(method.eventType);
        if (map == null) return;
        final Message message = map.get(method.code);
        if (message == null) return;
        Flowable.create(new FlowableOnSubscribe<Message>() {
            @Override
            public void subscribe(FlowableEmitter<Message> e) throws Exception {
                if (!e.isCancelled()) {
                    e.onNext(message);
                    e.onComplete();
                } else {
                    e.onError(new IllegalStateException("Subscriber : " + message.getObject().toString() + "has disposable!"));
                }
            }
        }, BackpressureStrategy.ERROR).observeOn(workThread).subscribe(new Consumer<Message>() {
            @Override
            public void accept(Message message) throws Exception {
                method.invoke(message.getObject());
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {

            }
        });
        map.remove(method.code);
        if(map.size() == 0){
            stickyEvent.remove(method.eventType);
        }
    }
/*

    */
/**
     * 根据传递的 eventType 类型返回特定类型(eventType)的 被观察者
     *//*

    public <T> Flowable toObservableSticky(final Class<T> eventType, Scheduler workThread) {
        synchronized (stickyEvent) {

            //Flowable<T> observable = bus.ofType(eventType).observeOn(workThread);
            final Object event = stickyEvent.get(eventType);

            if (event != null) {
                return bus.ofType(eventType).mergeWith(Flowable.create(new FlowableOnSubscribe<T>() {
                    @Override
                    public void subscribe(FlowableEmitter<T> e) throws Exception {
                        e.onNext(eventType.cast(event));
                    }

                    */
/*@Override
                    public void call(Subscriber<? super T> subscriber) {
                        subscriber.onNext(eventType.cast(event));
                    }*//*

                }, BackpressureStrategy.ERROR)).observeOn(workThread);
            } else {
                return bus.ofType(eventType).observeOn(workThread);
            }
        }
    }
*/


    /**
     * 注册
     *
     * @param subscriber 订阅者
     */
    public void register(Object subscriber) {
          /*避免重复创建*/
        if (eventTypesBySubscriber.containsKey(subscriber)) {
            return;
        }
        Class<?> subClass = subscriber.getClass();
        Method[] methods = subClass.getDeclaredMethods();
        boolean recive = false;
        for (Method method : methods) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                recive = true;
                //获得参数类型
                Class[] parameterType = method.getParameterTypes();
                //参数不为空 且参数个数为1
                if (parameterType != null && parameterType.length == 1) {

                    Class eventType = parameterType[0];

                    addEventTypeToMap(subscriber, eventType);
                    Subscribe sub = method.getAnnotation(Subscribe.class);
                    int code = sub.code();
                    ThreadMode threadMode = sub.threadMode();
                    boolean sticky = sub.sticky();

                    SubscriberMethod subscriberMethod = new SubscriberMethod(subscriber, method, eventType, code, threadMode,
                            sticky);

                    // if (isAdd(eventType, subscriberMethod)) {
                    addSubscriber(subscriberMethod);
                    //}
                    addSubscriberToMap(eventType, subscriberMethod);
                }
            }
        }
        /*没有接受对象，抛出异常*/
        if (!recive) {
            throw new RuntimeException("RxBus error:no recive targert event");
        }
    }


    /**
     * 检查是否已经添加过sub事件
     *
     * @param eventType
     * @param subscriberMethod
     * @return
     */
    private boolean isAdd(Class eventType, SubscriberMethod subscriberMethod) {
        boolean resulte = true;
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods != null && subscriberMethods.size() > 0) {
            for (SubscriberMethod subscriberMethod1 : subscriberMethods) {
                if (subscriberMethod1.code == subscriberMethod.code) {
                    resulte = false;
                    break;
                }
            }
        }
        return resulte;
    }


    /**
     * 将event的类型以订阅中subscriber为key保存到map里
     *
     * @param subscriber 订阅者
     * @param eventType  event类型
     */
    private void addEventTypeToMap(Object subscriber, Class eventType) {
        List<Class> eventTypes = eventTypesBySubscriber.get(subscriber);
        if (eventTypes == null) {
            eventTypes = new ArrayList<>();
            eventTypesBySubscriber.put(subscriber, eventTypes);
        }

        if (!eventTypes.contains(eventType)) {
            eventTypes.add(eventType);
        }
    }

    /**
     * 将注解方法信息以event类型为key保存到map中
     *
     * @param eventType        event类型
     * @param subscriberMethod 注解方法信息
     */
    private void addSubscriberToMap(Class eventType, SubscriberMethod subscriberMethod) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods == null) {
            subscriberMethods = new ArrayList<>();
            subscriberMethodByEventType.put(eventType, subscriberMethods);
        }

        if (!subscriberMethods.contains(subscriberMethod)) {
            subscriberMethods.add(subscriberMethod);
        }
    }


    /**
     * 将订阅事件以event类型为key保存到map,用于取消订阅时用
     *
     * @param subscriber   subscriber对象类
     * @param subscription 订阅事件
     */
    private void addSubscriptionToMap(Object subscriber, Disposable subscription) {
        List<Disposable> subscriptions = subscriptionsByEventType.get(subscriber);
        if (subscriptions == null) {
            subscriptions = new ArrayList<>();
            subscriptionsByEventType.put(subscriber, subscriptions);
        }

        if (!subscriptions.contains(subscription)) {
            subscriptions.add(subscription);
        }
    }


    /**
     * 用RxJava添加订阅者
     *
     * @param subscriberMethod
     */
    private void addSubscriber(final SubscriberMethod subscriberMethod) {
        Flowable observable;
        Scheduler workThread = getScheduler(subscriberMethod);
        if (subscriberMethod.sticky) {
            //observable = toObservableSticky(subscriberMethod.eventType, workThread);
            sendStickyEventToSubscriber(subscriberMethod,workThread);
        }
        observable = toObservable(subscriberMethod.code, subscriberMethod.eventType, workThread);
        Disposable subscription = observable
                .subscribe(new Consumer() {
                    @Override
                    public void accept(Object o) throws Exception {
                        callEvent(subscriberMethod, o);
                    }
                });
        addSubscriptionToMap(subscriberMethod.subscriber, subscription);
    }


    /**
     * 用于处理订阅事件在那个线程中执行
     *
     * @param subscriberMethod
     * @return
     */
    private Scheduler getScheduler(SubscriberMethod subscriberMethod) {

        Scheduler scheduler;

        switch (subscriberMethod.threadMode) {
            case MAIN:
                scheduler = AndroidSchedulers.mainThread();
                break;
            case NEW_THREAD:
                scheduler = Schedulers.newThread();
                break;
            case CURRENT_THREAD:
                scheduler = Schedulers.trampoline();
                break;
            case COMPULATION_THREAD:
                scheduler = Schedulers.computation();
                break;
            case IO:
                scheduler = Schedulers.io();
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscriberMethod.threadMode);
        }
        return scheduler;
    }


    /**
     * 回调到订阅者的方法中
     *
     * @param method
     * @param object
     */
    private void callEvent(SubscriberMethod method, Object object) {
        Class eventClass = object.getClass();
        List<SubscriberMethod> methods = subscriberMethodByEventType.get(eventClass);
        if (methods != null && methods.size() > 0) {
            for (SubscriberMethod subscriberMethod : methods) {
                Subscribe sub = subscriberMethod.method.getAnnotation(Subscribe.class);
                int c = sub.code();
                if (c == method.code && method.subscriber != null && method.subscriber.equals(subscriberMethod.subscriber)) {
                    subscriberMethod.invoke(object);
                }

            }
        }
    }


    /**
     * 取消注册
     *
     * @param subscriber
     */
    public void unRegister(Object subscriber) {
        List<Class> subscribedTypes = eventTypesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unSubscribeBySubscriber(subscriber);
                unSubscribeMethodByEventType(subscriber, eventType);
            }
            eventTypesBySubscriber.remove(subscriber);
        }
    }


    /**
     * subscriptions unsubscribe
     *
     * @param subscriber
     */
    private void unSubscribeBySubscriber(Object subscriber) {
        List<Disposable> subscriptions = subscriptionsByEventType.get(subscriber);
        if (subscriptions != null) {
            Iterator<Disposable> iterator = subscriptions.iterator();
            while (iterator.hasNext()) {
                Disposable subscription = iterator.next();
                if (subscription != null && !subscription.isDisposed()) {
                    subscription.dispose();
                    iterator.remove();
                }
            }
            subscriptionsByEventType.remove(subscriber);
        }
    }

    /**
     * 移除subscriber对应的subscriberMethods
     *
     * @param subscriber
     * @param eventType
     */
    private void unSubscribeMethodByEventType(Object subscriber, Class eventType) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods != null && subscriberMethods.size() > 0) {
            Iterator<SubscriberMethod> iterator = subscriberMethods.iterator();
            while (iterator.hasNext()) {
                SubscriberMethod subscriberMethod = iterator.next();
                if (subscriberMethod.subscriber.equals(subscriber)) {
                    iterator.remove();
                }
            }
        }
    }


    /**
     * 移除指定eventType的Sticky事件
     */
    private  <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvent) {
            return eventType.cast(stickyEvent.remove(eventType));
        }
    }

    /**
     * 移除所有的Sticky事件
     */
    private void removeAllStickyEvents() {
        synchronized (stickyEvent) {
            stickyEvent.clear();
        }
    }

}
