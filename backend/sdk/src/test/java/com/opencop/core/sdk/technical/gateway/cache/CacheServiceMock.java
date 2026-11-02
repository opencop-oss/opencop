// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// package com.opencop.core.sdk.technical.gateway.cache;
//
// import com.opencop.core.sdk.ports.gateway.cache.CacheException;
// import com.opencop.core.sdk.ports.gateway.cache.CacheService;
// import java.util.List;
//
// public class CacheServiceMock<O> implements CacheService<O> {
//
// private final SetBehavior<O> setBehavior;
// private final GetBehavior<O> getBehavior;
//
// public CacheServiceMock(SetBehavior<O> setBehavior, GetBehavior<O>
// getBehavior) {
// this.setBehavior = setBehavior;
// this.getBehavior = getBehavior;
// }
//
// @Override
// public void set(String fullKey, O value) throws CacheException {
// this.setBehavior.run(this, fullKey);
// }
//
// @Override
// public void set(String keyPrefix, String key, O value) throws CacheException
// {
// this.set(keyPrefix + key, value);
// }
//
// @Override
// public void addToList(String listKey, Object value) throws CacheException {
//
// }
//
// @Override
// public O get(String fullKey, Class<O> clazz) throws CacheException {
// // return this.getBehavior.run(this, key);
// return null;
// }
//
// @Override
// public O get(String keyPrefix, String key, Class<O> clazz) throws
// CacheException {
// // return this.getBehavior.run(this, key);
// return null;
// }
//
// @Override
// public List<String> getRangedEventKeys(String listKey, int start, int range,
// Class<O> clazz) throws CacheException {
// return List.of();
// }
//
// @FunctionalInterface
// public interface SetBehavior<O> {
// void run(CacheServiceMock<O> mock, String key);
// }
//
// @FunctionalInterface
// public interface GetBehavior<O> {
// void run(CacheServiceMock<O> mock, String key);
// }
//
// // public static SendBehavior<OpenCopEvent> onSendBehavior() {
// // return ((mock, event) -> {
// //
// // });
// // }
//
// }
