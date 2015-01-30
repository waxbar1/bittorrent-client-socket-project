package ece454.bittorrent;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Peer and Status are the classes we really care about Peers is a container;
 * feel free to do a different container
 */
public class Peer {
	
	private State currentState;
	private String ipAddress;
	private int port;
	private Socket peerSocket;
	private PrintWriter out;
	
	public Peer(String ip, int port) {
		this.ipAddress = ip;
		this.port = port;
		this.currentState = State.unknown;
	}
	
	// This is the formal interface and you should follow it
	public int insert(String filename) {
		String[][] netMsg = {
				{"command", "insert"},
				{"nodeNum", Integer.toString(Peers.getInstance().getNodeNum())},
				{"filename",filename}
			};
			
		writeToNodes(netMsg);
		return ReturnCodes.ERR_OK;
	}

	public int query() {
		writeToNodes("queryForChunks", Peers.getInstance().getNodeNum());
		return ReturnCodes.ERR_OK;
	}

	/*
	 * Note that we should have the peer list, so it is not needed as a
	 * parameter
	 */
	public int join() {
		writeToNodes("join", Peers.getInstance().getNodeNum());
		return 0;
	}

	public int leave() {
		writeToNodes("leave", Peers.getInstance().getNodeNum());
		return 0;
	}

	/*
	 * TODO: Feel free to hack around with the private data, 
	 * since this is part of your design.
	 * This is intended to provide some exemplars to help; 
	 * ignore it if you don't like it.
	 */
	
	public int writeToNodes(String command, int nodeNum) {
		String[][] netMsg = {
			{"command", command},
			{"nodeNum", Integer.toString(nodeNum)}
		};
		
		out.println(Peers.getInstance().net.encodeNetMsg(netMsg));
		return 0;
	}
	
	public int writeToNodes(String[][] netMsg) {
		out.println(Peers.getInstance().net.encodeNetMsg(netMsg));
		return ReturnCodes.ERR_OK;
	}
	
	public int setConnected() {
		currentState = State.connected;
		return ReturnCodes.ERR_OK;
	}
	
	public boolean isConnected() {
		return peerSocket != null && peerSocket.isConnected() && currentState == State.connected;
	}
	
	public int setDisconnected() {
		currentState = State.disconnected;
		return ReturnCodes.ERR_OK;
	}
	
	public String getIp() {
		return ipAddress;
	}
	
	public int getPort() {
		return port;
	} 
	
	public int setSocket(Socket s) {
		peerSocket = s;
		
		if (s != null) {
			try {
				out = new PrintWriter(peerSocket.getOutputStream(), true);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ReturnCodes.ERR_OK;
	}

	private enum State {
		connected, disconnected, unknown
	};
	
	public Socket getSocket() {
		return this.peerSocket;
	}

	
	

}
