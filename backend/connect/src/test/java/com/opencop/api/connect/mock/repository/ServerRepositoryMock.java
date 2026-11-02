// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// package com.opencop.api.connect.mock.repository;
//
// import static org.junit.jupiter.api.Assertions.assertEquals;
//
// import com.opencop.api.connect.business.entity.TakConnectionEntity;
// import com.opencop.api.connect.ports.gateway.repository.ServerRepository;
// import java.util.List;
// import java.util.UUID;
//
// public class ServerRepositoryMock<I> implements ServerRepository<I> {
//
//	private UpdateFlagBehavior onUpdateFlag;
//	private RetrieveInfoBehavior<I> onRetrieve;
//
//	public ServerRepositoryMock(RetrieveInfoBehavior<I> onRetrieve) {
//		this.onRetrieve = onRetrieve;
//	}
//
//	public void setOnUpdateFlag(UpdateFlagBehavior onUpdateFlag) {
//		this.onUpdateFlag = onUpdateFlag;
//	}
//
//	public void setOnRetrieve(RetrieveInfoBehavior<I> onRetrieve) {
//		this.onRetrieve = onRetrieve;
//	}
//
//	@Override
//	public void save(Object input) {
//		UUID.randomUUID();
//	}
//
//	@Override
//	public UUID create(TakConnectionEntity input) {
//		return UUID.randomUUID();
//	}
//
//	@Override
//	public boolean exists(UUID id) {
//		return false;
//	}
//
//	@Override
//	public boolean serverNameExists(String serverName) {
//		return false;
//	}
//
//	@Override
//	public boolean serverCertificateIdExists(String certificateId) {
//		return false;
//	}
//
//	@Override
//	public I getByCertificateId(String certificateId) {
//		return null;
//	}
//
//	@Override
//	public void updateConnectionFlag(UUID id, boolean flag) {
//		this.onUpdateFlag.run(id, flag);
//	}
//
//	@Override
//	public void delete(UUID id) {
//
//	}
//
//	@Override
//	public I retrieveServerInfo(UUID id) {
//		return onRetrieve.run(id);
//	}
//
//	@Override
//	public List<I> retrieveAllServerInfo() {
//		return List.of();
//	}
//
//	@FunctionalInterface
//	public interface UpdateFlagBehavior {
//		void run(UUID id, boolean old);
//	}
//
//	@FunctionalInterface
//	public interface RetrieveInfoBehavior<I> {
//		I run(UUID input);
//	}
//
//	public static UpdateFlagBehavior onUpdateFlag(UUID id, boolean expectation) {
//		return (sourceId, newValue) -> {
//			assertEquals(expectation, newValue);
//			assertEquals(id, sourceId);
//		};
//	}
//
//	public static RetrieveInfoBehavior<TakConnectionEntity> onRetrieveBehavior(UUID id) {
//		return mock -> new TakConnectionEntity.Builder()
//				.withId(id)
//				.withServerName("delta")
//				.withCredentialsStrategy("zip")
//				.withCloudProvider("azure")
//				.build();
//	}
//
// }
