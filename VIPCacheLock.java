package com.itutorgroup.tutorabc.activity.annotation;

import java.lang.annotation.*;

/**
 *   分布式锁注解
 * @author liuwei03
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface VIPCacheLock {
    String key() default "";
    int expireTime() default 0;
    int retry() default 1;
}
