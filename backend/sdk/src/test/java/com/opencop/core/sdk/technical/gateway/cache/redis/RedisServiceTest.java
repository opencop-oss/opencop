// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

class RedisServiceTest {

  // @Mock
  // private StringRedisTemplate redisTemplate;
  //
  // @Mock
  // private ValueOperations valueOperations;
  //
  // @Test
  // void writeToCache_Success() {
  // var key = "1";
  // TakEventWrapper event = new TakEventWrapper();
  //
  // MockitoAnnotations.openMocks(this);
  // Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  //
  // CacheService<TakEventWrapper> cacheService = new
  // RedisService<>(redisTemplate);
  //
  // assertDoesNotThrow(
  // () -> cacheService.set("opencop:event:geo:", key, event,
  // Duration.between(Instant.now(), Instant.MAX)));
  // }
  //
  // @Test
  // void getFromCache_Success() throws JsonProcessingException {
  // var key = "1";
  // TakEventWrapper event = new TakEventWrapper();
  //
  // MockitoAnnotations.openMocks(this);
  // Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  // Mockito.when(redisTemplate.opsForValue().get(key))
  // .thenReturn(new ObjectMapper().writeValueAsString(event));
  //
  // CacheService<TakEventWrapper> cacheService = new
  // RedisService<>(redisTemplate);
  //
  // assertDoesNotThrow(() -> cacheService.get("opencop:event:geo:", key,
  // TakEventWrapper.class));
  // }
  //
  // @Test
  // void getFromCache_Deserialisation_Failed() throws JsonProcessingException {
  // var key = "1";
  //
  // MockitoAnnotations.openMocks(this);
  // Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  // Mockito.when(redisTemplate.opsForValue().get(key))
  // .thenReturn(new ObjectMapper().writeValueAsString("1"));
  //
  // CacheService<TakEventWrapper> cacheService = new
  // RedisService<>(redisTemplate);
  //
  // var exception = assertThrows(CacheException.class,
  // () -> cacheService.get("opencop:event:geo:", key, TakEventWrapper.class));
  // assertEquals(CacheExceptionCodes.DESERIALIZATION_FAILED.code(),
  // exception.getCode());
  // assertEquals(CacheExceptionCodes.DESERIALIZATION_FAILED.description(),
  // exception.getMessage());
  // }
  //
  // @Test
  // void getFromCache_ValueDoesNotExist_Failed() {
  // var key = "1";
  //
  // MockitoAnnotations.openMocks(this);
  // Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  // Mockito.when(redisTemplate.opsForValue().get(key))
  // .thenReturn(null);
  //
  // CacheService<TakEventWrapper> cacheService = new
  // RedisService<>(redisTemplate);
  //
  // var exception = assertThrows(CacheException.class,
  // () -> cacheService.get("opencop:event:geo:", key, TakEventWrapper.class));
  // assertEquals(CacheExceptionCodes.VALUE_DOES_NOT_EXIST.code(),
  // exception.getCode());
  // assertEquals(CacheExceptionCodes.VALUE_DOES_NOT_EXIST.description(),
  // exception.getMessage());
  // }
}
