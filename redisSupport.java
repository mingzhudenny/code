package com.itutorgroup.dealsupport.common.cachecloud;

import com.itutorgroup.dealsupport.common.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

import java.util.UUID;

@Component
public class VIPCacheCloudSupport{
	private static final Logger logger = LoggerFactory.getLogger(VIPCacheCloudSupport.class);
	@Autowired
	RedisUtil redisUtils;
	
	@Autowired
	Pool<Jedis> jedisPool;
	
	public static String Redis_Success_Msg = "OK";
	public static Long Redis_Success_Code = 1L;
	
	public ThreadLocal<String> ClientThreadLocal = new ThreadLocal<String>();
	
	/*@SuppressWarnings("unchecked")
	public Pool<Jedis> getPool() {
		//return (Pool<Jedis>) ReflectUtils.getFieldValueByName(redisUtils, "jedisPool");
		return jedisPool;
	}*/
	
	public Long hset(String key, String field,String value) {
		Jedis jedis = jedisPool.getResource();
		try {
			Long result = jedis.hset(key, field, value);
			return result;
		} catch (Exception e) {
			logger.error("set|error:{}",e.getMessage(),e);
			return null;
		} finally {
			jedisPool.returnResource(jedis);
		}
	}
	
	public String set(String key, String value, String nxxx, String expx, long time) {
		Jedis jedis = jedisPool.getResource();
		try {
			String result = jedis.set(key, value, nxxx, expx, time);
			return result;
		} catch (Exception e) {
			logger.error("set|error:{}",e.getMessage(),e);
			return null;
		} finally {
			jedisPool.returnResource(jedis);
		}
	}
	
	
	public boolean lock(String lockKey,int milliseconds) {
		String lockValue = UUID.randomUUID().toString().replace("-", "").toUpperCase();
		ClientThreadLocal.set(lockValue);
		String result = set(lockKey, lockValue, "NX", "PX", milliseconds);
		if(StringUtils.equals(result, Redis_Success_Msg)) {
			return true;
		}
		return false;
	}
	
	public boolean unLock(String lockKey) {
		Jedis jedis = jedisPool.getResource();
		try {
			if(jedis.exists(lockKey)) {
				String localValue = ClientThreadLocal.get();
				String lockValue = jedis.get(lockKey);
				if(StringUtils.equals(localValue, lockValue)) {
					Long result = jedis.del(lockKey);
					if(result!=null && result==Redis_Success_Code) {
						return true;
					}
				}
			}
			return false;
		} catch (Exception e) {
			logger.error("unLock|error:{}",e.getMessage(),e);
			return false;
		}finally {
			jedisPool.returnResource(jedis);
		}

	}
}
