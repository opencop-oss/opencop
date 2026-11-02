// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// package com.opencop.api.connect.mock.gateway;
//
// import static com.opencop.api.connect.technical.gateway.socket.SocketStateEnum.CREATED;
//
// import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
// import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
// import com.opencop.api.connect.technical.gateway.socket.SocketStateEnum;
// import com.opencop.core.sdk.ports.gateway.socket.SocketConfig;
//
// public class SocketConnectorMock implements SocketConnector {
//
//	private ConnectorState socketState = ConnectorState;
//
//	private ConnectBehavior onConnect;
//	private DisconnectBehavior onDisconnect;
//
//	public SocketConnectorMock(ConnectBehavior onConnect, DisconnectBehavior onDisconnect) {
//		this.onConnect = onConnect;
//		this.onDisconnect = onDisconnect;
//	}
//
//	public void setSocketState(SocketStateEnum socketState) {
//		this.socketState = socketState;
//	}
//
//	@Override
//	public SocketStateEnum getSocketState() {
//		return socketState;
//	}
//
//	public void setOnConnect(ConnectBehavior onConnect) {
//		this.onConnect = onConnect;
//	}
//
//	public void setOnDisconnect(DisconnectBehavior onDisconnect) {
//		this.onDisconnect = onDisconnect;
//	}
//
//	@Override
//	public void connect(SocketConfig config) {
//		onConnect.run(this);
//	}
//
//	@Override
//	public boolean disconnect() {
//		return onDisconnect.run(this);
//	}
//
//	@FunctionalInterface
//	public interface ConnectBehavior {
//		void run(SocketConnectorMock mock);
//	}
//
//	@FunctionalInterface
//	public interface DisconnectBehavior {
//		boolean run(SocketConnectorMock mock);
//	}
//
//	public static SocketConnectorMock.ConnectBehavior onConnectBehavior(SocketStateEnum state) {
//		return mock -> mock.setSocketState(state);
//	}
//
//	public static SocketConnectorMock.DisconnectBehavior onDisconnectBehavior(boolean state,
// SocketStateEnum stateEnum) {
//		return mock -> {
//			if (state) {
//				mock.setSocketState(stateEnum);
//			}
//			return state;
//		};
//	}
//
// }
