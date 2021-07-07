package com.itutorgroup.vrsservice.common.aspect;

import com.alibaba.fastjson.JSON;
import com.itutorgroup.vrsservice.common.annotation.VIPCacheEvict;
import com.itutorgroup.vrsservice.common.annotation.VIPCachePut;
import com.itutorgroup.vrsservice.common.annotation.VIPCacheable;
import com.itutorgroup.vrsservice.common.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

@Component
@Aspect
public class VIPCacheAspect {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    RedisUtil redis;

    @Around("@annotation(com.itutorgroup.vrsservice.common.annotation.VIPCacheable)")
    public Object cache(ProceedingJoinPoint pjp) throws Throwable {
        Method method = getMethod(pjp);
        VIPCacheable cacheable = method.getAnnotation(VIPCacheable.class);
        LocalVariableTableParameterNameDiscoverer u = new LocalVariableTableParameterNameDiscoverer();
        String[] paraNameArr = u.getParameterNames(method);
        Object[] args = pjp.getArgs();

        //获取方法的返回类型,让缓存可以返回正确的类型
        Class returnType = ((MethodSignature) pjp.getSignature()).getReturnType();
        //获取返回值的类型，返回值只能是一个  
        Type genericReturnType = method.getGenericReturnType();
        //Type[] actualTypeArguments = ((ParameterizedType)genericReturnType).getActualTypeArguments(); 

        //SPEL上下文
        StandardEvaluationContext context = new StandardEvaluationContext();
        ExpressionParser parser = new SpelExpressionParser();
        //把方法参数放入SPEL上下文中
        for (int i = 0; i < paraNameArr.length; i++) {
            context.setVariable(paraNameArr[i], args[i]);
        }
        String key = cacheable.key();
        if (paraNameArr.length > 0) {
            key = parser.parseExpression(cacheable.key()).getValue(context, String.class);
        }
        logger.info("cacheable.key:{}", key);

        //判断返回值是否为VOID
        if (StringUtils.equals("void", genericReturnType.getTypeName())) {
            return pjp.proceed();
        }

        Object result = null;
        try {
            result = redis.get(key);
        } catch (Exception e) {
            logger.error("cacheable.get|error:{}",e.getMessage(),e);
        }
        logger.info("cacheable.value:{}", JSON.toJSONString(result));

        if (result == null) {
            try {
                result = pjp.proceed();
            } catch (Exception e) {
                logger.error("cacheable.proceed|error:{}",e.getMessage(),e);
                throw e;
            }
            if (result != null) {
                try {
                    int expireTime = cacheable.expireSeconds();
                    if (expireTime == 0) {
                        redis.set(key, JSON.toJSONString(result));
                    } else {
                        redis.set(key, JSON.toJSONString(result), expireTime);
                    }
                } catch (Throwable e) {
                    logger.error("cacheable.expire|error:{}",e.getMessage(),e);
                }
            }
        } else {
            result = JSON.parseObject((String) result, genericReturnType);
        }
        return result;
    }

    /**
     * 定义清除缓存逻辑
     *
     * @throws Throwable
     */
    @Around(value = "@annotation(com.itutorgroup.vrsservice.common.annotation.VIPCachePut)")
    public Object put(ProceedingJoinPoint pjp) throws Throwable {
        Method method = getMethod(pjp);
        VIPCachePut cacheable = method.getAnnotation(VIPCachePut.class);
        LocalVariableTableParameterNameDiscoverer u = new LocalVariableTableParameterNameDiscoverer();
        String[] paraNameArr = u.getParameterNames(method);
        Object[] args = pjp.getArgs();

        //获取方法的返回类型,让缓存可以返回正确的类型
        Class returnType = ((MethodSignature) pjp.getSignature()).getReturnType();
        //获取返回值的类型，返回值只能是一个  
        Type genericReturnType = method.getGenericReturnType();
        //Type[] actualTypeArguments = ((ParameterizedType)genericReturnType).getActualTypeArguments(); 

        //SPEL上下文
        StandardEvaluationContext context = new StandardEvaluationContext();
        ExpressionParser parser = new SpelExpressionParser();
        //把方法参数放入SPEL上下文中
        for (int i = 0; i < paraNameArr.length; i++) {
            context.setVariable(paraNameArr[i], args[i]);
        }
        String key = cacheable.key();
        if (paraNameArr.length > 0) {
            key = parser.parseExpression(cacheable.key()).getValue(context, String.class);
        }

        //判断返回值是否为VOID
        if (StringUtils.equals("void", genericReturnType.getTypeName())) {
            return pjp.proceed();
        }

        Object result = null;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            logger.error("cacheable.proceed|error:{}",e.getMessage(),e);
            throw e;
        }

        //尝试将返回值放入缓存
        try {
            if (null != result) {
                int expireTime = cacheable.expireSeconds();
                if (expireTime == 0) {
                    redis.set(key, JSON.toJSONString(result));
                } else {
                    redis.set(key, JSON.toJSONString(result), expireTime);
                }
            }
        } catch (Exception e) {
            logger.error("cacheable.expire|error:{}",e.getMessage(),e);
        }
        return result;
    }

    /**
     * 定义清除缓存逻辑
     *
     * @throws Throwable
     */
    @Around(value = "@annotation(com.itutorgroup.vrsservice.common.annotation.VIPCacheEvict)")
    public Object evict(ProceedingJoinPoint pjp) throws Throwable {
        Method method = getMethod(pjp);
        VIPCacheEvict cacheable = method.getAnnotation(VIPCacheEvict.class);
        try {
            LocalVariableTableParameterNameDiscoverer u = new LocalVariableTableParameterNameDiscoverer();
            String[] paraNameArr = u.getParameterNames(method);
            Object[] args = pjp.getArgs();

            //获取方法的返回类型,让缓存可以返回正确的类型
            Class returnType = ((MethodSignature) pjp.getSignature()).getReturnType();
            //获取返回值的类型，返回值只能是一个  
            Type genericReturnType = method.getGenericReturnType();
            //Type[] actualTypeArguments = ((ParameterizedType)genericReturnType).getActualTypeArguments(); 

            //SPEL上下文
            StandardEvaluationContext context = new StandardEvaluationContext();
            ExpressionParser parser = new SpelExpressionParser();
            //把方法参数放入SPEL上下文中
            for (int i = 0; i < paraNameArr.length; i++) {
                context.setVariable(paraNameArr[i], args[i]);
            }
            String[] keys = cacheable.key();
            boolean clearAll = cacheable.allEntries();
            if (paraNameArr.length > 0 && !clearAll) {
                for (int i = 0; i< keys.length; i++) {
                    keys[i] = parser.parseExpression(keys[i]).getValue(context, String.class);
                }
            }

            //判断返回值是否为VOID
            if (StringUtils.equals("void", genericReturnType.getTypeName())) {
                return pjp.proceed();
            }
            //存在命名空间的使用redis 的hash进行存取，易于管理
            for (String key : keys) {
                redis.del(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pjp.proceed();
    }

    /**
     * 获取被拦截方法对象
     * <p>
     * MethodSignature.getMethod() 获取的是顶层接口或者父类的方法对象
     * 而缓存的注解在实现类的方法上
     * 所以应该使用反射获取当前对象的方法对象
     */
    private Method getMethod(ProceedingJoinPoint pjp) {
        Method method = null;

        Signature signature = pjp.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method targetMethod = methodSignature.getMethod();

        try {
            method = pjp.getTarget().getClass().getMethod(pjp.getSignature().getName(), targetMethod.getParameterTypes());
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return method;
    }

    /**
     * 获取缓存的key
     * key 定义在注解上，支持SPEL表达式
     *
     * @param
     * @return
     */
    private String parseStringArg(String key, Method method, Object[] args) {
        //获取被拦截方法参数名列表(使用Spring支持类库)
        LocalVariableTableParameterNameDiscoverer u = new LocalVariableTableParameterNameDiscoverer();
        String[] paraNameArr = u.getParameterNames(method);

        //使用SPEL进行key的解析
        ExpressionParser parser = new SpelExpressionParser();
        //SPEL上下文
        StandardEvaluationContext context = new StandardEvaluationContext();

        //把方法参数放入SPEL上下文中
        for (int i = 0; i < paraNameArr.length; i++) {
            context.setVariable(paraNameArr[i], args[i]);
        }
        return parser.parseExpression(key).getValue(context, String.class);
    }
}
